package com.udnahc.opentasks.data.auth

import com.udnahc.opentasks.data.settings.AccountStateStore

/** Fail-closed validation for callbacks that can outlive the foreground session. */
class AccountBoundaryGuard(
    private val stateStore: AccountStateStore,
) {
    suspend fun activeBoundary(): AccountBoundary? {
        if (stateStore.readTransition() != null) return null
        return stateStore.readCacheBinding()
            ?.takeIf { it.isValidActiveBinding() }
            ?.asAccountBoundary()
    }

    suspend fun matches(accountId: String?, boundaryEpoch: Long): Boolean {
        if (accountId.isNullOrBlank() || boundaryEpoch <= 0L) return false
        val active = activeBoundary() ?: return false
        return active.accountId == accountId && active.boundaryEpoch == boundaryEpoch
    }
}
