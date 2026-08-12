package com.udnahc.opentasks.data.auth

import kotlin.coroutines.cancellation.CancellationException
import org.lighthousegames.logging.logging

private val log = logging("AccountBoundaryExecutor")

/**
 * Executes work that is authorized by the active local or authenticated cache boundary.
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
     * Captures only the live foreground active-cache identity before the caller
     * performs its first suspension. The full binding is revalidated after the
     * shared mutation gate is acquired.
     */
    fun captureForegroundBoundary(): AccountBoundary? {
        val binding = accountRepository.sessionState.value.activeBindingOrNull() ?: return null
        return binding.takeIf { it.isValidActiveBinding() }?.asAccountBoundary()
    }

    fun captureAuthenticatedForegroundBoundary(): AccountBoundary? {
        val authenticated = accountRepository.sessionState.value as? AccountSessionState.Authenticated
            ?: return null
        return authenticated.binding.takeIf { it.isValidPocketBaseBinding() }?.asAccountBoundary()
    }

    /**
     * Runs a foreground mutation and all of its account-owned side effects
     * under one held boundary. Unlike the restore-capable entrypoint below,
     * this path must reject immediately unless the live session already owns
     * an active local or remote cache.
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
        val restored = restoreActiveCache() ?: return null
        return mutationGate.withExclusive {
            validateRestoredBoundary(restored)
        }
    }

    suspend fun <T> withActiveCacheBoundary(
        block: suspend (AccountBoundary) -> T,
    ): T? = withActiveCacheBoundary(null, null, block)

    suspend fun <T> withActiveCacheBoundary(
        expectedAccountId: String? = null,
        expectedBoundaryEpoch: Long? = null,
        block: suspend (AccountBoundary) -> T,
    ): T? {
        val restored = restoreActiveCache() ?: return null
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

    suspend fun <T> withAuthenticatedBoundary(
        block: suspend (AccountBoundary) -> T,
    ): T? = withAuthenticatedBoundary(null, null, block)

    suspend fun <T> withAuthenticatedBoundary(
        expectedAccountId: String? = null,
        expectedBoundaryEpoch: Long? = null,
        block: suspend (AccountBoundary) -> T,
    ): T? {
        val restored = restoreActiveCache() as? AccountSessionState.Authenticated ?: return null
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
        val liveBinding = accountRepository.sessionState.value.activeBindingOrNull() ?: return null
        if (liveBinding.asAccountBoundary() != expected || !liveBinding.isValidActiveBinding()) return null

        val current = try {
            accountBoundaryGuard.activeBoundary()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            return null
        } ?: return null
        if (current.accountId != expected.accountId ||
            current.boundaryEpoch != expected.boundaryEpoch ||
            current != liveBinding.asAccountBoundary()
        ) {
            return null
        }
        return current
    }

    private suspend fun restoreActiveCache(): AccountSessionState? {
        val live = accountRepository.sessionState.value
        if (live.activeBindingOrNull()?.isValidActiveBinding() == true) {
            log.d { "Reusing the live active-cache session for background account-bound work" }
            return live
        }
        log.d { "Restoring the active-cache session for background account-bound work" }
        val restored = try {
            accountRepository.restoreSession()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            return null
        }
        return restored.takeIf { it.activeBindingOrNull()?.isValidActiveBinding() == true }
    }

    private suspend fun validateRestoredBoundary(
        restored: AccountSessionState,
        expectedAccountId: String? = null,
        expectedBoundaryEpoch: Long? = null,
    ): AccountBoundary? {
        val restoredBoundary = restored.activeBindingOrNull()?.asAccountBoundary() ?: return null
        val liveBoundary = accountRepository.sessionState.value.activeBindingOrNull()?.asAccountBoundary()
            ?: return null
        if (liveBoundary != restoredBoundary) return null

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
    "Foreground account-bound action was rejected because the active cache boundary is unavailable or stale",
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
