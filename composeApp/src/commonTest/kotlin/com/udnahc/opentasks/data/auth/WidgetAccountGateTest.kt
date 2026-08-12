package com.udnahc.opentasks.data.auth

import com.udnahc.opentasks.data.settings.AccountStateStore
import com.udnahc.opentasks.data.settings.LegacyCacheIdentity
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WidgetAccountGateTest {
    private val binding = CacheBinding(
        canonicalEndpoint = "https://tasks.example.com:443",
        serverInstanceId = "server",
        accountId = "account-a",
        capabilityVersion = 2,
        boundaryEpoch = 7L,
    )
    private val localBinding = CacheBinding(
        canonicalEndpoint = "",
        serverInstanceId = "",
        accountId = LOCAL_CACHE_OWNER_ID,
        capabilityVersion = 0,
        boundaryEpoch = 11L,
        mode = CacheMode.LOCAL_ONLY,
    )

    @Test
    fun localActiveCachePermitsPlatformCallbacksButRejectsRemoteOnlyWork() = runTest {
        val localState = AccountSessionState.LocalOnly(localBinding)
        val fixture = fixture(
            state = localState,
            stateStore = WidgetFakeAccountStateStore(localBinding),
        )
        var callbackRuns = 0

        val active = fixture.gate.withActiveCacheBoundary(
            expectedAccountId = LOCAL_CACHE_OWNER_ID,
            expectedBoundaryEpoch = localBinding.boundaryEpoch,
        ) {
            callbackRuns += 1
            it
        }
        val remote = fixture.gate.withAuthenticatedBoundary { callbackRuns += 1 }

        assertEquals(localBinding.asAccountBoundary(), active)
        assertNull(remote)
        assertEquals(1, callbackRuns)
    }

    @Test
    fun authenticatedOnlineBoundaryPermitsTheOperation() = runTest {
        val fixture = fixture(
            AccountSessionState.Authenticated(
                account = AuthenticatedAccount("account-a"),
                binding = binding,
                freshness = AccountSessionFreshness.ONLINE,
            )
        )
        var roomReads = 0

        val result = fixture.gate.withAuthenticatedBoundary { boundary ->
            roomReads++
            boundary
        }

        assertEquals(binding.asAccountBoundary(), result)
        assertEquals(1, roomReads)
        assertEquals(0, fixture.repository.restoreCalls)
    }

    @Test
    fun foregroundBoundaryUsesTheLiveSessionWithoutStartingAnotherRestore() = runTest {
        val fixture = fixture(
            AccountSessionState.Authenticated(
                account = AuthenticatedAccount("account-a"),
                binding = binding,
                freshness = AccountSessionFreshness.ONLINE,
            )
        )

        val result = fixture.gate.withForegroundBoundary { boundary -> boundary }

        assertEquals(binding.asAccountBoundary(), result)
        assertEquals(0, fixture.repository.restoreCalls)
    }

    @Test
    fun validPayloadAccountAndEpochExecuteInsideTheAuthenticatedBoundary() = runTest {
        val fixture = fixture(
            AccountSessionState.Authenticated(
                account = AuthenticatedAccount("account-a"),
                binding = binding,
                freshness = AccountSessionFreshness.ONLINE,
            )
        )
        var operationRan = false

        val result = fixture.gate.withAuthenticatedBoundary(
            expectedAccountId = "account-a",
            expectedBoundaryEpoch = 7L,
        ) {
            operationRan = true
            it
        }

        assertEquals(binding.asAccountBoundary(), result)
        assertEquals(true, operationRan)
    }

    @Test
    fun malformedPayloadsFailClosedBeforeAccountOwnedWork() = runTest {
        listOf(
            null to 0L,
            "" to binding.boundaryEpoch,
            "account-a" to 0L,
            "account-a" to -1L,
        ).forEach { (accountId, epoch) ->
            val fixture = fixture(
                AccountSessionState.Authenticated(
                    account = AuthenticatedAccount("account-a"),
                    binding = binding,
                    freshness = AccountSessionFreshness.ONLINE,
                )
            )
            var daoRead = false
            var mutation = false
            var scheduled = false
            var presented = false

            val result = fixture.gate.withAuthenticatedBoundary(
                expectedAccountId = accountId,
                expectedBoundaryEpoch = epoch,
            ) {
                daoRead = true
                mutation = true
                scheduled = true
                presented = true
            }

            assertNull(result)
            assertFalse(daoRead)
            assertFalse(mutation)
            assertFalse(scheduled)
            assertFalse(presented)
        }
    }

    @Test
    fun restoreUsingTheSharedNonReentrantGateDoesNotDeadlock() = runTest {
        val fixture = fixture(
            AccountSessionState.Authenticated(
                account = AuthenticatedAccount("account-a"),
                binding = binding,
                freshness = AccountSessionFreshness.ONLINE,
            )
        )

        val result = withTimeout(1_000) {
            fixture.gate.withAuthenticatedBoundary { "authorized" }
        }

        assertEquals("authorized", result)
        assertEquals(0, fixture.repository.restoreCalls)
    }

    @Test
    fun provenCacheOfflineBoundaryPermitsTheOperation() = runTest {
        val fixture = fixture(
            AccountSessionState.Authenticated(
                account = AuthenticatedAccount("account-a"),
                binding = binding,
                freshness = AccountSessionFreshness.OFFLINE,
            )
        )
        var sideEffects = 0

        fixture.gate.withAuthenticatedBoundary {
            sideEffects++
        }

        assertEquals(1, sideEffects)
    }

    @Test
    fun everyUnauthenticatedStateBlocksRoomReadsAndActions() = runTest {
        val transition = AccountTransition(
            sourceAccountId = "account-a",
            destinationAccountId = "account-b",
            canonicalEndpoint = binding.canonicalEndpoint,
            serverInstanceId = binding.serverInstanceId,
            capabilityVersion = binding.capabilityVersion,
            boundaryEpoch = 8L,
            phase = AccountTransitionPhase.PREPARED,
        )
        val states = listOf(
            AccountSessionState.Restoring,
            AccountSessionState.SignedOut,
            AccountSessionState.Transitioning(transition),
            AccountSessionState.ReauthenticationRequired(
                account = AuthenticatedAccount("account-a"),
                reason = AccountReauthenticationReason.AUTHENTICATION_REJECTED,
            ),
        )

        states.forEach { state ->
            val fixture = fixture(state)
            var roomReads = 0
            var refreshScheduled = false
            var actionMutated = false
            var syncScheduled = false

            val result = fixture.gate.withAuthenticatedBoundary {
                roomReads++
                refreshScheduled = true
                actionMutated = true
                syncScheduled = true
            }

            assertNull(result)
            assertEquals(0, roomReads, "Room read was allowed for $state")
            assertEquals(false, refreshScheduled, "Refresh was scheduled for $state")
            assertEquals(false, actionMutated, "Action mutated state for $state")
            assertEquals(false, syncScheduled, "Sync was scheduled for $state")
        }
    }

    @Test
    fun restoredBindingWithMismatchedEpochBlocksTheOperation() = runTest {
        val restoredBinding = binding.copy(boundaryEpoch = 8L)
        val fixture = fixture(
            AccountSessionState.Authenticated(
                account = AuthenticatedAccount("account-a"),
                binding = restoredBinding,
                freshness = AccountSessionFreshness.ONLINE,
            )
        )
        var configurationRead = false

        val result = fixture.gate.withAuthenticatedBoundary {
            configurationRead = true
        }

        assertNull(result)
        assertEquals(false, configurationRead)
    }

    @Test
    fun stateAndDurableBindingChangedAfterRestoreAreRejectedBeforeTheOperation() = runTest {
        val replacementBinding = binding.copy(
            accountId = "account-b",
            boundaryEpoch = 8L,
        )
        val stateStore = WidgetFakeAccountStateStore(binding)
        lateinit var repository: FakeAccountRepository
        var sourceRemindersCancelled = false
        val mutationGate = NonReentrantTestAccountMutationGate {
            sourceRemindersCancelled = true
            repository.publishState(
                AccountSessionState.Authenticated(
                    account = AuthenticatedAccount("account-b"),
                    binding = replacementBinding,
                    freshness = AccountSessionFreshness.ONLINE,
                )
            )
            stateStore.setBinding(replacementBinding)
        }
        repository = FakeAccountRepository(
            state = AccountSessionState.Authenticated(
                account = AuthenticatedAccount("account-a"),
                binding = binding,
                freshness = AccountSessionFreshness.ONLINE,
            ),
            mutationGate = mutationGate,
            initialLiveState = AccountSessionState.Restoring,
        )
        val gate = WidgetAccountGate(
            accountRepository = repository,
            accountBoundaryGuard = AccountBoundaryGuard(stateStore),
            mutationGate = mutationGate,
        )
        var operationRan = false
        var daoRead = false
        var mutation = false
        var scheduled = false
        var presented = false

        val result = withTimeout(1_000) {
            gate.withAuthenticatedBoundary(
                expectedAccountId = "account-a",
                expectedBoundaryEpoch = binding.boundaryEpoch,
            ) {
                operationRan = true
                daoRead = true
                mutation = true
                scheduled = true
                presented = true
            }
        }

        assertNull(result)
        assertTrue(sourceRemindersCancelled)
        assertFalse(operationRan)
        assertFalse(daoRead)
        assertFalse(mutation)
        assertFalse(scheduled)
        assertFalse(presented)
    }

    @Test
    fun heldBoundaryPreventsTransitionFromChangingAccountOwnedOperation() = runTest {
        val replacementBinding = binding.copy(accountId = "account-b", boundaryEpoch = 8L)
        val stateStore = WidgetFakeAccountStateStore(binding)
        val mutationGate = MutexAccountMutationGate()
        val repository = FakeAccountRepository(
            state = AccountSessionState.Authenticated(
                account = AuthenticatedAccount("account-a"),
                binding = binding,
                freshness = AccountSessionFreshness.ONLINE,
            ),
            mutationGate = mutationGate,
        )
        val gate = WidgetAccountGate(
            accountRepository = repository,
            accountBoundaryGuard = AccountBoundaryGuard(stateStore),
            mutationGate = mutationGate,
        )
        val operationEntered = CompletableDeferred<Unit>()
        val releaseOperation = CompletableDeferred<Unit>()
        val taskDoneByAccountForSharedLocalId = mutableMapOf(
            "account-a" to false,
            "account-b" to false,
        )
        var mutatedAccountId: String? = null
        var scheduledAccountId: String? = null
        var presentedAccountId: String? = null

        val operation = launch {
            gate.withAuthenticatedBoundary(
                expectedAccountId = "account-a",
                expectedBoundaryEpoch = binding.boundaryEpoch,
            ) { boundary ->
                operationEntered.complete(Unit)
                releaseOperation.await()
                taskDoneByAccountForSharedLocalId[boundary.accountId] = true
                mutatedAccountId = boundary.accountId
                scheduledAccountId = boundary.accountId
                presentedAccountId = boundary.accountId
            }
        }
        operationEntered.await()

        val transition = async {
            mutationGate.withExclusive {
                repository.publishState(
                    AccountSessionState.Authenticated(
                        account = AuthenticatedAccount("account-b"),
                        binding = replacementBinding,
                        freshness = AccountSessionFreshness.ONLINE,
                    )
                )
                stateStore.setBinding(replacementBinding)
            }
        }
        yield()
        assertFalse(transition.isCompleted)

        releaseOperation.complete(Unit)
        operation.join()
        transition.await()

        assertTrue(taskDoneByAccountForSharedLocalId.getValue("account-a"))
        assertFalse(taskDoneByAccountForSharedLocalId.getValue("account-b"))
        assertEquals("account-a", mutatedAccountId)
        assertEquals("account-a", scheduledAccountId)
        assertEquals("account-a", presentedAccountId)
    }

    @Test
    fun foregroundMaintenanceSchedulesSourceRemindersBeforeDestinationTransitionCancellation() = runTest {
        val replacementBinding = binding.copy(accountId = "account-b", boundaryEpoch = 8L)
        val stateStore = WidgetFakeAccountStateStore(binding)
        val mutationGate = MutexAccountMutationGate()
        val repository = FakeAccountRepository(
            state = AccountSessionState.Authenticated(
                account = AuthenticatedAccount("account-a"),
                binding = binding,
                freshness = AccountSessionFreshness.ONLINE,
            ),
            mutationGate = mutationGate,
        )
        val executor = AccountBoundaryExecutor(
            accountRepository = repository,
            accountBoundaryGuard = AccountBoundaryGuard(stateStore),
            mutationGate = mutationGate,
        )
        val maintenanceEntered = CompletableDeferred<Unit>()
        val releaseMaintenance = CompletableDeferred<Unit>()
        val events = mutableListOf<String>()

        val maintenance = launch {
            executor.withAuthenticatedBoundary(
                expectedAccountId = "account-a",
                expectedBoundaryEpoch = binding.boundaryEpoch,
            ) { boundary ->
                maintenanceEntered.complete(Unit)
                releaseMaintenance.await()
                events += "${boundary.accountId}:sync"
                events += "${boundary.accountId}:reminder-read"
                events += "${boundary.accountId}:schedule"
            }
        }
        maintenanceEntered.await()

        val transition = async {
            mutationGate.withExclusive {
                events += "account-b:cancel-source-reminders"
                repository.publishState(
                    AccountSessionState.Authenticated(
                        account = AuthenticatedAccount("account-b"),
                        binding = replacementBinding,
                        freshness = AccountSessionFreshness.ONLINE,
                    )
                )
                stateStore.setBinding(replacementBinding)
            }
        }
        yield()
        assertFalse(transition.isCompleted)

        releaseMaintenance.complete(Unit)
        maintenance.join()
        transition.await()

        assertEquals(
            listOf(
                "account-a:sync",
                "account-a:reminder-read",
                "account-a:schedule",
                "account-b:cancel-source-reminders",
            ),
            events,
        )
    }

    @Test
    fun acceptedFailureCleansUpBeforeTheHeldBoundaryIsReleased() = runTest {
        val replacementBinding = binding.copy(accountId = "account-b", boundaryEpoch = 8L)
        val stateStore = WidgetFakeAccountStateStore(binding)
        val mutationGate = MutexAccountMutationGate()
        val repository = FakeAccountRepository(
            state = AccountSessionState.Authenticated(
                account = AuthenticatedAccount("account-a"),
                binding = binding,
                freshness = AccountSessionFreshness.ONLINE,
            ),
            mutationGate = mutationGate,
        )
        val gate = WidgetAccountGate(
            accountRepository = repository,
            accountBoundaryGuard = AccountBoundaryGuard(stateStore),
            mutationGate = mutationGate,
        )
        val cleanupEntered = CompletableDeferred<Unit>()
        val releaseCleanup = CompletableDeferred<Unit>()
        var cleanupAccountId: String? = null

        val callback = launch {
            assertFailsWith<IllegalStateException> {
                gate.withAuthenticatedBoundary(
                    expectedAccountId = "account-a",
                    expectedBoundaryEpoch = binding.boundaryEpoch,
                ) { boundary ->
                    try {
                        error("accepted callback failed")
                    } catch (failure: Exception) {
                        cleanupEntered.complete(Unit)
                        releaseCleanup.await()
                        cleanupAccountId = boundary.accountId
                        throw failure
                    }
                }
            }
        }
        cleanupEntered.await()

        val transition = async {
            mutationGate.withExclusive {
                repository.publishState(
                    AccountSessionState.Authenticated(
                        account = AuthenticatedAccount("account-b"),
                        binding = replacementBinding,
                        freshness = AccountSessionFreshness.ONLINE,
                    )
                )
                stateStore.setBinding(replacementBinding)
            }
        }
        yield()
        assertFalse(transition.isCompleted)

        releaseCleanup.complete(Unit)
        callback.join()
        transition.await()

        assertEquals("account-a", cleanupAccountId)
    }

    @Test
    fun cancellationPropagatesAndReleasesTheHeldBoundary() = runTest {
        val fixture = fixture(
            state = AccountSessionState.Authenticated(
                account = AuthenticatedAccount("account-a"),
                binding = binding,
                freshness = AccountSessionFreshness.ONLINE,
            ),
            mutationGate = MutexAccountMutationGate(),
        )

        assertFailsWith<kotlin.coroutines.cancellation.CancellationException> {
            fixture.gate.withAuthenticatedBoundary(
                expectedAccountId = "account-a",
                expectedBoundaryEpoch = binding.boundaryEpoch,
            ) {
                throw kotlin.coroutines.cancellation.CancellationException("cancel callback")
            }
        }

        assertEquals("authorized", fixture.gate.withAuthenticatedBoundary { "authorized" })
    }

    @Test
    fun heldExecutorAllowsReentrantMutationGateOperations() = runTest {
        val mutationGate = MutexAccountMutationGate()
        val fixture = fixture(
            state = AccountSessionState.Authenticated(
                account = AuthenticatedAccount("account-a"),
                binding = binding,
                freshness = AccountSessionFreshness.ONLINE,
            ),
            mutationGate = mutationGate,
        )
        var nestedCalls = 0

        val result = withTimeout(1_000) {
            fixture.gate.withAuthenticatedBoundary(
                expectedAccountId = "account-a",
                expectedBoundaryEpoch = binding.boundaryEpoch,
            ) {
                mutationGate.withExclusive {
                    nestedCalls++
                }
                "authorized"
            }
        }

        assertEquals("authorized", result)
        assertEquals(1, nestedCalls)
    }

    private fun fixture(
        state: AccountSessionState,
        mutationGate: AccountMutationGate = NonReentrantTestAccountMutationGate(),
        stateStore: WidgetFakeAccountStateStore = WidgetFakeAccountStateStore(binding),
    ): Fixture {
        val repository = FakeAccountRepository(state, mutationGate)
        val gate = WidgetAccountGate(
            accountRepository = repository,
            accountBoundaryGuard = AccountBoundaryGuard(stateStore),
            mutationGate = mutationGate,
        )
        return Fixture(repository, gate)
    }

    private data class Fixture(
        val repository: FakeAccountRepository,
        val gate: WidgetAccountGate,
    )
}

internal class FakeAccountRepository(
    private var state: AccountSessionState,
    private val mutationGate: AccountMutationGate,
    initialLiveState: AccountSessionState = state,
) : AccountRepository {
    private val _sessionState = MutableStateFlow(initialLiveState)
    var restoreCalls = 0

    override val sessionState: StateFlow<AccountSessionState> = _sessionState.asStateFlow()

    override suspend fun restoreSession(): AccountSessionState = mutationGate.withExclusive {
        restoreCalls++
        state
    }

    override suspend fun startLocalOnly(): AccountSessionState = error("not used")

    override suspend fun clearLocalData(): AccountSessionState = error("not used")

    fun publishState(state: AccountSessionState) {
        this.state = state
        _sessionState.value = state
    }

    override suspend fun login(endpoint: String, email: String, password: String): AccountSessionState =
        error("not used")

    override suspend fun reauthenticate(email: String, password: String): AccountSessionState =
        error("not used")

    override suspend fun switchAccount(endpoint: String, email: String, password: String): AccountSessionState =
        error("not used")

    override suspend fun logout(): AccountSessionState = error("not used")
}

internal class WidgetFakeAccountStateStore(
    private var binding: CacheBinding?,
) : AccountStateStore {
    fun setBinding(binding: CacheBinding) {
        this.binding = binding
    }

    override suspend fun readCacheBinding(): CacheBinding? = binding
    override suspend fun writeCacheBinding(binding: CacheBinding) { this.binding = binding }
    override suspend fun clearCacheBinding() { binding = null }
    override suspend fun readTransition(): AccountTransition? = null
    override suspend fun writeTransition(transition: AccountTransition) = Unit
    override suspend fun clearTransition() = Unit

    override suspend fun persistBindingAndTransition(
        binding: CacheBinding?,
        transition: AccountTransition?,
    ) {
        this.binding = binding
    }

    override suspend fun <T> replaceCacheAndPersist(
        binding: CacheBinding?,
        transition: AccountTransition?,
        clearCache: suspend () -> T,
    ): T {
        val result = clearCache()
        this.binding = binding
        return result
    }

    override suspend fun readLegacyCacheIdentity(): LegacyCacheIdentity = LegacyCacheIdentity(null, null)
    override suspend fun readLastBoundaryEpoch(): Long = binding?.boundaryEpoch ?: 0L
}

private class NonReentrantTestAccountMutationGate(
    private val afterFirstRelease: () -> Unit = {},
) : AccountMutationGate {
    private val mutex = Mutex()
    private var completedCalls = 0

    override suspend fun <T> withExclusive(block: suspend () -> T): T {
        val result = mutex.withLock { block() }
        completedCalls++
        if (completedCalls == 1) afterFirstRelease()
        return result
    }
}
