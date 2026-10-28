package com.udnahc.opentasks.ui.screens.calendar

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.udnahc.opentasks.ui.screens.SelectableDayGrid
import com.udnahc.opentasks.ui.screens.WeekdayHeader
import com.udnahc.opentasks.ui.theme.OpenTasksTheme
import kotlinx.datetime.LocalDate
import opentasks.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.painterResource

/** Anchored to its period button by the caller's Box. */
@Composable
internal fun CalendarDatePicker(
    expanded: Boolean, selected: LocalDate, today: LocalDate, minDate: LocalDate, maxDate: LocalDate,
    onDismiss: () -> Unit, onDate: (LocalDate) -> Boolean,
) {
    if (!expanded) return
    val d = OpenTasksTheme.dimens
    var year by remember { mutableStateOf(selected.year) }
    var month by remember { mutableStateOf(selected.monthNumber) }
    var chooseYear by remember { mutableStateOf(false) }
    var enterDate by remember { mutableStateOf(false) }
    var input by remember { mutableStateOf(selected.toString()) }
    var error by remember { mutableStateOf<CalendarNavigationDateResult?>(null) }
    fun accept(date: LocalDate) { if (onDate(date)) onDismiss() else error = CalendarNavigationDateResult.OutOfRange }
    fun stepMonth(delta: Int) {
        val value = year * 12 + month - 1 + delta
        val nextYear = value / 12
        val nextMonth = value % 12 + 1
        if (nextYear < minDate.year || nextYear > maxDate.year) return
        if (nextYear == minDate.year && nextMonth < minDate.monthNumber || nextYear == maxDate.year && nextMonth > maxDate.monthNumber) return
        year = nextYear; month = nextMonth
    }
    DropdownMenu(expanded = true, onDismissRequest = onDismiss) {
        Column(Modifier.width(d.calendarPickerWidth).padding(d.paddingLarge)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { chooseYear = !chooseYear; enterDate = false }, modifier = Modifier.weight(1f)) { Text(stringResource(Res.string.calendar_period_month, calendarMonthName(month), year)) }
                IconButton(onClick = { stepMonth(-1) }) { Icon(painterResource(Res.drawable.ic_chevron_left), stringResource(Res.string.previous_month)) }
                IconButton(onClick = { stepMonth(1) }) { Icon(painterResource(Res.drawable.ic_chevron_right), stringResource(Res.string.next_month)) }
            }
            when {
                enterDate -> {
                    OutlinedTextField(input, { input = it; error = null }, label = { Text(stringResource(Res.string.calendar_go_to_date)) }, placeholder = { Text(stringResource(Res.string.calendar_date_format)) }, singleLine = true, isError = error != null, modifier = Modifier.fillMaxWidth())
                    TextButton(onClick = {
                        when (val parsed = parseCalendarNavigationDate(input, minDate, maxDate)) {
                            is CalendarNavigationDateResult.Valid -> accept(parsed.date)
                            else -> error = parsed
                        }
                    }) { Text(stringResource(Res.string.calendar_go)) }
                }
                chooseYear -> {
                    val years = remember(minDate, maxDate) { (minDate.year..maxDate.year).toList() }
                    LazyVerticalGrid(GridCells.Fixed(3), Modifier.height(d.minPagerHeight)) {
                        items(years, key = { it }) { value -> TextButton(onClick = { year = value; chooseYear = false }) { Text(value.toString()) } }
                    }
                }
                else -> {
                    WeekdayHeader()
                    SelectableDayGrid(month, year, if (year == selected.year && month == selected.monthNumber) selected.dayOfMonth else 0,
                        if (year == today.year && month == today.monthNumber) today.dayOfMonth else 0,
                        onDayClick = { day -> accept(LocalDate(year, month, day)) })
                }
            }
            if (error != null) Text(stringResource(if (error == CalendarNavigationDateResult.Invalid) Res.string.calendar_invalid_date else Res.string.calendar_date_out_of_range), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            Row {
                TextButton(onClick = { accept(today) }) { Text(stringResource(Res.string.today)) }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { enterDate = !enterDate; chooseYear = false }) { Text(stringResource(Res.string.calendar_enter_date)) }
            }
        }
    }
}
