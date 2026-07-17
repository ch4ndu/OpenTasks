package com.udnahc.opentasks.data.database

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(connection: SQLiteConnection) {
        connection.addColumnIfMissing("tasks", "pbId", "pbId TEXT DEFAULT NULL")
        connection.addColumnIfMissing("categories", "pbId", "pbId TEXT DEFAULT NULL")
        connection.addColumnIfMissing("categories", "updatedAt", "updatedAt INTEGER NOT NULL DEFAULT 0")
        connection.addColumnIfMissing("notes", "pbId", "pbId TEXT DEFAULT NULL")
        connection.execSQL("UPDATE categories SET updatedAt = createdAt WHERE updatedAt = 0")
    }
}

private fun SQLiteConnection.addColumnIfMissing(tableName: String, columnName: String, columnDefinition: String) {
    if (hasColumn(tableName, columnName)) return
    execSQL("ALTER TABLE $tableName ADD COLUMN $columnDefinition")
}

private fun SQLiteConnection.hasColumn(tableName: String, columnName: String): Boolean {
    val statement = prepare("PRAGMA table_info($tableName)")
    try {
        while (statement.step()) {
            if (statement.getText(1) == columnName) return true
        }
        return false
    } finally {
        statement.close()
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            """
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
        """.trimIndent()
        )
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

val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE tasks ADD COLUMN subtasks TEXT NOT NULL DEFAULT ''")
    }
}

val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("CREATE INDEX IF NOT EXISTS index_tasks_isDeleted_updatedAt ON tasks(isDeleted, updatedAt)")
        connection.execSQL("CREATE INDEX IF NOT EXISTS index_tasks_isDeleted_status_deadline ON tasks(isDeleted, status, deadline)")
        connection.execSQL("CREATE INDEX IF NOT EXISTS index_tasks_categoryId ON tasks(categoryId)")
        connection.execSQL("CREATE INDEX IF NOT EXISTS index_tasks_sourceExternalId ON tasks(sourceExternalId)")
        connection.execSQL("CREATE INDEX IF NOT EXISTS index_tasks_isSynced ON tasks(isSynced)")
        connection.execSQL("CREATE INDEX IF NOT EXISTS index_tasks_pbId ON tasks(pbId)")
        connection.execSQL("CREATE INDEX IF NOT EXISTS index_categories_isDeleted_sortOrder ON categories(isDeleted, sortOrder)")
        connection.execSQL("CREATE INDEX IF NOT EXISTS index_categories_name ON categories(name)")
        connection.execSQL("CREATE INDEX IF NOT EXISTS index_categories_isSynced ON categories(isSynced)")
        connection.execSQL("CREATE INDEX IF NOT EXISTS index_categories_pbId ON categories(pbId)")
        connection.execSQL("CREATE INDEX IF NOT EXISTS index_notes_isDeleted_updatedAt ON notes(isDeleted, updatedAt)")
        connection.execSQL("CREATE INDEX IF NOT EXISTS index_notes_isSynced ON notes(isSynced)")
        connection.execSQL("CREATE INDEX IF NOT EXISTS index_notes_pbId ON notes(pbId)")
        connection.execSQL("CREATE INDEX IF NOT EXISTS index_tags_isDeleted_name ON tags(isDeleted, name)")
        connection.execSQL("CREATE INDEX IF NOT EXISTS index_tags_name ON tags(name)")
        connection.execSQL("CREATE INDEX IF NOT EXISTS index_tags_isSynced ON tags(isSynced)")
        connection.execSQL("CREATE INDEX IF NOT EXISTS index_tags_pbId ON tags(pbId)")
        connection.execSQL("CREATE INDEX IF NOT EXISTS index_task_tags_isSynced ON task_tags(isSynced)")
        connection.execSQL("CREATE INDEX IF NOT EXISTS index_task_tags_pbId ON task_tags(pbId)")
        connection.execSQL("CREATE INDEX IF NOT EXISTS index_countdowns_isDeleted_targetDate ON countdowns(isDeleted, targetDate)")
        connection.execSQL("CREATE INDEX IF NOT EXISTS index_countdowns_isSynced ON countdowns(isSynced)")
        connection.execSQL("CREATE INDEX IF NOT EXISTS index_countdowns_pbId ON countdowns(pbId)")
    }
}

val MIGRATION_10_11 = object : Migration(10, 11) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `attachments` (
                `id` TEXT NOT NULL PRIMARY KEY,
                `ownerType` TEXT NOT NULL,
                `ownerId` TEXT NOT NULL,
                `kind` TEXT NOT NULL,
                `localPath` TEXT NOT NULL DEFAULT '',
                `thumbnailPath` TEXT NOT NULL DEFAULT '',
                `remoteFileName` TEXT,
                `mimeType` TEXT NOT NULL DEFAULT '',
                `fileName` TEXT NOT NULL DEFAULT '',
                `fileSizeBytes` INTEGER NOT NULL DEFAULT 0,
                `width` INTEGER NOT NULL DEFAULT 0,
                `height` INTEGER NOT NULL DEFAULT 0,
                `sortOrder` INTEGER NOT NULL DEFAULT 0,
                `syncState` TEXT NOT NULL DEFAULT 'LOCAL_ONLY',
                `lastSyncError` TEXT,
                `pbId` TEXT,
                `isSynced` INTEGER NOT NULL DEFAULT 0,
                `isDeleted` INTEGER NOT NULL DEFAULT 0,
                `createdAt` INTEGER NOT NULL DEFAULT 0,
                `updatedAt` INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
        connection.execSQL("CREATE INDEX IF NOT EXISTS index_attachments_ownerType_ownerId_kind_isDeleted_sortOrder ON attachments(ownerType, ownerId, kind, isDeleted, sortOrder)")
        connection.execSQL("CREATE INDEX IF NOT EXISTS index_attachments_isSynced ON attachments(isSynced)")
        connection.execSQL("CREATE INDEX IF NOT EXISTS index_attachments_syncState ON attachments(syncState)")
        connection.execSQL("CREATE INDEX IF NOT EXISTS index_attachments_pbId ON attachments(pbId)")
    }
}

val MIGRATION_11_12 = object : Migration(11, 12) {
    override fun migrate(connection: SQLiteConnection) {
        connection.addColumnIfMissing("tasks", "recurrenceAnchorDay", "recurrenceAnchorDay INTEGER DEFAULT NULL")
        connection.addColumnIfMissing("tasks", "completedAt", "completedAt INTEGER DEFAULT NULL")
    }
}
