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
    private val registry = ReminderKeyRegistry(
        SharedPreferencesReminderKeyStorage(
            context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE),
        )
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

    private companion object {
        const val PREFERENCES_NAME = "opentasks_reminder_key_store"
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
