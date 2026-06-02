package com.udnahc.opentasks.ui.util

interface FileSaver {
    suspend fun save(
        fileName: String,
        content: String,
        mimeType: String
    ): Boolean
}
