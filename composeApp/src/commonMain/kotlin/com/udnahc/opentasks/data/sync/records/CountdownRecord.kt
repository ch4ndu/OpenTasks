package com.udnahc.opentasks.data.sync.records

import com.udnahc.opentasks.data.model.Countdown
import com.udnahc.opentasks.data.model.CountdownType
import com.udnahc.opentasks.data.model.CountingMode
import com.udnahc.opentasks.data.model.RecurrenceType
import com.udnahc.opentasks.data.model.SmartListVisibility
import io.github.agrevster.pocketbaseKotlin.models.utils.BaseModel
import kotlinx.serialization.Serializable

@Serializable
class CountdownRecord(
    val localId: String = "",
    val title: String = "",
    val targetDate: Long = 0L,
    val countdownType: String = "COUNTDOWN",
    val countingMode: String = "COUNTDOWN",
    val reminders: String = "",
    val recurrenceType: String = "NONE",
    val recurrenceInterval: Int = 1,
    val recurrenceDaysOfWeek: String = "",
    val smartListVisibility: String = "ON_THE_DAY",
    val isCompleted: Boolean = false,
    val isDeleted: Boolean = false,
    val localCreatedAt: Long = 0L,
    val localUpdatedAt: Long = 0L,
) : BaseModel()

fun CountdownRecord.toCountdown(): Countdown = Countdown(
    id = localId,
    title = title,
    targetDate = targetDate,
    countdownType = CountdownType.entries.firstOrNull { it.name == countdownType } ?: CountdownType.COUNTDOWN,
    countingMode = CountingMode.entries.firstOrNull { it.name == countingMode } ?: CountingMode.COUNTDOWN,
    reminders = reminders,
    recurrenceType = RecurrenceType.entries.firstOrNull { it.name == recurrenceType } ?: RecurrenceType.NONE,
    recurrenceInterval = recurrenceInterval,
    recurrenceDaysOfWeek = recurrenceDaysOfWeek,
    smartListVisibility = SmartListVisibility.entries.firstOrNull { it.name == smartListVisibility } ?: SmartListVisibility.ON_THE_DAY,
    isCompleted = isCompleted,
    pbId = id,
    isSynced = true,
    isDeleted = isDeleted,
    createdAt = localCreatedAt,
    updatedAt = localUpdatedAt,
)

fun Countdown.toCountdownRecord(): CountdownRecord = CountdownRecord(
    localId = id,
    title = title,
    targetDate = targetDate,
    countdownType = countdownType.name,
    countingMode = countingMode.name,
    reminders = reminders,
    recurrenceType = recurrenceType.name,
    recurrenceInterval = recurrenceInterval,
    recurrenceDaysOfWeek = recurrenceDaysOfWeek,
    smartListVisibility = smartListVisibility.name,
    isCompleted = isCompleted,
    isDeleted = isDeleted,
    localCreatedAt = createdAt,
    localUpdatedAt = updatedAt,
)
