package com.udnahc.opentasks.data.extensions

/** Returns the current time in UTC milliseconds since epoch. */
expect fun utcNow(): Long

/** Converts a UTC epoch millis timestamp to local time epoch millis. */
expect fun utcToLocal(utcMillis: Long): Long

/** Converts a local time epoch millis timestamp to UTC epoch millis. */
expect fun localToUtc(localMillis: Long): Long
