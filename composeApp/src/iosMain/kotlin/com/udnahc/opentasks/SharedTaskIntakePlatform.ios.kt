package com.udnahc.opentasks

import platform.Foundation.NSNotificationCenter

internal actual fun requestPlatformSharedTaskIntakeScan() {
    NSNotificationCenter.defaultCenter.postNotificationName(
        aName = "OpenTasksSharedTaskIntakeReady",
        `object` = null,
    )
}
