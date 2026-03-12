package com.udnahc.opentasks

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.udnahc.opentasks.di.initKoin

fun main() {
    initKoin()
    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "OpenTasks",
        ) {
            App()
        }
    }
}