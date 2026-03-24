package com.udnahc.opentasks

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.udnahc.opentasks.di.initKoin
import opentasks.composeapp.generated.resources.Res
import opentasks.composeapp.generated.resources.ic_launcher
import org.jetbrains.compose.resources.painterResource

fun main() {
    initKoin()
    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "OpenTasks",
            icon = painterResource(Res.drawable.ic_launcher),
        ) {
            App()
        }
    }
}