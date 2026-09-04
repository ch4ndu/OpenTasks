package com.udnahc.opentasks.domain.time

import android.content.Context
import android.text.format.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

actual class LocalizedDateTimeFormatter actual constructor() : DateTimeTextFormatter {
    private var context: Context? = null

    constructor(context: Context) : this() {
        this.context = context.applicationContext
    }

    private val locale: Locale
        get() = Locale.getDefault()

    private val uses24HourTime: Boolean
        get() = context?.let(DateFormat::is24HourFormat)
            ?: !DateFormat.getBestDateTimePattern(locale, "j").contains('a')

    override fun formatShortDate(localMillis: Long): String =
        format(localMillis, "MMMd")

    override fun formatDateWithYear(localMillis: Long): String =
        format(localMillis, "yMMMd")

    override fun formatDateLabel(localMillis: Long): String =
        format(localMillis, "EEEMMMd")

    override fun formatTime(localMillis: Long): String =
        format(localMillis, if (uses24HourTime) "Hm" else "hma")

    override fun formatHour(hour: Int): String =
        format(
            localMillis = (((hour % 24) + 24) % 24) * 60L * 60L * 1000L,
            skeleton = if (uses24HourTime) "H" else "ha",
        )

    override fun formatMonthYear(localMillis: Long): String =
        format(localMillis, "MMMMy")

    override fun formatShortWeekday(localMillis: Long): String =
        format(localMillis, "EEE")

    override fun formatWeekRange(startLocalMillis: Long, endLocalMillis: Long): String =
        "${formatShortDate(startLocalMillis)} – ${formatShortDate(endLocalMillis)}"

    private fun format(localMillis: Long, skeleton: String): String {
        val pattern = DateFormat.getBestDateTimePattern(locale, skeleton)
        return SimpleDateFormat(pattern, locale).apply {
            timeZone = UTC
        }.format(Date(localMillis))
    }

    private companion object {
        val UTC: TimeZone = TimeZone.getTimeZone("UTC")
    }
}
