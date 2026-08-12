package com.udnahc.opentasks.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import com.udnahc.opentasks.data.model.RecurrenceType
import com.udnahc.opentasks.domain.usecase.task.QuickTaskToken
import com.udnahc.opentasks.domain.usecase.task.QuickTaskTokenKind
import com.udnahc.opentasks.ui.theme.OpenTasksTheme
import com.udnahc.opentasks.ui.util.formatLocalizedDateWithWeekday
import com.udnahc.opentasks.viewmodel.QuickAddTaskUiState
import opentasks.composeapp.generated.resources.Res
import opentasks.composeapp.generated.resources.add
import opentasks.composeapp.generated.resources.ic_close
import opentasks.composeapp.generated.resources.quick_add
import opentasks.composeapp.generated.resources.quick_add_example
import opentasks.composeapp.generated.resources.remove_inference
import opentasks.composeapp.generated.resources.task_save_failed
import opentasks.composeapp.generated.resources.title_hint
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun QuickAddTaskScreen(
    state: QuickAddTaskUiState,
    onInputChanged: (String) -> Unit,
    onDismissToken: (String) -> Unit,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onErrorShown: () -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val saveFailureMessage = stringResource(Res.string.task_save_failed)
    LaunchedEffect(state.saveFailed) {
        if (state.saveFailed) {
            showQuickAddSaveFailure(snackbarHostState, saveFailureMessage, onErrorShown)
        }
    }
    QuickAddTaskContent(
        state = state,
        snackbarHostState = snackbarHostState,
        onInputChanged = onInputChanged,
        onDismissToken = onDismissToken,
        onBack = onBack,
        onAdd = onAdd,
        requestFocus = true,
    )
}

internal suspend fun showQuickAddSaveFailure(
    snackbarHostState: SnackbarHostState,
    message: String,
    onErrorShown: () -> Unit,
) {
    snackbarHostState.showSnackbar(message)
    onErrorShown()
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun QuickAddTaskContent(
    state: QuickAddTaskUiState,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    onInputChanged: (String) -> Unit,
    onDismissToken: (String) -> Unit,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    requestFocus: Boolean = false,
) {
    val focusRequester = remember { FocusRequester() }
    if (requestFocus) {
        LaunchedEffect(focusRequester) { focusRequester.requestFocus() }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            OpenTasksTopBar(
                title = stringResource(Res.string.quick_add),
                navigationIcon = { OpenTasksBackButton(onClick = onBack) },
                actions = {
                    TextButton(
                        onClick = onAdd,
                        enabled = state.canSave,
                    ) {
                        if (state.isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(OpenTasksTheme.dimens.iconSmall),
                            )
                        } else {
                            Text(stringResource(Res.string.add))
                        }
                    }
                },
            )
        },
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = OpenTasksTheme.dimens.paddingXLarge),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(OpenTasksTheme.dimens.paddingLarge),
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = OpenTasksTheme.dimens.authPanelMaxWidth)
                    .padding(top = OpenTasksTheme.dimens.paddingXXLarge),
            ) {
                OutlinedTextField(
                    value = state.input,
                    onValueChange = onInputChanged,
                    singleLine = true,
                    enabled = !state.isSaving && !state.isSaved,
                    placeholder = { Text(stringResource(Res.string.title_hint)) },
                    supportingText = if (state.input.isEmpty()) {
                        { Text(stringResource(Res.string.quick_add_example)) }
                    } else {
                        null
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { if (state.canSave) onAdd() }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(OpenTasksTheme.dimens.paddingMedium),
                    verticalArrangement = Arrangement.spacedBy(OpenTasksTheme.dimens.paddingSmall),
                ) {
                    state.activeTokens.forEach { token ->
                        val label = quickTaskTokenLabel(token)
                        InputChip(
                            selected = true,
                            onClick = { onDismissToken(token.signature) },
                            label = { Text(label) },
                            trailingIcon = {
                                Icon(
                                    painter = painterResource(Res.drawable.ic_close),
                                    contentDescription = stringResource(
                                        Res.string.remove_inference,
                                        label,
                                    ),
                                    modifier = Modifier.size(OpenTasksTheme.dimens.iconSmall),
                                )
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun quickTaskTokenLabel(token: QuickTaskToken): String = when (token.kind) {
    QuickTaskTokenKind.DATE -> token.resolvedDate
        ?.let { formatLocalizedDateWithWeekday(it) }
        ?: token.sourceText
    QuickTaskTokenKind.TIME -> token.resolvedTime
        ?.let { formatTime(it.hour, it.minute) }
        ?: token.sourceText
    QuickTaskTokenKind.RECURRENCE -> when (token.recurrenceType) {
        RecurrenceType.NONE -> token.sourceText
        else -> recurrenceLabel(token.recurrenceType)
    }
}
