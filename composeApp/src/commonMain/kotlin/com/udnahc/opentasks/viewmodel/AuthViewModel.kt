package com.udnahc.opentasks.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.udnahc.opentasks.data.auth.AccountAuthenticationRejectedException
import com.udnahc.opentasks.data.auth.AccountCapabilityRejectedException
import com.udnahc.opentasks.data.auth.AccountConnectivityException
import com.udnahc.opentasks.data.auth.AccountSessionState
import com.udnahc.opentasks.data.auth.AccountTransitionBlockedException
import com.udnahc.opentasks.data.auth.AuthTokenStore
import com.udnahc.opentasks.data.auth.LegacyCacheOwnershipException
import com.udnahc.opentasks.data.auth.LocalServerReplacementConfirmation
import com.udnahc.opentasks.data.auth.LocalServerReplacementPreview
import com.udnahc.opentasks.data.auth.SecureTokenStoreException
import com.udnahc.opentasks.data.sync.PocketBaseConnectionException
import com.udnahc.opentasks.data.sync.SyncException
import com.udnahc.opentasks.data.sync.AuthoritativeLocalSeedSourceException
import com.udnahc.opentasks.data.sync.AuthoritativeReplacementConflictException
import com.udnahc.opentasks.domain.action.account.LoginAccountAction
import com.udnahc.opentasks.domain.action.account.LogoutAccountAction
import com.udnahc.opentasks.domain.action.account.ReauthenticateAccountAction
import com.udnahc.opentasks.domain.action.account.RestoreSessionAction
import com.udnahc.opentasks.domain.action.account.SwitchAccountAction
import com.udnahc.opentasks.domain.action.account.StartLocalOnlyAction
import com.udnahc.opentasks.domain.action.account.PrepareLocalServerReplacementAction
import com.udnahc.opentasks.domain.action.account.ConfirmLocalServerReplacementAction
import com.udnahc.opentasks.domain.action.account.CancelLocalServerReplacementPreparationAction
import com.udnahc.opentasks.domain.action.settings.ClearLocalDataAction
import com.udnahc.opentasks.domain.usecase.account.ObserveAccountSessionUseCase
import com.udnahc.opentasks.domain.usecase.settings.ObservePocketBaseUrlUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException
import org.lighthousegames.logging.logging

private val log = logging("AuthViewModel")

enum class AccountOperation {
    RESTORING,
    SIGNING_IN,
    REAUTHENTICATING,
    SWITCHING,
    LOGGING_OUT,
    STARTING_LOCAL,
    CLEARING_LOCAL,
    PREPARING_REPLACEMENT,
    REPLACING_SERVER_DATA,
    CANCELLING_REPLACEMENT,
}

enum class AccountUiError {
    INVALID_INPUT,
    AUTHENTICATION_REJECTED,
    CONNECTION_UNAVAILABLE,
    SERVER_UNSUPPORTED,
    CACHE_OWNERSHIP_UNPROVEN,
    TRANSITION_BLOCKED,
    CREDENTIAL_STORAGE_UNAVAILABLE,
    SESSION_RESTORE_FAILED,
    REPLACEMENT_CONFLICT,
    LOCAL_SEED_SOURCE_INVALID,
    REPLACEMENT_PREVIEW_CHANGED,
    GENERIC,
}

class AuthViewModel(
    observeAccountSession: ObserveAccountSessionUseCase,
    observePocketBaseUrl: ObservePocketBaseUrlUseCase,
    private val restoreSessionAction: RestoreSessionAction,
    private val loginAccountAction: LoginAccountAction,
    private val reauthenticateAccountAction: ReauthenticateAccountAction,
    private val switchAccountAction: SwitchAccountAction,
    private val logoutAccountAction: LogoutAccountAction,
    private val startLocalOnlyAction: StartLocalOnlyAction,
    private val clearLocalDataAction: ClearLocalDataAction,
    tokenStore: AuthTokenStore,
    private val prepareLocalServerReplacementAction: PrepareLocalServerReplacementAction? = null,
    private val confirmLocalServerReplacementAction: ConfirmLocalServerReplacementAction? = null,
    private val cancelLocalServerReplacementPreparationAction: CancelLocalServerReplacementPreparationAction? = null,
) : ViewModel() {
    private val observedSessionState = observeAccountSession()
    private val _sessionState = MutableStateFlow(observedSessionState.value)
    val sessionState: StateFlow<AccountSessionState> = _sessionState.asStateFlow()

    val savedEndpoint: StateFlow<String?> = observePocketBaseUrl()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _operation = MutableStateFlow<AccountOperation?>(null)
    val operation: StateFlow<AccountOperation?> = _operation.asStateFlow()

    private val _error = MutableStateFlow<AccountUiError?>(null)
    val error: StateFlow<AccountUiError?> = _error.asStateFlow()

    val storageWarning: String? = tokenStore.storageWarning

    private val _replacementPreview = MutableStateFlow<LocalServerReplacementPreview?>(null)
    val replacementPreview: StateFlow<LocalServerReplacementPreview?> = _replacementPreview.asStateFlow()

    init {
        viewModelScope.launch {
            observedSessionState.collect { state ->
                log.d { "Observed account session state=${state.diagnosticName()}" }
                _sessionState.value = state
                if (state is AccountSessionState.Transitioning &&
                    state.transition.purpose == com.udnahc.opentasks.data.auth.AccountTransitionPurpose.LOCAL_AUTHORITATIVE_REPLACEMENT
                ) {
                    _replacementPreview.value = null
                }
            }
        }
    }

    fun restoreSession() {
        log.d {
            "Session restore requested; state=${_sessionState.value.diagnosticName()}, " +
                "operation=${_operation.value ?: "none"}"
        }
        runOperation(
            operation = AccountOperation.RESTORING,
            mapError = { error ->
                val transition = (_sessionState.value as? AccountSessionState.Transitioning)?.transition
                if (transition?.purpose == com.udnahc.opentasks.data.auth.AccountTransitionPurpose.LOCAL_AUTHORITATIVE_REPLACEMENT) {
                    error.toAccountUiError()
                } else {
                    AccountUiError.SESSION_RESTORE_FAILED
                }
            },
        ) {
            restoreSessionAction()
        }
    }

    fun login(endpoint: String, email: String, password: String) {
        runOperation(AccountOperation.SIGNING_IN) {
            loginAccountAction(endpoint, email, password)
        }
    }

    fun startLocalOnly() {
        runOperation(AccountOperation.STARTING_LOCAL) {
            startLocalOnlyAction()
        }
    }

    fun clearLocalData() {
        runOperation(AccountOperation.CLEARING_LOCAL) {
            clearLocalDataAction()
        }
    }

    fun prepareLocalServerReplacement(endpoint: String, email: String, password: String) {
        runOperation(AccountOperation.PREPARING_REPLACEMENT) {
            val action = prepareLocalServerReplacementAction
                ?: error("Authoritative replacement preparation is unavailable")
            _replacementPreview.value = action(endpoint, email, password)
        }
    }

    fun confirmLocalServerReplacement() {
        runOperation(AccountOperation.REPLACING_SERVER_DATA) {
            val action = confirmLocalServerReplacementAction
                ?: error("Authoritative replacement confirmation is unavailable")
            when (val result = action()) {
                LocalServerReplacementConfirmation.Started -> _replacementPreview.value = null
                is LocalServerReplacementConfirmation.PreviewChanged -> {
                    _replacementPreview.value = result.preview
                    _error.value = AccountUiError.REPLACEMENT_PREVIEW_CHANGED
                }
            }
        }
    }

    fun cancelLocalServerReplacementPreparation() {
        runOperation(AccountOperation.CANCELLING_REPLACEMENT) {
            val action = cancelLocalServerReplacementPreparationAction
                ?: error("Authoritative replacement cancellation is unavailable")
            action()
            _replacementPreview.value = null
        }
    }

    fun reauthenticate(email: String, password: String) {
        runOperation(AccountOperation.REAUTHENTICATING) {
            reauthenticateAccountAction(email, password)
        }
    }

    fun switchAccount(email: String, password: String) {
        val endpoint = (_sessionState.value as? AccountSessionState.Authenticated)
            ?.binding
            ?.canonicalEndpoint
            ?: return
        runOperation(AccountOperation.SWITCHING) {
            switchAccountAction(endpoint, email, password)
        }
    }

    fun logout() {
        runOperation(AccountOperation.LOGGING_OUT) {
            logoutAccountAction()
        }
    }

    fun clearError() {
        _error.value = null
    }

    private fun runOperation(
        operation: AccountOperation,
        mapError: (Throwable) -> AccountUiError = Throwable::toAccountUiError,
        block: suspend () -> Unit,
    ) {
        if (_operation.value != null) {
            log.d { "Ignoring account operation=$operation because operation=${_operation.value} is active" }
            return
        }
        _error.value = null
        _operation.value = operation
        log.d { "Account operation started: $operation; state=${_sessionState.value.diagnosticName()}" }
        viewModelScope.launch {
            try {
                block()
                log.d { "Account operation succeeded: $operation; state=${_sessionState.value.diagnosticName()}" }
            } catch (error: CancellationException) {
                log.d { "Account operation cancelled: $operation; state=${_sessionState.value.diagnosticName()}" }
                throw error
            } catch (error: Throwable) {
                val uiError = mapError(error)
                _error.value = uiError
                log.e(error) {
                    "Account operation failed: $operation; error=$uiError; " +
                        "state=${_sessionState.value.diagnosticName()}"
                }
            } finally {
                _operation.value = null
                log.d { "Account operation finished: $operation; state=${_sessionState.value.diagnosticName()}" }
            }
        }
    }
}

private fun AccountSessionState.diagnosticName(): String = when (this) {
    AccountSessionState.Restoring -> "restoring"
    AccountSessionState.SignedOut -> "signed-out"
    is AccountSessionState.Authenticated -> "authenticated-${freshness.name.lowercase()}"
    is AccountSessionState.LocalOnly -> "local-only"
    is AccountSessionState.ReauthenticationRequired -> "reauthentication-${reason.name.lowercase()}"
    is AccountSessionState.Transitioning -> "transitioning-${transition.phase.name.lowercase()}"
}

private fun Throwable.toAccountUiError(): AccountUiError = when (this) {
    is AccountAuthenticationRejectedException -> AccountUiError.AUTHENTICATION_REJECTED
    is AccountConnectivityException,
    is PocketBaseConnectionException,
    is SyncException -> AccountUiError.CONNECTION_UNAVAILABLE
    is AccountCapabilityRejectedException -> AccountUiError.SERVER_UNSUPPORTED
    is LegacyCacheOwnershipException -> AccountUiError.CACHE_OWNERSHIP_UNPROVEN
    is AccountTransitionBlockedException -> AccountUiError.TRANSITION_BLOCKED
    is AuthoritativeReplacementConflictException -> AccountUiError.REPLACEMENT_CONFLICT
    is AuthoritativeLocalSeedSourceException -> AccountUiError.LOCAL_SEED_SOURCE_INVALID
    is SecureTokenStoreException -> AccountUiError.CREDENTIAL_STORAGE_UNAVAILABLE
    is IllegalArgumentException -> AccountUiError.INVALID_INPUT
    else -> AccountUiError.GENERIC
}

internal fun com.udnahc.opentasks.data.auth.AuthenticatedAccount.displayLabel(): String =
    displayName?.takeIf { it.isNotBlank() }
        ?: email?.takeIf { it.isNotBlank() }
        ?: accountId
