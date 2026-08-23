package com.udnahc.opentasks.domain.usecase.task

import com.udnahc.opentasks.data.extensions.MILLIS_PER_DAY
import com.udnahc.opentasks.data.extensions.MILLIS_PER_MINUTE
import com.udnahc.opentasks.data.model.TaskFormData
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TaskReminderEligibilityUseCaseTest {
    private val now = 1_000_000L

    @Test
    fun dateReminderIsEligibleOnlyWhenItsTriggerIsFuture() {
        val useCase = TaskReminderEligibilityUseCase(nowProvider = { now })
        val future = TaskFormData(
            title = "task",
            content = "",
            deadline = now + MILLIS_PER_DAY,
            dateReminders = "0",
        )
        val past = future.copy(deadline = now - MILLIS_PER_MINUTE)

        assertTrue(useCase(future))
        assertFalse(useCase(past))
    }

    @Test
    fun endDeadlineSentinelUsesTheDurationEndDeadline() {
        val useCase = TaskReminderEligibilityUseCase(nowProvider = { now })
        val futureEnd = TaskFormData(
            title = "task",
            content = "",
            deadline = now - MILLIS_PER_MINUTE,
            endDeadline = now + MILLIS_PER_MINUTE,
            durationReminders = "-1",
        )
        val pastEnd = futureEnd.copy(endDeadline = now - MILLIS_PER_MINUTE)

        assertTrue(useCase(futureEnd))
        assertFalse(useCase(pastEnd))
    }

    @Test
    fun legacyReminderIsUsedOnlyWhenExplicitReminderSetsAreEmpty() {
        val useCase = TaskReminderEligibilityUseCase(nowProvider = { now })
        val legacy = TaskFormData(
            title = "task",
            content = "",
            deadline = now + 2 * MILLIS_PER_DAY,
            reminderDays = 1,
        )
        val explicitPast = legacy.copy(
            deadline = now - MILLIS_PER_MINUTE,
            dateReminders = "0",
        )

        assertTrue(useCase(legacy))
        assertFalse(useCase(explicitPast))
    }

    @Test
    fun malformedReminderValuesAreIgnoredWithoutThrowing() {
        val useCase = TaskReminderEligibilityUseCase(nowProvider = { now })
        val malformed = TaskFormData(
            title = "task",
            content = "",
            deadline = now - MILLIS_PER_MINUTE,
            dateReminders = "not-a-minute,??",
            durationReminders = "invalid",
            reminderDays = 0,
        )

        assertFalse(useCase(malformed))
    }
}
