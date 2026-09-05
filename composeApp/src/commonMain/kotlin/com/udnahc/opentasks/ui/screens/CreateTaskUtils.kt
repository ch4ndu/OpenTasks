package com.udnahc.opentasks.ui.screens

import com.udnahc.opentasks.data.extensions.computeLocalMillis
import com.udnahc.opentasks.data.extensions.extractDay
import com.udnahc.opentasks.data.extensions.extractHour
import com.udnahc.opentasks.data.extensions.extractMinute
import com.udnahc.opentasks.data.extensions.extractMonth
import com.udnahc.opentasks.data.extensions.extractYear
import com.udnahc.opentasks.data.extensions.localMillisToLocalDate
import com.udnahc.opentasks.data.extensions.localMillisToLocalDateTime
import com.udnahc.opentasks.data.model.TaskFormData
import com.udnahc.opentasks.data.model.TaskStatus
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.until
import opentasks.composeapp.generated.resources.Res
import opentasks.composeapp.generated.resources.none
import opentasks.composeapp.generated.resources.reminder_1_day_early
import opentasks.composeapp.generated.resources.reminder_1_hour_early
import opentasks.composeapp.generated.resources.reminder_1_week_early
import opentasks.composeapp.generated.resources.reminder_2_days_early
import opentasks.composeapp.generated.resources.reminder_30_mins_early
import opentasks.composeapp.generated.resources.reminder_3_days_early
import opentasks.composeapp.generated.resources.reminder_5_mins_early
import opentasks.composeapp.generated.resources.reminder_at_the_end
import opentasks.composeapp.generated.resources.reminder_on_time
import org.jetbrains.compose.resources.StringResource

internal enum class ReminderOption(
    val labelRes: StringResource,
    val minutesValue: Int
) {
    NONE(Res.string.none, Int.MIN_VALUE),
    ON_TIME(Res.string.reminder_on_time, 0),
    FIVE_MINS_BEFORE(Res.string.reminder_5_mins_early, 5),
    THIRTY_MINS_BEFORE(Res.string.reminder_30_mins_early, 30),
    ONE_HOUR_BEFORE(Res.string.reminder_1_hour_early, 60),
    ONE_DAY_BEFORE(Res.string.reminder_1_day_early, 1440),
    TWO_DAYS_BEFORE(Res.string.reminder_2_days_early, 2880),
    THREE_DAYS_BEFORE(Res.string.reminder_3_days_early, 4320),
    ONE_WEEK_BEFORE(Res.string.reminder_1_week_early, 10080),
    AT_THE_END(Res.string.reminder_at_the_end, -1),
}

internal fun Set<ReminderOption>.toRemindersString(): String =
    filter { it != ReminderOption.NONE }
        .joinToString(",") { it.minutesValue.toString() }

internal fun String.toReminderSet(): Set<ReminderOption> {
    if (isBlank()) return emptySet()
    val values = split(",").mapNotNull { it.trim().toIntOrNull() }
    return ReminderOption.entries
        .filter { it != ReminderOption.NONE && it.minutesValue in values }
        .toSet()
}

internal const val PAGER_MONTH_RANGE = 120 // 10 years in each direction
internal const val PAGER_INITIAL_PAGE = PAGER_MONTH_RANGE // current month is at center

internal fun pageToMonthYear(page: Int, currentDate: kotlinx.datetime.LocalDate): Pair<Int, Int> {
    val offset = page - PAGER_INITIAL_PAGE
    val baseMonth = currentDate.monthNumber - 1 + offset // 0-indexed
    val year = currentDate.year + baseMonth.floorDiv(12)
    val month = baseMonth.mod(12) + 1 // back to 1-indexed
    return month to year
}

internal fun computeDeadlineMillis(
    year: Int,
    month: Int,
    day: Int,
    hour: Int,
    minute: Int
): Long = computeLocalMillis(year, month, day, hour, minute)

internal data class TaskEditorDateState(
    val deadline: Long?,
    val endDeadline: Long?,
    val pendingStartHour: Int,
    val pendingStartMinute: Int,
    val pendingEndHour: Int,
    val pendingEndMinute: Int,
) {
    val selectedDay: Int get() = deadline?.let(::extractDay) ?: 0
    val selectedMonth: Int get() = deadline?.let(::extractMonth) ?: 0
    val selectedYear: Int get() = deadline?.let(::extractYear) ?: 0
    val selectedHour: Int get() = deadline?.let(::extractHour) ?: pendingStartHour
    val selectedMinute: Int get() = deadline?.let(::extractMinute) ?: pendingStartMinute
    val endDay: Int get() = endDeadline?.let(::extractDay) ?: 0
    val endMonth: Int get() = endDeadline?.let(::extractMonth) ?: 0
    val endYear: Int get() = endDeadline?.let(::extractYear) ?: 0
    val endHour: Int get() = endDeadline?.let(::extractHour) ?: pendingEndHour
    val endMinute: Int get() = endDeadline?.let(::extractMinute) ?: pendingEndMinute
    val isValidRange: Boolean
        get() = when {
            deadline == null -> endDeadline == null
            endDeadline == null -> true
            else -> endDeadline >= deadline
        }
    val civilDaySpan: Int
        get() = if (deadline != null && endDeadline != null) {
            localMillisToLocalDate(deadline).until(
                localMillisToLocalDate(endDeadline),
                DateTimeUnit.DAY,
            ).toInt()
        } else {
            0
        }

    fun selectDate(day: Int, month: Int, year: Int): TaskEditorDateState {
        val newDate = localDateOrNull(year, month, day) ?: return this
        val previousDeadline = deadline
        val nextDeadline = previousDeadline?.withCivilDate(newDate)
            ?: computeDeadlineMillis(year, month, day, pendingStartHour, pendingStartMinute)
        val nextEndDeadline = if (previousDeadline != null && endDeadline != null) {
            val previousStartDate = localMillisToLocalDate(previousDeadline)
            val previousEndDate = localMillisToLocalDate(endDeadline)
            val daySpan = previousStartDate.until(previousEndDate, DateTimeUnit.DAY)
            endDeadline.withCivilDate(newDate.plus(daySpan, DateTimeUnit.DAY))
        } else {
            endDeadline
        }
        return copy(deadline = nextDeadline, endDeadline = nextEndDeadline)
    }

    fun selectStartTime(hour: Int, minute: Int): TaskEditorDateState = copy(
        deadline = deadline?.withTime(hour, minute),
        pendingStartHour = hour,
        pendingStartMinute = minute,
    )

    fun selectEndTime(hour: Int, minute: Int): TaskEditorDateState = copy(
        endDeadline = when {
            endDeadline != null -> endDeadline.withTime(hour, minute)
            deadline != null -> deadline.withTime(hour, minute)
            else -> null
        },
        pendingEndHour = hour,
        pendingEndMinute = minute,
    )

    fun clearDate(): TaskEditorDateState = copy(
        deadline = null,
        endDeadline = null,
        pendingEndHour = -1,
        pendingEndMinute = 0,
    )
}

internal fun initialTaskEditorDateState(
    formData: TaskFormData?,
    initialDay: Int,
    initialMonth: Int,
    initialYear: Int,
): TaskEditorDateState {
    val initialDeadline = formData?.deadline ?: localDateOrNull(initialYear, initialMonth, initialDay)?.let {
        computeDeadlineMillis(it.year, it.monthNumber, it.dayOfMonth, DEFAULT_START_HOUR, 0)
    }
    val initialEndDeadline = formData?.endDeadline
    return TaskEditorDateState(
        deadline = initialDeadline,
        endDeadline = initialEndDeadline,
        pendingStartHour = initialDeadline?.let(::extractHour) ?: DEFAULT_START_HOUR,
        pendingStartMinute = initialDeadline?.let(::extractMinute) ?: 0,
        pendingEndHour = initialEndDeadline?.let(::extractHour) ?: -1,
        pendingEndMinute = initialEndDeadline?.let(::extractMinute) ?: 0,
    )
}

internal fun TaskEditorDateState.toSaveableValues(): List<Long> = listOf(
    deadline ?: NO_DATE_MILLIS,
    endDeadline ?: NO_DATE_MILLIS,
    pendingStartHour.toLong(),
    pendingStartMinute.toLong(),
    pendingEndHour.toLong(),
    pendingEndMinute.toLong(),
)

internal fun taskEditorDateStateFromSaveableValues(values: List<Long>): TaskEditorDateState? {
    if (values.size != TASK_EDITOR_DATE_SAVED_FIELD_COUNT) return null
    return TaskEditorDateState(
        deadline = values[0].takeUnless { it == NO_DATE_MILLIS },
        endDeadline = values[1].takeUnless { it == NO_DATE_MILLIS },
        pendingStartHour = values[2].toInt(),
        pendingStartMinute = values[3].toInt(),
        pendingEndHour = values[4].toInt(),
        pendingEndMinute = values[5].toInt(),
    )
}

internal fun toggleTaskEditorCompletionStatus(
    currentStatus: TaskStatus,
    initialStatus: TaskStatus,
): TaskStatus = if (currentStatus == TaskStatus.DONE) {
    initialStatus.takeUnless { it == TaskStatus.DONE } ?: TaskStatus.TODO
} else {
    TaskStatus.DONE
}

internal fun taskEditorCompletionRestoreStatus(initialStatus: TaskStatus): TaskStatus =
    initialStatus.takeUnless { it == TaskStatus.DONE } ?: TaskStatus.TODO

internal fun durationMinutesForCivilSpan(
    daySpan: Int,
    startHour: Int,
    startMinute: Int,
    endHour: Int,
    endMinute: Int,
): Int = daySpan * MINUTES_PER_DAY +
        (endHour * 60 + endMinute) -
        (startHour * 60 + startMinute)

internal sealed interface TaskFormBuildResult {
    data class Ready(val formData: TaskFormData) : TaskFormBuildResult
    data object InvalidDateRange : TaskFormBuildResult
}

internal fun buildTaskFormDataForSave(
    draft: TaskFormData,
    dateState: TaskEditorDateState,
    status: TaskStatus,
): TaskFormBuildResult {
    if (!dateState.isValidRange) return TaskFormBuildResult.InvalidDateRange
    return TaskFormBuildResult.Ready(
        draft.copy(
            deadline = dateState.deadline,
            endDeadline = dateState.endDeadline,
            status = status,
        ),
    )
}

private fun Long.withCivilDate(date: LocalDate): Long {
    val localDateTime = localMillisToLocalDateTime(this)
    return LocalDateTime(date, localDateTime.time).toInstant(TimeZone.UTC).toEpochMilliseconds()
}

private fun Long.withTime(hour: Int, minute: Int): Long {
    val dateTime = localMillisToLocalDateTime(this)
    if (dateTime.hour == hour && dateTime.minute == minute) return this
    return computeDeadlineMillis(
        dateTime.year,
        dateTime.monthNumber,
        dateTime.dayOfMonth,
        hour,
        minute,
    )
}

private fun localDateOrNull(year: Int, month: Int, day: Int): LocalDate? =
    runCatching { LocalDate(year, month, day) }.getOrNull()

private const val DEFAULT_START_HOUR = 8
private const val MINUTES_PER_DAY = 24 * 60
private const val TASK_EDITOR_DATE_SAVED_FIELD_COUNT = 6
private const val NO_DATE_MILLIS = Long.MIN_VALUE
