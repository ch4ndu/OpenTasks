package com.udnahc.opentasks.data.attachment

import com.udnahc.opentasks.data.extensions.uuid4
import java.io.File
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.SamplingMode
import org.jetbrains.skia.impl.use

class PlatformAttachmentFileStorage(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : AttachmentFileStorage {
    private val directory = File(System.getProperty("user.home"), ".opentasks/attachments/images")

    override suspend fun storePickedImage(image: PickedImage): StoredAttachmentFile =
        storeBytes(image.bytes)

    override suspend fun storeRemoteImage(fileName: String, bytes: ByteArray): StoredAttachmentFile =
        storeBytes(bytes)

    override suspend fun readBytes(path: String): ByteArray? =
        withContext(ioDispatcher) { File(path).takeIf { it.isFile }?.readBytes() }

    override suspend fun exists(path: String): Boolean =
        withContext(ioDispatcher) { File(path).isFile }

    override suspend fun delete(path: String) {
        withContext(ioDispatcher) { File(path).delete() }
    }

    override suspend fun clearAll() {
        withContext(ioDispatcher) { directory.deleteRecursively() }
    }

    private suspend fun storeBytes(bytes: ByteArray): StoredAttachmentFile =
        withContext(ioDispatcher) {
            directory.mkdirs()
            val original = runCatching { Image.makeFromEncoded(bytes) }
                .getOrElse { throw AttachmentImageDecodeException() }
            val optimized = original.use {
                val optimized = it.scaleAndEncode(AttachmentFilePolicy.MAX_LONG_EDGE)
                val thumbnail = it.scaleAndEncode(AttachmentFilePolicy.THUMBNAIL_LONG_EDGE)
                OptimizedPair(optimized, thumbnail)
            }
            val id = uuid4()
            val localFile = File(directory, "$id.${optimized.fullSize.extension}")
            val thumbFile = File(directory, "${id}_thumb.${optimized.thumbnail.extension}")
            localFile.writeBytes(optimized.fullSize.bytes)
            thumbFile.writeBytes(optimized.thumbnail.bytes)
            StoredAttachmentFile(
                localPath = localFile.absolutePath,
                thumbnailPath = thumbFile.absolutePath,
                fileName = localFile.name,
                mimeType = optimized.fullSize.mimeType,
                fileSizeBytes = optimized.fullSize.bytes.size.toLong(),
                width = optimized.fullSize.width,
                height = optimized.fullSize.height,
            )
        }

    private fun Image.scaleAndEncode(longEdge: Int): Encoded {
        val width = imageInfo.width
        val height = imageInfo.height
        val maxDimension = maxOf(width, height)
        val targetWidth: Int
        val targetHeight: Int
        if (maxDimension > longEdge) {
            val ratio = longEdge.toFloat() / maxDimension.toFloat()
            targetWidth = (width * ratio).roundToInt().coerceAtLeast(1)
            targetHeight = (height * ratio).roundToInt().coerceAtLeast(1)
        } else {
            targetWidth = width
            targetHeight = height
        }
        if (targetWidth == width && targetHeight == height) {
            return encodePreferred(width, height)
        }
        val scaled = resize(targetWidth, targetHeight)
        return scaled.use { it.encodePreferred(targetWidth, targetHeight) }
    }

    private fun Image.resize(width: Int, height: Int): Image {
        val bitmap = Bitmap()
        try {
            val imageInfo = ImageInfo.makeN32Premul(width, height)
            if (!bitmap.allocPixels(imageInfo)) {
                throw IllegalArgumentException("Attachment image resize failed")
            }
            val pixmap = bitmap.peekPixels()
                ?: throw IllegalArgumentException("Attachment image resize failed")
            if (!scalePixels(pixmap, SamplingMode.LINEAR, true)) {
                throw IllegalArgumentException("Attachment image resize failed")
            }
            return Image.makeFromBitmap(bitmap)
        } finally {
            bitmap.close()
        }
    }

    private fun Image.encodePreferred(width: Int, height: Int): Encoded {
        encodeToData(EncodedImageFormat.WEBP, AttachmentFilePolicy.QUALITY)?.use { data ->
            return Encoded(
                bytes = data.bytes,
                extension = "webp",
                mimeType = "image/webp",
                width = width,
                height = height,
            )
        }
        encodeToData(EncodedImageFormat.JPEG, AttachmentFilePolicy.QUALITY)?.use { data ->
            return Encoded(
                bytes = data.bytes,
                extension = "jpg",
                mimeType = "image/jpeg",
                width = width,
                height = height,
            )
        }
        throw IllegalArgumentException("Attachment image encode failed")
    }

    private data class OptimizedPair(
        val fullSize: Encoded,
        val thumbnail: Encoded,
    )

    private data class Encoded(
        val bytes: ByteArray,
        val extension: String,
        val mimeType: String,
        val width: Int,
        val height: Int,
    )
}
