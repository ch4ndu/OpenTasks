package com.udnahc.opentasks.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.udnahc.opentasks.viewmodel.TaskMutationFailureEvent
import kotlinx.coroutines.flow.StateFlow

@Composable
internal fun TaskMutationFailureEffect(
    eventFlow: StateFlow<TaskMutationFailureEvent?>,
    consume: (TaskMutationFailureEvent) -> Boolean,
    onFailure: () -> Unit,
) {
    val event by eventFlow.collectAsState()
    LaunchedEffect(event) {
        val pending = event ?: return@LaunchedEffect
        if (consume(pending)) onFailure()
    }
}
