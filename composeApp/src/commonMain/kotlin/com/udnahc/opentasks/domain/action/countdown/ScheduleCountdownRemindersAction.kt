package com.udnahc.opentasks.domain.action.countdown

import com.udnahc.opentasks.data.extensions.MILLIS_PER_MINUTE
import com.udnahc.opentasks.data.extensions.utcNow
import com.udnahc.opentasks.data.model.COUNTDOWN_ID_PREFIX
import com.udnahc.opentasks.data.model.Countdown
import com.udnahc.opentasks.data.notification.NotificationScheduler
import com.udnahc.opentasks.data.repository.CountdownRepository
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant
import opentasks.composeapp.generated.resources.Res
import opentasks.composeapp.generated.resources.countdown_notification_in_day
import opentasks.composeapp.generated.resources.countdown_notification_in_days
import opentasks.composeapp.generated.resources.countdown_notification_in_hour
import opentasks.composeapp.generated.resources.countdown_notification_in_hours
import opentasks.composeapp.generated.resources.countdown_notification_in_minutes
import opentasks.composeapp.generated.resources.countdown_notification_today
import org.jetbrains.compose.resources.getString
import org.lighthousegames.logging.logging

private val log = logging("ScheduleCountdownRemindersAction")

private const val MAX_COUNTDOWN_REMINDERS = 100
private const val COUNTDOWN_REMINDER_HOUR = 9
private const val COUNTDOWN_REMINDER_MINUTE = 0

class ScheduleCountdownRemindersAction(
    private val scheduler: NotificationScheduler,
    private val countdownRepository: CountdownRepository,
) {
    suspend operator fun invoke(countdownId: String) {
        val countdown = countdownRepository.getCountdownByIdUtc(countdownId)
        if (countdown == null) {
            log.d { "Countdown $countdownId not found, cancelling pending reminders" }
            cancelPending(countdownId)
            return
        }
        scheduleForCountdown(countdown)
    }

    suspend fun invokeWithUtcCountdown(countdown: Countdown) {
        scheduleForCountdown(countdown)
    }

    private suspend fun scheduleForCountdown(countdown: Countdown) {
        log.d { "Scheduling reminders for countdown ${countdown.id}" }
        cancelPending(countdown.id)

        if (countdown.isCompleted || countdown.isDeleted) {
            log.d { "Cancelled pending reminders for completed/deleted countdown ${countdown.id}" }
            return
        }

        val now = utcNow()
        val reminderBaseAt = countdown.reminderBaseAtUtc()
        countdown.reminders.parseMinuteValues().forEachIndexed { index, offsetMinutes ->
            val triggerAt = reminderBaseAt - (offsetMinutes.toLong() * MILLIS_PER_MINUTE)
            if (triggerAt > now) {
                scheduler.schedule(
                    taskId = countdown.eventId(),
                    title = countdown.title,
                    body = reminderBody(offsetMinutes),
                    triggerAtMillis = triggerAt,
                    reminderId = index,
                    occurrenceDeadlineUtcMillis = null,
                    allowMarkDone = false,
                    rescheduleAfterFire = false,
                )
            }
        }
    }

    private fun cancelPending(countdownId: String) {
        val eventId = "$COUNTDOWN_ID_PREFIX$countdownId"
        for (reminderId in 0 until MAX_COUNTDOWN_REMINDERS) {
            scheduler.cancel(eventId, reminderId)
        }
    }

    private fun String.parseMinuteValues(): List<Int> =
        split(",").mapNotNull { it.trim().toIntOrNull() }

    private fun Countdown.eventId(): String = "$COUNTDOWN_ID_PREFIX$id"

    private fun Countdown.reminderBaseAtUtc(): Long {
        val timeZone = TimeZone.currentSystemDefault()
        val targetLocalDate = Instant.fromEpochMilliseconds(targetDate)
            .toLocalDateTime(timeZone)
            .date
        return LocalDateTime(
            date = targetLocalDate,
            time = LocalTime(COUNTDOWN_REMINDER_HOUR, COUNTDOWN_REMINDER_MINUTE),
        ).toInstant(timeZone).toEpochMilliseconds()
    }

    private suspend fun reminderBody(offsetMinutes: Int): String = when {
        offsetMinutes == 0 -> getString(Res.string.countdown_notification_today)
        offsetMinutes < 60 -> getString(Res.string.countdown_notification_in_minutes, offsetMinutes)
        offsetMinutes < 1440 -> {
            val hours = offsetMinutes / 60
            getString(
                if (hours == 1) {
                    Res.string.countdown_notification_in_hour
                } else {
                    Res.string.countdown_notification_in_hours
                },
                hours,
            )
        }
        else -> {
            val days = offsetMinutes / 1440
            getString(
                if (days == 1) {
                    Res.string.countdown_notification_in_day
                } else {
                    Res.string.countdown_notification_in_days
                },
                days,
            )
        }
    }
}
