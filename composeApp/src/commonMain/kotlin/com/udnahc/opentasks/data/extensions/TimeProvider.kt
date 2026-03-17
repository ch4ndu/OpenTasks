package com.udnahc.opentasks.data.extensions

/** Returns the current time in UTC milliseconds since epoch. */
fun utcNow(): Long = nowUtcMillis()

/** Converts a UTC epoch millis timestamp to local time epoch millis. */
fun utcToLocal(utcMillis: Long): Long = utcMillisToLocalMillis(utcMillis)

/** Converts a local time epoch millis timestamp to UTC epoch millis. */
fun localToUtc(localMillis: Long): Long = localMillisToUtcMillis(localMillis)

/** Returns the current year in local time. */
fun currentYear(): Int = todayLocal().year

/** Returns the current month (1-12) in local time. */
fun currentMonth(): Int = todayLocal().monthNumber

/** Returns the current day of month (1-31) in local time. */
fun currentDay(): Int = todayLocal().dayOfMonth
