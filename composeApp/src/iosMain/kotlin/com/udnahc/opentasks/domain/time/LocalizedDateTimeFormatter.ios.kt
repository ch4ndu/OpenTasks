package com.udnahc.opentasks.domain.time

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSCalendar
import platform.Foundation.NSLocale
import platform.Foundation.NSTimeZone
import platform.Foundation.currentLocale
import platform.Foundation.dateWithTimeIntervalSince1970
import platform.Foundation.localeIdentifier
import platform.Foundation.timeZoneForSecondsFromGMT

@OptIn(ExperimentalForeignApi::class)
actual class LocalizedDateTimeFormatter actual constructor() : DateTimeTextFormatter {
    override val formattingContextKey: String
        get() {
            val locale = NSLocale.currentLocale
            val calendar = NSCalendar.currentCalendar
            val hourCycle = NSDateFormatter().apply {
                this.locale = locale
                this.calendar = calendar
                setLocalizedDateFormatFromTemplate("j")
            }.dateFormat.orEmpty()
            return "${locale.localeIdentifier}|${calendar.calendarIdentifier}|$hourCycle"
        }

    override fun formatShortDate(localMillis: Long): String =
        format(localMillis, "MMMd")

    override fun formatDateWithYear(localMillis: Long): String =
        format(localMillis, "yMMMd")

    override fun formatDateLabel(localMillis: Long): String =
        format(localMillis, "EEEMMMd")

    override fun formatTime(localMillis: Long): String =
        format(localMillis, "jm")

    override fun formatHour(hour: Int): String =
        format((((hour % 24) + 24) % 24) * 60L * 60L * 1000L, "j")

    override fun formatMonthYear(localMillis: Long): String =
        format(localMillis, "MMMMy")

    override fun formatShortWeekday(localMillis: Long): String =
        format(localMillis, "EEE")

    override fun formatWeekRange(startLocalMillis: Long, endLocalMillis: Long): String =
        "${formatShortDate(startLocalMillis)} – ${formatShortDate(endLocalMillis)}"

    private fun format(localMillis: Long, template: String): String {
        val formatter = NSDateFormatter()
        formatter.timeZone = NSTimeZone.timeZoneForSecondsFromGMT(0)
        formatter.setLocalizedDateFormatFromTemplate(template)
        return formatter.stringFromDate(
            NSDate.dateWithTimeIntervalSince1970(localMillis / 1000.0)
        )
    }
}
