package com.udnahc.opentasks.ui.screens

import androidx.compose.runtime.Composable
import com.udnahc.opentasks.data.model.RecurrenceType
import opentasks.composeapp.generated.resources.Res
import opentasks.composeapp.generated.resources.am
import opentasks.composeapp.generated.resources.apr
import opentasks.composeapp.generated.resources.april
import opentasks.composeapp.generated.resources.aug
import opentasks.composeapp.generated.resources.august
import opentasks.composeapp.generated.resources.daily
import opentasks.composeapp.generated.resources.december
import opentasks.composeapp.generated.resources.dec
import opentasks.composeapp.generated.resources.every_weekday
import opentasks.composeapp.generated.resources.feb
import opentasks.composeapp.generated.resources.february
import opentasks.composeapp.generated.resources.fri
import opentasks.composeapp.generated.resources.jan
import opentasks.composeapp.generated.resources.january
import opentasks.composeapp.generated.resources.jul
import opentasks.composeapp.generated.resources.july
import opentasks.composeapp.generated.resources.jun
import opentasks.composeapp.generated.resources.june
import opentasks.composeapp.generated.resources.mar
import opentasks.composeapp.generated.resources.march
import opentasks.composeapp.generated.resources.may
import opentasks.composeapp.generated.resources.may_short
import opentasks.composeapp.generated.resources.mon
import opentasks.composeapp.generated.resources.monthly
import opentasks.composeapp.generated.resources.none
import opentasks.composeapp.generated.resources.nov
import opentasks.composeapp.generated.resources.november
import opentasks.composeapp.generated.resources.oct
import opentasks.composeapp.generated.resources.october
import opentasks.composeapp.generated.resources.pm
import opentasks.composeapp.generated.resources.sat
import opentasks.composeapp.generated.resources.sep
import opentasks.composeapp.generated.resources.september
import opentasks.composeapp.generated.resources.sun
import opentasks.composeapp.generated.resources.thu
import opentasks.composeapp.generated.resources.tue
import opentasks.composeapp.generated.resources.wed
import opentasks.composeapp.generated.resources.weekly
import opentasks.composeapp.generated.resources.yearly
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun recurrenceLabel(type: RecurrenceType): String = when (type) {
    RecurrenceType.NONE -> stringResource(Res.string.none)
    RecurrenceType.DAILY -> stringResource(Res.string.daily)
    RecurrenceType.WEEKLY -> stringResource(Res.string.weekly)
    RecurrenceType.MONTHLY -> stringResource(Res.string.monthly)
    RecurrenceType.YEARLY -> stringResource(Res.string.yearly)
    RecurrenceType.EVERY_WEEKDAY -> stringResource(Res.string.every_weekday)
}

@Composable
internal fun formatTime(
    hour: Int,
    minute: Int
): String {
    val amPm = if (hour < 12) stringResource(Res.string.am) else stringResource(Res.string.pm)
    val displayHour = if (hour == 0) 12 else if (hour > 12) hour - 12 else hour
    return "$displayHour:${minute.toString().padStart(2, '0')} $amPm"
}

@Composable
internal fun monthName(month: Int): String = when (month) {
    1 -> stringResource(Res.string.january)
    2 -> stringResource(Res.string.february)
    3 -> stringResource(Res.string.march)
    4 -> stringResource(Res.string.april)
    5 -> stringResource(Res.string.may)
    6 -> stringResource(Res.string.june)
    7 -> stringResource(Res.string.july)
    8 -> stringResource(Res.string.august)
    9 -> stringResource(Res.string.september)
    10 -> stringResource(Res.string.october)
    11 -> stringResource(Res.string.november)
    12 -> stringResource(Res.string.december)
    else -> ""
}

@Composable
internal fun monthNameShort(month: Int): String = when (month) {
    1 -> stringResource(Res.string.jan)
    2 -> stringResource(Res.string.feb)
    3 -> stringResource(Res.string.mar)
    4 -> stringResource(Res.string.apr)
    5 -> stringResource(Res.string.may_short)
    6 -> stringResource(Res.string.jun)
    7 -> stringResource(Res.string.jul)
    8 -> stringResource(Res.string.aug)
    9 -> stringResource(Res.string.sep)
    10 -> stringResource(Res.string.oct)
    11 -> stringResource(Res.string.nov)
    12 -> stringResource(Res.string.dec)
    else -> ""
}

@Composable
internal fun dayOfWeekName(
    firstDayOfMonth: Int,
    day: Int
): String {
    val dayOfWeek = (firstDayOfMonth + day - 1) % 7
    return when (dayOfWeek) {
        0 -> stringResource(Res.string.sun)
        1 -> stringResource(Res.string.mon)
        2 -> stringResource(Res.string.tue)
        3 -> stringResource(Res.string.wed)
        4 -> stringResource(Res.string.thu)
        5 -> stringResource(Res.string.fri)
        6 -> stringResource(Res.string.sat)
        else -> ""
    }
}
