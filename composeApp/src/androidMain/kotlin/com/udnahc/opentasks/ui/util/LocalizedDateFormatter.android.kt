package com.udnahc.opentasks.ui.util

import kotlinx.datetime.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

actual fun formatLocalizedDateWithWeekday(
    date: LocalDate,
    localeTag: String?,
): String = DateTimeFormatter
    .ofLocalizedDate(FormatStyle.FULL)
    .withLocale(localeTag?.let(Locale::forLanguageTag) ?: Locale.getDefault())
    .format(java.time.LocalDate.of(date.year, date.monthNumber, date.dayOfMonth))
