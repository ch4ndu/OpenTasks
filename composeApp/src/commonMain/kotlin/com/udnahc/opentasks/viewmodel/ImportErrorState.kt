package com.udnahc.opentasks.viewmodel

enum class ImportErrorType {
    GENERIC,
    EMPTY_CSV_FILE,
    EMPTY_ICS_FILE,
}

data class ImportErrorState(
    val type: ImportErrorType,
    val detail: String? = null,
)
