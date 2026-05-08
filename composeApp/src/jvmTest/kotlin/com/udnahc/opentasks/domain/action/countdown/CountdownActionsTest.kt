package com.udnahc.opentasks.domain.action.countdown

import com.udnahc.opentasks.data.notification.NotificationScheduler
import com.udnahc.opentasks.testutil.FakeCountdownRepository
import com.udnahc.opentasks.testutil.testCountdown
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class CountdownActionsTest {
    @Test
    fun addAndUpdateCountdownFillTimestampsAndScheduleById() = runTest {
        val repository = FakeCountdownRepository()
        val scheduler = ScheduleCountdownRemindersAction(NotificationScheduler(), repository)
        val countdown = testCountdown(id = "countdown", title = "Launch", createdAt = 0L, updatedAt = 0L)

        AddCountdownAction(repository, scheduler)(countdown)
        val inserted = repository.inserted.single()
        assertEquals("Launch", inserted.title)
        assertNotEquals(0L, inserted.createdAt)
        assertNotEquals(0L, inserted.updatedAt)

        UpdateCountdownAction(repository, scheduler)(inserted.copy(title = "Updated"))
        val updated = repository.updated.single()
        assertEquals("Updated", updated.title)
        assertNotEquals(inserted.updatedAt, updated.updatedAt)
    }

    @Test
    fun deleteCountdownUsesRepositoryDeleteContract() = runTest {
        val countdown = testCountdown(id = "countdown")
        val repository = FakeCountdownRepository(listOf(countdown))

        DeleteCountdownAction(
            repository,
            ScheduleCountdownRemindersAction(NotificationScheduler(), repository),
        )(countdown)

        assertEquals(countdown.id, repository.deleted.single().id)
    }
}
