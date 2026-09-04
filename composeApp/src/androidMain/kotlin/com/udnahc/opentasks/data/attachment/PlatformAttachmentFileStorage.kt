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
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.roundToInt

class PlatformAttachmentFileStorage(
    context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val leaseRecorder: AttachmentFileLeaseRecorder? = null,
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
        try {
            withContext(ioDispatcher) {
                if (directory.exists() && !directory.deleteRecursively()) {
                    throw AttachmentFileOperationException()
                }
                if (directory.exists()) throw AttachmentFileOperationException()
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            throw AttachmentFileOperationException()
        }
    }

    private suspend fun storeImage(fileName: String, bytes: ByteArray): StoredAttachmentFile {
        var leasedPaths = emptyList<String>()
        try {
            return withContext(ioDispatcher) {
                if (bytes.size.toLong() > AttachmentFilePolicy.MAX_SOURCE_BYTES) {
                    throw AttachmentFileTooLargeException(AttachmentFilePolicy.MAX_SOURCE_BYTES)
                }
                val original = decodeOrientedBitmap(bytes)
                val encoded = try {
                    OptimizedPair(
                        fullSize = original.scaleAndEncode(AttachmentFilePolicy.MAX_LONG_EDGE),
                        thumbnail = original.scaleAndEncode(AttachmentFilePolicy.THUMBNAIL_LONG_EDGE),
                    )
                } finally {
                    original.recycle()
                }
                currentCoroutineContext().ensureActive()
                if ((!directory.exists() && !directory.mkdirs()) || !directory.isDirectory) {
                    throw AttachmentFileOperationException()
                }
                val id = uuid4()
                val localFile = File(directory, "$id.${encoded.fullSize.extension}")
                val thumbFile = File(directory, "${id}_thumb.${encoded.thumbnail.extension}")
                if (localFile.exists() || thumbFile.exists()) throw AttachmentFileOperationException()
                leasedPaths = listOf(localFile.absolutePath, thumbFile.absolutePath)
                leaseRecorder?.lease(leasedPaths)
                currentCoroutineContext().ensureActive()
                localFile.writeBytes(encoded.fullSize.bytes)
                if (!localFile.isFile || localFile.length() != encoded.fullSize.bytes.size.toLong()) {
                    throw AttachmentFileOperationException()
                }
                thumbFile.writeBytes(encoded.thumbnail.bytes)
                if (!thumbFile.isFile || thumbFile.length() != encoded.thumbnail.bytes.size.toLong()) {
                    throw AttachmentFileOperationException()
                }
                StoredAttachmentFile(
                    localPath = localFile.absolutePath,
                    thumbnailPath = thumbFile.absolutePath,
                    fileName = localFile.name,
                    mimeType = encoded.fullSize.mimeType,
                    fileSizeBytes = encoded.fullSize.bytes.size.toLong(),
                    width = encoded.fullSize.width,
                    height = encoded.fullSize.height,
                )
            }
        } catch (error: CancellationException) {
            compensateFailedStore(leasedPaths)
            throw error
        } catch (error: Exception) {
            compensateFailedStore(leasedPaths)
            throw error.asAttachmentStorageFailure()
        }
    }

    private suspend fun compensateFailedStore(paths: List<String>) {
        withContext(NonCancellable + ioDispatcher) {
            for (path in paths) {
                val file = File(path)
                val absent = try {
                    file.delete()
                    !file.exists()
                } catch (_: Exception) {
                    false
                }
                if (absent) {
                    try {
                        leaseRecorder?.release(path)
                    } catch (_: Exception) {
                        // The durable entry remains available for a later retry.
                    }
                }
            }
        }
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

    private fun decodeOrientedBitmap(bytes: ByteArray): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (!AttachmentFilePolicy.acceptsSourceDimensions(bounds.outWidth, bounds.outHeight)) {
            throw AttachmentImageDecodeException()
        }
        val options = BitmapFactory.Options().apply {
            inSampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight)
        }
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
            ?: throw AttachmentImageDecodeException()
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
        return try {
            Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
                .also { oriented ->
                    if (oriented !== decoded) decoded.recycle()
                }
        } catch (_: Exception) {
            decoded.recycle()
            throw AttachmentImageDecodeException()
        }
    }

    private fun calculateInSampleSize(width: Int, height: Int): Int {
        val maxDimension = maxOf(width, height)
        var sampleSize = 1
        while (maxDimension / (sampleSize * 2) >= AttachmentFilePolicy.MAX_LONG_EDGE) {
            sampleSize *= 2
        }
        return sampleSize
    }

    private fun Bitmap.scaleAndEncode(longEdge: Int): Encoded {
        val maxDimension = maxOf(width, height)
        val scaled = if (maxDimension > longEdge) {
            val ratio = longEdge.toFloat() / maxDimension.toFloat()
            Bitmap.createScaledBitmap(
                this,
                (width * ratio).roundToInt().coerceAtLeast(1),
                (height * ratio).roundToInt().coerceAtLeast(1),
                true,
            )
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
        return try {
            val output = ByteArrayOutputStream()
            if (!scaled.compress(format, AttachmentFilePolicy.QUALITY, output)) {
                throw AttachmentImageDecodeException()
            }
            Encoded(output.toByteArray(), extension, mimeType, scaled.width, scaled.height)
        } finally {
            if (scaled !== this) scaled.recycle()
        }
    }

    private data class Encoded(
        val bytes: ByteArray,
        val extension: String,
        val mimeType: String,
        val width: Int,
        val height: Int,
    )

    private data class OptimizedPair(
        val fullSize: Encoded,
        val thumbnail: Encoded,
    )

    private fun Exception.asAttachmentStorageFailure(): Exception = when (this) {
        is AttachmentFileOperationException,
        is AttachmentFileTooLargeException,
        is AttachmentImageDecodeException,
        -> this
        else -> AttachmentFileOperationException()
    }
}
