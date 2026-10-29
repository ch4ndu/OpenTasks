package com.udnahc.opentasks.ui.screens

import com.udnahc.opentasks.ui.theme.isReimagined
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
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
import com.udnahc.opentasks.data.model.TaskPriority
import com.udnahc.opentasks.data.model.RecurrenceType
import com.udnahc.opentasks.domain.usecase.task.QuickTaskToken
import com.udnahc.opentasks.domain.usecase.task.QuickTaskTokenKind
import com.udnahc.opentasks.ui.theme.OpenTasksTheme
import com.udnahc.opentasks.ui.util.formatLocalizedDateWithWeekday
import com.udnahc.opentasks.viewmodel.QuickAddTaskUiState
import opentasks.composeapp.generated.resources.reimagined_open_full_editor
import opentasks.composeapp.generated.resources.*
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
    onOpenFullEditor: (() -> Unit)? = null,
    creationListName: String = "",
    creationPriority: TaskPriority = TaskPriority.NONE,
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
        onOpenFullEditor = onOpenFullEditor,
        creationListName = creationListName,
        creationPriority = creationPriority,
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
    onOpenFullEditor: (() -> Unit)? = null,
    creationListName: String = "",
    creationPriority: TaskPriority = TaskPriority.NONE,
) {
    val focusRequester = remember { FocusRequester() }
    if (requestFocus) {
        LaunchedEffect(focusRequester) { focusRequester.requestFocus() }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState, modifier = if (isReimagined) Modifier.imePadding() else Modifier) },
        topBar = {
            if (isReimagined) ReimaginedScreenHeader(stringResource(Res.string.quick_add), onBack) {
                androidx.compose.material3.FilledTonalButton(onClick = onAdd, enabled = state.canSave, modifier = Modifier.heightIn(min = OpenTasksTheme.dimens.minimumInteractiveTargetSize)) {
                    if (state.isSaving) CircularProgressIndicator(Modifier.size(OpenTasksTheme.dimens.iconSmall)) else Text(stringResource(Res.string.add))
                }
            } else OpenTasksTopBar(
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
                .then(if (isReimagined) Modifier.imePadding() else Modifier)
                .padding(horizontal = OpenTasksTheme.dimens.paddingXLarge),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(OpenTasksTheme.dimens.paddingLarge),
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = OpenTasksTheme.dimens.authPanelMaxWidth)
                    .then(if (isReimagined) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                    .padding(top = OpenTasksTheme.dimens.paddingXXLarge),
            ) {
                OutlinedTextField(
                    value = state.input,
                    onValueChange = onInputChanged,
                    singleLine = !isReimagined,
                    textStyle = if (isReimagined) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.bodyLarge,
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
                            enabled = !state.isSaving && !state.isSaved,
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
                if (isReimagined && creationListName.isNotEmpty()) ReimaginedPanel(Modifier.fillMaxWidth()) {
                    ReimaginedSectionLabel(stringResource(Res.string.calendar_view_list))
                    Text(creationListName, style = MaterialTheme.typography.bodyLarge)
                    ReimaginedSectionLabel(stringResource(Res.string.priority))
                    Text(stringResource(when (creationPriority) { TaskPriority.HIGH -> Res.string.high_priority; TaskPriority.MEDIUM -> Res.string.medium_priority; TaskPriority.LOW -> Res.string.low_priority; TaskPriority.NONE -> Res.string.no_priority }), style = MaterialTheme.typography.bodyLarge)
                }
                if (isReimagined && onOpenFullEditor != null) {
                    androidx.compose.material3.OutlinedButton(onClick = onOpenFullEditor, enabled = !state.isSaving && !state.isSaved) {
                        Text(stringResource(opentasks.composeapp.generated.resources.Res.string.reimagined_open_full_editor))
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
