package com.udnahc.opentasks.ui.util

import androidx.compose.runtime.Composable

private val IS_MAC = System.getProperty("os.name").orEmpty().startsWith("Mac", ignoreCase = true)

@Composable
actual fun rememberCalendarPermissionLauncher(onResult: (Boolean) -> Unit): () -> Unit {
    return { onResult(IS_MAC) }
}
