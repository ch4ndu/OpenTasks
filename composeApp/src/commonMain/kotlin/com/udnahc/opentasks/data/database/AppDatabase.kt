package com.udnahc.opentasks.data.database

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.room.TypeConverters
import com.udnahc.opentasks.data.dao.NoteDao
import com.udnahc.opentasks.data.dao.TaskDao
import com.udnahc.opentasks.data.dao.TaskListDao
import com.udnahc.opentasks.data.model.Note
import com.udnahc.opentasks.data.model.Task
import com.udnahc.opentasks.data.model.TaskList

@Database(entities = [Task::class, TaskList::class, Note::class], version = 2)
@TypeConverters(Converters::class)
@ConstructedBy(AppDatabaseConstructor::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
    abstract fun taskListDao(): TaskListDao
    abstract fun noteDao(): NoteDao
}

const val DB_NAME = "opentasks.db"

// Room KSP generates the actual implementations for each platform
@Suppress("NO_ACTUAL_FOR_EXPECT")
expect object AppDatabaseConstructor : RoomDatabaseConstructor<AppDatabase>
