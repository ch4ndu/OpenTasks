package com.udnahc.opentasks.viewmodel

import com.udnahc.opentasks.data.auth.AccountSessionState
import com.udnahc.opentasks.data.auth.FakeAccountRepository
import com.udnahc.opentasks.data.auth.MutexAccountMutationGate
import com.udnahc.opentasks.data.calendar.CalendarPermissionStatus
import com.udnahc.opentasks.data.calendar.CalendarProvider
import com.udnahc.opentasks.data.model.CalendarEvent
import com.udnahc.opentasks.data.model.Task
import com.udnahc.opentasks.data.notification.NotificationPermissionChecker
import com.udnahc.opentasks.data.repository.TaskRepository
import com.udnahc.opentasks.data.sync.PocketBaseClientProvider
import com.udnahc.opentasks.data.sync.SyncService
import com.udnahc.opentasks.domain.action.settings.ClearLocalDataAction
import com.udnahc.opentasks.domain.action.settings.SaveTextSizePreferenceAction
import com.udnahc.opentasks.domain.action.settings.SaveThemePreferenceAction
import com.udnahc.opentasks.domain.action.settings.SaveUiExperienceAction
import com.udnahc.opentasks.domain.action.settings.TriggerSyncAction
import com.udnahc.opentasks.domain.usecase.settings.CheckCalendarPermissionUseCase
import com.udnahc.opentasks.domain.usecase.settings.CheckNotificationPermissionUseCase
import com.udnahc.opentasks.domain.usecase.settings.ObserveTextSizePreferenceUseCase
import com.udnahc.opentasks.domain.usecase.settings.ObserveThemePreferenceUseCase
import com.udnahc.opentasks.domain.usecase.settings.ObserveUiExperienceUseCase
import com.udnahc.opentasks.domain.usecase.task.GenerateCsvExportUseCase
import com.udnahc.opentasks.domain.usecase.task.GenerateIcsExportUseCase
import com.udnahc.opentasks.testutil.FakeAppSettingsRepository
import com.udnahc.opentasks.testutil.FakeCategoryRepository
import com.udnahc.opentasks.testutil.FakeTaskRepository
import com.udnahc.opentasks.testutil.testTask
import com.udnahc.opentasks.ui.util.FileExportLauncher
import com.udnahc.opentasks.ui.util.FileExportRequest
import com.udnahc.opentasks.ui.util.FileExportResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsExportOwnershipTest : MainDispatcherRule() {
    @Test
    fun recreatedCallerCannotPrepareWhileOriginalNativeExportOrResultIsPending() = runTest(dispatcher) {
        val backingTasks = FakeTaskRepository(listOf(testTask(id = "one"), testTask(id = "two")))
        var reads = 0
        val tasks = object : TaskRepository by backingTasks {
            override suspend fun getAllTasksOnceUtc(): List<Task> {
                reads++
                return backingTasks.getAllTasksOnceUtc()
            }
        }
        val original = viewModel(tasks)
        val native = RetainedExportLauncher(original::onExportResult)
        original.prepareCsvExport(native)
        runCurrent()
        assertTrue(original.exportInProgress.value)
        assertTrue(native.isBusy)
        assertEquals(1, reads)

        // A new Settings ViewModel replaces the caller; the pending picker/write owner survives.
        val recreated = viewModel(tasks)
        native.onResult = recreated::onExportResult
        assertFalse(recreated.exportInProgress.value)
        recreated.prepareIcsExport(native)
        recreated.prepareCsvExport(native)
        runCurrent()
        assertEquals(1, reads)
        assertEquals(listOf("text/csv"), native.accepted.map { it.mimeType })
        assertTrue(native.isBusy || recreated.exportInProgress.value)

        // A completed write remains busy until the started caller consumes its terminal result.
        native.complete(FileExportResult.Completed(native.accepted.single().itemCount))
        recreated.prepareIcsExport(native)
        runCurrent()
        assertEquals(1, reads)
        assertTrue(native.deliver())
        assertEquals(ExportResult.Success(2), recreated.exportResult.value)
        assertFalse(native.isBusy || recreated.exportInProgress.value)

        recreated.prepareIcsExport(native)
        runCurrent()
        assertEquals(2, reads)
        assertEquals(listOf("text/csv", "text/calendar"), native.accepted.map { it.mimeType })
        assertTrue(recreated.exportInProgress.value)
        assertFalse(native.deliver()) // The prior completion cannot clear this new preparation.
        assertTrue(recreated.exportInProgress.value)
        native.complete(FileExportResult.Cancelled)
        assertTrue(native.deliver())
        assertFalse(recreated.exportInProgress.value)
        assertEquals(ExportResult.Idle, recreated.exportResult.value)
    }

    @Test
    fun rejectedNativeAdmissionReportsFailureAndDoesNotLeavePreparationHandedOff() = runTest(dispatcher) {
        val viewModel = viewModel(FakeTaskRepository(listOf(testTask(id = "one"))))
        val rejected = object : FileExportLauncher {
            override val isBusy = false
            override fun launch(request: FileExportRequest): Boolean = false
        }
        viewModel.prepareCsvExport(rejected)
        runCurrent()
        assertFalse(viewModel.exportInProgress.value)
        assertEquals(ExportResult.Error, viewModel.exportResult.value)

        viewModel.clearExportResult()
        viewModel.prepareIcsExport(rejected)
        runCurrent()
        assertFalse(viewModel.exportInProgress.value)
        assertEquals(ExportResult.Error, viewModel.exportResult.value)

        val accepted = RetainedExportLauncher(viewModel::onExportResult)
        viewModel.prepareCsvExport(accepted)
        runCurrent()
        assertEquals(1, accepted.accepted.size)
        assertTrue(viewModel.exportInProgress.value)
        accepted.complete(FileExportResult.Error())
        accepted.deliver()
        assertFalse(viewModel.exportInProgress.value)
    }

    @Test
    fun abandonmentDuringPreparationCannotLaunchOrClearTheReplacementExport() = runTest(dispatcher) {
        val releaseOldRead = CompletableDeferred<Unit>()
        val oldReadStarted = CompletableDeferred<Unit>()
        val backingTasks = FakeTaskRepository(listOf(testTask(id = "one"), testTask(id = "two")))
        var reads = 0
        val tasks = object : TaskRepository by backingTasks {
            override suspend fun getAllTasksOnceUtc(): List<Task> {
                reads++
                if (reads == 1) {
                    oldReadStarted.complete(Unit)
                    // A blocking/native read may finish after cancellation and a new preparation.
                    withContext(NonCancellable) { releaseOldRead.await() }
                }
                return backingTasks.getAllTasksOnceUtc()
            }
        }
        val viewModel = viewModel(tasks)
        val oldNative = RetainedExportLauncher(viewModel::onExportResult, viewModel::abandonExport)
        viewModel.prepareCsvExport(oldNative)
        runCurrent()
        assertTrue(oldReadStarted.isCompleted)
        assertTrue(viewModel.exportInProgress.value)
        assertFalse(oldNative.isBusy)

        oldNative.abandon()
        assertFalse(viewModel.exportInProgress.value)
        assertEquals(ExportResult.Idle, viewModel.exportResult.value)
        val replacement = RetainedExportLauncher(viewModel::onExportResult, viewModel::abandonExport)
        viewModel.prepareIcsExport(replacement)
        runCurrent()
        assertEquals(listOf("text/calendar"), replacement.accepted.map { it.mimeType })
        assertTrue(viewModel.exportInProgress.value)

        releaseOldRead.complete(Unit)
        runCurrent()
        assertTrue(oldNative.accepted.isEmpty())
        assertTrue(viewModel.exportInProgress.value)
        assertTrue(replacement.isBusy)
        assertEquals(ExportResult.Idle, viewModel.exportResult.value)
        replacement.complete(FileExportResult.Completed(replacement.accepted.single().itemCount))
        assertTrue(replacement.deliver())
        assertEquals(ExportResult.Success(2), viewModel.exportResult.value)
        assertFalse(viewModel.exportInProgress.value)
    }

    @Test
    fun acceptedNativeAbandonmentReleasesCallerAndRetiredResultsCannotFinishItsNextExport() = runTest(dispatcher) {
        val viewModel = viewModel(FakeTaskRepository(listOf(testTask(id = "one"))))
        val oldNative = RetainedExportLauncher(viewModel::onExportResult, viewModel::abandonExport)
        viewModel.prepareCsvExport(oldNative)
        runCurrent()
        assertTrue(oldNative.isBusy)
        assertTrue(viewModel.exportInProgress.value)

        // The native owner is retired before its abandonment callback resets the same retained VM.
        oldNative.abandon()
        assertFalse(viewModel.exportInProgress.value)
        assertEquals(ExportResult.Idle, viewModel.exportResult.value)
        val replacement = RetainedExportLauncher(viewModel::onExportResult, viewModel::abandonExport)
        viewModel.prepareIcsExport(replacement)
        runCurrent()
        oldNative.complete(FileExportResult.Completed(itemCount = 99))
        assertFalse(oldNative.deliver())
        assertTrue(viewModel.exportInProgress.value)
        assertTrue(replacement.isBusy)
        assertEquals(ExportResult.Idle, viewModel.exportResult.value)

        replacement.complete(FileExportResult.Cancelled)
        assertTrue(replacement.deliver())
        assertFalse(viewModel.exportInProgress.value)
        assertEquals(ExportResult.Idle, viewModel.exportResult.value)
    }

    private fun viewModel(tasks: TaskRepository): SettingsViewModel {
        val settings = FakeAppSettingsRepository()
        val provider = PocketBaseClientProvider()
        val mutationGate = MutexAccountMutationGate()
        return SettingsViewModel(
            observeUiExperience = ObserveUiExperienceUseCase(settings),
            observeThemePreference = ObserveThemePreferenceUseCase(settings),
            observeTextSizePreference = ObserveTextSizePreferenceUseCase(settings),
            triggerSyncAction = TriggerSyncAction(
                provider,
                SyncService(provider, emptyList(), accountMutationGate = mutationGate),
                coroutineDispatcher = dispatcher,
            ),
            saveUiExperienceAction = SaveUiExperienceAction(settings),
            saveThemePreferenceAction = SaveThemePreferenceAction(settings),
            saveTextSizePreferenceAction = SaveTextSizePreferenceAction(settings),
            clearLocalDataAction = ClearLocalDataAction(FakeAccountRepository(AccountSessionState.SignedOut, mutationGate)),
            checkNotificationPermission = CheckNotificationPermissionUseCase(NotificationPermissionChecker()),
            checkCalendarPermission = CheckCalendarPermissionUseCase(object : CalendarProvider {
                override fun isAvailable() = false
                override suspend fun checkPermission() = CalendarPermissionStatus.NOT_AVAILABLE
                override suspend fun requestPermission() = CalendarPermissionStatus.NOT_AVAILABLE
                override suspend fun fetchEvents(startUtcMillis: Long, endUtcMillis: Long): List<CalendarEvent> = emptyList()
            }),
            generateCsvExport = GenerateCsvExportUseCase(tasks, FakeCategoryRepository()),
            generateIcsExport = GenerateIcsExportUseCase(tasks),
            ioDispatcher = dispatcher,
        )
    }

    /** Exercises the caller against the native retained-owner contract without an OS picker fixture. */
    private class RetainedExportLauncher(
        var onResult: (FileExportResult) -> Unit,
        private val onAbandoned: () -> Unit = {},
    ) : FileExportLauncher {
        override var isBusy = false
            private set
        val accepted = mutableListOf<FileExportRequest>()
        private var terminal: FileExportResult? = null
        private var abandoned = false

        override fun launch(request: FileExportRequest): Boolean {
            if (abandoned || isBusy) return false
            isBusy = true
            accepted += request
            return true
        }

        fun complete(result: FileExportResult) {
            if (abandoned) return
            terminal = result
        }

        fun abandon() {
            abandoned = true
            terminal = null
            isBusy = false
            onAbandoned()
        }

        fun deliver(): Boolean {
            val result = terminal ?: return false
            terminal = null
            isBusy = false
            onResult(result)
            return true
        }
    }
}
