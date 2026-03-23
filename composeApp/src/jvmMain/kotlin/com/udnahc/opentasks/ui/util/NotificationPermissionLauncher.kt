package com.udnahc.opentasks.ui.util

import androidx.compose.runtime.Composable

@Composable
actual fun rememberNotificationPermissionLauncher(onResult: (Boolean) -> Unit): () -> Unit {
    return { onResult(true) }
}
