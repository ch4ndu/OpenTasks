package com.udnahc.opentasks.data.notification

import com.udnahc.opentasks.data.extensions.utcNow
import com.udnahc.opentasks.data.repository.AppSettingsRepository
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

private const val ALL_DAY_DISMISSALS_KEY = "all_day_notification_dismissals"
private const val DATE_SEPARATOR = "|"
private const val ID_SEPARATOR = ","

class AllDayNotificationDismissalStore(
    private val appSettingsRepository: AppSettingsRepository,
    private val nowUtcMillisProvider: () -> Long = ::utcNow,
) {
    suspend fun isDismissedToday(taskId: String): Boolean =
        taskId in dismissedIdsToday()

    suspend fun dismissToday(taskId: String) {
        val today = todayKey()
        val ids = dismissedIdsToday() + taskId
        appSettingsRepository.setValue(
            ALL_DAY_DISMISSALS_KEY,
            today + DATE_SEPARATOR + ids.sorted().joinToString(ID_SEPARATOR),
        )
    }

    private suspend fun dismissedIdsToday(): Set<String> {
        val todayPrefix = todayKey() + DATE_SEPARATOR
        val value = appSettingsRepository.getValue(ALL_DAY_DISMISSALS_KEY) ?: return emptySet()
        if (!value.startsWith(todayPrefix)) return emptySet()
        return value.removePrefix(todayPrefix)
            .split(ID_SEPARATOR)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toSet()
    }

    private fun todayKey(): String {
        val timeZone = TimeZone.currentSystemDefault()
        return Instant.fromEpochMilliseconds(nowUtcMillisProvider())
            .toLocalDateTime(timeZone)
            .date
            .toString()
    }
}
