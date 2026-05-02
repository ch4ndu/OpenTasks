package com.udnahc.opentasks.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.udnahc.opentasks.data.extensions.formatDateShort
import com.udnahc.opentasks.data.model.Task
import com.udnahc.opentasks.data.model.TaskPriority
import com.udnahc.opentasks.data.model.TaskStatus
import com.udnahc.opentasks.ui.theme.OpenTasksTheme
import com.udnahc.opentasks.ui.theme.PrimaryBlue
import com.udnahc.opentasks.ui.theme.StarGold
import com.udnahc.opentasks.ui.theme.priorityColor
import opentasks.composeapp.generated.resources.Res
import opentasks.composeapp.generated.resources.ic_star
import opentasks.composeapp.generated.resources.status_done
import opentasks.composeapp.generated.resources.status_in_progress
import opentasks.composeapp.generated.resources.status_todo
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

private val KanbanTodoColor = PrimaryBlue
private val KanbanInProgressColor = Color(0xFFFF9800)
private val KanbanDoneColor = Color(0xFF4CAF50)

@Composable
fun KanbanBoardContent(
    tasksByStatus: Map<TaskStatus, List<Task>>,
    onTaskClick: (Task) -> Unit,
    onStatusChange: (Task, TaskStatus) -> Unit,
    onToggleStar: (Task) -> Unit,
    topBarHeight: Dp,
    navBarHeight: Dp,
) {
    val dimens = OpenTasksTheme.dimens
    val density = LocalDensity.current

    // Drag state
    var draggedTask by remember { mutableStateOf<Task?>(null) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    var cardStartPosition by remember { mutableStateOf(Offset.Zero) }
    var draggedCardWidth by remember { mutableStateOf(0) }
    var highlightedColumn by remember { mutableStateOf<TaskStatus?>(null) }
    val columnBounds = remember { mutableStateMapOf<TaskStatus, Rect>() }
    var containerPosition by remember { mutableStateOf(Offset.Zero) }
    var initialTouchLocalOffset by remember { mutableStateOf(Offset.Zero) }
    var isDragging by remember { mutableStateOf(false) }
    var fingerRootX by remember { mutableStateOf(0f) }
    val phoneScrollState = rememberScrollState()

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(top = topBarHeight)
            .onGloballyPositioned { coords ->
                containerPosition = coords.positionInRoot()
            },
    ) {
        val isWideLayout = maxWidth >= 600.dp

        if (isWideLayout) {
            // Tablet/desktop: equal-width columns, no horizontal scroll
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        start = dimens.paddingMedium,
                        end = dimens.paddingMedium,
                        bottom = navBarHeight + dimens.fabAreaBottom,
                    ),
                horizontalArrangement = Arrangement.spacedBy(dimens.spacerLarge),
            ) {
                TaskStatus.entries.forEach { status ->
                    KanbanColumn(
                        status = status,
                        tasks = tasksByStatus[status].orEmpty(),
                        color = statusColor(status),
                        isDropTarget = highlightedColumn == status,
                        draggedTaskId = draggedTask?.id,
                        onTaskClick = onTaskClick,
                        onDragStart = { task, offset, width, _ ->
                            draggedTask = task
                            cardStartPosition = offset
                            draggedCardWidth = width
                            dragOffset = Offset.Zero
                        },
                        onDrag = { delta ->
                            dragOffset += delta
                            val currentPos = cardStartPosition + dragOffset
                            highlightedColumn = columnBounds.entries
                                .firstOrNull { (_, rect) -> rect.contains(currentPos) }
                                ?.key
                        },
                        onDragEnd = {
                            val target = highlightedColumn
                            val task = draggedTask
                            if (task != null && target != null && target != task.status) {
                                onStatusChange(task, target)
                            }
                            draggedTask = null
                            dragOffset = Offset.Zero
                            highlightedColumn = null
                        },
                        onDragCancel = {
                            draggedTask = null
                            dragOffset = Offset.Zero
                            highlightedColumn = null
                        },
                        onColumnPositioned = { rect -> columnBounds[status] = rect },
                        onToggleStar = onToggleStar,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                }
            }
        } else {
            // Phone: horizontally scrollable, each column ~60% of width
            val columnWidth = maxOf(dimens.kanbanColumnMinWidth, maxWidth * 0.6f)
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .horizontalScroll(phoneScrollState)
                    .padding(
                        start = dimens.paddingMedium,
                        end = dimens.paddingMedium,
                        bottom = navBarHeight + dimens.fabAreaBottom,
                    ),
                horizontalArrangement = Arrangement.spacedBy(dimens.spacerLarge),
            ) {
                TaskStatus.entries.forEach { status ->
                    KanbanColumn(
                        status = status,
                        tasks = tasksByStatus[status].orEmpty(),
                        color = statusColor(status),
                        isDropTarget = highlightedColumn == status,
                        draggedTaskId = draggedTask?.id,
                        onTaskClick = onTaskClick,
                        onDragStart = { task, offset, width, localOffset ->
                            draggedTask = task
                            cardStartPosition = offset
                            draggedCardWidth = width
                            dragOffset = Offset.Zero
                            initialTouchLocalOffset = localOffset
                            isDragging = true
                            fingerRootX = offset.x + localOffset.x
                        },
                        onDrag = { delta ->
                            dragOffset += delta
                            fingerRootX = cardStartPosition.x + initialTouchLocalOffset.x + dragOffset.x
                            val currentPos = cardStartPosition + dragOffset
                            highlightedColumn = columnBounds.entries
                                .firstOrNull { (_, rect) -> rect.contains(currentPos) }
                                ?.key
                        },
                        onDragEnd = {
                            isDragging = false
                            val target = highlightedColumn
                            val task = draggedTask
                            if (task != null && target != null && target != task.status) {
                                onStatusChange(task, target)
                            }
                            draggedTask = null
                            dragOffset = Offset.Zero
                            highlightedColumn = null
                        },
                        onDragCancel = {
                            isDragging = false
                            draggedTask = null
                            dragOffset = Offset.Zero
                            highlightedColumn = null
                        },
                        onColumnPositioned = { rect -> columnBounds[status] = rect },
                        onToggleStar = onToggleStar,
                        modifier = Modifier.width(columnWidth).fillMaxHeight(),
                    )
                }
            }

            // Auto-scroll when finger is near viewport edges during drag
            val viewportWidthPx = with(density) { maxWidth.toPx() }
            val leftEdgeThreshold = viewportWidthPx * 0.20f
            val rightEdgeThreshold = viewportWidthPx * 0.80f
            val autoScrollAmountPx = with(density) { dimens.kanbanAutoScrollAmount.toPx() }

            LaunchedEffect(isDragging) {
                if (!isDragging) return@LaunchedEffect

                while (isDragging) {
                    delay(500L)

                    val fingerScreenX = fingerRootX - containerPosition.x
                    val scrollDelta = when {
                        fingerScreenX > rightEdgeThreshold -> autoScrollAmountPx
                        fingerScreenX < leftEdgeThreshold -> -autoScrollAmountPx
                        else -> 0f
                    }

                    if (scrollDelta != 0f) {
                        phoneScrollState.scrollBy(scrollDelta)
                        // Wait one frame for column bounds to update after scroll
                        delay(16L)
                        val currentPos = cardStartPosition + dragOffset
                        highlightedColumn = columnBounds.entries
                            .firstOrNull { (_, rect) -> rect.contains(currentPos) }
                            ?.key
                    }
                }
            }
        }

        // Drag overlay
        val currentDraggedTask = draggedTask
        if (currentDraggedTask != null) {
            val overlayX = (cardStartPosition.x + dragOffset.x - containerPosition.x).roundToInt()
            val overlayY = (cardStartPosition.y + dragOffset.y - containerPosition.y).roundToInt()
            val overlayWidthDp = with(density) { draggedCardWidth.toDp() }
            Box(
                modifier = Modifier
                    .offset { IntOffset(overlayX, overlayY) }
                    .width(overlayWidthDp)
                    .shadow(dimens.paddingMedium, RoundedCornerShape(dimens.cornerLarge)),
            ) {
                KanbanTaskCard(
                    task = currentDraggedTask,
                    onClick = {},
                    onToggleStar = {},
                )
            }
        }
    }
}

@Composable
private fun KanbanColumn(
    status: TaskStatus,
    tasks: List<Task>,
    color: Color,
    isDropTarget: Boolean,
    draggedTaskId: String?,
    onTaskClick: (Task) -> Unit,
    onDragStart: (Task, Offset, Int, Offset) -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
    onColumnPositioned: (Rect) -> Unit,
    onToggleStar: (Task) -> Unit,
    modifier: Modifier = Modifier,
) {
    val dimens = OpenTasksTheme.dimens

    Card(
        modifier = modifier
            .onGloballyPositioned { coords ->
                onColumnPositioned(coords.boundsInRoot())
            },
        shape = RoundedCornerShape(dimens.cornerXLarge),
        colors = CardDefaults.cardColors(
            containerColor = if (isDropTarget) {
                color.copy(alpha = 0.12f)
            } else {
                MaterialTheme.colorScheme.surface
            },
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isDropTarget) dimens.paddingSmall else 1.dp,
        ),
    ) {
        Column {
            // Header with color strip
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = color,
                        shape = RoundedCornerShape(
                            topStart = dimens.cornerXLarge,
                            topEnd = dimens.cornerXLarge,
                        ),
                    )
                    .padding(
                        horizontal = dimens.paddingLarge,
                        vertical = dimens.paddingMedium,
                    ),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = statusLabel(status),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.weight(1f),
                    )
                    Box(
                        modifier = Modifier
                            .size(dimens.badgeSize)
                            .background(Color.White.copy(alpha = 0.3f), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = tasks.size.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                        )
                    }
                }
            }

            // Task list
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(
                    start = dimens.paddingMedium,
                    end = dimens.paddingMedium,
                    top = dimens.paddingMedium,
                    bottom = dimens.paddingMedium,
                ),
                verticalArrangement = Arrangement.spacedBy(dimens.spacerMedium),
            ) {
                items(tasks, key = { it.id }) { task ->
                    val isDragged = task.id == draggedTaskId
                    var cardPosition by remember { mutableStateOf(Offset.Zero) }
                    var cardWidth by remember { mutableStateOf(0) }

                    Box(
                        modifier = Modifier
                            .alpha(if (isDragged) 0.3f else 1f)
                            .onGloballyPositioned { coords ->
                                cardPosition = coords.positionInRoot()
                                cardWidth = coords.size.width
                            }
                            .pointerInput(task.id) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = { localOffset -> onDragStart(task, cardPosition, cardWidth, localOffset) },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        onDrag(dragAmount)
                                    },
                                    onDragEnd = onDragEnd,
                                    onDragCancel = onDragCancel,
                                )
                            },
                    ) {
                        KanbanTaskCard(
                            task = task,
                            onClick = { onTaskClick(task) },
                            onToggleStar = { onToggleStar(task) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun KanbanTaskCard(
    task: Task,
    onClick: () -> Unit,
    onToggleStar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dimens = OpenTasksTheme.dimens

    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(dimens.cornerLarge),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.background,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            // Priority color strip on left edge
            Box(
                modifier = Modifier
                    .width(dimens.paddingSmall)
                    .fillMaxHeight()
                    .background(priorityColor(task.priority)),
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(
                        start = dimens.paddingMedium,
                        end = dimens.paddingSmall,
                        top = dimens.paddingMedium,
                        bottom = dimens.paddingMedium,
                    ),
            ) {
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )

                if (task.deadline != null) {
                    Spacer(Modifier.height(dimens.spacerSmall))
                    Text(
                        text = formatDateShort(task.deadline),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (task.isStarred) {
                IconButton(
                    onClick = onToggleStar,
                    modifier = Modifier
                        .size(dimens.touchTargetMedium)
                        .align(Alignment.CenterVertically),
                ) {
                    Icon(
                        painter = painterResource(Res.drawable.ic_star),
                        contentDescription = null,
                        tint = StarGold,
                        modifier = Modifier.size(dimens.iconSmall),
                    )
                }
            }
        }
    }
}

@Composable
private fun statusLabel(status: TaskStatus): String = when (status) {
    TaskStatus.TODO -> stringResource(Res.string.status_todo)
    TaskStatus.IN_PROGRESS -> stringResource(Res.string.status_in_progress)
    TaskStatus.DONE -> stringResource(Res.string.status_done)
}

private fun statusColor(status: TaskStatus): Color = when (status) {
    TaskStatus.TODO -> KanbanTodoColor
    TaskStatus.IN_PROGRESS -> KanbanInProgressColor
    TaskStatus.DONE -> KanbanDoneColor
}

