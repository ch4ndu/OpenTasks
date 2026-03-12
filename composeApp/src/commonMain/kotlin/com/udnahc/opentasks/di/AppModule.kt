package com.udnahc.opentasks.di

import com.udnahc.opentasks.data.database.AppDatabase
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
            .build()
    }
    single { get<AppDatabase>().taskDao() }
    single<TaskRepository> { TaskRepositoryImpl(get()) }
    viewModel { TaskViewModel(get()) }
}
