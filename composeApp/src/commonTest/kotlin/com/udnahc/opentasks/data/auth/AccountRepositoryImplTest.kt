package com.udnahc.opentasks.data.auth

import com.udnahc.opentasks.data.notification.ReminderIdentity
import com.udnahc.opentasks.data.notification.ReminderRequest
import com.udnahc.opentasks.data.notification.ReminderScheduler
import com.udnahc.opentasks.data.settings.AccountStateStore
import com.udnahc.opentasks.data.settings.LegacyCacheIdentity
import com.udnahc.opentasks.data.sync.PocketBaseClientProvider
import com.udnahc.opentasks.data.sync.canonicalUrl
import io.github.agrevster.pocketbaseKotlin.PocketbaseClient
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AccountRepositoryImplTest {
    @Test
    fun loginPersistsBindingBeforePromotingTokenAndDoesNotPersistPassword() = runTest {
        val events = mutableListOf<String>()
        val fixture = fixture(events = events)
        val password = "not-persisted-password"

        val result = fixture.repository.login(
            endpoint = "https://tasks.example.com/",
            email = "a@example.com",
            password = password,
        )

        assertTrue(result is AccountSessionState.Authenticated)
        assertEquals("https://tasks.example.com:443", fixture.state.binding?.canonicalEndpoint)
        assertEquals("token-account-a", fixture.tokens.active)
        assertNull(fixture.tokens.pending)
        assertNull(fixture.state.transition)
        assertTrue(events.indexOf("persist") < events.indexOf("promote"))
        assertTrue(events.indexOf("promote") < events.indexOf("initial-pull"))
        assertFalse(fixture.state.serializedBoundary().contains(password))
        assertFalse(fixture.tokens.events.any { it.contains(password) })
    }

    @Test
    fun restoreRefreshesOnlineAndPromotesTheRefreshedActiveToken() = runTest {
        val binding = bindingFor("account-a")
        val refreshed = credential("account-a", "refreshed-token")
        val fixture = fixture(
            binding = binding,
            activeToken = "old-token",
            refreshResponses = mapOf("old-token" to refreshed),
        )

        val result = fixture.repository.restoreSession()

        assertTrue(result is AccountSessionState.Authenticated)
        assertEquals(AccountSessionFreshness.ONLINE, result.freshness)
        assertEquals("refreshed-token", fixture.tokens.active)
        assertNull(fixture.tokens.pending)
        assertEquals(binding, fixture.provider.activeBinding)
    }

    @Test
    fun restoreReusesTheAuthenticatedSessionWithoutRefreshingAgain() = runTest {
        val binding = bindingFor("account-a")
        val events = mutableListOf<String>()
        val fixture = fixture(
            events = events,
            binding = binding,
            activeToken = "old-token",
            refreshResponses = mapOf("old-token" to credential("account-a", "refreshed-token")),
        )

        val first = fixture.repository.restoreSession()
        val second = fixture.repository.restoreSession()

        assertTrue(first is AccountSessionState.Authenticated)
        assertEquals(first, second)
        assertEquals(listOf("refresh:old-token"), events.filter { it.startsWith("refresh:") })
    }

    @Test
    fun restoreAllowsOfflineUseOnlyForAProvenBoundCache() = runTest {
        val binding = bindingFor("account-a")
        val fixture = fixture(
            binding = binding,
            activeToken = "offline-token",
            refreshFailures = mapOf("offline-token" to connectivityFailure()),
        )

        val result = fixture.repository.restoreSession()

        assertTrue(result is AccountSessionState.Authenticated)
        assertEquals(AccountSessionFreshness.OFFLINE, result.freshness)
        assertEquals("offline-token", fixture.tokens.active)
        assertEquals(binding, fixture.provider.activeBinding)
    }

    @Test
    fun rejectedRestoreClearsTokenSlotsAndRequiresReauthentication() = runTest {
        val binding = bindingFor("account-a")
        val fixture = fixture(
            binding = binding,
            activeToken = "revoked-token",
            pendingToken = "stale-pending-token",
            refreshFailures = mapOf("revoked-token" to AccountAuthenticationRejectedException()),
        )

        val result = fixture.repository.restoreSession()

        assertEquals(
            AccountReauthenticationReason.AUTHENTICATION_REJECTED,
            (result as AccountSessionState.ReauthenticationRequired).reason,
        )
        assertEquals("account-a", result.account?.accountId)
        assertNull(fixture.tokens.active)
        assertNull(fixture.tokens.pending)
        assertNull(fixture.provider.activeBinding)
    }

    @Test
    fun restoreWithNoActiveTokenDoesNotUsePendingTokenAsAuthenticatedState() = runTest {
        val binding = bindingFor("account-a")
        val fixture = fixture(
            binding = binding,
            pendingToken = "destination-token",
        )

        val result = fixture.repository.restoreSession()

        assertEquals(AccountReauthenticationReason.TOKEN_UNAVAILABLE, (result as AccountSessionState.ReauthenticationRequired).reason)
        assertNull(fixture.tokens.active)
        assertNull(fixture.tokens.pending)
        assertNull(fixture.provider.activeBinding)
    }

    @Test
    fun restoreRejectsCapabilityOrAccountMismatch() = runTest {
        val binding = bindingFor("account-a")
        val fixture = fixture(
            binding = binding,
            activeToken = "mismatched-token",
            refreshResponses = mapOf("mismatched-token" to credential("account-b", "account-b-token")),
        )

        val result = fixture.repository.restoreSession()

        assertEquals(AccountReauthenticationReason.CAPABILITY_MISMATCH, (result as AccountSessionState.ReauthenticationRequired).reason)
        assertNull(fixture.tokens.active)
        assertNull(fixture.provider.activeBinding)
    }

    @Test
    fun matchingLegacyOwnerAdoptsARecordBearingCacheWithoutResettingIt() = runTest {
        val fixture = fixture(
            snapshot = LegacyCacheSnapshot(unsyncedRowCount = 0, isPristineInboxOnly = false),
            legacyIdentity = LegacyCacheIdentity("https://tasks.example.com", "server"),
        )

        val result = fixture.repository.login("https://tasks.example.com", "a@example.com", "password")

        assertTrue(result is AccountSessionState.Authenticated)
        assertEquals(0, fixture.resetter.resetCalls)
        assertEquals(0, fixture.resetter.replaceCalls)
    }

    @Test
    fun unprovenLegacyCacheWithUnsyncedRowsBlocksWithoutResetOrUpload() = runTest {
        val fixture = fixture(
            snapshot = LegacyCacheSnapshot(unsyncedRowCount = 1, isPristineInboxOnly = false),
            legacyIdentity = LegacyCacheIdentity("https://tasks.example.com", "other-server"),
        )

        assertFailsWith<LegacyCacheOwnershipException> {
            fixture.repository.login("https://tasks.example.com", "a@example.com", "password")
        }

        assertEquals(0, fixture.resetter.resetCalls)
        assertEquals(0, fixture.sync.initialPullCalls)
        assertNull(fixture.tokens.pending)
        assertNull(fixture.state.binding)
    }

    @Test
    fun fullySyncedUnprovenLegacyCacheIsResetBeforeFirstActivation() = runTest {
        val fixture = fixture(
            snapshot = LegacyCacheSnapshot(unsyncedRowCount = 0, isPristineInboxOnly = false),
            legacyIdentity = LegacyCacheIdentity("https://tasks.example.com", "other-server"),
        )

        val result = fixture.repository.login("https://tasks.example.com", "a@example.com", "password")

        assertTrue(result is AccountSessionState.Authenticated)
        assertEquals(1, fixture.resetter.resetCalls)
        assertEquals(1, fixture.sync.initialPullCalls)
    }

    @Test
    fun pristineLegacyCacheCanBeAdoptedWithoutOwnershipProof() = runTest {
        val fixture = fixture(
            loginCredential = credential("account-b", "token-account-b", legacyOwnerAccount = "account-a"),
            snapshot = LegacyCacheSnapshot(unsyncedRowCount = 0, isPristineInboxOnly = true),
            legacyIdentity = LegacyCacheIdentity("https://other.example.com", "other-server"),
        )

        val result = fixture.repository.login("https://tasks.example.com", "b@example.com", "password")

        assertTrue(result is AccountSessionState.Authenticated)
        assertEquals("account-b", fixture.state.binding?.accountId)
        assertEquals(0, fixture.resetter.resetCalls)
        assertEquals(1, fixture.sync.initialPullCalls)
    }

    @Test
    fun switchRequiresACleanSourceAndOrdersDestinationActivationAfterCacheReplacement() = runTest {
        val sourceBinding = bindingFor("account-a", boundaryEpoch = 4L)
        val destination = credential("account-b", "destination-token")
        val sourceRefresh = credential("account-a", "source-refreshed-token")
        val events = mutableListOf<String>()
        val fixture = fixture(
            events = events,
            binding = sourceBinding,
            activeToken = "source-token",
            loginCredential = destination,
            refreshResponses = mapOf("source-token" to sourceRefresh),
        )

        val result = fixture.repository.switchAccount(
            endpoint = "https://tasks.example.com/",
            email = "b@example.com",
            password = "password",
        )

        assertTrue(result is AccountSessionState.Authenticated)
        assertEquals("account-b", fixture.state.binding?.accountId)
        assertEquals(5L, fixture.state.binding?.boundaryEpoch)
        assertEquals("destination-token", fixture.tokens.active)
        assertNull(fixture.tokens.pending)
        assertNull(fixture.state.transition)
        assertEquals(1, fixture.scheduler.cancelAllCalls)
        assertEquals(1, fixture.resetter.clearAttachmentCalls)
        assertTrue(events.indexOf("sync") < events.indexOf("replace"))
        assertTrue(events.indexOf("replace") < events.indexOf("promote"))
        assertTrue(events.indexOf("promote") < events.indexOf("initial-pull"))
    }

    @Test
    fun switchBlocksBeforeDestructiveCleanupWhenSourceHasUnsyncedRows() = runTest {
        val fixture = fixture(
            binding = bindingFor("account-a"),
            activeToken = "source-token",
            loginCredential = credential("account-b", "destination-token"),
            refreshResponses = mapOf("source-token" to credential("account-a", "source-refreshed-token")),
            snapshot = LegacyCacheSnapshot(unsyncedRowCount = 1, isPristineInboxOnly = false),
        )

        assertFailsWith<AccountTransitionBlockedException> {
            fixture.repository.switchAccount("https://tasks.example.com", "b@example.com", "password")
        }

        assertEquals("account-a", fixture.state.binding?.accountId)
        assertEquals("source-refreshed-token", fixture.tokens.active)
        assertNull(fixture.tokens.pending)
        assertEquals(0, fixture.resetter.replaceCalls)
        assertEquals(0, fixture.scheduler.cancelAllCalls)
    }

    @Test
    fun logoutRequiresCleanSourceAndRetainsTheCanonicalInstallationEndpoint() = runTest {
        val binding = bindingFor("account-a")
        val fixture = fixture(
            binding = binding,
            activeToken = "source-token",
            refreshResponses = mapOf("source-token" to credential("account-a", "source-refreshed-token")),
        )

        val result = fixture.repository.logout()

        assertEquals(AccountSessionState.SignedOut, result)
        assertNull(fixture.state.binding)
        assertNull(fixture.state.transition)
        assertEquals(binding.canonicalEndpoint, fixture.state.installationEndpoint)
        assertNull(fixture.tokens.active)
        assertNull(fixture.tokens.pending)
        assertNull(fixture.provider.activeBinding)
        assertEquals(1, fixture.scheduler.cancelAllCalls)
        assertEquals(1, fixture.resetter.replaceCalls)
        assertEquals(1, fixture.resetter.clearAttachmentCalls)
    }

    @Test
    fun logoutBlocksWithoutClearingAnUnsyncedSourceCache() = runTest {
        val binding = bindingFor("account-a")
        val fixture = fixture(
            binding = binding,
            activeToken = "source-token",
            refreshResponses = mapOf("source-token" to credential("account-a", "source-refreshed-token")),
            snapshot = LegacyCacheSnapshot(unsyncedRowCount = 1, isPristineInboxOnly = false),
        )

        assertFailsWith<AccountTransitionBlockedException> { fixture.repository.logout() }

        assertEquals(binding, fixture.state.binding)
        assertEquals("source-refreshed-token", fixture.tokens.active)
        assertEquals(0, fixture.resetter.replaceCalls)
        assertEquals(0, fixture.scheduler.cancelAllCalls)
    }

    @Test
    fun preparedTransitionRecoveryDropsDestinationTokenAndRestoresSource() = runTest {
        val sourceBinding = bindingFor("account-a", boundaryEpoch = 3L)
        val prepared = AccountTransition(
            sourceAccountId = "account-a",
            destinationAccountId = "account-b",
            canonicalEndpoint = sourceBinding.canonicalEndpoint,
            serverInstanceId = sourceBinding.serverInstanceId,
            capabilityVersion = sourceBinding.capabilityVersion,
            boundaryEpoch = 4L,
            phase = AccountTransitionPhase.PREPARED,
        )
        val fixture = fixture(
            binding = sourceBinding,
            transition = prepared,
            activeToken = "source-token",
            pendingToken = "destination-token",
            refreshResponses = mapOf("source-token" to credential("account-a", "source-refreshed-token")),
        )

        val result = fixture.repository.restoreSession()

        assertTrue(result is AccountSessionState.Authenticated)
        assertEquals("account-a", result.account.accountId)
        assertEquals(sourceBinding, fixture.state.binding)
        assertNull(fixture.state.transition)
        assertEquals("source-refreshed-token", fixture.tokens.active)
        assertNull(fixture.tokens.pending)
    }

    @Test
    fun needsActivationRecoveryTreatsDestinationBindingAndPendingTokenAsAuthoritative() = runTest {
        val destinationBinding = bindingFor("account-b", boundaryEpoch = 8L)
        val needsActivation = AccountTransition(
            sourceAccountId = "account-a",
            destinationAccountId = "account-b",
            canonicalEndpoint = destinationBinding.canonicalEndpoint,
            serverInstanceId = destinationBinding.serverInstanceId,
            capabilityVersion = destinationBinding.capabilityVersion,
            boundaryEpoch = destinationBinding.boundaryEpoch,
            phase = AccountTransitionPhase.NEEDS_ACTIVATION,
        )
        val events = mutableListOf<String>()
        val fixture = fixture(
            events = events,
            binding = destinationBinding,
            transition = needsActivation,
            activeToken = "source-token",
            pendingToken = "destination-token",
            refreshResponses = mapOf("destination-token" to credential("account-b", "destination-refreshed-token")),
        )

        val result = fixture.repository.restoreSession()

        assertTrue(result is AccountSessionState.Authenticated)
        assertEquals("account-b", result.account.accountId)
        assertEquals(destinationBinding, fixture.provider.activeBinding)
        assertEquals("destination-refreshed-token", fixture.tokens.active)
        assertNull(fixture.tokens.pending)
        assertNull(fixture.state.transition)
        assertTrue(events.indexOf("promote-active") < events.indexOf("initial-pull"))
        assertTrue(fixture.resetter.clearAttachmentCalls > 0)
    }

    @Test
    fun accountBoundarySerializationRoundTripsWithoutSecrets() {
        val binding = bindingFor("account-a")
        val transition = AccountTransition(
            sourceAccountId = "account-a",
            destinationAccountId = "account-b",
            canonicalEndpoint = binding.canonicalEndpoint,
            serverInstanceId = binding.serverInstanceId,
            capabilityVersion = binding.capabilityVersion,
            boundaryEpoch = 2L,
            phase = AccountTransitionPhase.NEEDS_ACTIVATION,
        )
        val json = Json { explicitNulls = false }
        val bindingRaw = json.encodeToString(CacheBinding.serializer(), binding)
        val transitionRaw = json.encodeToString(AccountTransition.serializer(), transition)

        assertEquals(binding, json.decodeFromString<CacheBinding>(bindingRaw))
        assertEquals(transition, json.decodeFromString<AccountTransition>(transitionRaw))
        assertFalse(bindingRaw.contains("password"))
        assertFalse(transitionRaw.contains("token"))
    }

    @Test
    fun accountMutationGateSerializesConcurrentCriticalSections() = runTest {
        val gate = MutexAccountMutationGate()
        val firstEntered = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        val events = mutableListOf<String>()

        val first = async {
            gate.withExclusive {
                events += "first-start"
                firstEntered.complete(Unit)
                releaseFirst.await()
                events += "first-end"
            }
        }
        firstEntered.await()
        val second = async {
            gate.withExclusive { events += "second" }
        }

        assertEquals(listOf("first-start"), events)
        releaseFirst.complete(Unit)
        first.await()
        second.await()
        assertEquals(listOf("first-start", "first-end", "second"), events)
    }

    private fun fixture(
        events: MutableList<String> = mutableListOf(),
        binding: CacheBinding? = null,
        transition: AccountTransition? = null,
        activeToken: String? = null,
        pendingToken: String? = null,
        loginCredential: AccountCredential = credential("account-a", "token-account-a"),
        refreshResponses: Map<String, AccountCredential> = emptyMap(),
        refreshFailures: Map<String, Throwable> = emptyMap(),
        snapshot: LegacyCacheSnapshot = LegacyCacheSnapshot(0, true),
        legacyIdentity: LegacyCacheIdentity = LegacyCacheIdentity(null, null),
    ): Fixture {
        val state = InMemoryAccountStateStore(binding, transition, legacyIdentity, events)
        val tokens = FakeAuthTokenStore(activeToken, pendingToken, events)
        val authenticator = FakeAccountAuthenticator(loginCredential, refreshResponses, refreshFailures, events)
        val inspector = FakeAccountCacheInspector(snapshot)
        val resetter = FakeAccountCacheResetter(state, events)
        val sync = FakeAccountSyncCoordinator(events)
        val scheduler = FakeReminderScheduler()
        val provider = PocketBaseClientProvider()
        if (binding != null && state.installationEndpoint == null) {
            state.installationEndpoint = binding.canonicalEndpoint
        }
        return Fixture(
            state = state,
            tokens = tokens,
            resetter = resetter,
            sync = sync,
            scheduler = scheduler,
            provider = provider,
            repository = AccountRepositoryImpl(
                tokenStore = tokens,
                stateStore = state,
                authenticator = authenticator,
                cacheInspector = inspector,
                cacheResetter = resetter,
                mutationGate = MutexAccountMutationGate(),
                pbProvider = provider,
                syncService = sync,
                reminderScheduler = scheduler,
            ),
        )
    }

    private data class Fixture(
        val state: InMemoryAccountStateStore,
        val tokens: FakeAuthTokenStore,
        val resetter: FakeAccountCacheResetter,
        val sync: FakeAccountSyncCoordinator,
        val scheduler: FakeReminderScheduler,
        val provider: PocketBaseClientProvider,
        val repository: AccountRepositoryImpl,
    )
}

private fun bindingFor(
    accountId: String,
    boundaryEpoch: Long = 1L,
    endpoint: String = "https://tasks.example.com:443",
    serverInstanceId: String = "server",
    capabilityVersion: Int = 2,
): CacheBinding = CacheBinding(
    canonicalEndpoint = endpoint,
    serverInstanceId = serverInstanceId,
    accountId = accountId,
    capabilityVersion = capabilityVersion,
    boundaryEpoch = boundaryEpoch,
)

private fun credential(
    accountId: String,
    token: String,
    endpoint: String = "https://tasks.example.com",
    serverInstanceId: String = "server",
    capabilityVersion: Int = 2,
    legacyOwnerAccount: String = accountId,
): AccountCredential {
    val canonicalEndpoint = canonicalizeAccountEndpoint(endpoint)
    return AccountCredential(
        account = AuthenticatedAccount(accountId, "$accountId@example.com"),
        endpoint = canonicalEndpoint,
        token = token,
        capability = AccountCapability(
            capabilityVersion = capabilityVersion,
            serverInstanceId = serverInstanceId,
            legacyOwnerAccount = legacyOwnerAccount,
            legacyEndpoint = canonicalEndpoint.canonicalUrl,
            scopedRecordCounts = emptyMap(),
        ),
    )
}

private fun connectivityFailure(): AccountConnectivityException =
    AccountConnectivityException(IllegalStateException("offline"))

private class InMemoryAccountStateStore(
    var binding: CacheBinding? = null,
    var transition: AccountTransition? = null,
    private val legacyIdentity: LegacyCacheIdentity,
    private val events: MutableList<String>,
) : AccountStateStore {
    var installationEndpoint: String? = legacyIdentity.canonicalEndpoint
    private var lastBoundaryEpoch: Long = maxOf(binding?.boundaryEpoch ?: 0L, transition?.boundaryEpoch ?: 0L)

    override suspend fun readCacheBinding(): CacheBinding? = binding

    override suspend fun writeCacheBinding(binding: CacheBinding) {
        events += "write-binding"
        this.binding = binding
        installationEndpoint = binding.canonicalEndpoint
        lastBoundaryEpoch = maxOf(lastBoundaryEpoch, binding.boundaryEpoch)
    }

    override suspend fun clearCacheBinding() {
        events += "clear-binding"
        binding = null
    }

    override suspend fun readTransition(): AccountTransition? = transition

    override suspend fun writeTransition(transition: AccountTransition) {
        events += "write-transition-${transition.phase}"
        this.transition = transition
        lastBoundaryEpoch = maxOf(lastBoundaryEpoch, transition.boundaryEpoch)
    }

    override suspend fun clearTransition() {
        events += "clear-transition"
        transition = null
    }

    override suspend fun persistBindingAndTransition(
        binding: CacheBinding?,
        transition: AccountTransition?,
    ) {
        events += "persist"
        this.binding = binding
        this.transition = transition
        if (binding != null) installationEndpoint = binding.canonicalEndpoint
        lastBoundaryEpoch = maxOf(lastBoundaryEpoch, binding?.boundaryEpoch ?: 0L, transition?.boundaryEpoch ?: 0L)
    }

    override suspend fun <T> replaceCacheAndPersist(
        binding: CacheBinding?,
        transition: AccountTransition?,
        clearCache: suspend () -> T,
    ): T {
        val result = clearCache()
        persistBindingAndTransition(binding, transition)
        return result
    }

    override suspend fun readLegacyCacheIdentity(): LegacyCacheIdentity = legacyIdentity

    override suspend fun readLastBoundaryEpoch(): Long = lastBoundaryEpoch

    fun serializedBoundary(): String = buildString {
        append(binding?.let { Json.encodeToString(CacheBinding.serializer(), it) }.orEmpty())
        append(transition?.let { Json.encodeToString(AccountTransition.serializer(), it) }.orEmpty())
    }
}

private class FakeAuthTokenStore(
    var active: String? = null,
    var pending: String? = null,
    val events: MutableList<String>,
) : AuthTokenStore {
    override suspend fun readActiveToken(): String? = active

    override suspend fun writeActiveToken(token: String) {
        active = token
        events += "write-active:$token"
    }

    override suspend fun clearActiveToken() {
        active = null
        events += "clear-active"
    }

    override suspend fun readPendingToken(): String? = pending

    override suspend fun writePendingToken(token: String) {
        pending = token
        events += "write-pending:$token"
    }

    override suspend fun clearPendingToken() {
        pending = null
        events += "clear-pending"
    }

    override suspend fun promotePendingToken() {
        active = pending
        pending = null
        events += "promote"
        events += "promote-active"
    }

    override suspend fun clearAllTokens() {
        active = null
        pending = null
        events += "clear-all-tokens"
    }
}

private class FakeAccountAuthenticator(
    private val loginCredential: AccountCredential,
    private val refreshResponses: Map<String, AccountCredential>,
    private val refreshFailures: Map<String, Throwable>,
    private val events: MutableList<String>,
) : AccountAuthenticator {
    override suspend fun authenticate(
        endpoint: com.udnahc.opentasks.data.sync.PocketBaseEndpoint,
        email: String,
        password: String,
    ): AccountCredential {
        events += "authenticate:$email"
        return loginCredential
    }

    override suspend fun refresh(
        endpoint: com.udnahc.opentasks.data.sync.PocketBaseEndpoint,
        token: String,
    ): AccountCredential {
        events += "refresh:$token"
        refreshFailures[token]?.let { throw it }
        return refreshResponses[token] ?: error("No refresh response for token $token")
    }
}

private class FakeAccountCacheInspector(
    private val snapshot: LegacyCacheSnapshot,
) : AccountCacheInspectorContract {
    override suspend fun inspect(): LegacyCacheSnapshot = snapshot
}

private class FakeAccountCacheResetter(
    private val state: InMemoryAccountStateStore,
    private val events: MutableList<String>,
) : AccountCacheResetterContract {
    var resetCalls = 0
    var replaceCalls = 0
    var clearAttachmentCalls = 0

    override suspend fun resetWithinMutation() {
        resetCalls += 1
        events += "reset"
    }

    override suspend fun replaceCacheWithinMutation(
        binding: CacheBinding?,
        transition: AccountTransition?,
    ) {
        replaceCalls += 1
        events += "replace"
        state.persistBindingAndTransition(binding, transition)
    }

    override suspend fun clearAttachmentFilesWithinMutation() {
        clearAttachmentCalls += 1
        events += "clear-files"
    }
}

private class FakeAccountSyncCoordinator(
    private val events: MutableList<String>,
) : AccountSyncCoordinator {
    var syncCalls = 0
    var initialPullCalls = 0

    override suspend fun syncAllWithinMutation(client: PocketbaseClient) {
        syncCalls += 1
        events += "sync"
    }

    override suspend fun initialPullWithinMutation(client: PocketbaseClient) {
        initialPullCalls += 1
        events += "initial-pull"
    }
}

private class FakeReminderScheduler : ReminderScheduler {
    var cancelAllCalls = 0

    override suspend fun schedule(request: ReminderRequest) = Unit

    override suspend fun cancel(semanticKey: String) = Unit

    override suspend fun cancelPendingReminders(eventId: String) = Unit

    override suspend fun cancelReminders(eventId: String) = Unit

    override suspend fun cancelAll(eventId: String) = Unit

    override suspend fun startOngoing(identity: ReminderIdentity, title: String) = Unit

    override suspend fun stopOngoing(eventId: String) = Unit

    override suspend fun cancelAllAccountReminders() {
        cancelAllCalls += 1
    }
}
