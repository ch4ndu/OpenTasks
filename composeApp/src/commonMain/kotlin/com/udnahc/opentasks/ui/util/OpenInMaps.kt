package com.udnahc.opentasks.ui.util

import androidx.compose.runtime.Composable
import com.udnahc.opentasks.ExternalLaunchResult

/** Returns a lambda that opens the given location in the platform's maps app. */
@Composable
expect fun rememberOpenInMapsAction(): (String) -> ExternalLaunchResult
