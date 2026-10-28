package com.udnahc.opentasks.data.model

enum class UiExperience {
    CLASSIC, REIMAGINED;

    companion object {
        fun fromString(value: String?): UiExperience = entries.firstOrNull { it.name == value } ?: CLASSIC
    }
}
