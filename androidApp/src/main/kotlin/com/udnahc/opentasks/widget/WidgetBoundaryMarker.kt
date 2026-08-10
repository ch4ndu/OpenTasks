package com.udnahc.opentasks.widget

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.udnahc.opentasks.data.auth.AccountBoundary

/**
 * The small per-widget proof that rendered data belongs to the active account
 * boundary. Each Glance widget family stores this in its own Preferences state.
 */
internal object WidgetBoundaryMarker {
    internal val ACCOUNT_ID_KEY = stringPreferencesKey("widget_boundary_account_id")
    internal val BOUNDARY_EPOCH_KEY = longPreferencesKey("widget_boundary_epoch")

    internal data class Value(
        val accountId: String?,
        val boundaryEpoch: Long?,
    )

    internal fun read(preferences: Preferences): Value = Value(
        accountId = preferences[ACCOUNT_ID_KEY],
        boundaryEpoch = preferences[BOUNDARY_EPOCH_KEY],
    )

    internal fun write(preferences: MutablePreferences, boundary: AccountBoundary) {
        preferences[ACCOUNT_ID_KEY] = boundary.accountId
        preferences[BOUNDARY_EPOCH_KEY] = boundary.boundaryEpoch
    }

    internal fun clear(preferences: MutablePreferences) {
        preferences.remove(ACCOUNT_ID_KEY)
        preferences.remove(BOUNDARY_EPOCH_KEY)
    }

    internal fun matches(preferences: Preferences, boundary: AccountBoundary): Boolean =
        matches(read(preferences), boundary)

    internal fun matches(marker: Value, boundary: AccountBoundary): Boolean =
        !marker.accountId.isNullOrBlank() &&
            marker.accountId == boundary.accountId &&
            marker.boundaryEpoch == boundary.boundaryEpoch

    /** Return a value that is strictly newer even when two updates share a clock tick. */
    internal fun nextTrigger(current: Long, now: Long): Long = maxOf(current + 1L, now)
}
