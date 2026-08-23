package com.udnahc.opentasks.data.sync

/** Process-lifetime outcome for every ordinary sync pass. It is intentionally not persisted. */
sealed interface SyncOutcome {
    data object Idle : SyncOutcome
    data object Syncing : SyncOutcome
    data object Success : SyncOutcome
    data object Failed : SyncOutcome
    data object ReauthenticationRequired : SyncOutcome
}
