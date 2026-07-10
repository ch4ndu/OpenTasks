package com.udnahc.opentasks.ui.preview

import androidx.compose.runtime.Composable
import com.udnahc.opentasks.ui.screens.countdown.CountdownCard
import com.udnahc.opentasks.ui.screens.countdown.CountdownContent
import com.udnahc.opentasks.ui.screens.countdown.previewCountdowns
import com.udnahc.opentasks.ui.theme.OpenTasksTheme

@Composable
@LightDarkPreview
private fun CountdownContentPreview() {
    OpenTasksTheme {
        CountdownContent(
            countdowns = previewCountdowns,
            selectedFilter = null,
            onFilterSelected = {},
            onCountdownClick = {},
            onSettingsClick = {},
        )
    }
}

@Composable
@LightDarkPreview
private fun CountdownContentEmptyPreview() {
    OpenTasksTheme {
        CountdownContent(
            countdowns = emptyList(),
            selectedFilter = null,
            onFilterSelected = {},
            onCountdownClick = {},
            onSettingsClick = {},
        )
    }
}

@Composable
@LightDarkPreview
private fun CountdownCardPreview() {
    OpenTasksTheme {
        CountdownCard(
            item = previewCountdowns.first(),
            onClick = {},
        )
    }
}
