package com.udnahc.opentasks.data.model

enum class TextSizePreference(val scale: Float) {
    SMALL(1.0f),
    MEDIUM(1.2f),
    LARGE(1.44f);

    companion object {
        fun fromString(value: String?): TextSizePreference =
            entries.find { it.name == value } ?: SMALL
    }
}
