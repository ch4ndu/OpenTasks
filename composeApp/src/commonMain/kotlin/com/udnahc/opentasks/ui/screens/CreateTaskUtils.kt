package com.udnahc.opentasks.ui.screens

import com.udnahc.opentasks.data.extensions.computeLocalMillis
import com.udnahc.opentasks.data.extensions.uuid4
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
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
import org.lighthousegames.logging.logging

private val log = logging("CreateTaskUtils")

@Serializable
data class SubtaskItem(
    val id: String = uuid4(),
    val text: String = "",
    val isChecked: Boolean = false,
)

private val subtaskJson = Json {
    encodeDefaults = true
    ignoreUnknownKeys = true
}

internal fun String.toSubtaskItems(): List<SubtaskItem> {
    if (isBlank()) return emptyList()
    return runCatching { subtaskJson.decodeFromString<List<SubtaskItem>>(this) }
        .getOrElse {
            log.w(it) { "Failed to parse subtasks JSON" }
            emptyList()
        }
        .filter { it.text.isNotBlank() }
}

internal fun List<SubtaskItem>.toSubtasksJson(): String {
    val cleaned = filter { it.text.isNotBlank() }
        .map { it.copy(text = it.text.trim()) }
    return if (cleaned.isEmpty()) "" else subtaskJson.encodeToString(cleaned)
}

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

internal fun pageToMonthYear(page: Int): Pair<Int, Int> {
    val offset = page - PAGER_INITIAL_PAGE
    val baseMonth = com.udnahc.opentasks.data.extensions.currentMonth() - 1 + offset // 0-indexed
    val year = com.udnahc.opentasks.data.extensions.currentYear() + baseMonth.floorDiv(12)
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
