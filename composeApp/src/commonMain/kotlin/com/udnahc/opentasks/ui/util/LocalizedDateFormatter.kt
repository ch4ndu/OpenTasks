package com.udnahc.opentasks.ui.util

import kotlinx.datetime.LocalDate

expect fun formatLocalizedDateWithWeekday(
    date: LocalDate,
    localeTag: String? = null,
): String
