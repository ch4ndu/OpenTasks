package com.udnahc.opentasks.ui.util

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.lighthousegames.logging.logging

private val log = logging("AndroidFileSaver")

class AndroidFileSaver(private val context: Context) : FileSaver {

    override suspend fun save(fileName: String, content: String, mimeType: String): Boolean =
        withContext(Dispatchers.IO) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    saveWithMediaStore(fileName, content, mimeType)
                } else {
                    saveToDownloadsLegacy(fileName, content)
                }
            } catch (e: Exception) {
                log.e(e) { "Failed to save file $fileName" }
                false
            }
        }

    private fun saveWithMediaStore(fileName: String, content: String, mimeType: String): Boolean {
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(MediaStore.Downloads.MIME_TYPE, mimeType)
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = context.contentResolver.insert(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI, values
        ) ?: return false
        context.contentResolver.openOutputStream(uri)?.use { it.write(content.toByteArray()) }
        values.clear()
        values.put(MediaStore.Downloads.IS_PENDING, 0)
        context.contentResolver.update(uri, values, null, null)
        return true
    }

    @Suppress("DEPRECATION")
    private fun saveToDownloadsLegacy(fileName: String, content: String): Boolean {
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (!downloadsDir.exists()) downloadsDir.mkdirs()
        java.io.File(downloadsDir, fileName).writeText(content)
        return true
    }
}
