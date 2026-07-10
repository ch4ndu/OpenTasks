package com.udnahc.opentasks.ui.util

import com.udnahc.opentasks.data.extensions.startOfDayLocalMillis
import kotlinx.datetime.LocalDate
import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSDateFormatterFullStyle
import platform.Foundation.NSDateFormatterNoStyle
import platform.Foundation.NSLocale
import platform.Foundation.NSTimeZone
import platform.Foundation.dateWithTimeIntervalSince1970
import platform.Foundation.timeZoneForSecondsFromGMT

actual fun formatLocalizedDateWithWeekday(
    date: LocalDate,
    localeTag: String?,
): String {
    val formatter = NSDateFormatter().apply {
        dateStyle = NSDateFormatterFullStyle
        timeStyle = NSDateFormatterNoStyle
        localeTag?.let { locale = NSLocale(it) }
        timeZone = NSTimeZone.timeZoneForSecondsFromGMT(0)
    }
    val dateAtUtcMidnight = NSDate.dateWithTimeIntervalSince1970(
        startOfDayLocalMillis(date.year, date.monthNumber, date.dayOfMonth) / 1000.0,
    )
    return formatter.stringFromDate(dateAtUtcMidnight)
}
