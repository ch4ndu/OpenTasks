package com.udnahc.opentasks.ui.preview

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.udnahc.opentasks.data.model.ThemeMode
import com.udnahc.opentasks.data.model.UiExperience
import com.udnahc.opentasks.ui.screens.AccountSessionEntryMode
import com.udnahc.opentasks.ui.screens.AccountSessionScreen
import com.udnahc.opentasks.ui.theme.OpenTasksTheme

@Preview(name = "Classic light phone", widthDp = 360, heightDp = 800)
@Preview(name = "Classic light wide", widthDp = 1100, heightDp = 800)
@Composable
private fun ClassicWelcomeLight() = WelcomePreview(UiExperience.CLASSIC, ThemeMode.LIGHT)

@Preview(name = "Classic dark phone", widthDp = 360, heightDp = 800)
@Preview(name = "Classic dark wide", widthDp = 1100, heightDp = 800)
@Composable
private fun ClassicWelcomeDark() = WelcomePreview(UiExperience.CLASSIC, ThemeMode.DARK)

@Preview(name = "Reimagined light phone", widthDp = 360, heightDp = 800)
@Preview(name = "Reimagined light wide", widthDp = 1100, heightDp = 800)
@Composable
private fun ReimaginedWelcomeLight() = WelcomePreview(UiExperience.REIMAGINED, ThemeMode.LIGHT)

@Preview(name = "Reimagined dark phone", widthDp = 360, heightDp = 800)
@Preview(name = "Reimagined dark wide", widthDp = 1100, heightDp = 800)
@Composable
private fun ReimaginedWelcomeDark() = WelcomePreview(UiExperience.REIMAGINED, ThemeMode.DARK)

@Composable
private fun WelcomePreview(experience: UiExperience, theme: ThemeMode) {
    OpenTasksTheme(uiExperience = experience, themeMode = theme) {
        AccountSessionScreen(
            mode = AccountSessionEntryMode.SIGN_IN,
            account = null,
            endpoint = null,
            operation = null,
            error = null,
            storageWarning = null,
            allowLocalOnly = true,
            onSignIn = { _, _, _ -> },
            onReauthenticate = { _, _ -> },
            onUseWithoutSync = {},
            onClearError = {},
        )
    }
}
