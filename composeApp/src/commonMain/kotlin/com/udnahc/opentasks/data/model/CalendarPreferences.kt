package com.udnahc.opentasks.data.model

enum class CalendarViewPreference {
    LIST,
    YEAR,
    MONTH,
    WEEK,
    THREE_DAY,
    DAY;

    companion object {
        fun fromString(value: String?): CalendarViewPreference =
            entries.find { it.name == value } ?: MONTH
    }
}

enum class CalendarListDisplayModePreference {
    TIMELINE,
    CARD;

    companion object {
        fun fromString(value: String?): CalendarListDisplayModePreference =
            entries.find { it.name == value } ?: TIMELINE
    }
}
