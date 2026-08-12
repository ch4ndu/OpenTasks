package com.udnahc.opentasks.viewmodel

import com.udnahc.opentasks.data.auth.AccountRepository
import com.udnahc.opentasks.data.auth.AccountSessionFreshness
import com.udnahc.opentasks.data.auth.AccountSessionState
import com.udnahc.opentasks.data.auth.AuthTokenStore
import com.udnahc.opentasks.data.auth.AuthenticatedAccount
import com.udnahc.opentasks.data.auth.CacheBinding
import com.udnahc.opentasks.data.auth.LocalServerReplacementConfirmation
import com.udnahc.opentasks.data.sync.AuthoritativeLocalSeedSourceException
import com.udnahc.opentasks.domain.action.account.ConfirmLocalServerReplacementAction
import com.udnahc.opentasks.domain.action.account.LoginAccountAction
import com.udnahc.opentasks.domain.action.account.LogoutAccountAction
import com.udnahc.opentasks.domain.action.account.ReauthenticateAccountAction
import com.udnahc.opentasks.domain.action.account.RestoreSessionAction
import com.udnahc.opentasks.domain.action.account.SwitchAccountAction
import com.udnahc.opentasks.domain.action.account.StartLocalOnlyAction
import com.udnahc.opentasks.domain.action.settings.ClearLocalDataAction
import com.udnahc.opentasks.domain.usecase.account.ObserveAccountSessionUseCase
import com.udnahc.opentasks.domain.usecase.settings.ObservePocketBaseUrlUseCase
import com.udnahc.opentasks.testutil.FakeAppSettingsRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelTest : MainDispatcherRule() {
    @Test
    fun concurrentAccountOperationsKeepOnlyTheFirstRequest() = runTest(dispatcher) {
        val releaseLogin = CompletableDeferred<Unit>()
        val repository = FakeAccountRepository(loginGate = releaseLogin)
        val viewModel = authViewModel(repository)

        viewModel.login("https://tasks.example.com", "first@example.com", "first-password")
        runCurrent()
        viewModel.login("https://tasks.example.com", "second@example.com", "second-password")

        assertEquals(1, repository.loginRequests.size)
        assertEquals("first@example.com", repository.loginRequests.single().email)
        assertEquals(AccountOperation.SIGNING_IN, viewModel.operation.value)

        releaseLogin.complete(Unit)
        advanceUntilIdle()

        assertNull(viewModel.operation.value)
    }

    @Test
    fun sameAccountReauthenticationUsesTheReauthenticationAction() = runTest(dispatcher) {
        val repository = FakeAccountRepository(initialState = authenticatedState())
        val viewModel = authViewModel(repository)

        viewModel.reauthenticate("a@example.com", "fresh-password")
        advanceUntilIdle()

        assertEquals(
            listOf(AccountRequest(email = "a@example.com", password = "fresh-password")),
            repository.reauthenticateRequests,
        )
        assertTrue(repository.switchRequests.isEmpty())
        assertEquals("account-a", (viewModel.sessionState.value as AccountSessionState.Authenticated).account.accountId)
        assertNull(viewModel.operation.value)
    }

    @Test
    fun accountControlsForwardSwitchWithTheAuthenticatedCanonicalEndpointAndLogout() = runTest(dispatcher) {
        val repository = FakeAccountRepository(initialState = authenticatedState())
        val viewModel = authViewModel(repository)

        viewModel.switchAccount("b@example.com", "destination-password")
        advanceUntilIdle()
        viewModel.logout()
        advanceUntilIdle()

        assertEquals(
            listOf(
                SwitchRequest(
                    endpoint = "https://tasks.example.com:443",
                    email = "b@example.com",
                    password = "destination-password",
                ),
            ),
            repository.switchRequests,
        )
        assertEquals(1, repository.logoutCalls)
    }

    @Test
    fun failedRestoreRemainsFailClosedAndExposesTheRestoreError() = runTest(dispatcher) {
        val repository = FakeAccountRepository(
            initialState = AccountSessionState.Restoring,
            restoreFailure = IllegalStateException("restore failed"),
        )
        val viewModel = authViewModel(repository)

        viewModel.restoreSession()
        advanceUntilIdle()

        assertEquals(AccountSessionState.Restoring, viewModel.sessionState.value)
        assertEquals(AccountUiError.SESSION_RESTORE_FAILED, viewModel.error.value)
        assertNull(viewModel.operation.value)
    }

    @Test
    fun localSeedSourceFailureMapsToActionableAccountError() = runTest(dispatcher) {
        val repository = FakeAccountRepository(
            confirmationFailure = AuthoritativeLocalSeedSourceException(),
        )
        val viewModel = authViewModel(repository)

        viewModel.confirmLocalServerReplacement()
        advanceUntilIdle()

        assertEquals(AccountUiError.LOCAL_SEED_SOURCE_INVALID, viewModel.error.value)
        assertNull(viewModel.operation.value)
    }

    private fun authViewModel(repository: FakeAccountRepository): AuthViewModel = AuthViewModel(
        observeAccountSession = ObserveAccountSessionUseCase(repository),
        observePocketBaseUrl = ObservePocketBaseUrlUseCase(FakeAppSettingsRepository()),
        restoreSessionAction = RestoreSessionAction(repository),
        loginAccountAction = LoginAccountAction(repository),
        reauthenticateAccountAction = ReauthenticateAccountAction(repository),
        switchAccountAction = SwitchAccountAction(repository),
        logoutAccountAction = LogoutAccountAction(repository),
        startLocalOnlyAction = StartLocalOnlyAction(repository),
        clearLocalDataAction = ClearLocalDataAction(repository),
        tokenStore = NoOpAuthTokenStore,
        confirmLocalServerReplacementAction = ConfirmLocalServerReplacementAction(repository),
    )
}

private fun authenticatedState(): AccountSessionState.Authenticated {
    val account = AuthenticatedAccount(
        accountId = "account-a",
        email = "a@example.com",
    )
    return AccountSessionState.Authenticated(
        account = account,
        binding = CacheBinding(
            canonicalEndpoint = "https://tasks.example.com:443",
            serverInstanceId = "server-1",
            accountId = account.accountId,
            capabilityVersion = 2,
            boundaryEpoch = 4,
        ),
        freshness = AccountSessionFreshness.ONLINE,
    )
}

private data class AccountRequest(
    val email: String,
    val password: String,
)

private data class SwitchRequest(
    val endpoint: String,
    val email: String,
    val password: String,
)

private class FakeAccountRepository(
    initialState: AccountSessionState = AccountSessionState.SignedOut,
    private val loginGate: CompletableDeferred<Unit>? = null,
    private val restoreFailure: Throwable? = null,
    private val confirmationFailure: Throwable? = null,
) : AccountRepository {
    private val state = MutableStateFlow(initialState)
    override val sessionState: StateFlow<AccountSessionState> = state.asStateFlow()

    val loginRequests = mutableListOf<AccountRequest>()
    val reauthenticateRequests = mutableListOf<AccountRequest>()
    val switchRequests = mutableListOf<SwitchRequest>()
    var logoutCalls = 0

    override suspend fun restoreSession(): AccountSessionState {
        restoreFailure?.let { throw it }
        return state.value
    }

    override suspend fun startLocalOnly(): AccountSessionState = state.value

    override suspend fun clearLocalData(): AccountSessionState = state.value

    override suspend fun confirmLocalServerReplacement(): LocalServerReplacementConfirmation {
        confirmationFailure?.let { throw it }
        return LocalServerReplacementConfirmation.Started
    }

    override suspend fun login(
        endpoint: String,
        email: String,
        password: String,
    ): AccountSessionState {
        loginRequests += AccountRequest(email, password)
        loginGate?.await()
        return state.value
    }

    override suspend fun reauthenticate(email: String, password: String): AccountSessionState {
        reauthenticateRequests += AccountRequest(email, password)
        return state.value
    }

    override suspend fun switchAccount(
        endpoint: String,
        email: String,
        password: String,
    ): AccountSessionState {
        switchRequests += SwitchRequest(endpoint, email, password)
        return state.value
    }

    override suspend fun logout(): AccountSessionState {
        logoutCalls += 1
        return state.value
    }
}

private object NoOpAuthTokenStore : AuthTokenStore {
    override suspend fun readActiveToken(): String? = null

    override suspend fun writeActiveToken(token: String) = Unit

    override suspend fun clearActiveToken() = Unit

    override suspend fun readPendingToken(): String? = null

    override suspend fun writePendingToken(token: String) = Unit

    override suspend fun clearPendingToken() = Unit

    override suspend fun promotePendingToken() = Unit

    override suspend fun clearAllTokens() = Unit
}
