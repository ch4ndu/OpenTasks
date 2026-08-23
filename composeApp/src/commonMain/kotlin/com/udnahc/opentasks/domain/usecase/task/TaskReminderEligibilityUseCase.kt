package com.udnahc.opentasks.domain.usecase.task

import com.udnahc.opentasks.data.extensions.MILLIS_PER_MINUTE
import com.udnahc.opentasks.data.extensions.localNow
import com.udnahc.opentasks.data.model.TaskFormData

/** Determines whether a task form still has a reminder in the future. */
class TaskReminderEligibilityUseCase(
    private val nowProvider: () -> Long = ::localNow,
) {
    operator fun invoke(
        formData: TaskFormData,
        now: Long = nowProvider(),
    ): Boolean {
        val deadline = formData.deadline ?: return false
        val dateOffsets = formData.dateReminders.parseMinuteValues()
        val durationOffsets = formData.durationReminders.parseMinuteValues()
        val legacyOffsets = if (dateOffsets.isEmpty() && durationOffsets.isEmpty()) {
            formData.reminderDays.takeIf { it > 0 }?.let { listOf(it * MINUTES_PER_DAY) }.orEmpty()
        } else {
            emptyList()
        }

        return dateOffsets.any { offset ->
            deadline - (offset.toLong() * MILLIS_PER_MINUTE) > now
        } || durationOffsets.any { offset ->
            val triggerAt = if (offset == -1) {
                formData.endDeadline
            } else {
                deadline - (offset.toLong() * MILLIS_PER_MINUTE)
            }
            triggerAt?.let { it > now } == true
        } || legacyOffsets.any { offset ->
            deadline - (offset.toLong() * MILLIS_PER_MINUTE) > now
        }
    }

    private fun String.parseMinuteValues(): List<Int> =
        split(",").mapNotNull { it.trim().toIntOrNull() }

    private companion object {
        const val MINUTES_PER_DAY = 1440
    }
}
