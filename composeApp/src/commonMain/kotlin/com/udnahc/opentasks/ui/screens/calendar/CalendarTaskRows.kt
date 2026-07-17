package com.udnahc.opentasks.ui.screens.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.udnahc.opentasks.data.extensions.extractHour
import com.udnahc.opentasks.data.extensions.extractMinute
import com.udnahc.opentasks.data.extensions.formatDateShort
import com.udnahc.opentasks.data.extensions.formatTime12Hr
import com.udnahc.opentasks.data.extensions.formatTimeFromLocalMillis
import com.udnahc.opentasks.data.model.Task
import com.udnahc.opentasks.data.model.TaskStatus
import com.udnahc.opentasks.data.model.isCountdownItem
import com.udnahc.opentasks.ui.screens.EmptyPlaceholder
import com.udnahc.opentasks.ui.screens.TaskCheckboxButton
import com.udnahc.opentasks.ui.screens.TaskTitleText
import com.udnahc.opentasks.ui.theme.OpenTasksTheme
import com.udnahc.opentasks.ui.theme.PrimaryBlue
import com.udnahc.opentasks.ui.theme.priorityColor
import opentasks.composeapp.generated.resources.Res
import opentasks.composeapp.generated.resources.all_day
import opentasks.composeapp.generated.resources.ic_alarm
import opentasks.composeapp.generated.resources.ic_check_box
import opentasks.composeapp.generated.resources.ic_check_box_outline
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
            if (h == 0 && m == 0) stringResource(Res.string.all_day) else formatTimeFromLocalMillis(
                task.deadline
            )
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
            val isCountdownItem = task.isCountdownItem
            Icon(
                painter = painterResource(
                    if (!isCountdownItem && task.status == TaskStatus.DONE) Res.drawable.ic_check_box
                    else Res.drawable.ic_check_box_outline
                ),
                contentDescription = null,
                tint = if (!isCountdownItem && task.status == TaskStatus.DONE) MaterialTheme.colorScheme.onSurfaceVariant.copy(
                    alpha = 0.4f
                )
                else priorityColor(task.priority),
                modifier = Modifier
                    .size(dimens.calendarTimelineMarkerSize)
                    .offset(y = dimens.calendarTimelineDividerHeight)
                    .let { if (!isCountdownItem) it.clickable { onToggleComplete() } else it },
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
                    color = if (task.status == TaskStatus.DONE) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.onBackground,
                    textDecoration = if (task.status == TaskStatus.DONE) TextDecoration.LineThrough else null,
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
    categoryName: String,
    onToggleComplete: () -> Unit,
    onClick: () -> Unit,
) {
    val dimens = OpenTasksTheme.dimens
    val isCountdownItem = task.isCountdownItem
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = dimens.paddingLarge, vertical = dimens.paddingLarge),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!isCountdownItem) {
            TaskCheckboxButton(
                isChecked = task.status == TaskStatus.DONE,
                tint = if (task.status == TaskStatus.DONE) {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                } else {
                    priorityColor(task.priority)
                },
                onClick = onToggleComplete,
            )
        }
        Spacer(Modifier.width(dimens.spacerLarge))
        Column(modifier = Modifier.weight(1f)) {
            TaskTitleText(
                title = task.title,
                isCompleted = task.status == TaskStatus.DONE,
            )
            if (task.deadline != null) {
                val dayPrefix =
                    if (isToday) stringResource(Res.string.today) else formatDateShort(task.deadline)
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
            text = categoryName,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ── Calendar task row (used in month view) ──────────────────────────────────

@Composable
internal fun CalendarTaskRow(
    task: Task,
    categoryName: String,
    onToggleComplete: () -> Unit,
    onClick: () -> Unit,
) {
    val dimens = OpenTasksTheme.dimens
    val isCountdownItem = task.isCountdownItem
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = dimens.paddingLarge, vertical = dimens.paddingLarge),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!isCountdownItem) {
            TaskCheckboxButton(
                isChecked = task.status == TaskStatus.DONE,
                tint = if (task.status == TaskStatus.DONE) {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                } else {
                    priorityColor(task.priority)
                },
                onClick = onToggleComplete,
            )
        }
        Spacer(Modifier.width(dimens.spacerLarge))
        Column(modifier = Modifier.weight(1f)) {
            TaskTitleText(
                title = task.title,
                isCompleted = task.status == TaskStatus.DONE,
            )
            if (task.deadline != null) {
                Text(
                    text = formatTimeFromLocalMillis(task.deadline),
                    style = MaterialTheme.typography.labelMedium,
                    color = PrimaryBlue,
                )
            }
        }
        Text(
            text = categoryName,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ── Timeline event bar (shared by Day, 3-Day, and Week views) ───────────────

/**
 * Compact event bar used inside Day, 3-Day, and Week timeline grids.
 *
 * @param task           The task to display.
 * @param modifier       Outer modifier (size, offset, padding applied by caller).
 * @param onClick        Called when the bar is tapped.
 * @param onToggleComplete Called when the checkbox icon is tapped. When null the
 *                         checkbox icon is omitted entirely (Week view).
 * @param iconSize       Size of the checkbox icon. Ignored when [onToggleComplete] is null.
 * @param horizontalPadding Inner horizontal padding of the bar content.
 * @param iconSpacing    Space between the checkbox icon and the title text.
 * @param showTime       Whether to display the formatted time at the trailing edge.
 */
@Composable
internal fun TimelineEventBar(
    task: Task,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onToggleComplete: (() -> Unit)? = null,
    iconSize: Dp = OpenTasksTheme.dimens.iconSmall,
    horizontalPadding: Dp = 2.dp,
    iconSpacing: Dp = 2.dp,
    showTime: Boolean = false,
    timeText: String? = null,
) {
    val dimens = OpenTasksTheme.dimens
    val priorityColor = priorityColor(task.priority)
    val isCountdown = task.isCountdownItem
    val effectiveToggle = if (isCountdown) null else onToggleComplete
    val bgAlpha = if (effectiveToggle != null && task.status == TaskStatus.DONE) 0.1f else 0.2f
    val contentColor = if (effectiveToggle != null && task.status == TaskStatus.DONE)
        MaterialTheme.colorScheme.onSurfaceVariant else priorityColor

    if (effectiveToggle != null) {
        // Day view & 3-Day view: Row with checkbox icon + title (+ optional time)
        Row(
            modifier = modifier
                .clip(RoundedCornerShape(dimens.cornerTiny))
                .background(priorityColor.copy(alpha = bgAlpha))
                .clickable(onClick = onClick)
                .padding(horizontal = horizontalPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(
                    if (task.status == TaskStatus.DONE) Res.drawable.ic_check_box
                    else Res.drawable.ic_check_box_outline
                ),
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier
                    .size(iconSize)
                    .clickable(onClick = effectiveToggle),
            )
            Spacer(Modifier.width(iconSpacing))
            Text(
                text = task.title,
                style = OpenTasksTheme.typography.calendarEventTitle,
                color = contentColor,
                textDecoration = if (task.status == TaskStatus.DONE) TextDecoration.LineThrough else null,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = if (showTime) Modifier.weight(1f) else Modifier,
            )
            if (showTime && !timeText.isNullOrEmpty()) {
                Spacer(Modifier.width(dimens.paddingSmall))
                Text(
                    text = timeText,
                    style = MaterialTheme.typography.labelSmall,
                    color = contentColor,
                    maxLines = 1,
                )
            }
        }
    } else {
        // Week view: simple Box with title only, no checkbox
        Box(
            modifier = modifier
                .clip(RoundedCornerShape(dimens.cornerTiny))
                .background(priorityColor.copy(alpha = 0.4f))
                .clickable(onClick = onClick)
                .padding(horizontal = horizontalPadding),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                text = task.title,
                style = OpenTasksTheme.typography.calendarEventTitle,
                color = priorityColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// ── Empty day placeholder ───────────────────────────────────────────────────

@Composable
internal fun EmptyDayPlaceholder() {
    val dimens = OpenTasksTheme.dimens
    EmptyPlaceholder(
        text = stringResource(Res.string.no_tasks),
        modifier = Modifier.fillMaxWidth().padding(vertical = dimens.calendarEmptyPadding),
    )
}
