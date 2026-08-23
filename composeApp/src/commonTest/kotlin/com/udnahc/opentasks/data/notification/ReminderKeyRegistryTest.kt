package com.udnahc.opentasks.data.notification

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReminderKeyRegistryTest {
    @Test
    fun syntheticLegacyHashCollisionGetsDistinctSemanticIdsAndPendingIntentIdentities() = runTest {
        // "Aa" and "BB" deliberately share String.hashCode(), which was the old authority.
        val registry = ReminderKeyRegistry(InMemoryReminderKeyStorage())
        val first = ReminderIdentity("Aa", 10L, ReminderKind.DATE, 0)
        val second = ReminderIdentity("BB", 10L, ReminderKind.DATE, 0)

        val firstRecord = registry.allocatePending(first)
        val secondRecord = registry.allocatePending(second)

        assertNotEquals(first.semanticKey, second.semanticKey)
        assertNotEquals(firstRecord.notificationId, secondRecord.notificationId)
        assertNotEquals(
            reminderPendingIntentIdentity(first.semanticKey, "tap"),
            reminderPendingIntentIdentity(second.semanticKey, "tap"),
        )
        assertNotEquals(
            reminderPendingIntentIdentity(first.semanticKey, "tap"),
            reminderPendingIntentIdentity(first.semanticKey, "mark_done"),
        )
    }

    @Test
    fun allocatedKeysSurviveRestartAndTrackAlarmToDisplayedLifecycle() = runTest {
        val storage = InMemoryReminderKeyStorage()
        val identity = ReminderIdentity("task", 100L, ReminderKind.DATE, 2)
        val firstRegistry = ReminderKeyRegistry(storage)
        val pending = firstRegistry.allocatePending(identity)

        val restartedRegistry = ReminderKeyRegistry(storage)
        val restored = restartedRegistry.allocatePending(identity)
        val displayed = restartedRegistry.markDisplayed(identity.semanticKey)

        assertEquals(pending.notificationId, restored.notificationId)
        assertEquals(ReminderLifecycle.DISPLAYED, displayed?.lifecycle)
        restartedRegistry.remove(identity.semanticKey)
        assertNull(restartedRegistry.record(identity.semanticKey))
    }

    @Test
    fun eventIndexCancelsKeysOutsideTheLegacySlotRangeWithoutScanningSlots() = runTest {
        val registry = ReminderKeyRegistry(InMemoryReminderKeyStorage())
        val eventId = "task"
        val keys = (0..120).map { ordinal ->
            ReminderIdentity(eventId, 1_000L + ordinal, ReminderKind.DATE, ordinal)
        }
        for (key in keys) {
            registry.allocatePending(key)
        }

        val outsideLegacyRange = keys.last().semanticKey
        assertTrue(registry.recordsForEvent(eventId).any { it.semanticKey == outsideLegacyRange })
        registry.remove(outsideLegacyRange)

        assertFalse(registry.recordsForEvent(eventId).any { it.semanticKey == outsideLegacyRange })
    }

    @Test
    fun versionedLegacyCleanupRunsOnlyOnceAfterRestart() = runTest {
        val storage = InMemoryReminderKeyStorage()
        var cleanupCalls = 0

        ReminderKeyRegistry(storage).legacyCleanupOnce("task") { cleanupCalls += 1 }
        ReminderKeyRegistry(storage).legacyCleanupOnce("task") { cleanupCalls += 1 }

        assertEquals(1, cleanupCalls)
    }

    @Test
    fun persistenceFailurePropagatesWithoutLeavingAnUsableReminderRecord() = runTest {
        val storage = StagedFailingReminderKeyStorage(failOnEdit = 2)
        val registry = ReminderKeyRegistry(storage)
        val identity = ReminderIdentity("task", 100L, ReminderKind.DATE, 0)

        assertFailsWith<ReminderKeyPersistenceException> {
            registry.allocatePending(identity)
        }

        assertNull(registry.record(identity.semanticKey))
    }

    @Test
    fun emptyReplacementDropsOnlyPendingNonOngoingRecords() {
        val pending = ReminderKeyRecord(
            semanticKey = ReminderIdentity("task", 100L, ReminderKind.DATE, 0).semanticKey,
            eventId = "task",
            notificationId = 1,
            lifecycle = ReminderLifecycle.PENDING,
            kind = ReminderKind.DATE,
        )
        val displayed = pending.copy(
            semanticKey = ReminderIdentity("task", 200L, ReminderKind.DATE, 0).semanticKey,
            notificationId = 2,
            lifecycle = ReminderLifecycle.DISPLAYED,
        )
        val ongoing = pending.copy(
            semanticKey = ReminderIdentity("task", 300L, ReminderKind.ONGOING, 0).semanticKey,
            notificationId = 3,
            kind = ReminderKind.ONGOING,
        )

        val cancelled = pendingReplacementRecordsToCancel(
            storedRecords = listOf(pending, displayed, ongoing),
            replacementSemanticKeys = emptySet(),
        )

        assertEquals(listOf(pending), cancelled)
    }

    @Test
    fun replacementKeepsItsPendingSemanticKeys() {
        val retained = ReminderKeyRecord(
            semanticKey = ReminderIdentity("task", 100L, ReminderKind.DATE, 0).semanticKey,
            eventId = "task",
            notificationId = 1,
            lifecycle = ReminderLifecycle.PENDING,
            kind = ReminderKind.DATE,
        )
        val dropped = retained.copy(
            semanticKey = ReminderIdentity("task", 200L, ReminderKind.DATE, 0).semanticKey,
            notificationId = 2,
        )

        val cancelled = pendingReplacementRecordsToCancel(
            storedRecords = listOf(retained, dropped),
            replacementSemanticKeys = setOf(retained.semanticKey),
        )

        assertEquals(listOf(dropped), cancelled)
    }
}

private class InMemoryReminderKeyStorage : ReminderKeyStorage {
    private val strings = mutableMapOf<String, String>()
    private val stringSets = mutableMapOf<String, Set<String>>()

    override fun getString(key: String): String? = strings[key]

    override fun getStringSet(key: String): Set<String> = stringSets[key].orEmpty()

    override fun edit(block: ReminderKeyStorageEditor.() -> Unit) {
        object : ReminderKeyStorageEditor {
            override fun putString(key: String, value: String) {
                strings[key] = value
            }

            override fun putStringSet(key: String, value: Set<String>) {
                stringSets[key] = value.toSet()
            }

            override fun remove(key: String) {
                strings.remove(key)
                stringSets.remove(key)
            }
        }.apply(block)
    }
}

private class StagedFailingReminderKeyStorage(
    private val failOnEdit: Int,
) : ReminderKeyStorage {
    private val delegate = InMemoryReminderKeyStorage()
    private var editCount = 0

    override fun getString(key: String): String? = delegate.getString(key)

    override fun getStringSet(key: String): Set<String> = delegate.getStringSet(key)

    override fun edit(block: ReminderKeyStorageEditor.() -> Unit) {
        editCount += 1
        if (editCount == failOnEdit) {
            throw ReminderKeyPersistenceException("staged persistence failure")
        }
        delegate.edit(block)
    }
}
