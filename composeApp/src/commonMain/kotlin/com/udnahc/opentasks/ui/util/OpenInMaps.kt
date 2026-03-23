package com.udnahc.opentasks.ui.util

import androidx.compose.runtime.Composable

/** Returns a lambda that opens the given location in the platform's maps app. */
@Composable
expect fun rememberOpenInMapsAction(): (String) -> Unit
