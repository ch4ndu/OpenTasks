package com.udnahc.opentasks.ui.preview

import androidx.compose.runtime.Composable
import com.udnahc.opentasks.ui.screens.countdown.CountdownDetailContent
import com.udnahc.opentasks.ui.screens.countdown.previewCountdown
import com.udnahc.opentasks.ui.theme.OpenTasksTheme

@Composable
@LightDarkPreview
private fun CountdownDetailContentPreview() {
    OpenTasksTheme {
        CountdownDetailContent(
            countdown = previewCountdown,
            onBack = {},
            onEdit = {},
            onDelete = {},
        )
    }
}

@Composable
@LightDarkPreview
private fun CountdownDetailLoadingPreview() {
    OpenTasksTheme {
        CountdownDetailContent(
            countdown = null,
            onBack = {},
            onEdit = {},
            onDelete = {},
        )
    }
}
