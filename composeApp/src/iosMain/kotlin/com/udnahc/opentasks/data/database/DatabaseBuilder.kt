package com.udnahc.opentasks.data.database

import androidx.room.Room
import androidx.room.RoomDatabase
import platform.Foundation.NSHomeDirectory

fun getDatabaseBuilder(): RoomDatabase.Builder<AppDatabase> {
    val dbFilePath = NSHomeDirectory() + "/Documents/$DB_NAME"
    return Room.databaseBuilder<AppDatabase>(
        name = dbFilePath
    )
}
