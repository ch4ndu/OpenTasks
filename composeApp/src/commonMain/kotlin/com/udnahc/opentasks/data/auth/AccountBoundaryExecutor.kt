package com.udnahc.opentasks.data.auth

import kotlin.coroutines.cancellation.CancellationException
import org.lighthousegames.logging.logging

private val log = logging("AccountBoundaryExecutor")

/**
 * Executes work that is authorized by the authenticated account boundary.
 *
 * Session restoration happens before the shared mutation gate is acquired
 * because restoration itself enters that gate. The gate then covers live
 * session/binding validation and the complete authorized operation, so an
 * account transition cannot occur between validation and account-owned work.
 */
class AccountBoundaryExecutor(
    private val accountRepository: AccountRepository,
    private val accountBoundaryGuard: AccountBoundaryGuard,
    private val mutationGate: AccountMutationGate,
) {
    /**
     * Captures only the live foreground account identity before the caller
     * performs its first suspension. The full binding is revalidated after the
     * shared mutation gate is acquired.
     */
    fun captureForegroundBoundary(): AccountBoundary? {
        val authenticated = accountRepository.sessionState.value
            as? AccountSessionState.Authenticated
            ?: return null
        val accountId = authenticated.account.accountId
        val boundaryEpoch = authenticated.binding.boundaryEpoch
        if (accountId.isBlank() || boundaryEpoch <= 0L ||
            authenticated.binding.accountId != accountId
        ) {
            return null
        }
        return authenticated.binding.asAccountBoundary()
    }

    /**
     * Runs a foreground mutation and all of its account-owned side effects
     * under one held boundary. Unlike the restore-capable entrypoint below,
     * this path must reject immediately unless the live session is already
     * authenticated.
     */
    suspend fun <T> withForegroundBoundary(
        block: suspend (AccountBoundary) -> T,
    ): T {
        val expected = captureForegroundBoundary()
            ?: throw AccountBoundaryRejectedException()

        return withForegroundBoundary(expected, block)
    }

    /**
     * Runs work only if the exact boundary captured by a synchronous caller is
     * still active after the shared mutation gate is acquired.
     */
    suspend fun <T> withForegroundBoundary(
        expected: AccountBoundary,
        block: suspend (AccountBoundary) -> T,
    ): T {

        return mutationGate.withExclusive {
            val boundary = validateForegroundBoundary(expected)
                ?: throw AccountBoundaryRejectedException()
            block(boundary)
        }
    }

    suspend fun currentBoundary(): AccountBoundary? {
        val restored = restoreAuthenticated() ?: return null
        return mutationGate.withExclusive {
            validateRestoredBoundary(restored)
        }
    }

    suspend fun <T> withAuthenticatedBoundary(
        block: suspend (AccountBoundary) -> T,
    ): T? = withAuthenticatedBoundary(null, null, block)

    suspend fun <T> withAuthenticatedBoundary(
        expectedAccountId: String? = null,
        expectedBoundaryEpoch: Long? = null,
        block: suspend (AccountBoundary) -> T,
    ): T? {
        val restored = restoreAuthenticated() ?: return null
        if (!expectedBoundaryIsWellFormed(expectedAccountId, expectedBoundaryEpoch)) return null

        return mutationGate.withExclusive {
            val boundary = validateRestoredBoundary(
                restored = restored,
                expectedAccountId = expectedAccountId,
                expectedBoundaryEpoch = expectedBoundaryEpoch,
            ) ?: return@withExclusive null
            block(boundary)
        }
    }

    private suspend fun validateForegroundBoundary(
        expected: AccountBoundary,
    ): AccountBoundary? {
        val live = accountRepository.sessionState.value
            as? AccountSessionState.Authenticated
            ?: return null
        if (live.account.accountId != expected.accountId ||
            live.account.accountId != live.binding.accountId ||
            live.binding.boundaryEpoch != expected.boundaryEpoch
        ) {
            return null
        }

        val current = try {
            accountBoundaryGuard.activeBoundary()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            return null
        } ?: return null
        if (current.accountId != expected.accountId ||
            current.boundaryEpoch != expected.boundaryEpoch ||
            current != live.binding.asAccountBoundary()
        ) {
            return null
        }
        return current
    }

    private suspend fun restoreAuthenticated(): AccountSessionState.Authenticated? {
        val live = accountRepository.sessionState.value as? AccountSessionState.Authenticated
        if (live != null &&
            live.account.accountId.isNotBlank() &&
            live.account.accountId == live.binding.accountId &&
            live.binding.boundaryEpoch > 0L
        ) {
            log.d { "Reusing the live authenticated session for background account-bound work" }
            return live
        }
        log.d { "Restoring the account session for background account-bound work" }
        val restored = try {
            accountRepository.restoreSession()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            return null
        }
        val authenticated = restored as? AccountSessionState.Authenticated ?: return null
        if (authenticated.account.accountId != authenticated.binding.accountId) return null
        return authenticated
    }

    private suspend fun validateRestoredBoundary(
        restored: AccountSessionState.Authenticated,
        expectedAccountId: String? = null,
        expectedBoundaryEpoch: Long? = null,
    ): AccountBoundary? {
        val live = accountRepository.sessionState.value as? AccountSessionState.Authenticated ?: return null
        if (live.account.accountId != restored.account.accountId) return null
        if (live.account.accountId != live.binding.accountId) return null

        val restoredBoundary = restored.binding.asAccountBoundary()
        if (live.binding.asAccountBoundary() != restoredBoundary) return null

        val currentBoundary = try {
            accountBoundaryGuard.activeBoundary()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            return null
        }
        if (currentBoundary != restoredBoundary) return null
        if (expectedAccountId != null &&
            (currentBoundary.accountId != expectedAccountId ||
                currentBoundary.boundaryEpoch != expectedBoundaryEpoch)
        ) return null
        return currentBoundary
    }

    private fun expectedBoundaryIsWellFormed(
        expectedAccountId: String?,
        expectedBoundaryEpoch: Long?,
    ): Boolean {
        if ((expectedAccountId == null) != (expectedBoundaryEpoch == null)) return false
        if (expectedAccountId == null) return true
        return expectedAccountId.isNotBlank() && expectedBoundaryEpoch?.let { it > 0L } == true
    }
}

class AccountBoundaryRejectedException : IllegalStateException(
    "Foreground account-bound action was rejected because the authenticated account boundary is unavailable or stale",
)

/** Compatibility seam for direct action tests; production DI always supplies the executor. */
suspend fun <T> AccountBoundaryExecutor?.withForegroundActionBoundary(
    block: suspend () -> T,
): T = if (this == null) block() else withForegroundBoundary { block() }

/** Compatibility seam for direct tests; production callers provide both values. */
suspend fun <T> AccountBoundaryExecutor?.withForegroundActionBoundary(
    expectedBoundary: AccountBoundary?,
    block: suspend () -> T,
): T = if (this == null) {
    block()
} else {
    withForegroundBoundary(expectedBoundary ?: throw AccountBoundaryRejectedException()) { block() }
}
