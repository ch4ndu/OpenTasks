package com.udnahc.opentasks.di

import androidx.room.RoomDatabase
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import com.udnahc.opentasks.data.database.AppDatabase
import com.udnahc.opentasks.data.repository.CategoryRepository
import com.udnahc.opentasks.data.repository.CategoryRepositoryImpl
import com.udnahc.opentasks.data.repository.NoteRepository
import com.udnahc.opentasks.data.repository.NoteRepositoryImpl
import com.udnahc.opentasks.data.repository.TaskRepository
import com.udnahc.opentasks.data.repository.TaskRepositoryImpl
import com.udnahc.opentasks.data.repository.TagRepository
import com.udnahc.opentasks.data.repository.TagRepositoryImpl
import com.udnahc.opentasks.domain.action.category.AddCategoryAction
import com.udnahc.opentasks.domain.action.tag.AddTagAction
import com.udnahc.opentasks.domain.action.tag.TagTaskAction
import com.udnahc.opentasks.domain.action.note.AddNoteAction
import com.udnahc.opentasks.domain.action.note.DeleteNoteAction
import com.udnahc.opentasks.domain.action.note.UpdateNoteAction
import com.udnahc.opentasks.domain.action.task.AddTaskAction
import com.udnahc.opentasks.domain.action.task.DeleteTaskAction
import com.udnahc.opentasks.domain.action.task.ToggleTaskCompleteAction
import com.udnahc.opentasks.domain.action.task.UpdateTaskAction
import com.udnahc.opentasks.domain.action.settings.ClearPocketBaseUrlAction
import com.udnahc.opentasks.domain.action.settings.InitializeSyncAction
import com.udnahc.opentasks.domain.action.settings.SavePocketBaseUrlAction
import com.udnahc.opentasks.domain.action.settings.TriggerSyncAction
import com.udnahc.opentasks.domain.usecase.category.ObserveAllCategoriesUseCase
import com.udnahc.opentasks.domain.usecase.note.ObserveAllNotesUseCase
import com.udnahc.opentasks.domain.usecase.task.ObserveAllTasksUseCase
import com.udnahc.opentasks.domain.usecase.task.ObserveTasksByDayUseCase
import com.udnahc.opentasks.domain.usecase.task.ObserveTasksByPriorityUseCase
import com.udnahc.opentasks.domain.usecase.task.ObserveTasksForCategoryUseCase
import com.udnahc.opentasks.domain.usecase.tag.ObserveTagsForTaskUseCase
import com.udnahc.opentasks.domain.usecase.task.ObserveTasksForPriorityUseCase
import com.udnahc.opentasks.domain.usecase.settings.ObservePocketBaseUrlUseCase
import com.udnahc.opentasks.data.calendar.CalendarProvider
import com.udnahc.opentasks.domain.action.task.ImportCalendarEventsAction
import com.udnahc.opentasks.domain.action.task.RescheduleAllRemindersAction
import com.udnahc.opentasks.domain.action.task.ScheduleTaskRemindersAction
import com.udnahc.opentasks.data.sync.PocketBaseClientProvider
import com.udnahc.opentasks.data.sync.SyncService
import com.udnahc.opentasks.viewmodel.SettingsViewModel
import com.udnahc.opentasks.viewmodel.AppViewModel
import com.udnahc.opentasks.viewmodel.CalendarViewModel
import com.udnahc.opentasks.viewmodel.ImportCalendarViewModel
import com.udnahc.opentasks.viewmodel.ImportIcsViewModel
import com.udnahc.opentasks.viewmodel.MatrixViewModel
import com.udnahc.opentasks.viewmodel.NoteViewModel
import com.udnahc.opentasks.viewmodel.TaskListViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

expect val platformModule: Module

val sharedModule = module {
    single<AppDatabase> {
        get<androidx.room.RoomDatabase.Builder<AppDatabase>>()
            .fallbackToDestructiveMigration(true)
            .setDriver(BundledSQLiteDriver())
            .addCallback(object : RoomDatabase.Callback() {
                override fun onCreate(connection: SQLiteConnection) {
                    super.onCreate(connection)
                    connection.execSQL(
                        "INSERT OR IGNORE INTO `categories` (`id`, `name`, `icon`, `sortOrder`, `createdAt`) VALUES ('00000000-0000-0000-0000-000000000001', 'Inbox', 'inbox', 0, 0)"
                    )
                }
            })
            .build()
    }
    single { get<AppDatabase>().taskDao() }
    single { get<AppDatabase>().categoryDao() }
    single { get<AppDatabase>().noteDao() }
    single<TaskRepository> { TaskRepositoryImpl(get(), get()) }
    single<CategoryRepository> { CategoryRepositoryImpl(get(), get()) }
    single<NoteRepository> { NoteRepositoryImpl(get(), get()) }
    single { get<AppDatabase>().tagDao() }
    single { get<AppDatabase>().appSettingsDao() }
    single<TagRepository> { TagRepositoryImpl(get()) }

    // UseCases
    single { ObserveAllTasksUseCase(get()) }
    single { ObserveTasksByPriorityUseCase(get()) }
    single { ObserveTasksByDayUseCase(get()) }
    factory { ObserveTasksForCategoryUseCase(get()) }
    factory { ObserveTasksForPriorityUseCase(get()) }
    single { ObserveAllCategoriesUseCase(get()) }
    single { ObserveAllNotesUseCase(get()) }
    factory { ObserveTagsForTaskUseCase(get()) }
    single { ObservePocketBaseUrlUseCase(get()) }

    // Actions
    single { AddTaskAction(get()) }
    single { UpdateTaskAction(get()) }
    single { DeleteTaskAction(get()) }
    single { ToggleTaskCompleteAction(get()) }
    single { AddCategoryAction(get()) }
    single { AddNoteAction(get()) }
    single { UpdateNoteAction(get()) }
    single { DeleteNoteAction(get()) }
    single { AddTagAction(get()) }
    single { TagTaskAction(get()) }
    single { ImportCalendarEventsAction(get(), get(), get(), get()) }
    single { ScheduleTaskRemindersAction(get()) }
    single { RescheduleAllRemindersAction(get(), get()) }
    single { SavePocketBaseUrlAction(get(), get(), get()) }
    single { ClearPocketBaseUrlAction(get(), get()) }
    single { TriggerSyncAction(get(), get()) }
    single { InitializeSyncAction(get(), get(), get()) }

    // Sync
    single { PocketBaseClientProvider() }
    single { SyncService(get(), get(), get(), get()) }

    // ViewModels
    viewModel { AppViewModel(get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get()) }
    viewModel { MatrixViewModel(get(), get(), get(), get(), get(), get()) }
    viewModel { TaskListViewModel(get(), get(), get(), get()) }
    viewModel { CalendarViewModel(get(), get(), get()) }
    viewModel { NoteViewModel(get(), get(), get(), get()) }
    viewModel { ImportCalendarViewModel(get(), get()) }
    viewModel { ImportIcsViewModel(get()) }
    viewModel { SettingsViewModel(get(), get(), get(), get()) }
}
