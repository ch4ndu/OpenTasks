package com.udnahc.opentasks.data.attachment

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.os.Build
import androidx.exifinterface.media.ExifInterface
import com.udnahc.opentasks.data.extensions.uuid4
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PlatformAttachmentFileStorage(
    context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : AttachmentFileStorage {
    private val directory = File(context.filesDir, "attachments/images")

    override suspend fun storePickedImage(image: PickedImage): StoredAttachmentFile =
        storeImage(image.fileName, image.bytes)

    override suspend fun storeRemoteImage(fileName: String, bytes: ByteArray): StoredAttachmentFile =
        storeImage(fileName, bytes)

    override suspend fun readBytes(path: String, maxBytes: Long): ByteArray? =
        withContext(ioDispatcher) { File(path).readBoundedBytes(maxBytes) }

    override suspend fun exists(path: String): Boolean =
        withContext(ioDispatcher) { File(path).isFile }

    override suspend fun delete(path: String) {
        withContext(ioDispatcher) { File(path).delete() }
    }

    override suspend fun clearAll() {
        withContext(ioDispatcher) { directory.deleteRecursively() }
    }

    private suspend fun storeImage(fileName: String, bytes: ByteArray): StoredAttachmentFile =
        withContext(ioDispatcher) {
            directory.mkdirs()
            val original = decodeOrientedBitmap(bytes)
                ?: throw AttachmentImageDecodeException()
            val optimized = original.scaleAndEncode(AttachmentFilePolicy.MAX_LONG_EDGE)
            val thumb = original.scaleAndEncode(AttachmentFilePolicy.THUMBNAIL_LONG_EDGE)
            val id = uuid4()
            val localFile = File(directory, "$id.${optimized.extension}")
            val thumbFile = File(directory, "${id}_thumb.${thumb.extension}")
            localFile.writeBytes(optimized.bytes)
            thumbFile.writeBytes(thumb.bytes)
            original.recycle()
            StoredAttachmentFile(
                localPath = localFile.absolutePath,
                thumbnailPath = thumbFile.absolutePath,
                fileName = localFile.name,
                mimeType = optimized.mimeType,
                fileSizeBytes = optimized.bytes.size.toLong(),
                width = optimized.width,
                height = optimized.height,
            )
        }

    private fun File.readBoundedBytes(maxBytes: Long): ByteArray? {
        if (!isFile) return null
        require(maxBytes >= 0L) { "Attachment byte limit must not be negative" }
        inputStream().use { input ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var total = 0L
            while (total <= maxBytes) {
                val remaining = (maxBytes + 1L - total).coerceAtMost(buffer.size.toLong()).toInt()
                val read = input.read(buffer, 0, remaining)
                if (read < 0) break
                if (read == 0) continue
                total += read
                if (total > maxBytes) throw AttachmentFileTooLargeException(maxBytes)
                output.write(buffer, 0, read)
            }
            return output.toByteArray()
        }
    }

    private fun decodeOrientedBitmap(bytes: ByteArray): Bitmap? {
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
        val orientation = runCatching {
            ExifInterface(ByteArrayInputStream(bytes)).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL,
            )
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.preScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.preScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f)
                matrix.preScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(-90f)
                matrix.preScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(-90f)
            else -> return decoded
        }
        return runCatching {
            Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
        }.getOrNull()?.also { oriented ->
            if (oriented !== decoded) decoded.recycle()
        } ?: decoded
    }

    private fun Bitmap.scaleAndEncode(longEdge: Int): Encoded {
        val maxDimension = maxOf(width, height)
        val scaled = if (maxDimension > longEdge) {
            val ratio = longEdge.toFloat() / maxDimension.toFloat()
            Bitmap.createScaledBitmap(this, (width * ratio).toInt(), (height * ratio).toInt(), true)
        } else {
            this
        }
        val format = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Bitmap.CompressFormat.WEBP_LOSSY
        } else {
            Bitmap.CompressFormat.JPEG
        }
        val extension = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) "webp" else "jpg"
        val mimeType = if (extension == "webp") "image/webp" else "image/jpeg"
        val output = ByteArrayOutputStream()
        scaled.compress(format, AttachmentFilePolicy.QUALITY, output)
        val scaledWidth = scaled.width
        val scaledHeight = scaled.height
        if (scaled !== this) scaled.recycle()
        return Encoded(output.toByteArray(), extension, mimeType, scaledWidth, scaledHeight)
    }

    private data class Encoded(
        val bytes: ByteArray,
        val extension: String,
        val mimeType: String,
        val width: Int,
        val height: Int,
    )
}
