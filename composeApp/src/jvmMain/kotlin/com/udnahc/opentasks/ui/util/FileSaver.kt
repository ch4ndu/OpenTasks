package com.udnahc.opentasks.ui.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.FileDialog
import java.io.File
import javax.swing.SwingUtilities

class JvmFileSaver : FileSaver {

    override suspend fun save(fileName: String, content: String, mimeType: String): Boolean =
        withContext(Dispatchers.IO) {
            var result = false
            SwingUtilities.invokeAndWait {
                val dialog = FileDialog(null as java.awt.Frame?, "Save export file", FileDialog.SAVE)
                dialog.file = fileName
                dialog.isVisible = true
                val selectedFile = dialog.file
                val directory = dialog.directory
                if (selectedFile != null && directory != null) {
                    val file = File(directory, selectedFile)
                    file.writeText(content)
                    result = true
                }
            }
            result
        }
}
