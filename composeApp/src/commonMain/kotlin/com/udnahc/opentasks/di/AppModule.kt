package com.udnahc.opentasks.di

import androidx.room.RoomDatabase
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import com.udnahc.opentasks.data.database.AppDatabase
import com.udnahc.opentasks.data.repository.NoteRepository
import com.udnahc.opentasks.data.repository.NoteRepositoryImpl
import com.udnahc.opentasks.data.repository.TaskListRepository
import com.udnahc.opentasks.data.repository.TaskListRepositoryImpl
import com.udnahc.opentasks.data.repository.TaskRepository
import com.udnahc.opentasks.data.repository.TaskRepositoryImpl
import com.udnahc.opentasks.domain.action.note.AddNoteAction
import com.udnahc.opentasks.domain.action.note.DeleteNoteAction
import com.udnahc.opentasks.domain.action.note.UpdateNoteAction
import com.udnahc.opentasks.domain.action.task.AddTaskAction
import com.udnahc.opentasks.domain.action.task.DeleteTaskAction
import com.udnahc.opentasks.domain.action.task.ToggleTaskCompleteAction
import com.udnahc.opentasks.domain.action.task.UpdateTaskAction
import com.udnahc.opentasks.domain.action.tasklist.AddTaskListAction
import com.udnahc.opentasks.domain.usecase.note.ObserveAllNotesUseCase
import com.udnahc.opentasks.domain.usecase.task.ObserveAllTasksUseCase
import com.udnahc.opentasks.domain.usecase.task.ObserveTasksByDayUseCase
import com.udnahc.opentasks.domain.usecase.task.ObserveTasksByPriorityUseCase
import com.udnahc.opentasks.domain.usecase.task.ObserveTasksForListUseCase
import com.udnahc.opentasks.domain.usecase.task.ObserveTasksForPriorityUseCase
import com.udnahc.opentasks.domain.usecase.tasklist.ObserveAllTaskListsUseCase
import com.udnahc.opentasks.viewmodel.AppViewModel
import com.udnahc.opentasks.viewmodel.CalendarViewModel
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
                        "INSERT OR IGNORE INTO `task_lists` (`id`, `name`, `icon`, `sortOrder`, `createdAt`) VALUES (1, 'Inbox', 'inbox', 0, 0)"
                    )
                }
            })
            .build()
    }
    single { get<AppDatabase>().taskDao() }
    single { get<AppDatabase>().taskListDao() }
    single { get<AppDatabase>().noteDao() }
    single<TaskRepository> { TaskRepositoryImpl(get()) }
    single<TaskListRepository> { TaskListRepositoryImpl(get()) }
    single<NoteRepository> { NoteRepositoryImpl(get()) }

    // UseCases
    single { ObserveAllTasksUseCase(get()) }
    single { ObserveTasksByPriorityUseCase(get()) }
    single { ObserveTasksByDayUseCase(get()) }
    factory { ObserveTasksForListUseCase(get()) }
    factory { ObserveTasksForPriorityUseCase(get()) }
    single { ObserveAllTaskListsUseCase(get()) }
    single { ObserveAllNotesUseCase(get()) }

    // Actions
    single { AddTaskAction(get()) }
    single { UpdateTaskAction(get()) }
    single { DeleteTaskAction(get()) }
    single { ToggleTaskCompleteAction(get()) }
    single { AddTaskListAction(get()) }
    single { AddNoteAction(get()) }
    single { UpdateNoteAction(get()) }
    single { DeleteNoteAction(get()) }

    // ViewModels
    viewModel { AppViewModel(get(), get(), get(), get(), get(), get(), get(), get(), get()) }
    viewModel { MatrixViewModel(get(), get(), get(), get(), get(), get()) }
    viewModel { TaskListViewModel(get(), get(), get(), get()) }
    viewModel { CalendarViewModel(get(), get(), get()) }
    viewModel { NoteViewModel(get(), get(), get(), get()) }
}
