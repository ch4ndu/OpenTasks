package com.udnahc.opentasks.util

private const val JVM_DEVELOPMENT_DEBUG_PROPERTY = "opentasks.dev.debug"

actual fun isDebugBuild(): Boolean =
    System.getProperty(JVM_DEVELOPMENT_DEBUG_PROPERTY) == "true"
