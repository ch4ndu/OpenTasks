package com.udnahc.opentasks.ui.screens.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import com.udnahc.opentasks.data.extensions.extractHour
import com.udnahc.opentasks.data.extensions.extractMinute
import com.udnahc.opentasks.data.extensions.formatDateShort
import com.udnahc.opentasks.data.extensions.formatTimeFromLocalMillis
import androidx.compose.ui.tooling.preview.Preview
import com.udnahc.opentasks.data.model.Task
import com.udnahc.opentasks.ui.preview.PreviewSampleData
import com.udnahc.opentasks.ui.theme.OpenTasksTheme
import com.udnahc.opentasks.ui.theme.PrimaryBlue
import opentasks.composeapp.generated.resources.Res
import opentasks.composeapp.generated.resources.ic_alarm
import opentasks.composeapp.generated.resources.ic_check_box
import opentasks.composeapp.generated.resources.ic_check_box_outline
import opentasks.composeapp.generated.resources.all_day
import opentasks.composeapp.generated.resources.inbox
import opentasks.composeapp.generated.resources.no_tasks
import opentasks.composeapp.generated.resources.today
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

// ── Timeline task row (time | line+circle | card) ───────────────────────────

@Composable
internal fun TimelineTaskRow(
    task: Task,
    isFirst: Boolean,
    isLast: Boolean,
    onToggleComplete: () -> Unit,
    onClick: () -> Unit,
) {
    val dimens = OpenTasksTheme.dimens
    val lineColor = MaterialTheme.colorScheme.surfaceVariant

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = dimens.paddingLarge, end = dimens.paddingXLarge),
        verticalAlignment = Alignment.Top,
    ) {
        // ── Time label ───
        val timeText = if (task.deadline != null) {
            val h = extractHour(task.deadline)
            val m = extractMinute(task.deadline)
            if (h == 0 && m == 0) stringResource(Res.string.all_day) else formatTimeFromLocalMillis(task.deadline)
        } else stringResource(Res.string.all_day)

        Text(
            text = timeText,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .width(dimens.calendarTimeColumnWidth)
                .padding(top = dimens.paddingXLarge),
            textAlign = TextAlign.End,
        )

        Spacer(Modifier.width(dimens.spacerLarge))

        // ── Vertical line + circle ───
        Box(
            modifier = Modifier
                .width(dimens.calendarTimeIndicatorWidth)
                .height(dimens.calendarTimelineHeight),
            contentAlignment = Alignment.TopCenter,
        ) {
            // Vertical line — top half
            if (!isFirst) {
                Box(
                    modifier = Modifier
                        .width(dimens.calendarTimelineDividerWidth)
                        .height(dimens.calendarTimelineDividerHeight)
                        .align(Alignment.TopCenter)
                        .background(lineColor),
                )
            }
            // Circle
            Icon(
                painter = painterResource(
                    if (task.isCompleted) Res.drawable.ic_check_box
                    else Res.drawable.ic_check_box_outline
                ),
                contentDescription = null,
                tint = if (task.isCompleted) MaterialTheme.colorScheme.onSurfaceVariant
                else taskPriorityColor(task.priority),
                modifier = Modifier
                    .size(dimens.calendarTimelineMarkerSize)
                    .offset(y = dimens.calendarTimelineDividerHeight)
                    .clickable { onToggleComplete() },
            )
            // Vertical line — bottom half
            if (!isLast) {
                Box(
                    modifier = Modifier
                        .width(dimens.calendarTimelineDividerWidth)
                        .fillMaxHeight()
                        .padding(top = dimens.calendarTimelineDividerHeight + dimens.calendarTimelineMarkerSize + dimens.spacerTiny)
                        .align(Alignment.TopCenter)
                        .background(lineColor),
                )
            }
        }

        Spacer(Modifier.width(dimens.spacerLarge))

        // ── Task card ───
        Box(
            modifier = Modifier
                .weight(1f)
                .padding(vertical = dimens.spacerMedium)
                .clip(RoundedCornerShape(dimens.cornerXLarge))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                .padding(horizontal = dimens.paddingLarge, vertical = dimens.paddingLarge),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (task.isCompleted) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.onBackground,
                    textDecoration = if (task.isCompleted) TextDecoration.LineThrough else null,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (task.deadline != null && task.notifyBeforeValue > 0) {
                    Spacer(Modifier.width(dimens.spacerLarge))
                    Icon(
                        painter = painterResource(Res.drawable.ic_alarm),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(dimens.iconMedium),
                    )
                }
            }
        }
    }
}

// ── Card task row (checkbox | title + date | list name) ─────────────────────

@Composable
internal fun CardTaskRow(
    task: Task,
    isToday: Boolean,
    onToggleComplete: () -> Unit,
    onClick: () -> Unit,
) {
    val dimens = OpenTasksTheme.dimens
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = dimens.paddingLarge, vertical = dimens.paddingLarge),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = onToggleComplete,
            modifier = Modifier.size(dimens.touchTargetMedium),
        ) {
            Icon(
                painter = painterResource(
                    if (task.isCompleted) Res.drawable.ic_check_box
                    else Res.drawable.ic_check_box_outline
                ),
                contentDescription = null,
                tint = if (task.isCompleted) MaterialTheme.colorScheme.onSurfaceVariant
                else taskPriorityColor(task.priority),
                modifier = Modifier.size(dimens.iconLarge),
            )
        }
        Spacer(Modifier.width(dimens.spacerLarge))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = task.title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (task.isCompleted) MaterialTheme.colorScheme.onSurfaceVariant
                else MaterialTheme.colorScheme.onBackground,
                textDecoration = if (task.isCompleted) TextDecoration.LineThrough else null,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (task.deadline != null && !task.isCompleted) {
                val dayPrefix = if (isToday) stringResource(Res.string.today) else formatDateShort(task.deadline)
                val h = extractHour(task.deadline)
                val m = extractMinute(task.deadline)
                val timeStr =
                    if (h == 0 && m == 0) "" else ", ${formatTimeFromLocalMillis(task.deadline)}"
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "$dayPrefix$timeStr",
                        style = MaterialTheme.typography.labelMedium,
                        color = PrimaryBlue,
                    )
                    if (task.notifyBeforeValue > 0) {
                        Spacer(Modifier.width(dimens.spacerSmall))
                        Icon(
                            painter = painterResource(Res.drawable.ic_alarm),
                            contentDescription = null,
                            tint = PrimaryBlue,
                            modifier = Modifier.size(dimens.iconSmall),
                        )
                    }
                }
            }
        }
        Text(
            text = stringResource(Res.string.inbox),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ── Calendar task row (used in month view) ──────────────────────────────────

@Composable
internal fun CalendarTaskRow(
    task: Task,
    onToggleComplete: () -> Unit,
    onClick: () -> Unit,
) {
    val dimens = OpenTasksTheme.dimens
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = dimens.paddingLarge, vertical = dimens.paddingLarge),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = onToggleComplete,
            modifier = Modifier.size(dimens.touchTargetMedium),
        ) {
            Icon(
                painter = painterResource(
                    if (task.isCompleted) Res.drawable.ic_check_box
                    else Res.drawable.ic_check_box_outline
                ),
                contentDescription = null,
                tint = if (task.isCompleted) MaterialTheme.colorScheme.onSurfaceVariant
                else taskPriorityColor(task.priority),
                modifier = Modifier.size(dimens.iconLarge),
            )
        }
        Spacer(Modifier.width(dimens.spacerLarge))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = task.title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (task.isCompleted) MaterialTheme.colorScheme.onSurfaceVariant
                else MaterialTheme.colorScheme.onBackground,
                textDecoration = if (task.isCompleted) TextDecoration.LineThrough else null,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (task.deadline != null && !task.isCompleted) {
                Text(
                    text = formatTimeFromLocalMillis(task.deadline),
                    style = MaterialTheme.typography.labelMedium,
                    color = PrimaryBlue,
                )
            }
        }
        Text(
            text = stringResource(Res.string.inbox),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ── Empty day placeholder ───────────────────────────────────────────────────

@Composable
internal fun EmptyDayPlaceholder() {
    val dimens = OpenTasksTheme.dimens
    Box(
        modifier = Modifier.fillMaxWidth().padding(vertical = dimens.calendarEmptyPadding),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(Res.string.no_tasks),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ── Previews ─────────────────────────────────────────────────────────────────

@Composable
@Preview
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
@Preview
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
@Preview
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
@Preview
private fun EmptyDayPlaceholderPreview() {
    OpenTasksTheme {
        EmptyDayPlaceholder()
    }
}
