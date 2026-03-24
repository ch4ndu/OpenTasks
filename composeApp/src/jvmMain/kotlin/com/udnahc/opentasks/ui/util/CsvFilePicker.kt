package com.udnahc.opentasks.ui.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.FileDialog
import java.io.File
import java.io.FilenameFilter
import javax.swing.SwingUtilities

actual suspend fun pickCsvFileContent(): Pair<String, String>? = withContext(Dispatchers.IO) {
    var result: Pair<String, String>? = null
    SwingUtilities.invokeAndWait {
        val dialog = FileDialog(null as java.awt.Frame?, "Select .csv file", FileDialog.LOAD)
        dialog.filenameFilter = FilenameFilter { _, name -> name.endsWith(".csv", ignoreCase = true) }
        dialog.isVisible = true
        val fileName = dialog.file
        val directory = dialog.directory
        if (fileName != null && directory != null) {
            val file = File(directory, fileName)
            if (file.exists()) {
                result = fileName to file.readText()
            }
        }
    }
    result
}
