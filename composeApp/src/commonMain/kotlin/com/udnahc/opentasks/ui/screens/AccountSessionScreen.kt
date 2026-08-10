package com.udnahc.opentasks.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.udnahc.opentasks.data.auth.AccountReauthenticationReason
import com.udnahc.opentasks.data.auth.AccountSessionState
import com.udnahc.opentasks.data.auth.AccountTransition
import com.udnahc.opentasks.data.auth.AccountTransitionPhase
import com.udnahc.opentasks.data.auth.AuthenticatedAccount
import com.udnahc.opentasks.ui.theme.OpenTasksTheme
import com.udnahc.opentasks.ui.theme.WindowSizeCategory
import com.udnahc.opentasks.viewmodel.AccountOperation
import com.udnahc.opentasks.viewmodel.AccountUiError
import com.udnahc.opentasks.viewmodel.displayLabel
import opentasks.composeapp.generated.resources.Res
import opentasks.composeapp.generated.resources.account_email
import opentasks.composeapp.generated.resources.account_endpoint
import opentasks.composeapp.generated.resources.account_endpoint_hint
import opentasks.composeapp.generated.resources.account_endpoint_read_only
import opentasks.composeapp.generated.resources.account_password
import opentasks.composeapp.generated.resources.account_reauthenticate
import opentasks.composeapp.generated.resources.account_reauthenticate_description
import opentasks.composeapp.generated.resources.account_reauthentication_reason_authentication_rejected
import opentasks.composeapp.generated.resources.account_reauthentication_reason_cache_binding_missing
import opentasks.composeapp.generated.resources.account_reauthentication_reason_cache_binding_mismatch
import opentasks.composeapp.generated.resources.account_reauthentication_reason_capability_mismatch
import opentasks.composeapp.generated.resources.account_reauthentication_reason_legacy_cache
import opentasks.composeapp.generated.resources.account_reauthentication_reason_persisted_state
import opentasks.composeapp.generated.resources.account_reauthentication_reason_token_unavailable
import opentasks.composeapp.generated.resources.account_restore
import opentasks.composeapp.generated.resources.account_restoring
import opentasks.composeapp.generated.resources.account_restore_failed
import opentasks.composeapp.generated.resources.account_session_connection_failed
import opentasks.composeapp.generated.resources.account_session_credentials_rejected
import opentasks.composeapp.generated.resources.account_session_generic_error
import opentasks.composeapp.generated.resources.account_session_invalid_input
import opentasks.composeapp.generated.resources.account_session_server_unsupported
import opentasks.composeapp.generated.resources.account_session_storage_failed
import opentasks.composeapp.generated.resources.account_session_transition_blocked
import opentasks.composeapp.generated.resources.account_session_transition_prepared
import opentasks.composeapp.generated.resources.account_session_transition_needs_activation
import opentasks.composeapp.generated.resources.account_session_transitioning
import opentasks.composeapp.generated.resources.account_session_cache_ownership_unproven
import opentasks.composeapp.generated.resources.account_sign_in
import opentasks.composeapp.generated.resources.account_sign_in_description
import opentasks.composeapp.generated.resources.account_sign_in_title
import opentasks.composeapp.generated.resources.account_storage_warning
import opentasks.composeapp.generated.resources.account_dismiss
import opentasks.composeapp.generated.resources.account_retry
import opentasks.composeapp.generated.resources.loading
import org.jetbrains.compose.resources.stringResource

internal enum class AccountSessionEntryMode {
    SIGN_IN,
    REAUTHENTICATE,
}

@Composable
internal fun AccountSessionScreen(
    mode: AccountSessionEntryMode,
    account: AuthenticatedAccount?,
    endpoint: String?,
    operation: AccountOperation?,
    error: AccountUiError?,
    storageWarning: String?,
    reauthenticationReason: AccountReauthenticationReason? = null,
    onSignIn: (endpoint: String, email: String, password: String) -> Unit,
    onReauthenticate: (email: String, password: String) -> Unit,
    onClearError: () -> Unit,
) {
    val dimens = OpenTasksTheme.dimens
    var endpointInput by rememberSaveable(mode) { mutableStateOf(endpoint.orEmpty()) }
    var emailInput by rememberSaveable(mode, account?.accountId) {
        mutableStateOf(account?.email.orEmpty())
    }
    // Password deliberately is not saveable and never enters ViewModel state.
    var passwordInput by remember(mode) { mutableStateOf("") }
    val isBusy = operation != null

    androidx.compose.runtime.LaunchedEffect(mode, endpoint) {
        if (endpointInput.isBlank() && !endpoint.isNullOrBlank()) {
            endpointInput = endpoint
        }
    }

    fun submit() {
        val result = submitAccountSession(
            mode = mode,
            endpointInput = endpointInput,
            emailInput = emailInput,
            passwordInput = passwordInput,
            isBusy = isBusy,
        )
        passwordInput = result.passwordInput
        val submission = result.submission ?: return
        if (submission.mode == AccountSessionEntryMode.SIGN_IN) {
            onSignIn(submission.endpoint, submission.email, submission.password)
        } else {
            onReauthenticate(submission.email, submission.password)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState()),
        contentAlignment = Alignment.Center,
    ) {
        val panelModifier = Modifier
            .fillMaxWidth()
            .widthIn(max = dimens.authPanelMaxWidth)
            .padding(
                horizontal = when (OpenTasksTheme.windowSizeCategory) {
                    WindowSizeCategory.COMPACT -> dimens.paddingXLarge
                    WindowSizeCategory.MEDIUM -> dimens.paddingXXLarge
                    WindowSizeCategory.EXPANDED -> dimens.paddingXXLarge
                },
                vertical = dimens.paddingXXLarge,
            )
        if (accountSessionLayoutFor(OpenTasksTheme.windowSizeCategory) == AccountSessionLayout.COMPACT) {
            AccountSessionForm(
                modifier = panelModifier,
                mode = mode,
                account = account,
                endpointInput = endpointInput,
                emailInput = emailInput,
                passwordInput = passwordInput,
                isBusy = isBusy,
                error = error,
                storageWarning = storageWarning,
                reauthenticationReason = reauthenticationReason,
                onEndpointChanged = { endpointInput = it },
                onEmailChanged = { emailInput = it },
                onPasswordChanged = { passwordInput = it },
                onSubmit = ::submit,
                onClearError = onClearError,
            )
        } else {
            Card(
                modifier = panelModifier,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ),
            ) {
                AccountSessionForm(
                    modifier = Modifier.padding(dimens.paddingXXLarge),
                    mode = mode,
                    account = account,
                    endpointInput = endpointInput,
                    emailInput = emailInput,
                    passwordInput = passwordInput,
                    isBusy = isBusy,
                    error = error,
                    storageWarning = storageWarning,
                    reauthenticationReason = reauthenticationReason,
                    onEndpointChanged = { endpointInput = it },
                    onEmailChanged = { emailInput = it },
                    onPasswordChanged = { passwordInput = it },
                    onSubmit = ::submit,
                    onClearError = onClearError,
                )
            }
        }
    }
}

@Composable
private fun AccountSessionForm(
    modifier: Modifier,
    mode: AccountSessionEntryMode,
    account: AuthenticatedAccount?,
    endpointInput: String,
    emailInput: String,
    passwordInput: String,
    isBusy: Boolean,
    error: AccountUiError?,
    storageWarning: String?,
    reauthenticationReason: AccountReauthenticationReason?,
    onEndpointChanged: (String) -> Unit,
    onEmailChanged: (String) -> Unit,
    onPasswordChanged: (String) -> Unit,
    onSubmit: () -> Unit,
    onClearError: () -> Unit,
) {
    val dimens = OpenTasksTheme.dimens
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val title = when (mode) {
        AccountSessionEntryMode.SIGN_IN -> stringResource(Res.string.account_sign_in_title)
        AccountSessionEntryMode.REAUTHENTICATE -> stringResource(Res.string.account_reauthenticate)
    }
    val description = when (mode) {
        AccountSessionEntryMode.SIGN_IN -> stringResource(Res.string.account_sign_in_description)
        AccountSessionEntryMode.REAUTHENTICATE -> stringResource(
            Res.string.account_reauthenticate_description,
            account?.displayLabel().orEmpty(),
        )
    }
    val submitLabel = when (mode) {
        AccountSessionEntryMode.SIGN_IN -> stringResource(Res.string.account_sign_in)
        AccountSessionEntryMode.REAUTHENTICATE -> stringResource(Res.string.account_reauthenticate)
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(dimens.spacerXLarge),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (mode == AccountSessionEntryMode.SIGN_IN) {
            OutlinedTextField(
                value = endpointInput,
                onValueChange = onEndpointChanged,
                label = { Text(stringResource(Res.string.account_endpoint)) },
                placeholder = { Text(stringResource(Res.string.account_endpoint_hint)) },
                singleLine = true,
                enabled = !isBusy,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
                colors = accountTextFieldColors(),
            )
        } else if (endpointInput.isNotBlank()) {
            OutlinedTextField(
                value = endpointInput,
                onValueChange = {},
                label = { Text(stringResource(Res.string.account_endpoint)) },
                supportingText = { Text(stringResource(Res.string.account_endpoint_read_only)) },
                singleLine = true,
                enabled = false,
                modifier = Modifier.fillMaxWidth(),
                colors = accountTextFieldColors(),
            )
        }

        OutlinedTextField(
            value = emailInput,
            onValueChange = onEmailChanged,
            label = { Text(stringResource(Res.string.account_email)) },
            singleLine = true,
            enabled = !isBusy,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next,
            ),
            modifier = Modifier.fillMaxWidth(),
            colors = accountTextFieldColors(),
        )
        OutlinedTextField(
            value = passwordInput,
            onValueChange = onPasswordChanged,
            label = { Text(stringResource(Res.string.account_password)) },
            singleLine = true,
            enabled = !isBusy,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(
                onDone = {
                    focusManager.clearFocus()
                    onSubmit()
                },
            ),
            modifier = Modifier.fillMaxWidth(),
            colors = accountTextFieldColors(),
        )

        if (storageWarning != null && mode == AccountSessionEntryMode.SIGN_IN) {
            Text(
                text = stringResource(Res.string.account_storage_warning, storageWarning),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (reauthenticationReason != null) {
            Text(
                text = reauthenticationReasonText(reauthenticationReason),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        if (error != null) {
            AccountErrorMessage(error = error, onClear = onClearError)
        }

        Button(
            onClick = onSubmit,
            enabled = canSubmitAccountSession(
                mode = mode,
                endpointInput = endpointInput,
                emailInput = emailInput,
                passwordInput = passwordInput,
                isBusy = isBusy,
            ),
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (isBusy) {
                CircularProgressIndicator(
                    modifier = Modifier.width(dimens.iconDefault),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = dimens.dividerThick,
                )
                Spacer(Modifier.width(dimens.spacerLarge))
            }
            Text(if (isBusy) stringResource(Res.string.loading) else submitLabel)
        }
    }
}

@Composable
internal fun accountTextFieldColors(): TextFieldColors = OutlinedTextFieldDefaults.colors(
    focusedTextColor = MaterialTheme.colorScheme.onSurface,
    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
    disabledTextColor = MaterialTheme.colorScheme.onSurface,
    cursorColor = MaterialTheme.colorScheme.primary,
    focusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
    unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
    disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
    focusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
    unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
    disabledPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
)

@Composable
internal fun AccountSessionStatusScreen(
    operation: AccountOperation?,
    error: AccountUiError?,
    onRetry: () -> Unit,
    onClearError: () -> Unit,
) {
    AccountStatusPanel(
        title = stringResource(Res.string.account_restore),
        message = stringResource(Res.string.account_restoring),
        operation = operation,
        error = error,
        onRetry = onRetry,
        onClearError = onClearError,
    )
}

@Composable
internal fun AccountTransitionScreen(
    transition: AccountTransition,
    operation: AccountOperation?,
    error: AccountUiError?,
    onRetry: () -> Unit,
    onClearError: () -> Unit,
) {
    val phase = when (transition.phase) {
        AccountTransitionPhase.PREPARED -> stringResource(Res.string.account_session_transition_prepared)
        AccountTransitionPhase.NEEDS_ACTIVATION -> stringResource(
            Res.string.account_session_transition_needs_activation
        )
    }
    AccountStatusPanel(
        title = stringResource(Res.string.account_session_transitioning),
        message = phase,
        operation = operation,
        error = error,
        onRetry = onRetry,
        onClearError = onClearError,
    )
}

@Composable
private fun AccountStatusPanel(
    title: String,
    message: String,
    operation: AccountOperation?,
    error: AccountUiError?,
    onRetry: () -> Unit,
    onClearError: () -> Unit,
) {
    val dimens = OpenTasksTheme.dimens
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = dimens.authPanelMaxWidth)
                .padding(dimens.paddingXXLarge),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(dimens.spacerXLarge),
        ) {
            if (error == null) {
                CircularProgressIndicator()
            }
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            if (error == null) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (error != null) {
                AccountErrorMessage(error = error, onClear = onClearError)
            }
            if (shouldShowAccountRetry(operation, error)) {
                TextButton(onClick = onRetry) {
                    Text(stringResource(Res.string.account_retry))
                }
            }
        }
    }
}

internal fun shouldShowAccountRetry(
    operation: AccountOperation?,
    error: AccountUiError?,
): Boolean = operation == null && error != null

@Composable
internal fun AccountErrorMessage(
    error: AccountUiError,
    onClear: () -> Unit,
) {
    val message = when (error) {
        AccountUiError.INVALID_INPUT -> stringResource(Res.string.account_session_invalid_input)
        AccountUiError.AUTHENTICATION_REJECTED -> stringResource(
            Res.string.account_session_credentials_rejected
        )
        AccountUiError.CONNECTION_UNAVAILABLE -> stringResource(
            Res.string.account_session_connection_failed
        )
        AccountUiError.SERVER_UNSUPPORTED -> stringResource(
            Res.string.account_session_server_unsupported
        )
        AccountUiError.CACHE_OWNERSHIP_UNPROVEN -> stringResource(
            Res.string.account_session_cache_ownership_unproven
        )
        AccountUiError.TRANSITION_BLOCKED -> stringResource(
            Res.string.account_session_transition_blocked
        )
        AccountUiError.CREDENTIAL_STORAGE_UNAVAILABLE -> stringResource(
            Res.string.account_session_storage_failed
        )
        AccountUiError.SESSION_RESTORE_FAILED -> stringResource(Res.string.account_restore_failed)
        AccountUiError.GENERIC -> stringResource(Res.string.account_session_generic_error)
    }
    Column(verticalArrangement = Arrangement.spacedBy(OpenTasksTheme.dimens.spacerSmall)) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )
        TextButton(onClick = onClear) {
            Text(stringResource(Res.string.account_dismiss))
        }
    }
}

@Composable
private fun reauthenticationReasonText(reason: AccountReauthenticationReason): String = when (reason) {
    AccountReauthenticationReason.AUTHENTICATION_REJECTED -> stringResource(
        Res.string.account_reauthentication_reason_authentication_rejected
    )
    AccountReauthenticationReason.TOKEN_UNAVAILABLE -> stringResource(
        Res.string.account_reauthentication_reason_token_unavailable
    )
    AccountReauthenticationReason.CACHE_BINDING_MISSING -> stringResource(
        Res.string.account_reauthentication_reason_cache_binding_missing
    )
    AccountReauthenticationReason.CACHE_BINDING_MISMATCH -> stringResource(
        Res.string.account_reauthentication_reason_cache_binding_mismatch
    )
    AccountReauthenticationReason.CAPABILITY_MISMATCH -> stringResource(
        Res.string.account_reauthentication_reason_capability_mismatch
    )
    AccountReauthenticationReason.LEGACY_CACHE_OWNERSHIP_UNPROVEN -> stringResource(
        Res.string.account_reauthentication_reason_legacy_cache
    )
    AccountReauthenticationReason.PERSISTED_STATE_INVALID -> stringResource(
        Res.string.account_reauthentication_reason_persisted_state
    )
}
