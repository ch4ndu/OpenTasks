package com.udnahc.opentasks.di

import androidx.room.Room
import androidx.room.RoomDatabase
import com.udnahc.opentasks.data.database.AppDatabase
import com.udnahc.opentasks.domain.attachment.PendingTaskImageHandoff
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
            }
        } finally {
            database?.close()
            application.close()
            databaseFile.delete()
        }
    }
}
