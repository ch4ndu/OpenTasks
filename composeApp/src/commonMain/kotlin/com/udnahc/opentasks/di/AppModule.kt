package com.udnahc.opentasks.di

import androidx.room.RoomDatabase
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import com.udnahc.opentasks.data.database.AppDatabase
import com.udnahc.opentasks.data.repository.TaskListRepository
import com.udnahc.opentasks.data.repository.TaskListRepositoryImpl
import com.udnahc.opentasks.data.repository.TaskRepository
import com.udnahc.opentasks.data.repository.TaskRepositoryImpl
import com.udnahc.opentasks.viewmodel.TaskViewModel
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
    single<TaskRepository> { TaskRepositoryImpl(get()) }
    single<TaskListRepository> { TaskListRepositoryImpl(get()) }
    viewModel { TaskViewModel(get(), get()) }
}
