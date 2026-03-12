package com.udnahc.opentasks

import androidx.compose.ui.window.ComposeUIViewController
import com.udnahc.opentasks.di.initKoin

fun MainViewController(): platform.UIKit.UIViewController {
    initKoin()
    return ComposeUIViewController { App() }
}