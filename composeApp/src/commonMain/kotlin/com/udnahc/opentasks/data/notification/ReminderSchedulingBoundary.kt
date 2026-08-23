package com.udnahc.opentasks.data.notification

import com.udnahc.opentasks.data.auth.AccountBoundary
import com.udnahc.opentasks.data.auth.AccountMutationGate

internal const val ANDROID_S_API_LEVEL = 31

/**
 * Holds the account mutation gate through a platform reminder allocation and
 * arm operation. The authoritative boundary is reread only after acquisition;
 * an optional captured boundary is comparison-only and rejects a queued stale
 * caller rather than authorizing it.
 */
internal suspend fun <T> withHeldReminderBoundary(
    mutationGate: AccountMutationGate,
    activeBoundary: suspend () -> AccountBoundary?,
    expectedBoundary: AccountBoundary? = null,
    block: suspend (AccountBoundary) -> T,
): T = mutationGate.withExclusive {
    val boundary = activeBoundary()
        ?: throw IllegalStateException("Cannot schedule a reminder without an active account boundary")
    if (expectedBoundary != null && boundary != expectedBoundary) {
        throw IllegalStateException("Cannot schedule a reminder after the active account boundary changed")
    }
    block(boundary)
}

/** Exact alarms are available before Android S and capability-gated on S+. */
internal fun shouldUseExactAlarm(
    sdkInt: Int,
    canScheduleExactAlarms: Boolean,
): Boolean = sdkInt < ANDROID_S_API_LEVEL || canScheduleExactAlarms
