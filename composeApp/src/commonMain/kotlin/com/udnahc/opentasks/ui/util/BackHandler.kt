package com.udnahc.opentasks.ui.util

import androidx.compose.runtime.Composable

/** Platform-specific back-button handler. */
@Composable
expect fun PlatformBackHandler(enabled: Boolean = true, onBack: () -> Unit)
