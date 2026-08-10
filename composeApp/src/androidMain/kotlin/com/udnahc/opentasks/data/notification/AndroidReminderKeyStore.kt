package com.udnahc.opentasks.data.notification

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Process-restart-safe Android authority for reminder request codes. The common
 * registry serializes mutations with a Mutex; this adapter commits each edit so
 * a receiver process can restore pending and displayed reminders immediately.
 */
internal class AndroidReminderKeyStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val registry = ReminderKeyRegistry(
        SharedPreferencesReminderKeyStorage(preferences)
    )

    suspend fun allocatePending(identity: ReminderIdentity): ReminderKeyRecord =
        processMutex.withLock { registry.allocatePending(identity) }

    suspend fun markDisplayed(semanticKey: String): ReminderKeyRecord? =
        processMutex.withLock { registry.markDisplayed(semanticKey) }

    suspend fun record(semanticKey: String): ReminderKeyRecord? =
        processMutex.withLock { registry.record(semanticKey) }

    suspend fun recordsForEvent(eventId: String): List<ReminderKeyRecord> =
        processMutex.withLock { registry.recordsForEvent(eventId) }

    suspend fun remove(semanticKey: String): ReminderKeyRecord? =
        processMutex.withLock { registry.remove(semanticKey) }

    suspend fun cleanupLegacyOnce(eventId: String, cleanup: () -> Unit) =
        processMutex.withLock { registry.legacyCleanupOnce(eventId, cleanup) }

    suspend fun allRecords(): List<ReminderKeyRecord> {
        val keys = processMutex.withLock {
            preferences.all.keys
                .filter { it.startsWith(KEY_ID_PREFIX) }
                .map { it.removePrefix(KEY_ID_PREFIX) }
        }
        return keys.mapNotNull { registry.record(it) }
    }

    private companion object {
        const val PREFERENCES_NAME = "opentasks_reminder_key_store"
        const val KEY_ID_PREFIX = "reminder.key.id."
        val processMutex = Mutex()
    }
}

private class SharedPreferencesReminderKeyStorage(
    private val preferences: SharedPreferences,
) : ReminderKeyStorage {
    override fun getString(key: String): String? = preferences.getString(key, null)

    override fun getStringSet(key: String): Set<String> =
        preferences.getStringSet(key, emptySet()).orEmpty().toSet()

    override fun edit(block: ReminderKeyStorageEditor.() -> Unit) {
        val editor = preferences.edit()
        object : ReminderKeyStorageEditor {
            override fun putString(key: String, value: String) {
                editor.putString(key, value)
            }

            override fun putStringSet(key: String, value: Set<String>) {
                editor.putStringSet(key, value)
            }

            override fun remove(key: String) {
                editor.remove(key)
            }
        }.apply(block)
        check(editor.commit()) { "Unable to persist Android reminder key store" }
    }
}
