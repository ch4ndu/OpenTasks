package com.udnahc.opentasks.data.extensions

import platform.Foundation.NSDate
import platform.Foundation.NSTimeZone
import platform.Foundation.localTimeZone
import platform.Foundation.secondsFromGMT
import platform.Foundation.timeIntervalSince1970

actual fun utcNow(): Long =
    (NSDate().timeIntervalSince1970 * 1000).toLong()

actual fun utcToLocal(utcMillis: Long): Long {
    val offset = NSTimeZone.localTimeZone.secondsFromGMT
    return utcMillis + (offset * 1000)
}

actual fun localToUtc(localMillis: Long): Long {
    val offset = NSTimeZone.localTimeZone.secondsFromGMT
    return localMillis - (offset * 1000)
}
