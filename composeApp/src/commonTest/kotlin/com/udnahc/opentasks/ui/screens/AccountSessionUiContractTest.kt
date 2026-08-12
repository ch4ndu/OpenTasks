package com.udnahc.opentasks.ui.screens

import com.udnahc.opentasks.data.auth.AccountReauthenticationReason
import com.udnahc.opentasks.data.auth.AccountSessionFreshness
import com.udnahc.opentasks.data.auth.AccountSessionState
import com.udnahc.opentasks.data.auth.AccountTransition
import com.udnahc.opentasks.data.auth.AccountTransitionPhase
import com.udnahc.opentasks.data.auth.AuthenticatedAccount
import com.udnahc.opentasks.data.auth.CacheBinding
import com.udnahc.opentasks.data.auth.CacheMode
import com.udnahc.opentasks.data.auth.LOCAL_CACHE_OWNER_ID
import com.udnahc.opentasks.ui.theme.WindowSizeCategory
import com.udnahc.opentasks.viewmodel.AccountOperation
import com.udnahc.opentasks.viewmodel.AccountUiError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AccountSessionUiContractTest {
    private val account = AuthenticatedAccount(
        accountId = "account-a",
        email = "a@example.com",
        displayName = "Account A",
    )
    private val binding = CacheBinding(
        canonicalEndpoint = "https://tasks.example.com:443",
        serverInstanceId = "server-1",
        accountId = account.accountId,
        capabilityVersion = 2,
        boundaryEpoch = 7,
    )

    @Test
    fun startupSessionStatesRouteToTheCorrectAccountSurface() {
        val transition = AccountTransition(
            sourceAccountId = account.accountId,
            destinationAccountId = "account-b",
            canonicalEndpoint = binding.canonicalEndpoint,
            serverInstanceId = binding.serverInstanceId,
            capabilityVersion = binding.capabilityVersion,
            boundaryEpoch = binding.boundaryEpoch + 1,
            phase = AccountTransitionPhase.PREPARED,
        )

        val routes = listOf(
            AccountSessionState.Restoring to AccountSessionRoute.RESTORING,
            AccountSessionState.SignedOut to AccountSessionRoute.SIGN_IN,
            AccountSessionState.ReauthenticationRequired(
                account = null,
                reason = AccountReauthenticationReason.CACHE_BINDING_MISSING,
            ) to AccountSessionRoute.SIGN_IN,
            AccountSessionState.ReauthenticationRequired(
                account = account,
                reason = AccountReauthenticationReason.AUTHENTICATION_REJECTED,
                canonicalEndpoint = binding.canonicalEndpoint,
            ) to AccountSessionRoute.REAUTHENTICATE,
            AccountSessionState.Transitioning(transition) to AccountSessionRoute.TRANSITIONING,
            AccountSessionState.Authenticated(
                account = account,
                binding = binding,
                freshness = AccountSessionFreshness.ONLINE,
            ) to AccountSessionRoute.ACTIVE,
            AccountSessionState.LocalOnly(
                CacheBinding(
                    canonicalEndpoint = "",
                    serverInstanceId = "",
                    accountId = LOCAL_CACHE_OWNER_ID,
                    capabilityVersion = 0,
                    boundaryEpoch = 9L,
                    mode = CacheMode.LOCAL_ONLY,
                )
            ) to AccountSessionRoute.ACTIVE,
        )

        routes.forEach { (state, expectedRoute) ->
            assertEquals(expectedRoute, accountSessionRoute(state))
        }
    }

    @Test
    fun signInValidationRequiresEndpointEmailAndPasswordAndRejectsBusyOperations() {
        val mode = AccountSessionEntryMode.SIGN_IN

        assertFalse(canSubmitAccountSession(mode, "", "a@example.com", "password", isBusy = false))
        assertFalse(canSubmitAccountSession(mode, "https://tasks.example.com", "", "password", false))
        assertFalse(canSubmitAccountSession(mode, "https://tasks.example.com", "a@example.com", "", false))
        assertFalse(canSubmitAccountSession(mode, "https://tasks.example.com", "a@example.com", "password", true))
        assertTrue(canSubmitAccountSession(mode, "https://tasks.example.com", "a@example.com", "password", false))
    }

    @Test
    fun reauthenticationValidationKeepsEndpointReadOnlyAndDoesNotRequireItAsInput() {
        val mode = AccountSessionEntryMode.REAUTHENTICATE

        assertTrue(canSubmitAccountSession(mode, "", "a@example.com", "password", isBusy = false))
        assertFalse(canSubmitAccountSession(mode, "", "", "password", false))
        assertFalse(canSubmitAccountSession(mode, "", "a@example.com", "", false))
    }

    @Test
    fun acceptedSubmissionClearsPasswordAndRejectedSubmissionRetainsIt() {
        val accepted = submitAccountSession(
            mode = AccountSessionEntryMode.SIGN_IN,
            endpointInput = "https://tasks.example.com",
            emailInput = "a@example.com",
            passwordInput = "secret",
            isBusy = false,
        )

        assertEquals("", accepted.passwordInput)
        assertEquals("a@example.com", accepted.submission?.email)
        assertEquals("secret", accepted.submission?.password)

        val reauthentication = submitAccountSession(
            mode = AccountSessionEntryMode.REAUTHENTICATE,
            endpointInput = "",
            emailInput = "a@example.com",
            passwordInput = "secret",
            isBusy = false,
        )
        assertEquals(AccountSessionEntryMode.REAUTHENTICATE, reauthentication.submission?.mode)
        assertEquals("", reauthentication.passwordInput)

        val rejected = submitAccountSession(
            mode = AccountSessionEntryMode.SIGN_IN,
            endpointInput = "",
            emailInput = "a@example.com",
            passwordInput = "secret",
            isBusy = false,
        )
        assertNull(rejected.submission)
        assertEquals("secret", rejected.passwordInput)
    }

    @Test
    fun accountSessionUsesCompactPanelOnlyForCompactWindows() {
        assertEquals(AccountSessionLayout.COMPACT, accountSessionLayoutFor(WindowSizeCategory.COMPACT))
        assertEquals(AccountSessionLayout.LARGER, accountSessionLayoutFor(WindowSizeCategory.MEDIUM))
        assertEquals(AccountSessionLayout.LARGER, accountSessionLayoutFor(WindowSizeCategory.EXPANDED))
    }

    @Test
    fun accountControlsExposeSameAccountActionsAndDisableThemDuringTransitions() {
        val noAccount = accountControlAvailability(null, isLocalOnly = false, operation = null)
        assertFalse(noAccount.canSwitchAccount)
        assertFalse(noAccount.canLogout)
        assertFalse(noAccount.canClearLocalData)
        assertFalse(noAccount.canConnectPocketBase)

        val available = accountControlAvailability(account, isLocalOnly = false, operation = null)
        assertTrue(available.canSwitchAccount)
        assertTrue(available.canLogout)
        assertFalse(available.canClearLocalData)
        assertFalse(available.canConnectPocketBase)

        val busy = accountControlAvailability(account, false, AccountOperation.SWITCHING)
        assertFalse(busy.canSwitchAccount)
        assertFalse(busy.canLogout)

        val local = accountControlAvailability(null, isLocalOnly = true, operation = null)
        assertFalse(local.canSwitchAccount)
        assertFalse(local.canLogout)
        assertTrue(local.canClearLocalData)
        assertTrue(local.canConnectPocketBase)
    }

    @Test
    fun retryIsShownOnlyAfterAnAccountOperationFails() {
        assertFalse(shouldShowAccountRetry(operation = null, error = null))
        assertFalse(
            shouldShowAccountRetry(
                operation = AccountOperation.RESTORING,
                error = null,
            )
        )
        assertFalse(
            shouldShowAccountRetry(
                operation = AccountOperation.RESTORING,
                error = AccountUiError.SESSION_RESTORE_FAILED,
            )
        )
        assertTrue(
            shouldShowAccountRetry(
                operation = null,
                error = AccountUiError.SESSION_RESTORE_FAILED,
            )
        )
    }
}
