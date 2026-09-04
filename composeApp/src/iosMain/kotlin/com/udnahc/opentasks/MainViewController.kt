package com.udnahc.opentasks

import androidx.compose.ui.window.ComposeUIViewController
import com.udnahc.opentasks.di.initKoin
import platform.Foundation.NSLock

private val koinInitializationLock = NSLock()
private var isKoinInitialized = false

fun initializeOpenTasksKoin() {
    koinInitializationLock.lock()
    try {
        if (isKoinInitialized) return
        initKoin()
        isKoinInitialized = true
    } finally {
        koinInitializationLock.unlock()
    }
}

fun MainViewController(): platform.UIKit.UIViewController {
    initializeOpenTasksKoin()
    return ComposeUIViewController { App() }
}
