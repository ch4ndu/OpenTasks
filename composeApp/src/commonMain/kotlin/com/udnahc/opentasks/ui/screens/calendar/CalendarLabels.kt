package com.udnahc.opentasks.ui.screens.calendar

import androidx.compose.runtime.Composable
import opentasks.composeapp.generated.resources.Res
import opentasks.composeapp.generated.resources.apr
import opentasks.composeapp.generated.resources.april
import opentasks.composeapp.generated.resources.aug
import opentasks.composeapp.generated.resources.august
import opentasks.composeapp.generated.resources.dec
import opentasks.composeapp.generated.resources.december
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
import opentasks.composeapp.generated.resources.nov
import opentasks.composeapp.generated.resources.november
import opentasks.composeapp.generated.resources.oct
import opentasks.composeapp.generated.resources.october
import opentasks.composeapp.generated.resources.sat
import opentasks.composeapp.generated.resources.sep
import opentasks.composeapp.generated.resources.september
import opentasks.composeapp.generated.resources.sun
import opentasks.composeapp.generated.resources.thu
import opentasks.composeapp.generated.resources.tue
import opentasks.composeapp.generated.resources.wed
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun calendarMonthName(month: Int): String = when (month) {
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
internal fun calendarMonthNameShort(month: Int): String = when (month) {
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
internal fun calendarWeekdayShort(dayIndex: Int): String = when (dayIndex) {
    0 -> stringResource(Res.string.sun)
    1 -> stringResource(Res.string.mon)
    2 -> stringResource(Res.string.tue)
    3 -> stringResource(Res.string.wed)
    4 -> stringResource(Res.string.thu)
    5 -> stringResource(Res.string.fri)
    6 -> stringResource(Res.string.sat)
    else -> ""
}

@Composable
internal fun calendarWeekdayNarrow(dayIndex: Int): String =
    calendarWeekdayShort(dayIndex).take(1)
