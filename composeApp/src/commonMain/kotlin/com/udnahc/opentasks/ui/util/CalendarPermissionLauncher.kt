package com.udnahc.opentasks.ui.util

import androidx.compose.runtime.Composable

/**
 * Returns a lambda that, when invoked, triggers the platform's calendar permission request.
 * [onResult] is called with true if permission was granted, false otherwise.
 */
@Composable
expect fun rememberCalendarPermissionLauncher(onResult: (Boolean) -> Unit): () -> Unit
