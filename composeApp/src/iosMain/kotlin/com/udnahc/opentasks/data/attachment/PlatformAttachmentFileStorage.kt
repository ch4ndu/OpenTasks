package com.udnahc.opentasks.data.attachment

import com.udnahc.opentasks.data.extensions.uuid4
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.cValuesOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.CoreFoundation.CFDataCreate
import platform.CoreFoundation.CFDataCreateMutable
import platform.CoreFoundation.CFDataGetBytePtr
import platform.CoreFoundation.CFDataGetLength
import platform.CoreFoundation.CFDataRef
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFDictionaryRef
import platform.CoreFoundation.CFDictionarySetValue
import platform.CoreFoundation.CFNumberCreate
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.kCFNumberDoubleType
import platform.CoreFoundation.kCFNumberIntType
import platform.CoreFoundation.kCFBooleanTrue
import platform.CoreGraphics.CGImageGetHeight
import platform.CoreGraphics.CGImageGetWidth
import platform.CoreServices.kUTTypeJPEG
import platform.Foundation.NSData
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask
import platform.Foundation.create
import platform.Foundation.writeToFile
import platform.ImageIO.CGImageDestinationAddImage
import platform.ImageIO.CGImageDestinationCreateWithData
import platform.ImageIO.CGImageDestinationFinalize
import platform.ImageIO.CGImageSourceCreateThumbnailAtIndex
import platform.ImageIO.CGImageSourceCreateWithData
import platform.ImageIO.kCGImageDestinationLossyCompressionQuality
import platform.ImageIO.kCGImageSourceCreateThumbnailFromImageAlways
import platform.ImageIO.kCGImageSourceCreateThumbnailWithTransform
import platform.ImageIO.kCGImageSourceThumbnailMaxPixelSize

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
class PlatformAttachmentFileStorage(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : AttachmentFileStorage {
    private val directory: String by lazy {
        val documentDirectory = NSFileManager.defaultManager
            .URLsForDirectory(NSDocumentDirectory, NSUserDomainMask)
            .firstOrNull() as? NSURL
        "${documentDirectory?.path ?: ""}/attachments/images"
    }

    override suspend fun storePickedImage(image: PickedImage): StoredAttachmentFile =
        storeBytes(image.fileName, image.bytes)

    override suspend fun storeRemoteImage(fileName: String, bytes: ByteArray): StoredAttachmentFile =
        storeBytes(fileName, bytes)

    override suspend fun readBytes(path: String): ByteArray? =
        withContext(ioDispatcher) {
            val data = NSData.create(contentsOfFile = path) ?: return@withContext null
            val bytes = data.bytes ?: return@withContext null
            bytes.reinterpret<ByteVar>().readBytes(data.length.toInt())
        }

    override suspend fun exists(path: String): Boolean =
        NSFileManager.defaultManager.fileExistsAtPath(path)

    override suspend fun delete(path: String) {
        NSFileManager.defaultManager.removeItemAtPath(path, null)
    }

    override suspend fun clearAll() {
        NSFileManager.defaultManager.removeItemAtPath(directory, null)
    }

    private suspend fun storeBytes(fileName: String, bytes: ByteArray): StoredAttachmentFile =
        withContext(ioDispatcher) {
            val fileManager = NSFileManager.defaultManager
            val directoryReady = fileManager.createDirectoryAtPath(directory, true, null, null) ||
                fileManager.fileExistsAtPath(directory)
            if (!directoryReady) {
                throw IllegalStateException("Attachment directory creation failed")
            }
            val optimized = bytes.scaleAndEncode(AttachmentFilePolicy.MAX_LONG_EDGE)
            val thumbnail = bytes.scaleAndEncode(AttachmentFilePolicy.THUMBNAIL_LONG_EDGE)
            val localFileName = "${uuid4()}.jpg"
            val thumbFileName = "${localFileName.substringBeforeLast(".")}_thumb.jpg"
            val localPath = "$directory/$localFileName"
            val thumbnailPath = "$directory/$thumbFileName"
            if (!optimized.bytes.toNSData().writeToFile(localPath, true)) {
                throw IllegalStateException("Attachment image write failed")
            }
            if (!thumbnail.bytes.toNSData().writeToFile(thumbnailPath, true)) {
                fileManager.removeItemAtPath(localPath, null)
                throw IllegalStateException("Attachment thumbnail write failed")
            }
            StoredAttachmentFile(
                localPath = localPath,
                thumbnailPath = thumbnailPath,
                fileName = localFileName,
                mimeType = JPEG_MIME_TYPE,
                fileSizeBytes = optimized.bytes.size.toLong(),
                width = optimized.width,
                height = optimized.height,
            )
        }

    private fun ByteArray.scaleAndEncode(longEdge: Int): EncodedImage {
        val sourceData = toCFData()
            ?: throw AttachmentImageDecodeException()
        var source: platform.ImageIO.CGImageSourceRef? = null
        var thumbnail: platform.CoreGraphics.CGImageRef? = null
        try {
            source = CGImageSourceCreateWithData(sourceData, null)
                ?: throw AttachmentImageDecodeException()
            thumbnail = withThumbnailOptions(longEdge) { options ->
                CGImageSourceCreateThumbnailAtIndex(source, 0u, options)
            } ?: throw AttachmentImageDecodeException()
            val encodedBytes = encodeJpeg(thumbnail)
            return EncodedImage(
                bytes = encodedBytes,
                width = CGImageGetWidth(thumbnail).toInt(),
                height = CGImageGetHeight(thumbnail).toInt(),
            )
        } finally {
            thumbnail?.let { CFRelease(it) }
            source?.let { CFRelease(it) }
            CFRelease(sourceData)
        }
    }

    private fun ByteArray.toCFData(): CFDataRef? =
        usePinned { pinned ->
            CFDataCreate(
                null,
                pinned.addressOf(0).reinterpret(),
                size.convert(),
            )
        }

    private fun encodeJpeg(image: platform.CoreGraphics.CGImageRef): ByteArray {
        val encodedData = CFDataCreateMutable(null, 0)
            ?: throw IllegalArgumentException("Attachment image encode failed")
        var destination: platform.ImageIO.CGImageDestinationRef? = null
        try {
            destination = CGImageDestinationCreateWithData(encodedData, kUTTypeJPEG, 1u, null)
                ?: throw IllegalArgumentException("Attachment image encode failed")
            withJpegEncodeOptions { options ->
                CGImageDestinationAddImage(destination, image, options)
                if (!CGImageDestinationFinalize(destination)) {
                    throw IllegalArgumentException("Attachment image encode failed")
                }
            }
            return encodedData.toByteArray()
        } finally {
            destination?.let { CFRelease(it) }
            CFRelease(encodedData)
        }
    }

    private inline fun <T> withThumbnailOptions(longEdge: Int, block: (CFDictionaryRef?) -> T): T = memScoped {
        val options = CFDictionaryCreateMutable(null, 0, null, null)
            ?: return@memScoped block(null)
        val maxPixel = CFNumberCreate(null, kCFNumberIntType, cValuesOf(longEdge))
        if (maxPixel != null) {
            CFDictionarySetValue(options, kCGImageSourceThumbnailMaxPixelSize, maxPixel)
        }
        CFDictionarySetValue(options, kCGImageSourceCreateThumbnailFromImageAlways, kCFBooleanTrue)
        CFDictionarySetValue(options, kCGImageSourceCreateThumbnailWithTransform, kCFBooleanTrue)
        try {
            block(options)
        } finally {
            maxPixel?.let { CFRelease(it) }
            CFRelease(options)
        }
    }

    private inline fun <T> withJpegEncodeOptions(block: (CFDictionaryRef?) -> T): T = memScoped {
        val options = CFDictionaryCreateMutable(null, 0, null, null)
            ?: return@memScoped block(null)
        val qualityNumber = CFNumberCreate(null, kCFNumberDoubleType, cValuesOf(JPEG_QUALITY))
        if (qualityNumber != null) {
            CFDictionarySetValue(options, kCGImageDestinationLossyCompressionQuality, qualityNumber)
        }
        try {
            block(options)
        } finally {
            qualityNumber?.let { CFRelease(it) }
            CFRelease(options)
        }
    }

    private fun ByteArray.toNSData(): NSData =
        usePinned { pinned ->
            NSData.create(
                bytes = pinned.addressOf(0),
                length = size.convert(),
            )
        }

    private fun CFDataRef.toByteArray(): ByteArray {
        val length = CFDataGetLength(this).toInt()
        val rawBytes = CFDataGetBytePtr(this)
            ?: throw IllegalArgumentException("Attachment image encode failed")
        return rawBytes.readBytes(length)
    }

    private fun NSData.toByteArray(): ByteArray? {
        val rawBytes = bytes ?: return null
        return rawBytes.reinterpret<ByteVar>().readBytes(length.toInt())
    }

    private data class EncodedImage(
        val bytes: ByteArray,
        val width: Int,
        val height: Int,
    )

    private companion object {
        const val JPEG_MIME_TYPE = "image/jpeg"
        const val JPEG_QUALITY = 0.8
    }
}
