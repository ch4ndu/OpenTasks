package com.udnahc.opentasks.domain.action.countdown

import com.udnahc.opentasks.domain.action.reminder.RebuildReminderQueueAction
import kotlinx.coroutines.CancellationException
import org.lighthousegames.logging.logging

private val log = logging("CountdownReminderMaintenance")

/** Runs the existing platform-aware reminder maintenance after a committed countdown write. */
internal suspend fun runCountdownReminderMaintenance(
    rebuildReminderQueueAction: RebuildReminderQueueAction?,
    schedule: suspend () -> Unit,
): Throwable? {
    if (rebuildReminderQueueAction != null) {
        return rebuildReminderQueueAction.afterRecordChangeResult(schedule)
    }
    return try {
        schedule()
        null
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        log.w(error) { "Countdown reminder maintenance failed after a committed write" }
        error
    }
}
