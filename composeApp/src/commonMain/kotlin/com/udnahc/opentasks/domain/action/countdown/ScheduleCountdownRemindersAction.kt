package com.udnahc.opentasks.domain.action.countdown

import com.udnahc.opentasks.data.extensions.MILLIS_PER_MINUTE
import com.udnahc.opentasks.data.extensions.computeNextDeadlineUtc
import com.udnahc.opentasks.data.extensions.localToUtc
import com.udnahc.opentasks.data.extensions.utcToLocal
import com.udnahc.opentasks.data.extensions.utcNow
import com.udnahc.opentasks.data.model.COUNTDOWN_ID_PREFIX
import com.udnahc.opentasks.data.model.Countdown
import com.udnahc.opentasks.data.notification.PlainReminderTextProvider
import com.udnahc.opentasks.data.notification.ReminderScheduler
import com.udnahc.opentasks.data.notification.ReminderTextProvider
import com.udnahc.opentasks.data.notification.ReminderRequest
import com.udnahc.opentasks.data.repository.CountdownRepository
import com.udnahc.opentasks.domain.usecase.countdown.nextCountdownOccurrenceOnOrAfter
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import org.lighthousegames.logging.logging
import kotlin.time.Instant

private val log = logging("ScheduleCountdownRemindersAction")

private const val COUNTDOWN_REMINDER_HOUR = 9
private const val COUNTDOWN_REMINDER_MINUTE = 0

class ScheduleCountdownRemindersAction(
    private val scheduler: ReminderScheduler,
    private val countdownRepository: CountdownRepository,
    private val textProvider: ReminderTextProvider = PlainReminderTextProvider,
    private val nowUtcMillisProvider: () -> Long = ::utcNow,
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

    suspend fun invokeAfterOccurrence(
        countdownId: String,
        occurrenceTargetUtcMillis: Long,
    ) {
        val countdown = countdownRepository.getCountdownByIdUtc(countdownId)
        if (countdown == null) {
            cancelPending(countdownId)
            return
        }
        scheduleForCountdown(countdown, occurrenceTargetUtcMillis)
    }

    suspend fun buildFutureRequests(
        countdown: Countdown,
        occurrenceLimit: Int,
    ): List<ReminderRequest> {
        if (occurrenceLimit <= 0 || countdown.isCompleted || countdown.isDeleted) return emptyList()
        val now = nowUtcMillisProvider()
        val requests = mutableListOf<ReminderRequest>()
        var occurrence = countdown.occurrenceTargetUtc(now, afterOccurrenceTargetUtcMillis = null)
        repeat(occurrenceLimit) {
            val baseAt = countdown.reminderBaseAtUtc(occurrence)
            countdown.reminders.parseMinuteValues().forEachIndexed { index, offsetMinutes ->
                val triggerAt = baseAt - offsetMinutes.toLong() * MILLIS_PER_MINUTE
                if (triggerAt > now) {
                    requests += ReminderRequest(
                        eventId = countdown.eventId(),
                        title = countdown.title,
                        body = textProvider.countdownDue(offsetMinutes),
                        triggerAtUtcMillis = triggerAt,
                        reminderId = index,
                        occurrenceUtcMillis = occurrence,
                    )
                }
            }
            if (countdown.recurrenceType == com.udnahc.opentasks.data.model.RecurrenceType.NONE) {
                return requests
            }
            occurrence = countdown.nextOccurrenceUtc(occurrence)
        }
        return requests
    }

    private suspend fun scheduleForCountdown(
        countdown: Countdown,
        afterOccurrenceTargetUtcMillis: Long? = null,
    ) {
        log.d { "Scheduling reminders for countdown ${countdown.id}" }
        cancelPending(countdown.id)

        if (countdown.isCompleted || countdown.isDeleted) {
            log.d { "Cancelled pending reminders for completed/deleted countdown ${countdown.id}" }
            return
        }

        val now = nowUtcMillisProvider()
        val occurrenceTarget = countdown.occurrenceTargetUtc(now, afterOccurrenceTargetUtcMillis)
        val reminderBaseAt = countdown.reminderBaseAtUtc(occurrenceTarget)
        val reminderValues = countdown.reminders.parseMinuteValues()
        val futureTriggers = reminderValues.mapIndexedNotNull { index, offsetMinutes ->
            val triggerAt = reminderBaseAt - (offsetMinutes.toLong() * MILLIS_PER_MINUTE)
            if (triggerAt > now) Triple(index, offsetMinutes, triggerAt) else null
        }
        val lastTriggerAt = futureTriggers.maxOfOrNull { it.third }
        futureTriggers.forEach { (index, offsetMinutes, triggerAt) ->
                scheduler.schedule(
                    taskId = countdown.eventId(),
                    title = countdown.title,
                    body = textProvider.countdownDue(offsetMinutes),
                    triggerAtMillis = triggerAt,
                    reminderId = index,
                    occurrenceDeadlineUtcMillis = occurrenceTarget,
                    allowMarkDone = false,
                    rescheduleAfterFire = countdown.recurrenceType != com.udnahc.opentasks.data.model.RecurrenceType.NONE &&
                        triggerAt == lastTriggerAt,
                )
        }
    }

    private fun cancelPending(countdownId: String) {
        val eventId = "$COUNTDOWN_ID_PREFIX$countdownId"
        scheduler.cancelReminders(eventId)
    }

    private fun String.parseMinuteValues(): List<Int> =
        split(",").mapNotNull { it.trim().toIntOrNull() }

    private fun Countdown.eventId(): String = "$COUNTDOWN_ID_PREFIX$id"

    private fun Countdown.occurrenceTargetUtc(
        now: Long,
        afterOccurrenceTargetUtcMillis: Long?,
    ): Long {
        if (recurrenceType == com.udnahc.opentasks.data.model.RecurrenceType.NONE) return targetDate
        val timeZone = TimeZone.currentSystemDefault()
        val today = Instant.fromEpochMilliseconds(now).toLocalDateTime(timeZone).date
        var occurrence = localToUtc(
            nextCountdownOccurrenceOnOrAfter(
                copy(targetDate = utcToLocal(targetDate)),
                today,
            )
        )
        if (afterOccurrenceTargetUtcMillis != null) {
            while (occurrence <= afterOccurrenceTargetUtcMillis) occurrence = nextOccurrenceUtc(occurrence)
        }
        val offsets = reminders.parseMinuteValues()
        while (
            offsets.isNotEmpty() && offsets.none { offset ->
                reminderBaseAtUtc(occurrence) - offset.toLong() * MILLIS_PER_MINUTE > now
            }
        ) {
            occurrence = nextOccurrenceUtc(occurrence)
        }
        return occurrence
    }

    fun isValidOccurrence(countdown: Countdown, occurrenceTargetUtcMillis: Long): Boolean {
        if (occurrenceTargetUtcMillis < countdown.targetDate) return false
        if (countdown.recurrenceType == com.udnahc.opentasks.data.model.RecurrenceType.NONE) {
            return occurrenceTargetUtcMillis == countdown.targetDate
        }
        var occurrence = countdown.targetDate
        while (occurrence < occurrenceTargetUtcMillis) {
            val next = countdown.nextOccurrenceUtc(occurrence)
            if (next <= occurrence) return false
            occurrence = next
        }
        return occurrence == occurrenceTargetUtcMillis
    }

    private fun Countdown.nextOccurrenceUtc(current: Long): Long = computeNextDeadlineUtc(
        currentDeadlineUtcMillis = current,
        recurrenceType = recurrenceType.name,
        interval = recurrenceInterval,
    )

    private fun Countdown.reminderBaseAtUtc(occurrenceTargetUtcMillis: Long): Long {
        val timeZone = TimeZone.currentSystemDefault()
        val targetLocalDate = Instant.fromEpochMilliseconds(occurrenceTargetUtcMillis)
            .toLocalDateTime(timeZone)
            .date
        return LocalDateTime(
            date = targetLocalDate,
            time = LocalTime(COUNTDOWN_REMINDER_HOUR, COUNTDOWN_REMINDER_MINUTE),
        ).toInstant(timeZone).toEpochMilliseconds()
    }
}
