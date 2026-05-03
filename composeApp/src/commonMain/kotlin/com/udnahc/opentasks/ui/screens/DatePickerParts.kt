package com.udnahc.opentasks.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.udnahc.opentasks.data.extensions.dayOfWeekIndex
import com.udnahc.opentasks.data.extensions.daysInMonth
import com.udnahc.opentasks.ui.theme.OpenTasksTheme
import com.udnahc.opentasks.ui.theme.PrimaryBlue
import opentasks.composeapp.generated.resources.Res
import opentasks.composeapp.generated.resources.fri
import opentasks.composeapp.generated.resources.ic_chevron_left
import opentasks.composeapp.generated.resources.ic_chevron_right
import opentasks.composeapp.generated.resources.mon
import opentasks.composeapp.generated.resources.next_month
import opentasks.composeapp.generated.resources.previous_month
import opentasks.composeapp.generated.resources.sat
import opentasks.composeapp.generated.resources.sun
import opentasks.composeapp.generated.resources.thu
import opentasks.composeapp.generated.resources.tue
import opentasks.composeapp.generated.resources.wed
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.painterResource

@Composable
internal fun MonthPagerHeader(
    title: String,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            style = MaterialTheme.typography.titleMedium,
        )
        Row {
            IconButton(onClick = onPreviousMonth) {
                Icon(
                    painter = painterResource(Res.drawable.ic_chevron_left),
                    contentDescription = stringResource(Res.string.previous_month),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onNextMonth) {
                Icon(
                    painter = painterResource(Res.drawable.ic_chevron_right),
                    contentDescription = stringResource(Res.string.next_month),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
internal fun WeekdayHeader(modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth()) {
        listOf(
            Res.string.sun,
            Res.string.mon,
            Res.string.tue,
            Res.string.wed,
            Res.string.thu,
            Res.string.fri,
            Res.string.sat,
        ).forEach { dayRes ->
            Text(
                text = stringResource(dayRes),
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelMedium,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
internal fun SelectableDayGrid(
    month: Int,
    year: Int,
    selectedDay: Int,
    todayDay: Int,
    onDayClick: (Int) -> Unit,
    useLargeCells: Boolean = false,
) {
    val dimens = OpenTasksTheme.dimens
    val totalDays = daysInMonth(year, month)
    val firstDow = dayOfWeekIndex(year, month, 1)
    val rows = ((totalDays + firstDow + 6) / 7).coerceAtLeast(6)
    val cellHeight = if (useLargeCells) dimens.touchTargetLarge else dimens.reminderRowButtonHeight
    val daySize = if (useLargeCells) dimens.calendarDaySize else dimens.reminderDayButtonSize

    Column {
        for (row in 0 until rows) {
            Row(modifier = Modifier.fillMaxWidth()) {
                for (col in 0..6) {
                    val dayIndex = row * 7 + col - firstDow + 1
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(cellHeight)
                            .then(
                                if (dayIndex in 1..totalDays) {
                                    Modifier.clickable { onDayClick(dayIndex) }
                                } else Modifier
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (dayIndex in 1..totalDays) {
                            val isSelected = dayIndex == selectedDay
                            val isToday = dayIndex == todayDay
                            val shape = if (useLargeCells) CircleShape else RoundedCornerShape(50)
                            Box(
                                modifier = Modifier
                                    .size(daySize)
                                    .then(
                                        when {
                                            isSelected -> Modifier.background(PrimaryBlue, shape)
                                            isToday -> Modifier.background(
                                                PrimaryBlue.copy(alpha = 0.3f),
                                                shape,
                                            )
                                            else -> Modifier
                                        }
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = dayIndex.toString(),
                                    color = if (isSelected) Color.White
                                    else MaterialTheme.colorScheme.onBackground,
                                    style = if (useLargeCells) MaterialTheme.typography.bodySmall
                                    else MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
