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

val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE tasks ADD COLUMN isStarred INTEGER NOT NULL DEFAULT 0")
    }
}

val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE tasks ADD COLUMN section TEXT DEFAULT NULL")
    }
}

val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(connection: SQLiteConnection) {
        // Cannot use ALTER TABLE DROP COLUMN — requires SQLite 3.35.0+ (Android 14+).
        // Use the standard create-copy-drop-rename pattern for backwards compatibility.
        connection.execSQL(
            """
            CREATE TABLE IF NOT EXISTS tasks_new (
                id TEXT NOT NULL PRIMARY KEY,
                title TEXT NOT NULL,
                content TEXT NOT NULL,
                priority TEXT NOT NULL,
                deadline INTEGER,
                endDeadline INTEGER,
                notifyBeforeValue INTEGER NOT NULL,
                notifyBeforeUnit TEXT NOT NULL,
                recurrenceType TEXT NOT NULL,
                recurrenceInterval INTEGER NOT NULL,
                status TEXT NOT NULL DEFAULT 'TODO',
                isStarred INTEGER NOT NULL DEFAULT 0,
                section TEXT,
                isUrgent INTEGER NOT NULL,
                isImportant INTEGER NOT NULL,
                categoryId TEXT NOT NULL,
                isAllDay INTEGER NOT NULL,
                sourceExternalId TEXT,
                location TEXT NOT NULL,
                url TEXT NOT NULL,
                organizer TEXT NOT NULL,
                eventStatus TEXT NOT NULL,
                attendees TEXT NOT NULL,
                durationReminders TEXT NOT NULL,
                dateReminders TEXT NOT NULL,
                pbId TEXT,
                isSynced INTEGER NOT NULL,
                isDeleted INTEGER NOT NULL,
                createdAt INTEGER NOT NULL,
                updatedAt INTEGER NOT NULL
            )
            """.trimIndent()
        )
        connection.execSQL(
            """
            INSERT INTO tasks_new (
                id, title, content, priority, deadline, endDeadline,
                notifyBeforeValue, notifyBeforeUnit, recurrenceType, recurrenceInterval,
                status, isStarred, section, isUrgent, isImportant, categoryId, isAllDay,
                sourceExternalId, location, url, organizer, eventStatus, attendees,
                durationReminders, dateReminders, pbId, isSynced, isDeleted, createdAt, updatedAt
            )
            SELECT
                id, title, content, priority, deadline, endDeadline,
                notifyBeforeValue, notifyBeforeUnit, recurrenceType, recurrenceInterval,
                CASE WHEN isCompleted = 1 THEN 'DONE' ELSE 'TODO' END,
                isStarred, section, isUrgent, isImportant, categoryId, isAllDay,
                sourceExternalId, location, url, organizer, eventStatus, attendees,
                durationReminders, dateReminders, pbId, isSynced, isDeleted, createdAt, updatedAt
            FROM tasks
            """.trimIndent()
        )
        connection.execSQL("DROP TABLE tasks")
        connection.execSQL("ALTER TABLE tasks_new RENAME TO tasks")
    }
}

val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE task_tags ADD COLUMN pbId TEXT DEFAULT NULL")
        connection.execSQL("ALTER TABLE task_tags ADD COLUMN isSynced INTEGER NOT NULL DEFAULT 0")
        connection.execSQL("ALTER TABLE task_tags ADD COLUMN isDeleted INTEGER NOT NULL DEFAULT 0")
        connection.execSQL("ALTER TABLE task_tags ADD COLUMN createdAt INTEGER NOT NULL DEFAULT 0")
        connection.execSQL("ALTER TABLE task_tags ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
        connection.execSQL("UPDATE task_tags SET createdAt = CAST(strftime('%s', 'now') AS INTEGER) * 1000 WHERE createdAt = 0")
        connection.execSQL("UPDATE task_tags SET updatedAt = createdAt WHERE updatedAt = 0")
        connection.execSQL("CREATE INDEX IF NOT EXISTS index_task_tags_tagId ON task_tags(tagId)")
    }
}
