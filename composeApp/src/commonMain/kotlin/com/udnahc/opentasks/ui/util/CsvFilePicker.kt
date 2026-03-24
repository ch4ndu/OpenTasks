package com.udnahc.opentasks.ui.util

/**
 * Opens a native file picker dialog filtered to .csv files.
 * Returns a pair of (fileName, fileContent) or null if cancelled.
 * Only functional on JVM Desktop.
 */
expect suspend fun pickCsvFileContent(): Pair<String, String>?
