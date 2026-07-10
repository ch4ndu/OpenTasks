package com.udnahc.opentasks.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.udnahc.opentasks.data.extensions.uuid4

const val COUNTDOWN_ID_PREFIX = "countdown_"

val Task.isCountdownItem: Boolean
    get() = id.startsWith(COUNTDOWN_ID_PREFIX)

/**
 * Converts a Countdown to a Task representation for display in calendar views.
 * The countdown appears as an all-day event with a priority color mapped from its type.
 */
fun Countdown.toCalendarTask(effectiveTargetDate: Long = targetDate): Task = Task(
    id = "${COUNTDOWN_ID_PREFIX}$id",
    title = title,
    content = "",
    priority = when (countdownType) {
        CountdownType.HOLIDAY -> TaskPriority.NONE      // green
        CountdownType.BIRTHDAY -> TaskPriority.HIGH      // red/pink
        CountdownType.ANNIVERSARY -> TaskPriority.LOW    // blue
        CountdownType.COUNTDOWN -> TaskPriority.MEDIUM   // amber
    },
    deadline = effectiveTargetDate,
    isAllDay = true,
    status = if (isCompleted) TaskStatus.DONE else TaskStatus.TODO,
)

@Entity(
    tableName = "countdowns",
    indices = [
        Index("isDeleted", "targetDate"),
        Index("isSynced"),
        Index("pbId"),
    ],
)
data class Countdown(
    @PrimaryKey val id: String = uuid4(),
    val title: String,
    val targetDate: Long,
    val countdownType: CountdownType = CountdownType.COUNTDOWN,
    val countingMode: CountingMode = CountingMode.COUNTDOWN,
    val reminders: String = "",
    val recurrenceType: RecurrenceType = RecurrenceType.NONE,
    val recurrenceInterval: Int = 1,
    val recurrenceDaysOfWeek: String = "",
    val smartListVisibility: SmartListVisibility = SmartListVisibility.ON_THE_DAY,
    val isCompleted: Boolean = false,
    val pbId: String? = null,
    val isSynced: Boolean = false,
    val isDeleted: Boolean = false,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
)
