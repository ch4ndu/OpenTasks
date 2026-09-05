package com.udnahc.opentasks

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.udnahc.opentasks.data.auth.AccountBoundaryExecutor
import com.udnahc.opentasks.data.auth.AccountBoundary
import com.udnahc.opentasks.data.auth.AccountBoundaryRejectedException
import com.udnahc.opentasks.data.auth.AccountSessionState
import com.udnahc.opentasks.data.auth.CacheBinding
import com.udnahc.opentasks.data.auth.activeBindingOrNull
import com.udnahc.opentasks.data.auth.authenticatedAccountOrNull
import com.udnahc.opentasks.ExternalInputFailure
import com.udnahc.opentasks.SharedTaskPayloadEvent
import com.udnahc.opentasks.claimSharedIcsPayloadForReview
import com.udnahc.opentasks.claimSharedTaskPayloadForReview
import com.udnahc.opentasks.claimSharedTaskRejectionForReview
import com.udnahc.opentasks.completeSharedTaskReview
import com.udnahc.opentasks.deactivateSharedTaskIntake
import com.udnahc.opentasks.sharedTaskIntakeStatus
import com.udnahc.opentasks.updateSharedTaskIntakeReadiness
import com.udnahc.opentasks.data.model.COUNTDOWN_ID_PREFIX
import com.udnahc.opentasks.data.model.CountdownType
import com.udnahc.opentasks.data.model.TaskFormData
import com.udnahc.opentasks.data.model.TaskPriority
import com.udnahc.opentasks.data.model.AppConstants
import com.udnahc.opentasks.data.model.isCountdownItem
import com.udnahc.opentasks.data.notification.ExactReminderPermissionStatus
import com.udnahc.opentasks.data.notification.NotificationCapability
import com.udnahc.opentasks.domain.action.attachment.RetryAttachmentTombstoneFileCleanupAction
import com.udnahc.opentasks.domain.action.reminder.RebuildReminderQueueAction
import com.udnahc.opentasks.domain.action.settings.InitializeSyncAction
import com.udnahc.opentasks.domain.action.settings.TriggerSyncAction
import com.udnahc.opentasks.domain.action.task.TaskWriteResult
import com.udnahc.opentasks.data.repository.CommittedMutation
import com.udnahc.opentasks.domain.time.LocalDaySignal
import com.udnahc.opentasks.domain.usecase.settings.CheckNotificationPermissionUseCase
import com.udnahc.opentasks.domain.usecase.task.QuickTaskCreationContext
import com.udnahc.opentasks.domain.usecase.task.TaskReminderEligibilityUseCase
import com.udnahc.opentasks.navigation.AppNavController
import com.udnahc.opentasks.navigation.Screen
import com.udnahc.opentasks.navigation.asQuickAddTask
import com.udnahc.opentasks.navigation.screenNavSavedStateConfiguration
import com.udnahc.opentasks.ui.screens.CompleteSeriesDialog
import com.udnahc.opentasks.ui.screens.AccountSessionEntryMode
import com.udnahc.opentasks.ui.screens.AccountSessionRoute
import com.udnahc.opentasks.ui.screens.AccountSessionScreen
import com.udnahc.opentasks.ui.screens.AccountSessionStatusScreen
import com.udnahc.opentasks.ui.screens.AccountTransitionScreen
import com.udnahc.opentasks.ui.screens.CreateNoteBottomSheet
import com.udnahc.opentasks.ui.screens.CreateTaskScreen
import com.udnahc.opentasks.ui.screens.DestinationLoadingScreen
import com.udnahc.opentasks.ui.screens.EisenhowerMatrixScreen
import com.udnahc.opentasks.ui.screens.ImportCalendarDialog
import com.udnahc.opentasks.ui.screens.ImportCsvDialog
import com.udnahc.opentasks.ui.screens.ImportIcsDialog
import com.udnahc.opentasks.ui.screens.NotesScreen
import com.udnahc.opentasks.ui.screens.MissingDestinationScreen
import com.udnahc.opentasks.ui.screens.QuadrantDetailScreen
import com.udnahc.opentasks.ui.screens.QuickAddTaskScreen
import com.udnahc.opentasks.ui.screens.SettingsScreen
import com.udnahc.opentasks.ui.screens.TaskCreationChoiceBottomSheet
import com.udnahc.opentasks.ui.screens.TaskListScreen
import com.udnahc.opentasks.ui.screens.TaskNotificationBottomSheet
import com.udnahc.opentasks.ui.screens.calendar.CalendarScreen
import com.udnahc.opentasks.ui.screens.countdown.CountdownDetailScreen
import com.udnahc.opentasks.ui.screens.countdown.CountdownScreen
import com.udnahc.opentasks.ui.screens.countdown.CreateCountdownScreen
import com.udnahc.opentasks.ui.screens.accountSessionRoute
import com.udnahc.opentasks.ui.theme.OpenTasksTheme
import com.udnahc.opentasks.ui.theme.PrimaryBlue
import com.udnahc.opentasks.ui.util.FileImportResult
import com.udnahc.opentasks.ui.util.ImportFileType
import com.udnahc.opentasks.ui.util.rememberFileImportLauncher
import com.udnahc.opentasks.ui.util.rememberNotificationPermissionLauncher
import com.udnahc.opentasks.viewmodel.AppViewModel
import com.udnahc.opentasks.viewmodel.AppearanceViewModel
import com.udnahc.opentasks.viewmodel.AuthViewModel
import com.udnahc.opentasks.viewmodel.CalendarViewModel
import com.udnahc.opentasks.viewmodel.CountdownFormViewModel
import com.udnahc.opentasks.viewmodel.CountdownDestinationState
import com.udnahc.opentasks.viewmodel.CountdownMutationEvent
import com.udnahc.opentasks.viewmodel.CountdownViewModel
import com.udnahc.opentasks.viewmodel.ImportCalendarViewModel
import com.udnahc.opentasks.viewmodel.ImportCsvViewModel
import com.udnahc.opentasks.viewmodel.ImportIcsViewModel
import com.udnahc.opentasks.viewmodel.MatrixViewModel
import com.udnahc.opentasks.viewmodel.NoteMutationOperation
import com.udnahc.opentasks.viewmodel.NoteMutationState
import com.udnahc.opentasks.viewmodel.NoteViewModel
import com.udnahc.opentasks.viewmodel.QuickAddTaskSaveEvent
import com.udnahc.opentasks.viewmodel.QuickAddTaskViewModel
import com.udnahc.opentasks.viewmodel.TaskFormSaveEvent
import com.udnahc.opentasks.viewmodel.TaskFormDestinationState
import com.udnahc.opentasks.viewmodel.TaskFormViewModel
import com.udnahc.opentasks.viewmodel.TaskListViewModel
import com.udnahc.opentasks.viewmodel.TaskNotificationViewModel
import com.udnahc.opentasks.viewmodel.TaskNotificationSheetFeedback
import com.udnahc.opentasks.viewmodel.SharedIcsImportResult
import com.udnahc.opentasks.viewmodel.taskNotificationSheetDecision
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import opentasks.composeapp.generated.resources.Res
import opentasks.composeapp.generated.resources.add_task
import opentasks.composeapp.generated.resources.exact_reminder_permission_message
import opentasks.composeapp.generated.resources.external_launch_failed
import opentasks.composeapp.generated.resources.ic_add
import opentasks.composeapp.generated.resources.ic_calendar
import opentasks.composeapp.generated.resources.ic_check_box
import opentasks.composeapp.generated.resources.ic_grid_view
import opentasks.composeapp.generated.resources.ic_note
import opentasks.composeapp.generated.resources.ic_schedule
import opentasks.composeapp.generated.resources.calendar
import opentasks.composeapp.generated.resources.countdown_title
import opentasks.composeapp.generated.resources.import_failed_generic
import opentasks.composeapp.generated.resources.import_button
import opentasks.composeapp.generated.resources.shared_content_invalid
import opentasks.composeapp.generated.resources.shared_content_too_large
import opentasks.composeapp.generated.resources.shared_content_too_many_items
import opentasks.composeapp.generated.resources.shared_ics_import_confirmation_message
import opentasks.composeapp.generated.resources.shared_ics_import_confirmation_title
import opentasks.composeapp.generated.resources.import_success
import opentasks.composeapp.generated.resources.cancel
import opentasks.composeapp.generated.resources.image_save_partial_failed
import opentasks.composeapp.generated.resources.not_urgent_important
import opentasks.composeapp.generated.resources.not_urgent_unimportant
import opentasks.composeapp.generated.resources.open_settings
import opentasks.composeapp.generated.resources.task_save_failed
import opentasks.composeapp.generated.resources.task_update_failed
import opentasks.composeapp.generated.resources.task_not_found
import opentasks.composeapp.generated.resources.task_mutation_saved_warning
import opentasks.composeapp.generated.resources.task_delete_failed
import opentasks.composeapp.generated.resources.task_notification_saved_warning
import opentasks.composeapp.generated.resources.task_notification_obsolete
import opentasks.composeapp.generated.resources.task_notification_task_missing
import opentasks.composeapp.generated.resources.task_notification_stale
import opentasks.composeapp.generated.resources.countdown_save_failed
import opentasks.composeapp.generated.resources.countdown_delete_failed
import opentasks.composeapp.generated.resources.countdown_saved_warning
import opentasks.composeapp.generated.resources.countdown_not_found
import opentasks.composeapp.generated.resources.note_delete_failed
import opentasks.composeapp.generated.resources.note_mutation_saved_warning
import opentasks.composeapp.generated.resources.note_save_failed
import opentasks.composeapp.generated.resources.matrix
import opentasks.composeapp.generated.resources.notes
import opentasks.composeapp.generated.resources.tasks
import opentasks.composeapp.generated.resources.urgent_important
import opentasks.composeapp.generated.resources.urgent_unimportant
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import org.lighthousegames.logging.logging
import kotlinx.datetime.LocalDate

private val log = logging("App")

@Composable
fun App(
    deepLinkNotificationEvent: NotificationDeepLinkEvent? = null,
    widgetNavigationEvent: WidgetNavigationEvent? = null,
    onNotificationDeepLinkEventConsumed: (NotificationDeepLinkEvent) -> Unit = {},
    onAccountBoundaryChanged: suspend (com.udnahc.opentasks.data.auth.CacheBinding?) -> Unit = {},
    onTaskNotificationWidgetsRefresh: suspend (AccountBoundary) -> Unit = {},
    onSystemBarIconAppearanceChanged: (useDarkIcons: Boolean) -> Unit = {},
) {
    val appearanceViewModel: AppearanceViewModel = koinViewModel()
    val authViewModel: AuthViewModel = koinViewModel()
    val themeMode by appearanceViewModel.themePreference.collectAsState()
    val textSizePreference by appearanceViewModel.textSizePreference.collectAsState()
    val sessionState by authViewModel.sessionState.collectAsState()
    val accountOperation by authViewModel.operation.collectAsState()
    val accountError by authViewModel.error.collectAsState()
    val savedEndpoint by authViewModel.savedEndpoint.collectAsState()
    OpenTasksTheme(
        themeMode = themeMode,
        textSizePreference = textSizePreference,
    ) {
        val useDarkSystemBarIcons = MaterialTheme.colorScheme.background.luminance() > 0.5f
        SideEffect {
            onSystemBarIconAppearanceChanged(useDarkSystemBarIcons)
        }
        LaunchedEffect(authViewModel) { authViewModel.restoreSession() }
        LaunchedEffect(sessionState) {
            onAccountBoundaryChanged(
                sessionState.activeBindingOrNull(),
            )
        }
        when (accountSessionRoute(sessionState)) {
            AccountSessionRoute.RESTORING -> AccountSessionStatusScreen(
                operation = accountOperation,
                error = accountError,
                onRetry = authViewModel::restoreSession,
                onClearError = authViewModel::clearError,
            )

            AccountSessionRoute.SIGN_IN -> {
                val reauthenticationState = sessionState as? AccountSessionState.ReauthenticationRequired
                AccountSessionScreen(
                    mode = AccountSessionEntryMode.SIGN_IN,
                    account = reauthenticationState?.account,
                    endpoint = reauthenticationState?.canonicalEndpoint ?: savedEndpoint,
                    operation = accountOperation,
                    error = accountError,
                    storageWarning = authViewModel.storageWarning,
                    reauthenticationReason = reauthenticationState?.reason,
                    allowLocalOnly = sessionState == AccountSessionState.SignedOut,
                    onSignIn = authViewModel::login,
                    onReauthenticate = { _, _ -> },
                    onUseWithoutSync = authViewModel::startLocalOnly,
                    onClearError = authViewModel::clearError,
                )

            }

            AccountSessionRoute.REAUTHENTICATE -> {
                val reauthenticationState = sessionState as AccountSessionState.ReauthenticationRequired
                AccountSessionScreen(
                    mode = AccountSessionEntryMode.REAUTHENTICATE,
                    account = reauthenticationState.account,
                    endpoint = reauthenticationState.canonicalEndpoint ?: savedEndpoint,
                    operation = accountOperation,
                    error = accountError,
                    storageWarning = authViewModel.storageWarning,
                    reauthenticationReason = reauthenticationState.reason,
                    allowLocalOnly = false,
                    onSignIn = authViewModel::login,
                    onReauthenticate = authViewModel::reauthenticate,
                    onUseWithoutSync = {},
                    onClearError = authViewModel::clearError,
                )
            }

            AccountSessionRoute.TRANSITIONING -> {
                val transitioningState = sessionState as AccountSessionState.Transitioning
                AccountTransitionScreen(
                    transition = transitioningState.transition,
                    operation = accountOperation,
                    error = accountError,
                    onRetry = authViewModel::restoreSession,
                    onClearError = authViewModel::clearError,
                )
            }

            AccountSessionRoute.ACTIVE -> {
                val binding = sessionState.activeBindingOrNull()
                    ?: error("Active session route requires a cache binding")
                key(binding.boundaryEpoch) {
                    ActiveAppContent(
                        sessionState = sessionState,
                        authViewModel = authViewModel,
                        deepLinkNotificationEvent = deepLinkNotificationEvent,
                        widgetNavigationEvent = widgetNavigationEvent,
                        onNotificationDeepLinkEventConsumed = onNotificationDeepLinkEventConsumed,
                        onTaskNotificationWidgetsRefresh = onTaskNotificationWidgetsRefresh,
                    )
                }
            }
        }
    }
}

@Composable
private fun ActiveAppContent(
    sessionState: AccountSessionState,
    authViewModel: AuthViewModel,
    deepLinkNotificationEvent: NotificationDeepLinkEvent?,
    widgetNavigationEvent: WidgetNavigationEvent?,
    onNotificationDeepLinkEventConsumed: (NotificationDeepLinkEvent) -> Unit,
    onTaskNotificationWidgetsRefresh: suspend (AccountBoundary) -> Unit,
) {
    val binding = sessionState.activeBindingOrNull() ?: return
    val isRemoteSyncEnabled = sessionState is AccountSessionState.Authenticated
    val backStack = rememberNavBackStack(screenNavSavedStateConfiguration, Screen.Matrix)
    val navController = remember(backStack) { AppNavController(backStack) }
    var taskNotificationEvent by remember { mutableStateOf<NotificationDeepLinkEvent?>(null) }
    val initializeSyncAction = koinInject<InitializeSyncAction>()
    val rebuildReminderQueueAction = koinInject<RebuildReminderQueueAction>()
    val retryAttachmentTombstoneFileCleanupAction =
        koinInject<RetryAttachmentTombstoneFileCleanupAction>()
    val triggerSyncAction = koinInject<TriggerSyncAction>()
    val accountBoundaryExecutor = koinInject<AccountBoundaryExecutor>()
    val localDaySignal = koinInject<LocalDaySignal>()
    val currentDate by localDaySignal.dates.collectAsState(initial = localDaySignal.snapshot())
    val startupMaintenanceCompletion = remember(binding.boundaryEpoch) {
        CompletableDeferred<Boolean>()
    }
    val hasObservedFirstResume = remember(binding.boundaryEpoch) { mutableStateOf(false) }
    LaunchedEffect(binding.boundaryEpoch) {
        var accepted = false
        try {
            accepted = try {
                withContext(Dispatchers.IO) {
                    accountBoundaryExecutor.withForegroundBoundary { boundary ->
                        if (!boundary.matches(binding)) {
                            return@withForegroundBoundary false
                        }
                        try {
                            retryAttachmentTombstoneFileCleanupAction()
                        } catch (e: CancellationException) {
                            throw e
                        } catch (_: Exception) {
                            log.e { "Attachment tombstone file cleanup failed for active cache" }
                        }
                        if (isRemoteSyncEnabled) {
                            try {
                                initializeSyncAction()
                            } catch (e: CancellationException) {
                                throw e
                            } catch (_: Exception) {
                                log.e { "Initial sync failed for authenticated account" }
                            }
                        }
                        try {
                            rebuildReminderQueueAction()
                        } catch (e: CancellationException) {
                            throw e
                        } catch (_: Exception) {
                            log.e { "Initial reminder queue rebuild failed for active cache" }
                        }
                        true
                    }
                }
            } catch (_: AccountBoundaryRejectedException) {
                false
            }
        } finally {
            startupMaintenanceCompletion.complete(accepted)
        }
    }
    val syncScope = rememberCoroutineScope()
    LifecycleResumeEffect(binding.boundaryEpoch) {
        localDaySignal.refresh()
        val isFirstResume = !hasObservedFirstResume.value
        hasObservedFirstResume.value = true
        if (!isFirstResume) {
            syncScope.launch(Dispatchers.IO) {
                if (!startupMaintenanceCompletion.await()) return@launch
                try {
                    accountBoundaryExecutor.withForegroundBoundary { boundary ->
                        if (!boundary.matches(binding)) {
                            return@withForegroundBoundary
                        }
                        if (isRemoteSyncEnabled) {
                            try {
                                triggerSyncAction.syncNow()
                            } catch (e: CancellationException) {
                                throw e
                            } catch (_: Exception) {
                                log.e { "Resume sync failed for authenticated account" }
                            }
                        }
                        try {
                            rebuildReminderQueueAction()
                        } catch (e: CancellationException) {
                            throw e
                        } catch (_: Exception) {
                            log.e { "Resume reminder queue rebuild failed for active cache" }
                        }
                    }
                } catch (_: AccountBoundaryRejectedException) {
                    log.w { "Resume maintenance skipped because the foreground account boundary changed" }
                }
            }
        }
        onPauseOrDispose { }
    }
    fun handleNotificationEvent(event: NotificationDeepLinkEvent) {
        if (event.eventId.startsWith(COUNTDOWN_ID_PREFIX)) {
            event.countdownIdIfMatches(binding)?.let { countdownId ->
                navController.navigate(Screen.CountdownDetail(countdownId))
            }
            return
        }
        if (!event.matches(binding)) return
        navController.navigateToTab(Screen.Matrix)
        taskNotificationEvent = event
    }
    deepLinkNotificationEvent?.let { event ->
        LaunchedEffect(event) {
            try {
                handleNotificationEvent(event)
            } finally {
                onNotificationDeepLinkEventConsumed(event)
            }
        }
    }
    LaunchedEffect(navController) {
        notificationDeepLinkEvent.collect { event ->
            if (event != null) {
                if (event.matches(binding)) handleNotificationEvent(event)
                clearNotificationDeepLinkEvent(event)
            }
        }
    }
    var calendarNavigationEvent by remember { mutableStateOf<WidgetNavigationEvent?>(null) }
    LaunchedEffect(widgetNavigationEvent?.id) {
        val event = widgetNavigationEvent ?: return@LaunchedEffect
        if (!event.matches(binding)) return@LaunchedEffect
        when (event.action) {
            WidgetNavigationAction.CREATE_TASK -> navController.navigate(Screen.CreateTask())
            WidgetNavigationAction.VIEW_LIST -> navController.navigateToTab(Screen.TaskList)
            WidgetNavigationAction.VIEW_TASK -> {
                event.taskId?.takeIf { it.isNotBlank() }?.let { taskId ->
                    navController.navigate(Screen.EditTask(taskId))
                }
            }
            WidgetNavigationAction.VIEW_CALENDAR -> {
                val date = event.calendarDate?.takeIf { it.isValid } ?: return@LaunchedEffect
                calendarNavigationEvent = event.copy(calendarDate = date)
                navController.navigateToTab(Screen.Calendar)
            }
        }
    }
    AccountEpochViewModelStoreProvider(binding.boundaryEpoch) {
        MainScreen(
            navController = navController,
            backStack = backStack,
            calendarTodayDay = currentDate.day,
            currentDate = currentDate,
            calendarNavigationEvent = calendarNavigationEvent,
            onCalendarNavigationConsumed = { eventId ->
                calendarNavigationEvent = consumeCalendarNavigationEvent(calendarNavigationEvent, eventId)
            },
            taskNotificationEvent = taskNotificationEvent,
            onTaskNotificationDismiss = { taskNotificationEvent = null },
            onTaskNotificationWidgetsRefresh = onTaskNotificationWidgetsRefresh,
            binding = binding,
            currentAccount = sessionState.authenticatedAccountOrNull(),
            isRemoteSyncEnabled = isRemoteSyncEnabled,
            authViewModel = authViewModel,
        )
    }
}

private data class BottomNavItem(
    val iconRes: DrawableResource,
    val labelRes: StringResource,
    val isCalendar: Boolean = false,
)

private data class TaskFormBackHandler(
    val owner: Any,
    val onBack: () -> Unit,
)

/** An entry-scoped store owns lifecycle; this key also separates form destinations by task. */
internal fun taskFormViewModelKey(screen: Screen): String = when (screen) {
    is Screen.CreateTask -> "create:${screen.priorityOrdinal}:${screen.categoryId}:${screen.day}:${screen.month}:${screen.year}"
    is Screen.EditTask -> "edit:${screen.taskId}"
    else -> error("TaskFormViewModel is only valid for task-form navigation entries")
}

internal fun quickAddTaskViewModelKey(screen: Screen.QuickAddTask): String =
    "quick:${screen.priorityOrdinal}:${screen.categoryId}:${screen.day}:${screen.month}:${screen.year}"

internal fun Screen.QuickAddTask.creationContext(): QuickTaskCreationContext = QuickTaskCreationContext(
    categoryId = categoryId,
    priority = TaskPriority.entries.getOrElse(priorityOrdinal) { TaskPriority.NONE },
    fallbackDate = if (day > 0 && month > 0 && year > 0) {
        runCatching { LocalDate(year, month, day) }.getOrNull()
    } else {
        null
    },
)

internal fun taskFabCreationScreen(
    selectedTab: Int,
    selectedListId: String,
    calendarDay: Int,
    calendarMonth: Int,
    calendarYear: Int,
): Screen.CreateTask = Screen.CreateTask(
    categoryId = if (selectedTab == 1) selectedListId else AppConstants.DEFAULT_INBOX_ID,
    day = if (selectedTab == 2) calendarDay else 0,
    month = if (selectedTab == 2) calendarMonth else 0,
    year = if (selectedTab == 2) calendarYear else 0,
)

/** Keeps a departing form from unregistering the handler that replaced it. */
internal class TaskFormBackHandlerRegistry {
    private var handler: TaskFormBackHandler? = null

    fun register(owner: Any, onBack: (() -> Unit)?) {
        if (onBack == null) {
            if (handler?.owner == owner) handler = null
        } else {
            handler = TaskFormBackHandler(owner, onBack)
        }
    }

    fun handle(fallback: () -> Unit) {
        handler?.onBack?.invoke() ?: fallback()
    }
}

internal fun kotlinx.coroutines.CoroutineScope.launchSharedTaskRejectionFeedback(
    payloadId: Long,
    showFeedback: suspend () -> Unit,
) = launch {
    try {
        showFeedback()
    } finally {
        completeSharedTaskReview(payloadId)
    }
}

internal fun childModalBusyRouteAfterChange(
    currentBusyRoute: NavKey?,
    activeRoute: NavKey,
    reportingRoute: NavKey,
    isBusy: Boolean,
): NavKey? {
    if (reportingRoute != activeRoute) return currentBusyRoute
    return reportingRoute.takeIf { isBusy }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainScreen(
    navController: AppNavController,
    backStack: NavBackStack<NavKey>,
    calendarTodayDay: Int?,
    currentDate: LocalDate,
    calendarNavigationEvent: WidgetNavigationEvent?,
    onCalendarNavigationConsumed: (Long) -> Unit,
    taskNotificationEvent: NotificationDeepLinkEvent?,
    onTaskNotificationDismiss: () -> Unit,
    onTaskNotificationWidgetsRefresh: suspend (AccountBoundary) -> Unit,
    binding: CacheBinding,
    currentAccount: com.udnahc.opentasks.data.auth.AuthenticatedAccount?,
    isRemoteSyncEnabled: Boolean,
    authViewModel: AuthViewModel,
) {
    val noteViewModel: NoteViewModel = koinViewModel()
    val matrixViewModel: MatrixViewModel = koinViewModel()
    val taskNotificationViewModel: TaskNotificationViewModel = koinViewModel()
    val appViewModel: AppViewModel = koinViewModel()
    val noteMutationState by noteViewModel.mutationState.collectAsState()
    val accountOperation by authViewModel.operation.collectAsState()
    val accountError by authViewModel.error.collectAsState()
    val replacementPreview by authViewModel.replacementPreview.collectAsState()
    val isRefreshing by appViewModel.isRefreshing.collectAsState()
    val sharedIcsImportConfirmationId by appViewModel.sharedIcsImportConfirmation.collectAsState()
    val isSharedIcsIntakeBusy by appViewModel.isSharedIcsIntakeBusy.collectAsState()
    val sharedPayloadEvent by sharedTaskPayload.collectAsState()
    val sharedIntakeStatus by sharedTaskIntakeStatus.collectAsState()
    val onPullToRefresh = remember(appViewModel, isRemoteSyncEnabled) {
        if (isRemoteSyncEnabled) ({ appViewModel.triggerSync() }) else ({})
    }
    var selectedListId by rememberSaveable { mutableStateOf(AppConstants.DEFAULT_INBOX_ID) }
    var calendarSelectedYear by remember { mutableIntStateOf(0) }
    var calendarSelectedMonth by remember { mutableIntStateOf(0) }
    var calendarSelectedDay by remember { mutableIntStateOf(0) }
    var showCreateNote by rememberSaveable(binding.boundaryEpoch) { mutableStateOf(false) }
    var editNoteId by rememberSaveable(binding.boundaryEpoch) { mutableStateOf<String?>(null) }
    var noteSheetRequestToken by rememberSaveable(binding.boundaryEpoch) { mutableStateOf(0L) }
    var showImportCalendar by remember { mutableStateOf(false) }
    var showImportIcs by remember { mutableStateOf(false) }
    var showImportCsv by remember { mutableStateOf(false) }
    var pendingTaskCreation by remember { mutableStateOf<Screen.CreateTask?>(null) }
    var sharedTaskReviewId by rememberSaveable(binding.boundaryEpoch) { mutableStateOf<Long?>(null) }
    var childModalBusyRoute by remember(binding.boundaryEpoch) { mutableStateOf<NavKey?>(null) }
    val taskFormBackHandlerRegistry = remember { TaskFormBackHandlerRegistry() }
    val snackbarHostState = remember { SnackbarHostState() }
    val taskMutationSavedWarning = stringResource(Res.string.task_mutation_saved_warning)
    val taskUpdateFailed = stringResource(Res.string.task_update_failed)
    val notificationSavedWarning = stringResource(Res.string.task_notification_saved_warning)
    val notificationObsolete = stringResource(Res.string.task_notification_obsolete)
    val notificationTaskMissing = stringResource(Res.string.task_notification_task_missing)
    val notificationStale = stringResource(Res.string.task_notification_stale)
    val countdownSaveFailed = stringResource(Res.string.countdown_save_failed)
    val countdownDeleteFailed = stringResource(Res.string.countdown_delete_failed)
    val countdownSavedWarning = stringResource(Res.string.countdown_saved_warning)
    val taskNotFound = stringResource(Res.string.task_not_found)
    val countdownNotFound = stringResource(Res.string.countdown_not_found)
    val noteSaveFailed = stringResource(Res.string.note_save_failed)
    val noteDeleteFailed = stringResource(Res.string.note_delete_failed)
    val noteMutationSavedWarning = stringResource(Res.string.note_mutation_saved_warning)
    val externalLaunchFailed = stringResource(Res.string.external_launch_failed)
    val checkNotificationPermissionUseCase = koinInject<CheckNotificationPermissionUseCase>()
    val taskReminderEligibilityUseCase = koinInject<TaskReminderEligibilityUseCase>()
    val snackbarScope = rememberCoroutineScope()
    val onTaskMutationFailure = remember(snackbarHostState, snackbarScope, taskUpdateFailed) {
        {
            snackbarScope.launch {
                snackbarHostState.showSnackbar(taskUpdateFailed)
            }
            Unit
        }
    }
    var pendingGlobalPostSaveReminderCheck by remember { mutableStateOf<TaskFormData?>(null) }
    val requestGlobalNotificationPermission = rememberNotificationPermissionLauncher {
        val formData = pendingGlobalPostSaveReminderCheck
        pendingGlobalPostSaveReminderCheck = null
        if (formData != null) {
            maybeShowExactReminderSnackbar(
                formData = formData,
                checkNotificationPermissionUseCase = checkNotificationPermissionUseCase,
                taskReminderEligibilityUseCase = taskReminderEligibilityUseCase,
                snackbarHostState = snackbarHostState,
                scope = snackbarScope,
            )
        }
    }

    fun requestPostSaveReminderCheck(formData: TaskFormData) {
        if (checkNotificationPermissionUseCase.capability != NotificationCapability.SUPPORTED) return
        if (!taskReminderEligibilityUseCase(formData)) return
        pendingGlobalPostSaveReminderCheck = formData
        requestGlobalNotificationPermission()
    }

    fun NoteMutationOperation.matchesOpenNoteSheet(): Boolean {
        if (requestToken != noteSheetRequestToken) return false
        return when (this) {
            is NoteMutationOperation.Create -> showCreateNote && editNoteId == null
            is NoteMutationOperation.Update -> !showCreateNote && noteId == editNoteId
            is NoteMutationOperation.Delete -> !showCreateNote && note.id == editNoteId
        }
    }

    fun closeOpenNoteSheet() {
        showCreateNote = false
        editNoteId = null
        noteSheetRequestToken += 1L
    }

    fun retireOpenNoteSheet() {
        val terminalState = noteMutationState
        val operation = when (terminalState) {
            is NoteMutationState.Success -> terminalState.operation
            is NoteMutationState.Error -> terminalState.operation
            NoteMutationState.Idle,
            is NoteMutationState.Busy,
            -> null
        }
        if (operation != null) {
            noteViewModel.consumeMutationState(terminalState)
        }
        closeOpenNoteSheet()
    }

    fun openCreateNoteSheet() {
        retireOpenNoteSheet()
        showCreateNote = true
    }

    fun openEditNoteSheet(noteId: String) {
        retireOpenNoteSheet()
        editNoteId = noteId
    }

    LaunchedEffect(
        noteMutationState,
        noteSheetRequestToken,
        showCreateNote,
        editNoteId,
    ) {
        val terminalState = noteMutationState
        val operation = when (terminalState) {
            is NoteMutationState.Success -> terminalState.operation
            is NoteMutationState.Error -> terminalState.operation
            NoteMutationState.Idle,
            is NoteMutationState.Busy,
            -> return@LaunchedEffect
        }
        val matchesOpenSheet = operation.matchesOpenNoteSheet()
        if (!noteViewModel.consumeMutationState(terminalState)) return@LaunchedEffect
        if (!matchesOpenSheet) return@LaunchedEffect
        when (terminalState) {
            is NoteMutationState.Success -> {
                val hasWarning = terminalState.hasPostCommitWarning
                closeOpenNoteSheet()
                if (hasWarning) {
                    snackbarHostState.showSnackbar(noteMutationSavedWarning)
                }
            }
            is NoteMutationState.Error -> {
                val message = if (operation is NoteMutationOperation.Delete) {
                    noteDeleteFailed
                } else {
                    noteSaveFailed
                }
                snackbarHostState.showSnackbar(message)
            }
            NoteMutationState.Idle,
            is NoteMutationState.Busy,
            -> Unit
        }
    }

    val tabs = remember {
        listOf(
            BottomNavItem(iconRes = Res.drawable.ic_grid_view, labelRes = Res.string.matrix),
            BottomNavItem(iconRes = Res.drawable.ic_check_box, labelRes = Res.string.tasks),
            BottomNavItem(
                iconRes = Res.drawable.ic_calendar,
                labelRes = Res.string.calendar,
                isCalendar = true,
            ),
            BottomNavItem(iconRes = Res.drawable.ic_note, labelRes = Res.string.notes),
            BottomNavItem(iconRes = Res.drawable.ic_schedule, labelRes = Res.string.countdown_title),
        )
    }

    // Derive selected tab and visibility from the back stack
    val currentScreen = backStack.last()
    val currentScreenState = rememberUpdatedState(currentScreen)
    val onChildModalBusyChanged = remember {
        { route: NavKey, isBusy: Boolean ->
            childModalBusyRoute = childModalBusyRouteAfterChange(
                currentBusyRoute = childModalBusyRoute,
                activeRoute = currentScreenState.value,
                reportingRoute = route,
                isBusy = isBusy,
            )
        }
    }
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
            && currentScreen !is Screen.QuickAddTask
            && currentScreen !is Screen.EditTask
            && currentScreen !is Screen.Settings
            && currentScreen !is Screen.CreateCountdown
            && currentScreen !is Screen.CountdownDetail
            && currentScreen !is Screen.EditCountdown

    val isSharedIntakeUiBusy = currentScreen is Screen.CreateTask ||
        currentScreen is Screen.QuickAddTask ||
        currentScreen is Screen.EditTask ||
        currentScreen is Screen.CreateCountdown ||
        currentScreen is Screen.EditCountdown ||
        showCreateNote ||
        editNoteId != null ||
        showImportCalendar ||
        showImportIcs ||
        showImportCsv ||
        pendingTaskCreation != null ||
        taskNotificationEvent != null ||
        accountOperation != null ||
        replacementPreview != null ||
        pendingGlobalPostSaveReminderCheck != null ||
        isSharedIcsIntakeBusy ||
        childModalBusyRoute == currentScreen

    LaunchedEffect(binding.accountId, binding.boundaryEpoch, isSharedIntakeUiBusy) {
        updateSharedTaskIntakeReadiness(
            accountId = binding.accountId,
            boundaryEpoch = binding.boundaryEpoch,
            isMounted = true,
            isUiBusy = isSharedIntakeUiBusy,
        )
    }

    DisposableEffect(binding.accountId, binding.boundaryEpoch) {
        onDispose {
            deactivateSharedTaskIntake(binding.accountId, binding.boundaryEpoch)
        }
    }

    LaunchedEffect(currentScreen, sharedTaskReviewId) {
        val reviewId = sharedTaskReviewId ?: return@LaunchedEffect
        if (currentScreen !is Screen.CreateTask) {
            completeSharedTaskReview(reviewId)
            sharedTaskReviewId = null
        }
    }

    val onSettingsClick = remember { { navController.navigate(Screen.Settings) } }

    LaunchedEffect(
        sharedPayloadEvent?.id,
        sharedIntakeStatus.revision,
        isSharedIntakeUiBusy,
        binding.accountId,
        binding.boundaryEpoch,
    ) {
        val event = sharedPayloadEvent ?: return@LaunchedEffect
        if (isSharedIntakeUiBusy ||
            !sharedIntakeStatus.isAppActive ||
            !sharedIntakeStatus.isMounted ||
            sharedIntakeStatus.accountId != binding.accountId ||
            sharedIntakeStatus.boundaryEpoch != binding.boundaryEpoch
        ) {
            return@LaunchedEffect
        }
        when (event) {
            is SharedTaskPayloadEvent.Rejected -> {
                val rejection = claimSharedTaskRejectionForReview(
                    event.id,
                    binding.accountId,
                    binding.boundaryEpoch,
                ) ?: return@LaunchedEffect
                snackbarScope.launchSharedTaskRejectionFeedback(event.id) {
                    val message = when (rejection.reason) {
                        ExternalInputFailure.TOO_LARGE -> getString(Res.string.shared_content_too_large)
                        ExternalInputFailure.TOO_MANY_ITEMS -> getString(Res.string.shared_content_too_many_items)
                        else -> getString(Res.string.shared_content_invalid)
                    }
                    snackbarHostState.showSnackbar(message)
                }
            }

            is SharedTaskPayloadEvent.Accepted -> {
                val payload = event.payload
                when {
                    payload.hasIcsContent -> {
                        val claimed = claimSharedIcsPayloadForReview(
                            payload.id,
                            binding.accountId,
                            binding.boundaryEpoch,
                        )
                        if (claimed != null && !appViewModel.requestSharedIcsImport(claimed)) {
                            completeSharedTaskReview(claimed.id)
                        }
                    }

                    payload.hasTaskContent -> {
                        val claimed = claimSharedTaskPayloadForReview(
                            payload.id,
                            binding.accountId,
                            binding.boundaryEpoch,
                        )
                        if (claimed != null) {
                            try {
                                navController.navigate(
                                    Screen.CreateTask(
                                        description = claimed.description,
                                        url = claimed.url,
                                    )
                                )
                                sharedTaskReviewId = claimed.id
                            } catch (error: Exception) {
                                completeSharedTaskReview(claimed.id)
                                throw error
                            }
                        }
                    }

                    else -> clearSharedTaskPayload(payload.id)
                }
            }
        }
    }

    LaunchedEffect(appViewModel) {
        appViewModel.sharedIcsImportResult.collect { result ->
            if (result == null || !appViewModel.consumeSharedIcsImportResult(result)) return@collect
            when (result) {
                is SharedIcsImportResult.Success -> {
                    snackbarHostState.showSnackbar(
                        getString(Res.string.import_success, result.importedCount),
                    )
                }

                is SharedIcsImportResult.Failed -> {
                    snackbarHostState.showSnackbar(getString(Res.string.import_failed_generic))
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        fun handleBack() {
            taskFormBackHandlerRegistry.handle { navController.popBackStack() }
        }

        fun registerTaskFormBackHandler(owner: Any, onBack: (() -> Unit)?) {
            taskFormBackHandlerRegistry.register(owner, onBack)
        }

        NavDisplay(
            backStack = backStack,
            onBack = { handleBack() },
            modifier = Modifier.fillMaxSize(),
            entryDecorators = listOf(
                rememberSaveableStateHolderNavEntryDecorator(),
                rememberViewModelStoreNavEntryDecorator<NavKey>(),
            ),
            entryProvider = entryProvider {
                entry<Screen.Matrix> {
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
                        syncEnabled = isRemoteSyncEnabled,
                        onRefresh = onPullToRefresh,
                        onTaskMutationFailure = onTaskMutationFailure,
                        onModalBusyChanged = { isBusy ->
                            onChildModalBusyChanged(Screen.Matrix, isBusy)
                        },
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
                        syncEnabled = isRemoteSyncEnabled,
                        onRefresh = onPullToRefresh,
                        onTaskMutationFailure = onTaskMutationFailure,
                        onModalBusyChanged = { isBusy ->
                            onChildModalBusyChanged(Screen.TaskList, isBusy)
                        },
                    )
                }

                entry<Screen.Calendar> {
                    val calendarViewModel: CalendarViewModel = koinViewModel()
                    CalendarScreen(
                        viewModel = calendarViewModel,
                        widgetNavigationEvent = calendarNavigationEvent,
                        onWidgetNavigationConsumed = onCalendarNavigationConsumed,
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
                        syncEnabled = isRemoteSyncEnabled,
                        onRefresh = onPullToRefresh,
                        onTaskMutationFailure = onTaskMutationFailure,
                        onModalBusyChanged = { isBusy ->
                            onChildModalBusyChanged(Screen.Calendar, isBusy)
                        },
                    )
                }

                entry<Screen.Notes> {
                    NotesScreen(
                        viewModel = noteViewModel,
                        onNoteClick = { note -> openEditNoteSheet(note.id) },
                        onSettingsClick = onSettingsClick,
                        isRefreshing = isRefreshing,
                        syncEnabled = isRemoteSyncEnabled,
                        onRefresh = onPullToRefresh,
                    )
                }

                entry<Screen.Settings> {
                    SettingsScreen(
                        onBack = { navController.popBackStack() },
                        onImportCalendar = { showImportCalendar = true },
                        onImportIcs = { showImportIcs = true },
                        onImportCsv = { showImportCsv = true },
                        currentAccount = currentAccount,
                        currentEndpoint = binding.canonicalEndpoint.takeIf { isRemoteSyncEnabled },
                        isLocalOnly = !isRemoteSyncEnabled,
                        accountOperation = accountOperation,
                        accountError = accountError,
                        onSwitchAccount = { email, password ->
                            authViewModel.switchAccount(email, password)
                        },
                        onClearAccountError = authViewModel::clearError,
                        onLogout = authViewModel::logout,
                        onClearLocalData = authViewModel::clearLocalData,
                        replacementPreview = replacementPreview,
                        onPrepareReplacement = authViewModel::prepareLocalServerReplacement,
                        onConfirmReplacement = authViewModel::confirmLocalServerReplacement,
                        onCancelReplacementPreparation = authViewModel::cancelLocalServerReplacementPreparation,
                        onModalBusyChanged = { isBusy ->
                            onChildModalBusyChanged(Screen.Settings, isBusy)
                        },
                    )
                }

                entry<Screen.QuadrantDetail> { screen ->
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
                            pendingTaskCreation = Screen.CreateTask(priorityOrdinal = taskPriority.ordinal)
                        },
                        onTaskMutationFailure = onTaskMutationFailure,
                        onModalBusyChanged = { isBusy ->
                            onChildModalBusyChanged(screen, isBusy)
                        },
                    )
                }

                entry<Screen.QuickAddTask> { screen ->
                    val quickAddViewModel: QuickAddTaskViewModel = koinViewModel(
                        key = quickAddTaskViewModelKey(screen),
                        parameters = { parametersOf(screen.creationContext()) },
                    )
                    val state by quickAddViewModel.uiState.collectAsState()
                    LaunchedEffect(quickAddViewModel, screen) {
                        quickAddViewModel.saveEvent.collect { event ->
                            val saveEvent = event ?: return@collect
                            if (!quickAddViewModel.consumeSaveEvent(saveEvent)) return@collect
                            when (saveEvent) {
                                is QuickAddTaskSaveEvent.Saved -> {
                                    if (backStack.lastOrNull() == screen) navController.popBackStack()
                                    if (saveEvent.postCommitWarning != null) {
                                        snackbarScope.launch {
                                            snackbarHostState.showSnackbar(taskMutationSavedWarning)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    QuickAddTaskScreen(
                        state = state,
                        onInputChanged = quickAddViewModel::onInputChanged,
                        onDismissToken = quickAddViewModel::dismissToken,
                        onBack = { navController.popBackStack() },
                        onAdd = quickAddViewModel::save,
                        onErrorShown = quickAddViewModel::clearError,
                    )
                }

                entry<Screen.CreateTask> { screen ->
                    val taskFormViewModel: TaskFormViewModel = koinViewModel(
                        key = taskFormViewModelKey(screen),
                    )
                    val categories by taskFormViewModel.categories.collectAsState()
                    val filteredCategories by taskFormViewModel.filteredCategories.collectAsState()
                    val categorySearchQuery by taskFormViewModel.categorySearchQuery.collectAsState()
                    val pendingImages by taskFormViewModel.pendingImages.collectAsState()
                    val isSaving by taskFormViewModel.isSaving.collectAsState()
                    LaunchedEffect(taskFormViewModel) {
                        taskFormViewModel.saveEvent.collect { event ->
                            val saveEvent = event ?: return@collect
                            if (!taskFormViewModel.consumeSaveEvent(saveEvent)) return@collect
                            when (saveEvent) {
                                is TaskFormSaveEvent.Saved -> {
                                    navController.popBackStack()
                                    if (saveEvent.postCommitWarning != null) {
                                        snackbarScope.launch {
                                            snackbarHostState.showSnackbar(taskMutationSavedWarning)
                                        }
                                    }
                                    requestPostSaveReminderCheck(saveEvent.formData)
                                }

                                is TaskFormSaveEvent.TaskCreatedWithImageError -> {
                                    navController.replaceTop(Screen.EditTask(saveEvent.taskId))
                                    snackbarScope.launch {
                                        snackbarHostState.showSnackbar(getString(Res.string.image_save_partial_failed))
                                        if (saveEvent.postCommitWarning != null) {
                                            snackbarHostState.showSnackbar(taskMutationSavedWarning)
                                        }
                                        requestPostSaveReminderCheck(saveEvent.formData)
                                    }
                                }

                                is TaskFormSaveEvent.ImagesFailed -> {
                                    if (saveEvent.postCommitWarning != null) {
                                        snackbarScope.launch {
                                            snackbarHostState.showSnackbar(taskMutationSavedWarning)
                                        }
                                    }
                                }

                                is TaskFormSaveEvent.Error -> {
                                    snackbarScope.launch {
                                        snackbarHostState.showSnackbar(getString(Res.string.task_save_failed))
                                    }
                                }

                                is TaskFormSaveEvent.StaleOccurrence -> {
                                    snackbarScope.launch {
                                        snackbarHostState.showSnackbar(getString(Res.string.task_save_failed))
                                    }
                                }

                                TaskFormSaveEvent.Missing -> {
                                    snackbarScope.launch {
                                        snackbarHostState.showSnackbar(taskNotFound)
                                    }
                                }

                                is TaskFormSaveEvent.DeleteError -> {
                                    snackbarScope.launch {
                                        snackbarHostState.showSnackbar(getString(Res.string.task_delete_failed))
                                    }
                                }

                                is TaskFormSaveEvent.Deleted -> {
                                    navController.popBackStack()
                                    if (saveEvent.postCommitWarning != null) {
                                        snackbarScope.launch {
                                            snackbarHostState.showSnackbar(taskMutationSavedWarning)
                                        }
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
                        currentDate = currentDate,
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
                        onBackRequestChanged = ::registerTaskFormBackHandler,
                        onExternalLaunchFailure = {
                            snackbarScope.launch {
                                snackbarHostState.showSnackbar(externalLaunchFailed)
                            }
                        },
                    )
                }

                entry<Screen.EditTask> { screen ->
                    val taskFormViewModel: TaskFormViewModel = koinViewModel(
                        key = taskFormViewModelKey(screen),
                    )
                    LaunchedEffect(taskFormViewModel) {
                        taskFormViewModel.saveEvent.collect { event ->
                            val saveEvent = event ?: return@collect
                            if (!taskFormViewModel.consumeSaveEvent(saveEvent)) return@collect
                            when (saveEvent) {
                                is TaskFormSaveEvent.Saved -> {
                                    navController.popBackStack()
                                    if (saveEvent.postCommitWarning != null) {
                                        snackbarScope.launch {
                                            snackbarHostState.showSnackbar(taskMutationSavedWarning)
                                        }
                                    }
                                    requestPostSaveReminderCheck(saveEvent.formData)
                                }

                                is TaskFormSaveEvent.TaskCreatedWithImageError -> Unit

                                is TaskFormSaveEvent.ImagesFailed -> {
                                    snackbarScope.launch {
                                        snackbarHostState.showSnackbar(getString(Res.string.image_save_partial_failed))
                                        if (saveEvent.postCommitWarning != null) {
                                            snackbarHostState.showSnackbar(taskMutationSavedWarning)
                                        }
                                        requestPostSaveReminderCheck(saveEvent.formData)
                                    }
                                }

                                is TaskFormSaveEvent.Error -> {
                                    snackbarScope.launch {
                                        snackbarHostState.showSnackbar(getString(Res.string.task_save_failed))
                                    }
                                }

                                is TaskFormSaveEvent.StaleOccurrence -> {
                                    snackbarScope.launch {
                                        snackbarHostState.showSnackbar(getString(Res.string.task_save_failed))
                                    }
                                }

                                TaskFormSaveEvent.Missing -> {
                                    snackbarScope.launch {
                                        snackbarHostState.showSnackbar(taskNotFound)
                                    }
                                }

                                is TaskFormSaveEvent.DeleteError -> {
                                    snackbarScope.launch {
                                        snackbarHostState.showSnackbar(getString(Res.string.task_delete_failed))
                                    }
                                }

                                is TaskFormSaveEvent.Deleted -> {
                                    navController.popBackStack()
                                    if (saveEvent.postCommitWarning != null) {
                                        snackbarScope.launch {
                                            snackbarHostState.showSnackbar(taskMutationSavedWarning)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    LaunchedEffect(screen.taskId) { taskFormViewModel.setTaskId(screen.taskId) }
                    val destinationState by taskFormViewModel.destinationState.collectAsState()
                    val editTaskImages by taskFormViewModel.editTaskImages.collectAsState()
                    val pendingImages by taskFormViewModel.pendingImages.collectAsState()
                    val categories by taskFormViewModel.categories.collectAsState()
                    val filteredCategories by taskFormViewModel.filteredCategories.collectAsState()
                    val categorySearchQuery by taskFormViewModel.categorySearchQuery.collectAsState()
                    val isSaving by taskFormViewModel.isSaving.collectAsState()
                    val pendingFormCompletion by taskFormViewModel.pendingFormCompletion.collectAsState()
                    val retainedFormDraft by taskFormViewModel.retainedFormDraft.collectAsState()
                    when (val state = destinationState) {
                        TaskFormDestinationState.Loading -> DestinationLoadingScreen()
                        TaskFormDestinationState.Missing -> MissingDestinationScreen(
                            message = taskNotFound,
                            onBack = { navController.popBackStack() },
                        )
                        is TaskFormDestinationState.Ready -> {
                            CreateTaskScreen(
                                onBack = { navController.popBackStack() },
                                editTask = state.task,
                                currentDate = currentDate,
                                categories = categories,
                                filteredCategories = filteredCategories,
                                categorySearchQuery = categorySearchQuery,
                                onCategorySearchQueryChange = {
                                    taskFormViewModel.setCategorySearchQuery(it)
                                },
                                onAddCategory = { name -> taskFormViewModel.addCategory(name) },
                                onSave = { formData ->
                                    taskFormViewModel.saveExistingTask(screen.taskId, formData)
                                },
                                retainedFormData = retainedFormDraft,
                                isSaving = isSaving,
                                existingImages = editTaskImages,
                                pendingImages = pendingImages,
                                onAddPendingImage = { taskFormViewModel.addPendingImage(it) },
                                onRemovePendingImage = { taskFormViewModel.removePendingImage(it) },
                                onDiscardPendingImages = { taskFormViewModel.discardPendingImages() },
                                confirmDiscardPendingImagesOnBack = true,
                                onBackRequestChanged = ::registerTaskFormBackHandler,
                                onRemoveTaskImage = { taskFormViewModel.removeTaskImage(it) },
                                onDelete = {
                                    taskFormViewModel.deleteTask(screen.taskId)
                                },
                                onExternalLaunchFailure = {
                                    snackbarScope.launch {
                                        snackbarHostState.showSnackbar(externalLaunchFailed)
                                    }
                                },
                            )
                            if (pendingFormCompletion != null) {
                                CompleteSeriesDialog(
                                    onCompleteOccurrence = taskFormViewModel::confirmPendingFormOccurrence,
                                    onCompleteSeries = taskFormViewModel::confirmPendingFormSeries,
                                    onDismiss = taskFormViewModel::dismissPendingFormCompletion,
                                    enabled = !isSaving,
                                )
                            }
                        }
                    }
                }

                entry<Screen.Countdown> {
                    val viewModel: CountdownViewModel = koinViewModel()
                    CountdownScreen(
                        viewModel = viewModel,
                        onCountdownClick = { countdown ->
                            navController.navigate(Screen.CountdownDetail(countdown.id))
                        },
                        onSettingsClick = onSettingsClick,
                    )
                }

                entry<Screen.CreateCountdown> { screen ->
                    val viewModel: CountdownFormViewModel = koinViewModel()
                    val isSaving by viewModel.isSaving.collectAsState()
                    LaunchedEffect(viewModel) {
                        viewModel.mutationEvent.collect { event ->
                            val mutationEvent = event ?: return@collect
                            if (!viewModel.consumeMutationEvent(mutationEvent)) return@collect
                            when (mutationEvent) {
                                is CountdownMutationEvent.Saved -> {
                                    navController.popBackStack()
                                    if (mutationEvent.postCommitWarning != null) {
                                        snackbarHostState.showSnackbar(countdownSavedWarning)
                                    }
                                }
                                is CountdownMutationEvent.Failed -> {
                                    snackbarHostState.showSnackbar(countdownSaveFailed)
                                }
                                is CountdownMutationEvent.Deleted -> Unit
                            }
                        }
                    }
                    val initialType =
                        CountdownType.entries.getOrElse(screen.typeOrdinal) { CountdownType.COUNTDOWN }
                    CreateCountdownScreen(
                        editCountdown = null,
                        initialType = initialType,
                        currentDate = currentDate,
                        onSave = { countdown -> viewModel.addCountdown(countdown) },
                        onBack = { navController.popBackStack() },
                        isSaving = isSaving,
                    )
                }

                entry<Screen.CountdownDetail> { screen ->
                    val viewModel: CountdownFormViewModel = koinViewModel()
                    LaunchedEffect(screen.countdownId) { viewModel.setCountdownId(screen.countdownId) }
                    LaunchedEffect(viewModel) {
                        viewModel.mutationEvent.collect { event ->
                            val mutationEvent = event ?: return@collect
                            if (!viewModel.consumeMutationEvent(mutationEvent)) return@collect
                            when (mutationEvent) {
                                is CountdownMutationEvent.Deleted -> {
                                    navController.popBackStack()
                                    if (mutationEvent.postCommitWarning != null) {
                                        snackbarHostState.showSnackbar(countdownSavedWarning)
                                    }
                                }
                                is CountdownMutationEvent.Failed -> {
                                    snackbarHostState.showSnackbar(countdownDeleteFailed)
                                }
                                is CountdownMutationEvent.Saved -> Unit
                            }
                        }
                    }
                    val destinationState by viewModel.destinationState.collectAsState()
                    when (val state = destinationState) {
                        CountdownDestinationState.Loading -> DestinationLoadingScreen()
                        CountdownDestinationState.Missing -> MissingDestinationScreen(
                            message = countdownNotFound,
                            onBack = { navController.popBackStack() },
                        )
                        is CountdownDestinationState.Ready -> CountdownDetailScreen(
                            countdown = state.occurrence,
                            onBack = { navController.popBackStack() },
                            onEdit = {
                                navController.navigate(Screen.EditCountdown(screen.countdownId))
                            },
                            onDelete = { viewModel.deleteCountdown(state.countdown) },
                            onModalBusyChanged = { isBusy ->
                                onChildModalBusyChanged(screen, isBusy)
                            },
                        )
                    }
                }

                entry<Screen.EditCountdown> { screen ->
                    val viewModel: CountdownFormViewModel = koinViewModel()
                    LaunchedEffect(screen.countdownId) { viewModel.setCountdownId(screen.countdownId) }
                    val isSaving by viewModel.isSaving.collectAsState()
                    LaunchedEffect(viewModel) {
                        viewModel.mutationEvent.collect { event ->
                            val mutationEvent = event ?: return@collect
                            if (!viewModel.consumeMutationEvent(mutationEvent)) return@collect
                            when (mutationEvent) {
                                is CountdownMutationEvent.Saved -> {
                                    navController.popBackStack()
                                    if (mutationEvent.postCommitWarning != null) {
                                        snackbarHostState.showSnackbar(countdownSavedWarning)
                                    }
                                }
                                is CountdownMutationEvent.Failed -> {
                                    snackbarHostState.showSnackbar(countdownSaveFailed)
                                }
                                is CountdownMutationEvent.Deleted -> Unit
                            }
                        }
                    }
                    val destinationState by viewModel.destinationState.collectAsState()
                    when (val state = destinationState) {
                        CountdownDestinationState.Loading -> DestinationLoadingScreen()
                        CountdownDestinationState.Missing -> MissingDestinationScreen(
                            message = countdownNotFound,
                            onBack = { navController.popBackStack() },
                        )
                        is CountdownDestinationState.Ready -> CreateCountdownScreen(
                            editCountdown = state.countdown,
                            initialType = state.countdown.countdownType,
                            currentDate = currentDate,
                            onSave = viewModel::updateCountdown,
                            onBack = { navController.popBackStack() },
                            isSaving = isSaving,
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
                calendarTodayDay = calendarTodayDay,
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
                        openCreateNoteSheet()
                    } else {
                        pendingTaskCreation = taskFabCreationScreen(
                            selectedTab = selectedTab,
                            selectedListId = selectedListId,
                            calendarDay = calendarSelectedDay,
                            calendarMonth = calendarSelectedMonth,
                            calendarYear = calendarSelectedYear,
                        )
                    }
                },
            )
        }
    }

    pendingTaskCreation?.let { creation ->
        val taskCreationSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        TaskCreationChoiceBottomSheet(
            sheetState = taskCreationSheetState,
            onDismiss = { pendingTaskCreation = null },
            onQuickAdd = {
                pendingTaskCreation = null
                navController.navigate(creation.asQuickAddTask())
            },
            onFullTask = {
                pendingTaskCreation = null
                navController.navigate(creation)
            },
        )
    }

    // Create note bottom sheet
    if (showCreateNote) {
        val createNoteSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        CreateNoteBottomSheet(
            sheetState = createNoteSheetState,
            requestToken = noteSheetRequestToken,
            mutationState = noteMutationState,
            onDismiss = ::retireOpenNoteSheet,
            onSave = { title, content ->
                noteViewModel.addNote(noteSheetRequestToken, title, content)
            },
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
        var retainedEditNote by remember(editNoteIdVal) { mutableStateOf<com.udnahc.opentasks.data.model.Note?>(null) }
        val ownedDeleteSnapshot = when (val state = noteMutationState) {
            is NoteMutationState.Busy -> state.operation as? NoteMutationOperation.Delete
            is NoteMutationState.Success -> state.operation as? NoteMutationOperation.Delete
            NoteMutationState.Idle,
            is NoteMutationState.Error,
            -> null
        }?.takeIf { operation ->
            operation.requestToken == noteSheetRequestToken && operation.note.id == editNoteIdVal
        }?.note
        val matchingMutationOperation = when (val state = noteMutationState) {
            is NoteMutationState.Busy -> state.operation
            is NoteMutationState.Success -> state.operation
            is NoteMutationState.Error -> state.operation
            NoteMutationState.Idle -> null
        }?.takeIf { operation -> operation.matchesOpenNoteSheet() }
        val noteForSheet = editNote ?: ownedDeleteSnapshot ?: retainedEditNote
        LaunchedEffect(
            editNoteIdVal,
            editNote,
            ownedDeleteSnapshot,
            matchingMutationOperation,
        ) {
            if (editNote != null) {
                hasObservedEditNote = true
                retainedEditNote = editNote
            } else if (
                hasObservedEditNote &&
                ownedDeleteSnapshot == null &&
                matchingMutationOperation == null
            ) {
                retireOpenNoteSheet()
            }
        }
        noteForSheet?.let { note ->
            val editNoteSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            CreateNoteBottomSheet(
                sheetState = editNoteSheetState,
                editNote = note,
                requestToken = noteSheetRequestToken,
                mutationState = noteMutationState,
                onDismiss = ::retireOpenNoteSheet,
                onSave = { title, content ->
                    noteViewModel.updateNote(
                        noteSheetRequestToken,
                        note.copy(title = title, content = content),
                    )
                },
                onDelete = {
                    noteViewModel.deleteNote(noteSheetRequestToken, note)
                },
            )
        }
    }

    if (taskNotificationEvent != null) {
        val taskNotificationUiState by taskNotificationViewModel.uiState.collectAsState()
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
                taskNotificationViewModel.markDone(
                    onTaskUpdated = onTaskNotificationWidgetsRefresh,
                    onResult = { mutation: CommittedMutation<TaskWriteResult> ->
                        taskNotificationViewModel.clearActionError()
                        val decision = taskNotificationSheetDecision(mutation)
                        if (decision.close) {
                            closeTaskNotificationSheet()
                        }
                        val feedback = decision.feedback
                        if (feedback != null) {
                            snackbarScope.launch {
                                snackbarHostState.showSnackbar(
                                    when (feedback) {
                                        TaskNotificationSheetFeedback.SAVED_WARNING -> notificationSavedWarning
                                        TaskNotificationSheetFeedback.OBSOLETE -> notificationObsolete
                                        TaskNotificationSheetFeedback.TASK_MISSING -> notificationTaskMissing
                                        TaskNotificationSheetFeedback.STALE -> notificationStale
                                    },
                                )
                            }
                        }
                    },
                )
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

    sharedIcsImportConfirmationId?.let { payloadId ->
        AlertDialog(
            onDismissRequest = { appViewModel.dismissSharedIcsImport(payloadId) },
            title = {
                Text(stringResource(Res.string.shared_ics_import_confirmation_title))
            },
            text = {
                Text(stringResource(Res.string.shared_ics_import_confirmation_message))
            },
            confirmButton = {
                TextButton(onClick = { appViewModel.confirmSharedIcsImport(payloadId) }) {
                    Text(stringResource(Res.string.import_button))
                }
            },
            dismissButton = {
                TextButton(onClick = { appViewModel.dismissSharedIcsImport(payloadId) }) {
                    Text(stringResource(Res.string.cancel))
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
        val pickIcsFile = rememberFileImportLauncher { result ->
            when (result) {
                is FileImportResult.Selected -> importIcsViewModel.importFromIcsContent(
                    result.file.name,
                    result.file.content,
                )
                FileImportResult.Cancelled -> Unit
                is FileImportResult.Error -> importIcsViewModel.fileSelectionFailed(
                    reason = result.reason,
                    detail = result.detail,
                )
            }
        }
        ImportIcsDialog(
            viewModel = importIcsViewModel,
            onPickFile = { pickIcsFile(ImportFileType.ICS) },
            onDismiss = { showImportIcs = false },
        )
    }

    // Import CSV (TickTick) dialog
    if (showImportCsv) {
        val importCsvViewModel: ImportCsvViewModel = koinViewModel()
        val pickCsvFile = rememberFileImportLauncher { result ->
            when (result) {
                is FileImportResult.Selected -> importCsvViewModel.importFromCsvContent(
                    result.file.name,
                    result.file.content,
                )
                FileImportResult.Cancelled -> Unit
                is FileImportResult.Error -> importCsvViewModel.fileSelectionFailed(
                    reason = result.reason,
                    detail = result.detail,
                )
            }
        }
        ImportCsvDialog(
            viewModel = importCsvViewModel,
            onPickFile = { pickCsvFile(ImportFileType.CSV) },
            onDismiss = { showImportCsv = false },
        )
    }
}

private fun maybeShowExactReminderSnackbar(
    formData: TaskFormData,
    checkNotificationPermissionUseCase: CheckNotificationPermissionUseCase,
    taskReminderEligibilityUseCase: TaskReminderEligibilityUseCase,
    snackbarHostState: SnackbarHostState,
    scope: kotlinx.coroutines.CoroutineScope,
) {
    if (!taskReminderEligibilityUseCase(formData)) return
    scope.launch {
        if (checkNotificationPermissionUseCase.exactReminderStatus() != ExactReminderPermissionStatus.NOT_GRANTED) {
            return@launch
        }
        val result = snackbarHostState.showSnackbar(
            message = org.jetbrains.compose.resources.getString(Res.string.exact_reminder_permission_message),
            actionLabel = org.jetbrains.compose.resources.getString(Res.string.open_settings),
        )
        if (result == SnackbarResult.ActionPerformed) {
            if (
                checkNotificationPermissionUseCase.openExactReminderSettings() ==
                ExternalLaunchResult.FAILURE
            ) {
                snackbarHostState.showSnackbar(
                    org.jetbrains.compose.resources.getString(Res.string.external_launch_failed)
                )
            }
        }
    }
}

@Composable
private fun BottomNavBar(
    tabs: List<BottomNavItem>,
    selectedTab: Int,
    calendarTodayDay: Int?,
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
                        CalendarTabIcon(item.iconRes, calendarTodayDay)
                    } else {
                        Icon(
                            painter = painterResource(item.iconRes),
                            contentDescription = null,
                            modifier = Modifier.size(OpenTasksTheme.dimens.iconNavBar),
                        )
                    }
                },
                label = { Text(stringResource(item.labelRes)) },
                alwaysShowLabel = true,
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
private fun CalendarTabIcon(
    iconRes: DrawableResource,
    todayDay: Int?,
) {
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
            text = todayDay?.toString().orEmpty(),
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
