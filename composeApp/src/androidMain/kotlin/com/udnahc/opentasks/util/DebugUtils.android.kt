package com.udnahc.opentasks.util

private var isAndroidDebugBuild = true

actual fun isDebugBuild(): Boolean = isAndroidDebugBuild

fun initializeAndroidDebugBuild(isDebugBuild: Boolean) {
    isAndroidDebugBuild = isDebugBuild
}
