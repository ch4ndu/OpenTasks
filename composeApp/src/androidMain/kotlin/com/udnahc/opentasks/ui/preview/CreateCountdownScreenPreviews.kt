package com.udnahc.opentasks.ui.preview

import androidx.compose.runtime.Composable
import com.udnahc.opentasks.data.model.Countdown
import com.udnahc.opentasks.data.model.CountdownType
import com.udnahc.opentasks.ui.screens.countdown.CreateCountdownContent
import com.udnahc.opentasks.ui.theme.OpenTasksTheme
import kotlinx.datetime.LocalDate

@Composable
@LightDarkPreview
private fun CreateCountdownContentPreview() {
    OpenTasksTheme {
        CreateCountdownContent(
            editCountdown = null,
            initialType = CountdownType.COUNTDOWN,
            currentDate = LocalDate(2026, 7, 17),
            onSave = {},
            onBack = {},
            isSaving = false,
        )
    }
}

@Composable
@LightDarkPreview
private fun CreateCountdownEditPreview() {
    OpenTasksTheme {
        CreateCountdownContent(
            editCountdown = Countdown(
                id = "preview-edit",
                title = "Christmas",
                targetDate = 1766620800000L,
                countdownType = CountdownType.HOLIDAY,
            ),
            initialType = CountdownType.HOLIDAY,
            currentDate = LocalDate(2026, 7, 17),
            onSave = {},
            onBack = {},
            isSaving = false,
        )
    }
}
