package com.udnahc.opentasks.ui.preview

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.udnahc.opentasks.ui.screens.calendar.CalendarTaskRow
import com.udnahc.opentasks.ui.screens.calendar.CardTaskRow
import com.udnahc.opentasks.ui.screens.calendar.EmptyDayPlaceholder
import com.udnahc.opentasks.ui.screens.calendar.TimelineEventBar
import com.udnahc.opentasks.ui.screens.calendar.TimelineTaskRow
import com.udnahc.opentasks.ui.theme.OpenTasksTheme

@Composable
@LightDarkPreview
private fun TimelineTaskRowPreview() {
    OpenTasksTheme {
        TimelineTaskRow(
            task = PreviewSampleData.sampleTasks[0],
            isFirst = true,
            isLast = true,
            onToggleComplete = {},
            onClick = {},
        )
    }
}

@Composable
@LightDarkPreview
private fun CardTaskRowPreview() {
    OpenTasksTheme {
        CardTaskRow(
            task = PreviewSampleData.sampleTasks[0],
            isToday = true,
            onToggleComplete = {},
            onClick = {},
        )
    }
}

@Composable
@LightDarkPreview
private fun CalendarTaskRowPreview() {
    OpenTasksTheme {
        CalendarTaskRow(
            task = PreviewSampleData.sampleTasks[0],
            onToggleComplete = {},
            onClick = {},
        )
    }
}

@Composable
@LightDarkPreview
private fun TimelineEventBarDayPreview() {
    OpenTasksTheme {
        TimelineEventBar(
            task = PreviewSampleData.sampleTasks[0],
            modifier = Modifier.fillMaxWidth().height(24.dp),
            onClick = {},
            onToggleComplete = {},
            iconSize = OpenTasksTheme.dimens.iconMedium,
            horizontalPadding = OpenTasksTheme.dimens.paddingSmall,
            iconSpacing = OpenTasksTheme.dimens.paddingSmall,
            showTime = true,
        )
    }
}

@Composable
@LightDarkPreview
private fun TimelineEventBarWeekPreview() {
    OpenTasksTheme {
        TimelineEventBar(
            task = PreviewSampleData.sampleTasks[0],
            modifier = Modifier
                .fillMaxWidth()
                .height(OpenTasksTheme.dimens.calendarMonthGridEventHeight)
                .padding(vertical = 1.dp),
            onClick = {},
        )
    }
}

@Composable
@LightDarkPreview
private fun EmptyDayPlaceholderPreview() {
    OpenTasksTheme {
        EmptyDayPlaceholder()
    }
}
