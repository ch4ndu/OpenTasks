package com.udnahc.opentasks

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import com.udnahc.opentasks.data.extensions.currentDay
import com.udnahc.opentasks.data.model.NotifyBeforeUnit
import com.udnahc.opentasks.data.model.TaskPriority
import com.udnahc.opentasks.ui.screens.CreateNoteBottomSheet
import com.udnahc.opentasks.ui.screens.CreateTaskBottomSheet
import com.udnahc.opentasks.ui.screens.EisenhowerMatrixScreen
import com.udnahc.opentasks.ui.screens.NotesScreen
import com.udnahc.opentasks.ui.screens.QuadrantDetailScreen
import com.udnahc.opentasks.ui.screens.calendar.CalendarScreen
import com.udnahc.opentasks.ui.screens.TaskListScreen
import com.udnahc.opentasks.ui.theme.OpenTasksTheme
import com.udnahc.opentasks.ui.theme.PrimaryBlue
import com.udnahc.opentasks.ui.util.PlatformBackHandler
import com.udnahc.opentasks.viewmodel.TaskViewModel
import opentasks.composeapp.generated.resources.Res
import opentasks.composeapp.generated.resources.add_task
import opentasks.composeapp.generated.resources.ic_add
import opentasks.composeapp.generated.resources.ic_calendar
import opentasks.composeapp.generated.resources.ic_check_box
import opentasks.composeapp.generated.resources.ic_grid_view
import opentasks.composeapp.generated.resources.ic_note
import opentasks.composeapp.generated.resources.not_urgent_important
import opentasks.composeapp.generated.resources.not_urgent_unimportant
import opentasks.composeapp.generated.resources.urgent_important
import opentasks.composeapp.generated.resources.urgent_unimportant
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
@Preview
fun App() {
    OpenTasksTheme {
        val viewModel: TaskViewModel = koinViewModel()
        MainScreen(viewModel = viewModel)
    }
}

private data class BottomNavItem(
    val iconRes: DrawableResource,
    val isCalendar: Boolean = false,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainScreen(
    viewModel: TaskViewModel,
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var selectedTaskId by remember { mutableStateOf<Long?>(null) }
    var showCreateSheet by remember { mutableStateOf(false) }
    var selectedQuadrant by remember { mutableStateOf<TaskPriority?>(null) }
    var selectedListId by rememberSaveable { mutableStateOf(1L) }
    var calendarSelectedYear by remember { mutableIntStateOf(0) }
    var calendarSelectedMonth by remember { mutableIntStateOf(0) }
    var calendarSelectedDay by remember { mutableIntStateOf(0) }
    var showCreateNote by remember { mutableStateOf(false) }
    var editNoteId by remember { mutableStateOf<Long?>(null) }

    val tabs = remember {
        listOf(
            BottomNavItem(iconRes = Res.drawable.ic_grid_view),
            BottomNavItem(iconRes = Res.drawable.ic_check_box),
            BottomNavItem(iconRes = Res.drawable.ic_calendar, isCalendar = true),
            BottomNavItem(iconRes = Res.drawable.ic_note),
        )
    }

    PlatformBackHandler(enabled = selectedQuadrant != null) {
        selectedQuadrant = null
    }

    selectedQuadrant?.let { priority ->
        val quadrantTitle = quadrantTitle(priority)
        QuadrantDetailScreen(
            title = quadrantTitle,
            priority = priority,
            viewModel = viewModel,
            onBack = { selectedQuadrant = null },
            onTaskClick = { task -> selectedTaskId = task.id },
        )
    } ?: Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        // Content layer — fills entire screen, scrolls behind bars
        Box(
            modifier = Modifier
                .fillMaxSize(),
        ) {
            when (selectedTab) {
                0 -> EisenhowerMatrixScreen(
                    viewModel = viewModel,
                    onTaskClick = { task -> selectedTaskId = task.id },
                    onQuadrantClick = { priority -> selectedQuadrant = priority },
                )

                1 -> TaskListScreen(
                    viewModel = viewModel,
                    selectedListId = selectedListId,
                    onSelectedListChanged = { selectedListId = it },
                    onTaskClick = { task -> selectedTaskId = task.id },
                )

                2 -> CalendarScreen(
                    viewModel = viewModel,
                    onTaskClick = { task -> selectedTaskId = task.id },
                    onSelectedDateChanged = { year, month, day ->
                        calendarSelectedYear = year
                        calendarSelectedMonth = month
                        calendarSelectedDay = day
                    },
                )
                3 -> NotesScreen(
                    viewModel = viewModel,
                    onNoteClick = { note -> editNoteId = note.id },
                )
            }
        }

        Box(modifier = Modifier.align(Alignment.BottomCenter)) {
            BottomNavBar(
                tabs = tabs,
                selectedTab = selectedTab,
                onTabSelected = { selectedTab = it },
            )
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd),
        ) {
            CreateTaskFab(
                onClick = {
                    if (selectedTab == 3) showCreateNote = true else showCreateSheet = true
                },
            )
        }
    }

    // Task edit bottom sheet
    selectedTaskId?.let { taskId ->
        EditTaskSheet(
            viewModel = viewModel,
            taskId = taskId,
            onDismiss = { selectedTaskId = null },
        )
    }

    // Create task bottom sheet
    if (showCreateSheet) {
        CreateTaskSheetWrapper(
            viewModel = viewModel,
            initialListId = if (selectedTab == 1) selectedListId else 1L,
            initialDay = if (selectedTab == 2) calendarSelectedDay else 0,
            initialMonth = if (selectedTab == 2) calendarSelectedMonth else 0,
            initialYear = if (selectedTab == 2) calendarSelectedYear else 0,
            onDismiss = { showCreateSheet = false },
        )
    }

    // Create note bottom sheet
    if (showCreateNote) {
        val createNoteSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        CreateNoteBottomSheet(
            sheetState = createNoteSheetState,
            onDismiss = { showCreateNote = false },
            onSave = { title, content -> viewModel.addNote(title, content) },
        )
    }

    // Edit note bottom sheet
    val editNoteIdVal = editNoteId
    if (editNoteIdVal != null) {
        val notes by viewModel.notes.collectAsState()
        val editNote = remember(editNoteIdVal, notes) { notes.find { it.id == editNoteIdVal } }
        if (editNote != null) {
            val editNoteSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            CreateNoteBottomSheet(
                sheetState = editNoteSheetState,
                editNote = editNote,
                onDismiss = { editNoteId = null },
                onSave = { title, content ->
                    viewModel.updateNote(editNote.copy(title = title, content = content))
                },
                onDelete = {
                    viewModel.deleteNote(editNote)
                    editNoteId = null
                },
            )
        } else {
            editNoteId = null
        }
    }
}

@Composable
private fun BottomNavBar(
    tabs: List<BottomNavItem>,
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
) {
    NavigationBar(
        modifier = Modifier,
        containerColor = MaterialTheme.colorScheme.background.copy(alpha = 0.8f),
    ) {
        tabs.forEachIndexed { index, item ->
            val selected = index == selectedTab
            NavigationBarItem(
                selected = selected,
                onClick = { onTabSelected(index) },
                icon = {
                    if (item.isCalendar) {
                        CalendarTabIcon(item.iconRes)
                    } else {
                        Icon(
                            painter = painterResource(item.iconRes),
                            contentDescription = null,
                            modifier = Modifier.size(OpenTasksTheme.dimens.iconNavBar),
                        )
                    }
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = PrimaryBlue,
                    selectedTextColor = PrimaryBlue,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    indicatorColor = Color.Transparent,
                ),
            )
        }
    }
}

@Composable
private fun CalendarTabIcon(iconRes: DrawableResource) {
    Box(
        modifier = Modifier.size(OpenTasksTheme.dimens.iconNavBar),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
        )
        Text(
            text = currentDay().toString(),
            style = OpenTasksTheme.typography.calendarDayNumber,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.background,
            modifier = Modifier.align(Alignment.Center)
        )
    }
}

@Composable
private fun CreateTaskFab(
    onClick: () -> Unit,
) {
    FloatingActionButton(
        onClick = onClick,
        shape = CircleShape,
        containerColor = PrimaryBlue,
        contentColor = Color.White,
        modifier = Modifier
            .navigationBarsPadding()
            .padding(end = OpenTasksTheme.dimens.paddingXLarge, bottom = OpenTasksTheme.dimens.fabBottomPadding),
    ) {
        Icon(
            painter = painterResource(Res.drawable.ic_add),
            contentDescription = stringResource(Res.string.add_task),
            tint = Color.White,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditTaskSheet(
    viewModel: TaskViewModel,
    taskId: Long,
    onDismiss: () -> Unit,
) {
    val tasks by viewModel.tasks.collectAsState()
    val taskLists by viewModel.taskLists.collectAsState()
    val editTask = tasks.find { it.id == taskId }
    if (editTask == null) {
        onDismiss()
    } else {
        val editSheetState = rememberModalBottomSheetState(
            skipPartiallyExpanded = true,
        )
        CreateTaskBottomSheet(
            sheetState = editSheetState,
            editTask = editTask,
            taskLists = taskLists,
            onAddList = { name -> viewModel.addList(name) },
            onDismiss = onDismiss,
            onSave = { title, content, priority, deadline, reminderDays, recurrence, listId ->
                val notifyUnit =
                    if (reminderDays > 0) NotifyBeforeUnit.DAYS else NotifyBeforeUnit.NONE
                viewModel.updateTask(
                    editTask.copy(
                        title = title,
                        content = content,
                        priority = priority,
                        deadline = deadline,
                        notifyBeforeValue = reminderDays,
                        notifyBeforeUnit = notifyUnit,
                        recurrenceType = recurrence,
                        listId = listId,
                    )
                )
                onDismiss()
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateTaskSheetWrapper(
    viewModel: TaskViewModel,
    initialListId: Long,
    initialDay: Int,
    initialMonth: Int,
    initialYear: Int,
    onDismiss: () -> Unit,
) {
    val taskLists by viewModel.taskLists.collectAsState()
    val createSheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
    )
    CreateTaskBottomSheet(
        sheetState = createSheetState,
        initialListId = initialListId,
        initialDay = initialDay,
        initialMonth = initialMonth,
        initialYear = initialYear,
        taskLists = taskLists,
        onAddList = { name -> viewModel.addList(name) },
        onDismiss = onDismiss,
        onSave = { title, content, priority, deadline, reminderDays, recurrence, listId ->
            val notifyUnit =
                if (reminderDays > 0) NotifyBeforeUnit.DAYS else NotifyBeforeUnit.NONE
            viewModel.addTask(
                title = title,
                content = content,
                priority = priority,
                deadline = deadline,
                notifyBeforeValue = reminderDays,
                notifyBeforeUnit = notifyUnit,
                recurrenceType = recurrence,
                listId = listId,
            )
            onDismiss()
        },
    )
}

@Composable
private fun quadrantTitle(priority: TaskPriority): String = when (priority) {
    TaskPriority.HIGH -> stringResource(Res.string.urgent_important)
    TaskPriority.MEDIUM -> stringResource(Res.string.not_urgent_important)
    TaskPriority.LOW -> stringResource(Res.string.urgent_unimportant)
    TaskPriority.NONE -> stringResource(Res.string.not_urgent_unimportant)
}

@Composable
private fun PlaceholderContent(title: String) {
    Box(
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}
