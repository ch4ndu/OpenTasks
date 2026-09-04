package com.udnahc.opentasks.viewmodel

enum class ImportErrorType {
    GENERIC,
    FILE_TOO_LARGE,
    EMPTY_CSV_FILE,
    EMPTY_ICS_FILE,
    CALENDAR_ACCESS_DENIED,
    CALENDAR_TRANSPORT,
    CALENDAR_INVALID_RESPONSE,
    CALENDAR_TOO_MANY_EVENTS,
}

data class ImportErrorState(
    val type: ImportErrorType,
    val detail: String? = null,
)
