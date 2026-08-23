package com.udnahc.opentasks.data.notification

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal enum class ReminderLifecycle {
    PENDING,
    DISPLAYED,
}

internal data class ReminderKeyRecord(
    val semanticKey: String,
    val eventId: String,
    val notificationId: Int,
    val lifecycle: ReminderLifecycle,
    val kind: ReminderKind,
)

/** A durable reminder-key write failed before an alarm may be armed. */
internal class ReminderKeyPersistenceException(message: String) : IllegalStateException(message)

/**
 * Replacement only removes future pending alarms that no longer belong to the
 * requested semantic set. Delivered and ongoing records remain visible until
 * their own explicit cleanup paths consume them.
 */
internal fun pendingReplacementRecordsToCancel(
    storedRecords: List<ReminderKeyRecord>,
    replacementSemanticKeys: Set<String>,
): List<ReminderKeyRecord> = storedRecords.filter { record ->
    record.lifecycle == ReminderLifecycle.PENDING &&
        record.kind != ReminderKind.ONGOING &&
        record.semanticKey !in replacementSemanticKeys
}

/**
 * Minimal persistent primitive used by Android's SharedPreferences-backed
 * reminder key store and deterministic tests.
 */
internal interface ReminderKeyStorage {
    fun getString(key: String): String?
    fun getStringSet(key: String): Set<String>
    fun edit(block: ReminderKeyStorageEditor.() -> Unit)
}

internal interface ReminderKeyStorageEditor {
    fun putString(key: String, value: String)
    fun putStringSet(key: String, value: Set<String>)
    fun remove(key: String)
}

/**
 * Maintains semantic-key identity independent of platform request-code
 * mechanics. All mutations are serialized so a process restart or concurrent
 * receiver delivery cannot reuse an allocated ID.
 */
internal class ReminderKeyRegistry(
    private val storage: ReminderKeyStorage,
) {
    private val mutex = Mutex()

    suspend fun allocatePending(identity: ReminderIdentity): ReminderKeyRecord = mutex.withLock {
        val key = identity.semanticKey
        val existingId = storage.getString(keyIdKey(key))?.toIntOrNull()
        val notificationId = existingId ?: allocateNewId(key)
        storage.edit {
            putString(keyIdKey(key), notificationId.toString())
            putString(keyEventKey(key), identity.eventId)
            putString(keyLifecycleKey(key), ReminderLifecycle.PENDING.name)
            putString(idKey(notificationId), key)
            val eventKeys = storage.getStringSet(eventKeysKey(identity.eventId)) + key
            putStringSet(eventKeysKey(identity.eventId), eventKeys)
        }
        ReminderKeyRecord(key, identity.eventId, notificationId, ReminderLifecycle.PENDING, identity.kind)
    }

    suspend fun markDisplayed(semanticKey: String): ReminderKeyRecord? = mutex.withLock {
        val record = recordForKey(semanticKey) ?: return@withLock null
        storage.edit { putString(keyLifecycleKey(semanticKey), ReminderLifecycle.DISPLAYED.name) }
        record.copy(lifecycle = ReminderLifecycle.DISPLAYED)
    }

    suspend fun record(semanticKey: String): ReminderKeyRecord? = mutex.withLock {
        recordForKey(semanticKey)
    }

    suspend fun recordsForEvent(eventId: String): List<ReminderKeyRecord> = mutex.withLock {
        storage.getStringSet(eventKeysKey(eventId)).mapNotNull(::recordForKey)
    }

    suspend fun remove(semanticKey: String): ReminderKeyRecord? = mutex.withLock {
        val record = recordForKey(semanticKey) ?: return@withLock null
        storage.edit {
            remove(keyIdKey(semanticKey))
            remove(keyEventKey(semanticKey))
            remove(keyLifecycleKey(semanticKey))
            remove(idKey(record.notificationId))
            val remaining = storage.getStringSet(eventKeysKey(record.eventId)) - semanticKey
            if (remaining.isEmpty()) remove(eventKeysKey(record.eventId))
            else putStringSet(eventKeysKey(record.eventId), remaining)
        }
        record
    }

    suspend fun legacyCleanupOnce(eventId: String, cleanup: () -> Unit) = mutex.withLock {
        val marker = legacyCleanupMarkerKey(eventId)
        if (storage.getString(marker) != LEGACY_CLEANUP_VERSION) {
            cleanup()
            storage.edit { putString(marker, LEGACY_CLEANUP_VERSION) }
        }
    }

    private fun allocateNewId(semanticKey: String): Int {
        var candidate = storage.getString(NEXT_ID_KEY)?.toIntOrNull()?.coerceAtLeast(1) ?: 1
        while (storage.getString(idKey(candidate)) != null) {
            if (candidate == Int.MAX_VALUE) {
                throw IllegalStateException("No positive Android reminder IDs remain for $semanticKey")
            }
            candidate += 1
        }
        if (candidate == Int.MAX_VALUE) {
            throw IllegalStateException("No positive Android reminder IDs remain for $semanticKey")
        }
        storage.edit {
            putString(NEXT_ID_KEY, (candidate + 1).toString())
        }
        return candidate
    }

    private fun recordForKey(semanticKey: String): ReminderKeyRecord? {
        val notificationId = storage.getString(keyIdKey(semanticKey))?.toIntOrNull() ?: return null
        val eventId = storage.getString(keyEventKey(semanticKey)) ?: return null
        val lifecycle = storage.getString(keyLifecycleKey(semanticKey))
            ?.let { value -> ReminderLifecycle.entries.firstOrNull { it.name == value } }
            ?: return null
        val kind = ReminderIdentity.fromSemanticKey(semanticKey)?.kind ?: return null
        return ReminderKeyRecord(semanticKey, eventId, notificationId, lifecycle, kind)
    }

    private fun keyIdKey(semanticKey: String): String = "reminder.key.id.$semanticKey"
    private fun keyEventKey(semanticKey: String): String = "reminder.key.event.$semanticKey"
    private fun keyLifecycleKey(semanticKey: String): String = "reminder.key.lifecycle.$semanticKey"
    private fun idKey(notificationId: Int): String = "reminder.id.key.$notificationId"
    private fun eventKeysKey(eventId: String): String = "reminder.event.keys.$eventId"
    private fun legacyCleanupMarkerKey(eventId: String): String = "reminder.legacy.cleanup.$eventId"

    private companion object {
        const val NEXT_ID_KEY = "reminder.next.id"
        const val LEGACY_CLEANUP_VERSION = "1"
    }
}
