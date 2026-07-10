package com.udnahc.opentasks

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.udnahc.opentasks.data.extensions.MILLIS_PER_MINUTE
import com.udnahc.opentasks.data.extensions.currentDay
import com.udnahc.opentasks.data.extensions.localNow
import com.udnahc.opentasks.data.model.COUNTDOWN_ID_PREFIX
import com.udnahc.opentasks.data.model.CountdownType
import com.udnahc.opentasks.data.model.TaskFormData
import com.udnahc.opentasks.data.model.TaskPriority
import com.udnahc.opentasks.data.model.isCountdownItem
import com.udnahc.opentasks.data.notification.ExactReminderPermissionStatus
import com.udnahc.opentasks.domain.action.reminder.RebuildReminderQueueAction
import com.udnahc.opentasks.domain.action.settings.InitializeSyncAction
import com.udnahc.opentasks.domain.action.settings.TriggerSyncAction
import com.udnahc.opentasks.domain.action.task.ImportCalendarEventsAction
import com.udnahc.opentasks.domain.time.LocalDaySignal
import com.udnahc.opentasks.domain.usecase.settings.CheckNotificationPermissionUseCase
import com.udnahc.opentasks.domain.usecase.task.ParseIcsUseCase
import com.udnahc.opentasks.navigation.AppNavController
import com.udnahc.opentasks.navigation.Screen
import com.udnahc.opentasks.ui.screens.CreateNoteBottomSheet
import com.udnahc.opentasks.ui.screens.CreateTaskScreen
import com.udnahc.opentasks.ui.screens.EisenhowerMatrixScreen
import com.udnahc.opentasks.ui.screens.ImportCalendarDialog
import com.udnahc.opentasks.ui.screens.ImportCsvDialog
import com.udnahc.opentasks.ui.screens.ImportIcsDialog
import com.udnahc.opentasks.ui.screens.NotesScreen
import com.udnahc.opentasks.ui.screens.QuadrantDetailScreen
import com.udnahc.opentasks.ui.screens.SettingsScreen
import com.udnahc.opentasks.ui.screens.TaskListScreen
import com.udnahc.opentasks.ui.screens.TaskNotificationBottomSheet
import com.udnahc.opentasks.ui.screens.calendar.CalendarScreen
import com.udnahc.opentasks.ui.screens.countdown.CountdownDetailScreen
import com.udnahc.opentasks.ui.screens.countdown.CountdownScreen
import com.udnahc.opentasks.ui.screens.countdown.CreateCountdownScreen
import com.udnahc.opentasks.ui.theme.OpenTasksTheme
import com.udnahc.opentasks.ui.theme.PrimaryBlue
import com.udnahc.opentasks.ui.util.pickCsvFileContent
import com.udnahc.opentasks.ui.util.pickIcsFileContent
import com.udnahc.opentasks.ui.util.rememberNotificationPermissionLauncher
import com.udnahc.opentasks.viewmodel.AppViewModel
import com.udnahc.opentasks.viewmodel.CalendarViewModel
import com.udnahc.opentasks.viewmodel.CountdownFormViewModel
import com.udnahc.opentasks.viewmodel.CountdownViewModel
import com.udnahc.opentasks.viewmodel.ImportCalendarViewModel
import com.udnahc.opentasks.viewmodel.ImportCsvViewModel
import com.udnahc.opentasks.viewmodel.ImportIcsViewModel
import com.udnahc.opentasks.viewmodel.MatrixViewModel
import com.udnahc.opentasks.viewmodel.NoteViewModel
import com.udnahc.opentasks.viewmodel.SettingsViewModel
import com.udnahc.opentasks.viewmodel.TaskFormSaveEvent
import com.udnahc.opentasks.viewmodel.TaskFormViewModel
import com.udnahc.opentasks.viewmodel.TaskListViewModel
import com.udnahc.opentasks.viewmodel.TaskNotificationViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import opentasks.composeapp.generated.resources.Res
import opentasks.composeapp.generated.resources.add_task
import opentasks.composeapp.generated.resources.exact_reminder_permission_message
import opentasks.composeapp.generated.resources.ic_add
import opentasks.composeapp.generated.resources.ic_calendar
import opentasks.composeapp.generated.resources.ic_check_box
import opentasks.composeapp.generated.resources.ic_grid_view
import opentasks.composeapp.generated.resources.ic_note
import opentasks.composeapp.generated.resources.ic_schedule
import opentasks.composeapp.generated.resources.import_failed_generic
import opentasks.composeapp.generated.resources.import_success
import opentasks.composeapp.generated.resources.image_save_partial_failed
import opentasks.composeapp.generated.resources.not_urgent_important
import opentasks.composeapp.generated.resources.not_urgent_unimportant
import opentasks.composeapp.generated.resources.open_settings
import opentasks.composeapp.generated.resources.task_save_failed
import opentasks.composeapp.generated.resources.urgent_important
import opentasks.composeapp.generated.resources.urgent_unimportant
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.lighthousegames.logging.logging

private val IosTransitionEasing = CubicBezierEasing(0.2833f, 0.99f, 0.31833f, 0.99f)
private val log = logging("App")

private fun isTabScreen(key: Any): Boolean =
    key is Screen.Matrix || key is Screen.TaskList || key is Screen.Calendar || key is Screen.Notes || key is Screen.Countdown

@Composable
fun App(
    deepLinkNotificationEvent: NotificationDeepLinkEvent? = null,
    deepLinkCountdownId: String = "",
    widgetAction: String = "",
) {
    val settingsViewModel: SettingsViewModel = koinViewModel()
    val themeMode by settingsViewModel.themePreference.collectAsState()
    val textSizePreference by settingsViewModel.textSizePreference.collectAsState()
    OpenTasksTheme(
        themeMode = themeMode,
        textSizePreference = textSizePreference,
    ) {
        val backStack = remember { NavBackStack<NavKey>(Screen.Matrix) }
        val navController = remember { AppNavController(backStack) }
        var taskNotificationEvent by remember { mutableStateOf<NotificationDeepLinkEvent?>(null) }
        val initializeSyncAction = koinInject<InitializeSyncAction>()
        val rebuildReminderQueueAction = koinInject<RebuildReminderQueueAction>()
        val triggerSyncAction = koinInject<TriggerSyncAction>()
        val localDaySignal = koinInject<LocalDaySignal>()
        val isSyncInitialized = remember { mutableStateOf(false) }
        LaunchedEffect(Unit) {
            withContext(Dispatchers.IO) {
                try {
                    initializeSyncAction()
                } catch (e: Exception) {
                    log.e(e) { "Initial sync failed" }
                }
                try {
                    rebuildReminderQueueAction()
                } catch (e: Exception) {
                    log.e(e) { "Initial reminder queue rebuild failed" }
                }
            }
            isSyncInitialized.value = true
        }
        val syncScope = rememberCoroutineScope()
        LifecycleResumeEffect(isSyncInitialized.value) {
            localDaySignal.refresh()
            if (isSyncInitialized.value) {
                syncScope.launch(Dispatchers.IO) {
                    try {
                        triggerSyncAction.syncNow()
                    } catch (e: Exception) {
                        log.e(e) { "Resume sync failed" }
                    }
                    try {
                        rebuildReminderQueueAction()
                    } catch (e: Exception) {
                        log.e(e) { "Resume reminder queue rebuild failed" }
                    }
                }
            }
            onPauseOrDispose { }
        }
        fun handleNotificationEvent(event: NotificationDeepLinkEvent) {
            if (event.eventId.startsWith(COUNTDOWN_ID_PREFIX)) {
                navController.navigate(Screen.CountdownDetail(event.eventId.removePrefix(COUNTDOWN_ID_PREFIX)))
            } else {
                navController.navigateToTab(Screen.Matrix)
                taskNotificationEvent = event
            }
        }
        if (deepLinkNotificationEvent != null) {
            LaunchedEffect(deepLinkNotificationEvent) {
                handleNotificationEvent(deepLinkNotificationEvent)
            }
        }
        if (deepLinkCountdownId.isNotEmpty()) {
            LaunchedEffect(deepLinkCountdownId) {
                navController.navigate(Screen.CountdownDetail(deepLinkCountdownId))
            }
        }
        LaunchedEffect(navController) {
            notificationDeepLinkEvent.collect { event ->
                if (event != null) {
                    handleNotificationEvent(event)
                    clearNotificationDeepLinkEvent(event)
                }
            }
        }
        if (widgetAction.isNotEmpty()) {
            LaunchedEffect(widgetAction) {
                when (widgetAction) {
                    "create_task" -> navController.navigate(Screen.CreateTask())
                    "view_list" -> navController.navigateToTab(Screen.TaskList)
                }
            }
        }
        MainScreen(
            navController = navController,
            backStack = backStack,
            taskNotificationEvent = taskNotificationEvent,
            onTaskNotificationDismiss = { taskNotificationEvent = null },
        )
    }
}

private data class BottomNavItem(
    val iconRes: DrawableResource,
    val isCalendar: Boolean = false,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainScreen(
    navController: AppNavController,
    backStack: NavBackStack<NavKey>,
    taskNotificationEvent: NotificationDeepLinkEvent?,
    onTaskNotificationDismiss: () -> Unit,
) {
    val noteViewModel: NoteViewModel = koinViewModel()
    val taskNotificationViewModel: TaskNotificationViewModel = koinViewModel()
    val appViewModel: AppViewModel = koinViewModel()
    val taskNotificationUiState by taskNotificationViewModel.uiState.collectAsState()
    val isRefreshing by appViewModel.isRefreshing.collectAsState()
    val onPullToRefresh = remember(appViewModel) { { appViewModel.triggerSync() } }
    var selectedListId by rememberSaveable { mutableStateOf("00000000-0000-0000-0000-000000000001") }
    var calendarSelectedYear by remember { mutableIntStateOf(0) }
    var calendarSelectedMonth by remember { mutableIntStateOf(0) }
    var calendarSelectedDay by remember { mutableIntStateOf(0) }
    var showCreateNote by remember { mutableStateOf(false) }
    var editNoteId by remember { mutableStateOf<String?>(null) }
    var showImportCalendar by remember { mutableStateOf(false) }
    var showImportIcs by remember { mutableStateOf(false) }
    var showImportCsv by remember { mutableStateOf(false) }
    var taskFormBackHandler by remember { mutableStateOf<(() -> Unit)?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val checkNotificationPermissionUseCase = koinInject<CheckNotificationPermissionUseCase>()
    val parseIcsUseCase = koinInject<ParseIcsUseCase>()
    val importCalendarEventsAction = koinInject<ImportCalendarEventsAction>()
    val snackbarScope = rememberCoroutineScope()
    var pendingGlobalPostSaveReminderCheck by remember { mutableStateOf<TaskFormData?>(null) }
    val requestGlobalNotificationPermission = rememberNotificationPermissionLauncher {
        val formData = pendingGlobalPostSaveReminderCheck
        pendingGlobalPostSaveReminderCheck = null
        if (formData != null) {
            maybeShowExactReminderSnackbar(
                formData = formData,
                checkNotificationPermissionUseCase = checkNotificationPermissionUseCase,
                snackbarHostState = snackbarHostState,
                scope = snackbarScope,
            )
        }
    }

    fun requestPostSaveReminderCheck(formData: TaskFormData) {
        if (!formData.hasFutureReminder()) return
        pendingGlobalPostSaveReminderCheck = formData
        requestGlobalNotificationPermission()
    }

    val tabs = remember {
        listOf(
            BottomNavItem(iconRes = Res.drawable.ic_grid_view),
            BottomNavItem(iconRes = Res.drawable.ic_check_box),
            BottomNavItem(iconRes = Res.drawable.ic_calendar, isCalendar = true),
            BottomNavItem(iconRes = Res.drawable.ic_note),
            BottomNavItem(iconRes = Res.drawable.ic_schedule),
        )
    }

    // Derive selected tab and visibility from the back stack
    val currentScreen = backStack.last()
    val selectedTab = remember(currentScreen) {
        when (currentScreen) {
            is Screen.Matrix, is Screen.QuadrantDetail -> 0
            is Screen.TaskList -> 1
            is Screen.Calendar -> 2
            is Screen.Notes -> 3
            is Screen.Countdown -> 4
            else -> 0
        }
    }
    val showBottomNav = currentScreen !is Screen.QuadrantDetail
            && currentScreen !is Screen.CreateTask
            && currentScreen !is Screen.EditTask
            && currentScreen !is Screen.Settings
            && currentScreen !is Screen.CreateCountdown
            && currentScreen !is Screen.CountdownDetail
            && currentScreen !is Screen.EditCountdown

    val onSettingsClick = remember { { navController.navigate(Screen.Settings) } }

    LaunchedEffect(navController) {
        sharedTaskPayload.collect { payload ->
            if (payload == null) return@collect
            when {
                payload.hasIcsContent -> {
                    try {
                        val count = withContext(Dispatchers.IO) {
                            val events = parseIcsUseCase(payload.icsContent)
                            importCalendarEventsAction(events)
                        }
                        snackbarHostState.showSnackbar(getString(Res.string.import_success, count))
                    } catch (e: Exception) {
                        log.e(e) { "Shared ICS import failed" }
                        snackbarHostState.showSnackbar(getString(Res.string.import_failed_generic))
                    } finally {
                        clearSharedTaskPayload(payload.id)
                    }
                }

                payload.hasTaskContent -> {
                    navController.navigate(
                        Screen.CreateTask(
                            description = payload.description,
                            url = payload.url,
                        )
                    )
                    clearSharedTaskPayload(payload.id)
                }

                else -> clearSharedTaskPayload(payload.id)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        fun handleBack() {
            taskFormBackHandler?.invoke() ?: navController.popBackStack()
        }

        NavDisplay(
            backStack = backStack,
            onBack = { handleBack() },
            modifier = Modifier.fillMaxSize(),
            entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator()),
//            transitionSpec = {
//                if (isTabScreen(initialState.key) && isTabScreen(targetState.key)) {
//                    ContentTransform(
//                        fadeIn(animationSpec = snap()),
//                        fadeOut(animationSpec = snap()),
//                    )
//                } else {
//                    ContentTransform(
//                        slideIntoContainer(
//                            towards = AnimatedContentTransitionScope.SlideDirection.Left,
//                            animationSpec = tween(500, easing = IosTransitionEasing),
//                        ),
//                        slideOutOfContainer(
//                            towards = AnimatedContentTransitionScope.SlideDirection.Left,
//                            targetOffset = { it / 4 },
//                            animationSpec = tween(500, easing = IosTransitionEasing),
//                        ),
//                    )
//                }
//            },
//            popTransitionSpec = {
//                if (isTabScreen(initialState.key) && isTabScreen(targetState.key)) {
//                    ContentTransform(
//                        fadeIn(animationSpec = snap()),
//                        fadeOut(animationSpec = snap()),
//                    )
//                } else {
//                    ContentTransform(
//                        slideIntoContainer(
//                            towards = AnimatedContentTransitionScope.SlideDirection.Right,
//                            initialOffset = { it / 4 },
//                            animationSpec = tween(500, easing = IosTransitionEasing),
//                        ),
//                        slideOutOfContainer(
//                            towards = AnimatedContentTransitionScope.SlideDirection.Right,
//                            animationSpec = tween(500, easing = IosTransitionEasing),
//                        ),
//                    )
//                }
//            },
            entryProvider = entryProvider {
                entry<Screen.Matrix> {
                    val matrixViewModel: MatrixViewModel = koinViewModel()
                    EisenhowerMatrixScreen(
                        viewModel = matrixViewModel,
                        onTaskClick = { task ->
                            navController.navigate(Screen.EditTask(task.id))
                        },
                        onQuadrantClick = { priority ->
                            navController.navigate(Screen.QuadrantDetail(priority.ordinal))
                        },
                        onSettingsClick = onSettingsClick,
                        isRefreshing = isRefreshing,
                        onRefresh = onPullToRefresh,
                    )
                }

                entry<Screen.TaskList> {
                    val taskListViewModel: TaskListViewModel = koinViewModel()
                    TaskListScreen(
                        viewModel = taskListViewModel,
                        selectedCategoryId = selectedListId,
                        onSelectedCategoryChanged = { selectedListId = it },
                        onTaskClick = { task ->
                            navController.navigate(Screen.EditTask(task.id))
                        },
                        onSettingsClick = onSettingsClick,
                        isRefreshing = isRefreshing,
                        onRefresh = onPullToRefresh,
                    )
                }

                entry<Screen.Calendar> {
                    val calendarViewModel: CalendarViewModel = koinViewModel()
                    CalendarScreen(
                        viewModel = calendarViewModel,
                        onTaskClick = { task ->
                            if (task.isCountdownItem) {
                                val countdownId = task.id.removePrefix(COUNTDOWN_ID_PREFIX)
                                navController.navigate(Screen.CountdownDetail(countdownId))
                            } else {
                                navController.navigate(Screen.EditTask(task.id))
                            }
                        },
                        onSelectedDateChanged = { year, month, day ->
                            calendarSelectedYear = year
                            calendarSelectedMonth = month
                            calendarSelectedDay = day
                        },
                        onSettingsClick = onSettingsClick,
                        isRefreshing = isRefreshing,
                        onRefresh = onPullToRefresh,
                    )
                }

                entry<Screen.Notes> {
                    NotesScreen(
                        viewModel = noteViewModel,
                        onNoteClick = { note -> editNoteId = note.id },
                        onSettingsClick = onSettingsClick,
                        isRefreshing = isRefreshing,
                        onRefresh = onPullToRefresh,
                    )
                }

                entry<Screen.Settings> {
                    SettingsScreen(
                        onBack = { navController.popBackStack() },
                        onImportCalendar = { showImportCalendar = true },
                        onImportIcs = { showImportIcs = true },
                        onImportCsv = { showImportCsv = true },
                        onLogout = { navController.navigateToTab(Screen.Matrix) },
                    )
                }

                entry<Screen.QuadrantDetail> { screen ->
                    val matrixViewModel: MatrixViewModel = koinViewModel()
                    val priority = TaskPriority.entries[screen.priorityOrdinal]
                    val quadrantTitle = quadrantTitle(priority)
                    QuadrantDetailScreen(
                        title = quadrantTitle,
                        priority = priority,
                        viewModel = matrixViewModel,
                        onBack = { navController.popBackStack() },
                        onTaskClick = { task ->
                            navController.navigate(Screen.EditTask(task.id))
                        },
                        onCreateTask = { taskPriority ->
                            navController.navigate(
                                Screen.CreateTask(priorityOrdinal = taskPriority.ordinal)
                            )
                        },
                    )
                }

                entry<Screen.CreateTask> { screen ->
                    val taskFormViewModel: TaskFormViewModel = koinViewModel()
                    val categories by taskFormViewModel.categories.collectAsState()
                    val filteredCategories by taskFormViewModel.filteredCategories.collectAsState()
                    val categorySearchQuery by taskFormViewModel.categorySearchQuery.collectAsState()
                    val pendingImages by taskFormViewModel.pendingImages.collectAsState()
                    val isSaving by taskFormViewModel.isSaving.collectAsState()
                    var pendingPostSaveReminderCheck by remember {
                        mutableStateOf<TaskFormData?>(
                            null
                        )
                    }
                    val requestNotificationPermission = rememberNotificationPermissionLauncher {
                        val formData = pendingPostSaveReminderCheck
                        pendingPostSaveReminderCheck = null
                        if (formData != null) {
                            maybeShowExactReminderSnackbar(
                                formData = formData,
                                checkNotificationPermissionUseCase = checkNotificationPermissionUseCase,
                                snackbarHostState = snackbarHostState,
                                scope = snackbarScope,
                            )
                        }
                    }
                    DisposableEffect(Unit) {
                        onDispose { pendingPostSaveReminderCheck = null }
                    }
                    LaunchedEffect(taskFormViewModel) {
                        taskFormViewModel.saveEvents.collect { event ->
                            when (event) {
                                is TaskFormSaveEvent.Saved -> {
                                    navController.popBackStack()
                                    if (event.formData.hasFutureReminder()) {
                                        pendingPostSaveReminderCheck = event.formData
                                        requestNotificationPermission()
                                    }
                                }

                                is TaskFormSaveEvent.TaskCreatedWithImageError -> {
                                    navController.replaceTop(Screen.EditTask(event.taskId))
                                    snackbarScope.launch {
                                        snackbarHostState.showSnackbar(getString(Res.string.image_save_partial_failed))
                                        requestPostSaveReminderCheck(event.formData)
                                    }
                                }

                                is TaskFormSaveEvent.ImagesFailed -> Unit

                                is TaskFormSaveEvent.Error -> {
                                    snackbarScope.launch {
                                        snackbarHostState.showSnackbar(getString(Res.string.task_save_failed))
                                    }
                                }
                            }
                        }
                    }
                    CreateTaskScreen(
                        onBack = { navController.popBackStack() },
                        initialPriority = TaskPriority.entries[screen.priorityOrdinal],
                        initialCategoryId = screen.categoryId,
                        initialTitle = screen.title,
                        initialDescription = screen.description,
                        initialUrl = screen.url,
                        initialDay = screen.day,
                        initialMonth = screen.month,
                        initialYear = screen.year,
                        categories = categories,
                        filteredCategories = filteredCategories,
                        categorySearchQuery = categorySearchQuery,
                        onCategorySearchQueryChange = {
                            taskFormViewModel.setCategorySearchQuery(it)
                        },
                        onAddCategory = { name -> taskFormViewModel.addCategory(name) },
                        onSave = { formData -> taskFormViewModel.saveNewTask(formData) },
                        isSaving = isSaving,
                        pendingImages = pendingImages,
                        onAddPendingImage = { taskFormViewModel.addPendingImage(it) },
                        onRemovePendingImage = { taskFormViewModel.removePendingImage(it) },
                        onDiscardPendingImages = { taskFormViewModel.discardPendingImages() },
                        confirmDiscardPendingImagesOnBack = true,
                        onBackRequestChanged = { taskFormBackHandler = it },
                    )
                }

                entry<Screen.EditTask> { screen ->
                    val taskFormViewModel: TaskFormViewModel = koinViewModel()
                    var pendingPostSaveReminderCheck by remember {
                        mutableStateOf<TaskFormData?>(
                            null
                        )
                    }
                    val requestNotificationPermission = rememberNotificationPermissionLauncher {
                        val formData = pendingPostSaveReminderCheck
                        pendingPostSaveReminderCheck = null
                        if (formData != null) {
                            maybeShowExactReminderSnackbar(
                                formData = formData,
                                checkNotificationPermissionUseCase = checkNotificationPermissionUseCase,
                                snackbarHostState = snackbarHostState,
                                scope = snackbarScope,
                            )
                        }
                    }
                    DisposableEffect(Unit) {
                        onDispose { pendingPostSaveReminderCheck = null }
                    }
                    LaunchedEffect(taskFormViewModel) {
                        taskFormViewModel.saveEvents.collect { event ->
                            when (event) {
                                is TaskFormSaveEvent.Saved -> {
                                    navController.popBackStack()
                                    if (event.formData.hasFutureReminder()) {
                                        pendingPostSaveReminderCheck = event.formData
                                        requestNotificationPermission()
                                    }
                                }

                                is TaskFormSaveEvent.TaskCreatedWithImageError -> Unit

                                is TaskFormSaveEvent.ImagesFailed -> {
                                    snackbarScope.launch {
                                        snackbarHostState.showSnackbar(getString(Res.string.image_save_partial_failed))
                                        if (event.formData.hasFutureReminder()) {
                                            pendingPostSaveReminderCheck = event.formData
                                            requestNotificationPermission()
                                        }
                                    }
                                }

                                is TaskFormSaveEvent.Error -> {
                                    snackbarScope.launch {
                                        snackbarHostState.showSnackbar(getString(Res.string.task_save_failed))
                                    }
                                }
                            }
                        }
                    }
                    LaunchedEffect(screen.taskId) { taskFormViewModel.setTaskId(screen.taskId) }
                    val editTask by taskFormViewModel.editTask.collectAsState()
                    val editTaskImages by taskFormViewModel.editTaskImages.collectAsState()
                    val pendingImages by taskFormViewModel.pendingImages.collectAsState()
                    val categories by taskFormViewModel.categories.collectAsState()
                    val filteredCategories by taskFormViewModel.filteredCategories.collectAsState()
                    val categorySearchQuery by taskFormViewModel.categorySearchQuery.collectAsState()
                    val isSaving by taskFormViewModel.isSaving.collectAsState()
                    val currentEditTask = editTask
                    if (currentEditTask != null) {
                        CreateTaskScreen(
                            onBack = { navController.popBackStack() },
                            editTask = currentEditTask,
                            categories = categories,
                            filteredCategories = filteredCategories,
                            categorySearchQuery = categorySearchQuery,
                            onCategorySearchQueryChange = {
                                taskFormViewModel.setCategorySearchQuery(it)
                            },
                            onAddCategory = { name -> taskFormViewModel.addCategory(name) },
                            onSave = { formData ->
                                taskFormViewModel.saveExistingTask(currentEditTask, formData)
                            },
                            isSaving = isSaving,
                            existingImages = editTaskImages,
                            pendingImages = pendingImages,
                            onAddPendingImage = { taskFormViewModel.addPendingImage(it) },
                            onRemovePendingImage = { taskFormViewModel.removePendingImage(it) },
                            onDiscardPendingImages = { taskFormViewModel.discardPendingImages() },
                            confirmDiscardPendingImagesOnBack = true,
                            onBackRequestChanged = { taskFormBackHandler = it },
                            onRemoveTaskImage = { taskFormViewModel.removeTaskImage(it) },
                            onDelete = {
                                taskFormViewModel.deleteTask(currentEditTask)
                                navController.popBackStack()
                            },
                        )
                    }
                }

                entry<Screen.Countdown> {
                    val viewModel: CountdownViewModel = koinViewModel()
                    CountdownScreen(
                        viewModel = viewModel,
                        onCountdownClick = { countdown ->
                            navController.navigate(Screen.CountdownDetail(countdown.id))
                        },
                        onDeleteCountdown = viewModel::deleteCountdown,
                        onSettingsClick = onSettingsClick,
                    )
                }

                entry<Screen.CreateCountdown> { screen ->
                    val viewModel: CountdownFormViewModel = koinViewModel()
                    val initialType =
                        CountdownType.entries.getOrElse(screen.typeOrdinal) { CountdownType.COUNTDOWN }
                    CreateCountdownScreen(
                        editCountdown = null,
                        initialType = initialType,
                        onSave = { countdown -> viewModel.addCountdown(countdown) },
                        onBack = { navController.popBackStack() },
                    )
                }

                entry<Screen.CountdownDetail> { screen ->
                    val viewModel: CountdownFormViewModel = koinViewModel()
                    LaunchedEffect(screen.countdownId) { viewModel.setCountdownId(screen.countdownId) }
                    val countdown by viewModel.editCountdown.collectAsState()
                    val detailCountdown by viewModel.detailCountdown.collectAsState()
                    CountdownDetailScreen(
                        countdown = detailCountdown,
                        onBack = { navController.popBackStack() },
                        onEdit = {
                            navController.navigate(Screen.EditCountdown(screen.countdownId))
                        },
                        onDelete = {
                            val current = countdown ?: return@CountdownDetailScreen
                            viewModel.deleteCountdown(current) {
                            }
                            navController.popBackStack()
                        },
                    )
                }

                entry<Screen.EditCountdown> { screen ->
                    val viewModel: CountdownFormViewModel = koinViewModel()
                    LaunchedEffect(screen.countdownId) { viewModel.setCountdownId(screen.countdownId) }
                    val editCountdown by viewModel.editCountdown.collectAsState()
                    editCountdown?.let { countdown ->
                        CreateCountdownScreen(
                            editCountdown = countdown,
                            initialType = countdown.countdownType,
                            onSave = { updated ->
                                viewModel.updateCountdown(updated)
                            },
                            onBack = { navController.popBackStack() },
                        )
                    }
                }
            },
        )

        // Bottom nav bar — slides out when entering detail screens
        AnimatedVisibility(
            visible = showBottomNav,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            BottomNavBar(
                tabs = tabs,
                selectedTab = selectedTab,
                onTabSelected = { index ->
                    val target: NavKey = when (index) {
                        0 -> Screen.Matrix
                        1 -> Screen.TaskList
                        2 -> Screen.Calendar
                        3 -> Screen.Notes
                        4 -> Screen.Countdown
                        else -> Screen.Matrix
                    }
                    navController.navigateToTab(target)
                },
            )
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )

        // FAB — slides out with nav bar
        AnimatedVisibility(
            visible = showBottomNav,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier.align(Alignment.BottomEnd),
        ) {
            CreateTaskFab(
                onClick = {
                    if (selectedTab == 4) {
                        navController.navigate(Screen.CreateCountdown())
                    } else if (selectedTab == 3) {
                        showCreateNote = true
                    } else {
                        navController.navigate(
                            Screen.CreateTask(
                                categoryId = if (selectedTab == 1) selectedListId else "00000000-0000-0000-0000-000000000001",
                                day = if (selectedTab == 2) calendarSelectedDay else 0,
                                month = if (selectedTab == 2) calendarSelectedMonth else 0,
                                year = if (selectedTab == 2) calendarSelectedYear else 0,
                            )
                        )
                    }
                },
            )
        }
    }

    // Create note bottom sheet
    if (showCreateNote) {
        val createNoteSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        CreateNoteBottomSheet(
            sheetState = createNoteSheetState,
            onDismiss = { showCreateNote = false },
            onSave = { title, content -> noteViewModel.addNote(title, content) },
        )
    }

    // Edit note bottom sheet
    val editNoteIdVal = editNoteId
    LaunchedEffect(editNoteIdVal) {
        noteViewModel.selectNote(editNoteIdVal)
    }
    if (editNoteIdVal != null) {
        val editNote by noteViewModel.selectedNote.collectAsState()
        var hasObservedEditNote by remember(editNoteIdVal) { mutableStateOf(false) }
        LaunchedEffect(editNoteIdVal, editNote) {
            if (editNote != null) {
                hasObservedEditNote = true
            } else if (hasObservedEditNote) {
                editNoteId = null
            }
        }
        editNote?.let { note ->
            val editNoteSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            CreateNoteBottomSheet(
                sheetState = editNoteSheetState,
                editNote = note,
                onDismiss = { editNoteId = null },
                onSave = { title, content ->
                    noteViewModel.updateNote(note.copy(title = title, content = content))
                },
                onDelete = {
                    noteViewModel.deleteNote(note)
                    editNoteId = null
                },
            )
        }
    }

    if (taskNotificationEvent != null) {
        LaunchedEffect(taskNotificationEvent) {
            taskNotificationViewModel.setNotificationEvent(taskNotificationEvent)
        }
        val taskNotificationSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        fun closeTaskNotificationSheet() {
            taskNotificationViewModel.clearNotificationEvent()
            onTaskNotificationDismiss()
        }
        TaskNotificationBottomSheet(
            sheetState = taskNotificationSheetState,
            uiState = taskNotificationUiState,
            onDismiss = { closeTaskNotificationSheet() },
            onMarkDone = {
                taskNotificationViewModel.markDone { closeTaskNotificationSheet() }
            },
            onGotIt = {
                taskNotificationViewModel.gotIt { closeTaskNotificationSheet() }
            },
            onEdit = {
                val taskId = taskNotificationUiState.event?.eventId
                if (taskId != null) {
                    closeTaskNotificationSheet()
                    navController.navigate(Screen.EditTask(taskId))
                }
            },
        )
    }

    // Import calendar dialog
    if (showImportCalendar) {
        val importCalendarViewModel: ImportCalendarViewModel = koinViewModel()
        ImportCalendarDialog(
            viewModel = importCalendarViewModel,
            onDismiss = { showImportCalendar = false },
        )
    }

    // Import ICS file dialog
    if (showImportIcs) {
        val importIcsViewModel: ImportIcsViewModel = koinViewModel()
        val icsScope = rememberCoroutineScope()
        ImportIcsDialog(
            viewModel = importIcsViewModel,
            onPickFile = {
                icsScope.launch {
                    val result = pickIcsFileContent()
                    if (result != null) {
                        importIcsViewModel.importFromIcsContent(result.first, result.second)
                    }
                }
            },
            onDismiss = { showImportIcs = false },
        )
    }

    // Import CSV (TickTick) dialog
    if (showImportCsv) {
        val importCsvViewModel: ImportCsvViewModel = koinViewModel()
        val csvScope = rememberCoroutineScope()
        ImportCsvDialog(
            viewModel = importCsvViewModel,
            onPickFile = {
                csvScope.launch {
                    val result = pickCsvFileContent()
                    if (result != null) {
                        importCsvViewModel.importFromCsvContent(result.first, result.second)
                    }
                }
            },
            onDismiss = { showImportCsv = false },
        )
    }
}

private fun maybeShowExactReminderSnackbar(
    formData: TaskFormData,
    checkNotificationPermissionUseCase: CheckNotificationPermissionUseCase,
    snackbarHostState: SnackbarHostState,
    scope: kotlinx.coroutines.CoroutineScope,
) {
    if (!formData.hasFutureReminder()) return
    scope.launch {
        if (checkNotificationPermissionUseCase.exactReminderStatus() != ExactReminderPermissionStatus.NOT_GRANTED) {
            return@launch
        }
        val result = snackbarHostState.showSnackbar(
            message = org.jetbrains.compose.resources.getString(Res.string.exact_reminder_permission_message),
            actionLabel = org.jetbrains.compose.resources.getString(Res.string.open_settings),
        )
        if (result == SnackbarResult.ActionPerformed) {
            checkNotificationPermissionUseCase.openExactReminderSettings()
        }
    }
}

private fun TaskFormData.hasFutureReminder(): Boolean {
    val deadlineValue = deadline ?: return false
    val now = localNow()
    val dateOffsets = dateReminders.parseMinuteValues()
    val durationOffsets = durationReminders.parseMinuteValues()
    val legacyOffsets = if (dateOffsets.isEmpty() && durationOffsets.isEmpty()) {
        legacyReminderMinutes()
    } else {
        emptyList()
    }
    return dateOffsets.any { deadlineValue - (it * MILLIS_PER_MINUTE) > now } ||
            durationOffsets.any { offset ->
                val triggerAt =
                    if (offset == -1) endDeadline else deadlineValue - (offset * MILLIS_PER_MINUTE)
                triggerAt != null && triggerAt > now
            } ||
            legacyOffsets.any { deadlineValue - (it * MILLIS_PER_MINUTE) > now }
}

private fun String.parseMinuteValues(): List<Int> =
    split(",").mapNotNull { it.trim().toIntOrNull() }

private fun TaskFormData.legacyReminderMinutes(): List<Int> {
    val value = reminderDays.takeIf { it > 0 } ?: return emptyList()
    return listOf(value * 1440)
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
            .padding(
                end = OpenTasksTheme.dimens.paddingXLarge,
                bottom = OpenTasksTheme.dimens.fabBottomPadding
            ),
    ) {
        Icon(
            painter = painterResource(Res.drawable.ic_add),
            contentDescription = stringResource(Res.string.add_task),
            tint = Color.White,
        )
    }
}

@Composable
private fun quadrantTitle(priority: TaskPriority): String = when (priority) {
    TaskPriority.HIGH -> stringResource(Res.string.urgent_important)
    TaskPriority.MEDIUM -> stringResource(Res.string.not_urgent_important)
    TaskPriority.LOW -> stringResource(Res.string.urgent_unimportant)
    TaskPriority.NONE -> stringResource(Res.string.not_urgent_unimportant)
}
