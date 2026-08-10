package com.udnahc.opentasks.di

import androidx.room.Room
import androidx.room.RoomDatabase
import com.udnahc.opentasks.data.auth.AccountBoundaryExecutor
import com.udnahc.opentasks.data.database.AppDatabase
import com.udnahc.opentasks.domain.attachment.PendingTaskImageHandoff
import com.udnahc.opentasks.domain.action.countdown.AddCountdownAction
import com.udnahc.opentasks.domain.action.countdown.DeleteCountdownAction
import com.udnahc.opentasks.domain.action.countdown.UpdateCountdownAction
import com.udnahc.opentasks.domain.action.task.AddTaskAction
import com.udnahc.opentasks.domain.action.task.DeleteTaskAction
import com.udnahc.opentasks.domain.action.task.ImportCalendarEventsAction
import com.udnahc.opentasks.domain.action.task.ImportCsvTasksAction
import com.udnahc.opentasks.domain.action.task.ToggleTaskCompleteAction
import com.udnahc.opentasks.domain.action.task.UpdateTaskAction
import com.udnahc.opentasks.domain.action.task.UpdateTaskStatusAction
import com.udnahc.opentasks.domain.usecase.task.GenerateCsvExportUseCase
import com.udnahc.opentasks.domain.usecase.task.GenerateIcsExportUseCase
import com.udnahc.opentasks.domain.usecase.task.TaskDueTextProvider
import com.udnahc.opentasks.viewmodel.AppViewModel
import com.udnahc.opentasks.viewmodel.ImportCalendarViewModel
import com.udnahc.opentasks.viewmodel.ImportCsvViewModel
import com.udnahc.opentasks.viewmodel.ImportIcsViewModel
import com.udnahc.opentasks.viewmodel.MatrixViewModel
import com.udnahc.opentasks.viewmodel.SettingsViewModel
import com.udnahc.opentasks.viewmodel.TaskFormViewModel
import com.udnahc.opentasks.viewmodel.TaskListViewModel
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertSame

@OptIn(ExperimentalCoroutinesApi::class)
class WaveSixDiResolutionTest {
    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUpMainDispatcher() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun resetMainDispatcher() {
        Dispatchers.resetMain()
    }

    @Test
    fun waveSixDomainBindingsAndAffectedViewModelsResolve() {
        val databaseFile = File.createTempFile("opentasks-wave-six-di", ".db")
        val application = koinApplication {
            modules(
                platformModule,
                sharedModule,
                module {
                    single<RoomDatabase.Builder<AppDatabase>> {
                        Room.databaseBuilder<AppDatabase>(name = databaseFile.absolutePath)
                    }
                },
            )
        }
        var database: AppDatabase? = null

        try {
            with(application.koin) {
                database = get()
                get<TaskDueTextProvider>()
                get<PendingTaskImageHandoff>()
                get<GenerateCsvExportUseCase>()
                get<GenerateIcsExportUseCase>()
                get<TaskListViewModel>()
                get<MatrixViewModel>()
                get<TaskFormViewModel>()
                get<ImportCsvViewModel>()
                get<ImportIcsViewModel>()
                get<ImportCalendarViewModel>()
                get<SettingsViewModel>()
                get<AppViewModel>()

                val foregroundExecutor = get<AccountBoundaryExecutor>()
                assertSame(foregroundExecutor, get<AddTaskAction>().accountBoundaryExecutor)
                assertSame(foregroundExecutor, get<UpdateTaskAction>().accountBoundaryExecutor)
                assertSame(foregroundExecutor, get<ToggleTaskCompleteAction>().accountBoundaryExecutor)
                assertSame(foregroundExecutor, get<UpdateTaskStatusAction>().accountBoundaryExecutor)
                assertSame(foregroundExecutor, get<DeleteTaskAction>().accountBoundaryExecutor)
                assertSame(foregroundExecutor, get<ImportCsvTasksAction>().accountBoundaryExecutor)
                assertSame(foregroundExecutor, get<ImportCalendarEventsAction>().accountBoundaryExecutor)
                assertSame(foregroundExecutor, get<AddCountdownAction>().accountBoundaryExecutor)
                assertSame(foregroundExecutor, get<UpdateCountdownAction>().accountBoundaryExecutor)
                assertSame(foregroundExecutor, get<DeleteCountdownAction>().accountBoundaryExecutor)
            }
        } finally {
            database?.close()
            application.close()
            databaseFile.delete()
        }
    }
}
