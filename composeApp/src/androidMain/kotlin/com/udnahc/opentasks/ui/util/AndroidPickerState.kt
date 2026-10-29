package com.udnahc.opentasks.ui.util

import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import java.util.UUID

/** Keeps native picker work through configuration changes, without saving payloads in a Bundle. */
@Composable
internal inline fun <reified T : ViewModel> rememberAndroidPickerState(
    crossinline create: () -> T,
    crossinline abandon: (T) -> Unit,
    crossinline isAbandoned: (T) -> Boolean,
): T {
    val activity = LocalActivity.current as? ComponentActivity
        ?: error("An Android picker requires a ComponentActivity")
    // The saveable call site belongs to the account/dialog or task navigation entry. A different
    // owner receives a different token and cannot claim the previous owner's activity result.
    var token by rememberSaveable { mutableStateOf(UUID.randomUUID().toString()) }
    var state = viewModel<T>(viewModelStoreOwner = activity, key = token) { create() }
    if (isAbandoned(state)) {
        // A navigation entry may return after abandoning a picker. Retire its old result key too.
        token = UUID.randomUUID().toString()
        state = viewModel<T>(viewModelStoreOwner = activity, key = token) { create() }
    }
    DisposableEffect(activity, state) {
        onDispose {
            if (!activity.isChangingConfigurations) abandon(state)
        }
    }
    return state
}
