package com.udnahc.opentasks.data.database

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.room.TypeConverters
import com.udnahc.opentasks.data.dao.AppSettingsDao
import com.udnahc.opentasks.data.dao.CountdownDao
import com.udnahc.opentasks.data.dao.NoteDao
import com.udnahc.opentasks.data.dao.TagDao
import com.udnahc.opentasks.data.dao.TaskDao
import com.udnahc.opentasks.data.dao.CategoryDao
import com.udnahc.opentasks.data.model.AppSettings
import com.udnahc.opentasks.data.model.Category
import com.udnahc.opentasks.data.model.Countdown
import com.udnahc.opentasks.data.model.Note
import com.udnahc.opentasks.data.model.Tag
import com.udnahc.opentasks.data.model.Task
import com.udnahc.opentasks.data.model.TaskTag

@Database(entities = [Task::class, Category::class, Note::class, Tag::class, TaskTag::class, AppSettings::class, Countdown::class], version = 8)
@TypeConverters(Converters::class)
@ConstructedBy(AppDatabaseConstructor::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
    abstract fun categoryDao(): CategoryDao
    abstract fun noteDao(): NoteDao
    abstract fun tagDao(): TagDao
    abstract fun appSettingsDao(): AppSettingsDao
    abstract fun countdownDao(): CountdownDao
}

const val DB_NAME = "opentasks.db"

// Room KSP generates the actual implementations for each platform
@Suppress("NO_ACTUAL_FOR_EXPECT")
expect object AppDatabaseConstructor : RoomDatabaseConstructor<AppDatabase>
