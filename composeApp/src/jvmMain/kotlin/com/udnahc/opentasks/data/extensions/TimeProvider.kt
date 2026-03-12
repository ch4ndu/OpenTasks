package com.udnahc.opentasks.data.extensions

import java.util.TimeZone

actual fun utcNow(): Long = System.currentTimeMillis()

actual fun utcToLocal(utcMillis: Long): Long {
    val offset = TimeZone.getDefault().getOffset(utcMillis)
    return utcMillis + offset
}

actual fun localToUtc(localMillis: Long): Long {
    val offset = TimeZone.getDefault().getOffset(localMillis)
    return localMillis - offset
}
