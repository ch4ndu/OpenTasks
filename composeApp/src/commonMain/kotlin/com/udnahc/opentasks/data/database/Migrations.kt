package com.udnahc.opentasks.data.database

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("""
            CREATE TABLE IF NOT EXISTS `countdowns` (
                `id` TEXT NOT NULL PRIMARY KEY,
                `title` TEXT NOT NULL,
                `targetDate` INTEGER NOT NULL,
                `countdownType` TEXT NOT NULL DEFAULT 'COUNTDOWN',
                `countingMode` TEXT NOT NULL DEFAULT 'COUNTDOWN',
                `reminders` TEXT NOT NULL DEFAULT '',
                `recurrenceType` TEXT NOT NULL DEFAULT 'NONE',
                `recurrenceInterval` INTEGER NOT NULL DEFAULT 1,
                `recurrenceDaysOfWeek` TEXT NOT NULL DEFAULT '',
                `smartListVisibility` TEXT NOT NULL DEFAULT 'ON_THE_DAY',
                `isCompleted` INTEGER NOT NULL DEFAULT 0,
                `pbId` TEXT,
                `isSynced` INTEGER NOT NULL DEFAULT 0,
                `isDeleted` INTEGER NOT NULL DEFAULT 0,
                `createdAt` INTEGER NOT NULL DEFAULT 0,
                `updatedAt` INTEGER NOT NULL DEFAULT 0
            )
        """.trimIndent())
    }
}

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE tags ADD COLUMN pbId TEXT DEFAULT NULL")
        connection.execSQL("ALTER TABLE tags ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
    }
}
