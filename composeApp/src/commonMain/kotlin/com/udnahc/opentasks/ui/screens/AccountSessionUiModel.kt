package com.udnahc.opentasks.ui.screens

import com.udnahc.opentasks.data.auth.AccountSessionState
import com.udnahc.opentasks.data.auth.AuthenticatedAccount
import com.udnahc.opentasks.ui.theme.WindowSizeCategory
import com.udnahc.opentasks.viewmodel.AccountOperation

internal enum class AccountSessionRoute {
    RESTORING,
    SIGN_IN,
    REAUTHENTICATE,
    TRANSITIONING,
    ACTIVE,
}

internal fun accountSessionRoute(state: AccountSessionState): AccountSessionRoute = when (state) {
    AccountSessionState.Restoring -> AccountSessionRoute.RESTORING
    AccountSessionState.SignedOut -> AccountSessionRoute.SIGN_IN
    is AccountSessionState.ReauthenticationRequired ->
        if (state.account == null) AccountSessionRoute.SIGN_IN
        else AccountSessionRoute.REAUTHENTICATE

    is AccountSessionState.Transitioning -> AccountSessionRoute.TRANSITIONING
    is AccountSessionState.Authenticated,
    is AccountSessionState.LocalOnly -> AccountSessionRoute.ACTIVE
}

internal enum class AccountSessionLayout {
    COMPACT,
    LARGER,
}

internal fun accountSessionLayoutFor(sizeCategory: WindowSizeCategory): AccountSessionLayout =
    if (sizeCategory == WindowSizeCategory.COMPACT) {
        AccountSessionLayout.COMPACT
    } else {
        AccountSessionLayout.LARGER
    }

internal data class AccountSessionSubmission(
    val mode: AccountSessionEntryMode,
    val endpoint: String,
    val email: String,
    val password: String,
)

internal data class AccountSessionSubmitResult(
    val submission: AccountSessionSubmission?,
    val passwordInput: String,
)

internal fun canSubmitAccountSession(
    mode: AccountSessionEntryMode,
    endpointInput: String,
    emailInput: String,
    passwordInput: String,
    isBusy: Boolean,
): Boolean = !isBusy &&
    emailInput.isNotBlank() &&
    passwordInput.isNotBlank() &&
    (mode == AccountSessionEntryMode.REAUTHENTICATE || endpointInput.isNotBlank())

internal fun submitAccountSession(
    mode: AccountSessionEntryMode,
    endpointInput: String,
    emailInput: String,
    passwordInput: String,
    isBusy: Boolean,
): AccountSessionSubmitResult {
    if (!canSubmitAccountSession(mode, endpointInput, emailInput, passwordInput, isBusy)) {
        return AccountSessionSubmitResult(
            submission = null,
            passwordInput = passwordInput,
        )
    }

    return AccountSessionSubmitResult(
        submission = AccountSessionSubmission(
            mode = mode,
            endpoint = endpointInput,
            email = emailInput,
            password = passwordInput,
        ),
        // Password input is intentionally cleared as soon as the request is
        // accepted, before the ViewModel action starts.
        passwordInput = "",
    )
}

internal data class AccountControlAvailability(
    val canSwitchAccount: Boolean,
    val canLogout: Boolean,
    val canClearLocalData: Boolean,
    val canConnectPocketBase: Boolean,
)

internal fun accountControlAvailability(
    currentAccount: AuthenticatedAccount?,
    isLocalOnly: Boolean,
    operation: AccountOperation?,
): AccountControlAvailability = AccountControlAvailability(
    canSwitchAccount = currentAccount != null && !isLocalOnly && operation == null,
    canLogout = currentAccount != null && !isLocalOnly && operation == null,
    canClearLocalData = isLocalOnly && operation == null,
    canConnectPocketBase = isLocalOnly && operation == null,
)
