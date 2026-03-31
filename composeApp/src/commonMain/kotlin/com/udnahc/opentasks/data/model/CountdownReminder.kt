package com.udnahc.opentasks.data.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Immutable
@Serializable
data class CountdownReminder(
    val daysBefore: Int,
    val time: String,  // "HH:mm" format, e.g. "09:00"
)

fun List<CountdownReminder>.toJsonString(): String = Json.encodeToString(this)

fun String.toCountdownReminders(): List<CountdownReminder> =
    if (isBlank()) emptyList() else Json.decodeFromString(this)
