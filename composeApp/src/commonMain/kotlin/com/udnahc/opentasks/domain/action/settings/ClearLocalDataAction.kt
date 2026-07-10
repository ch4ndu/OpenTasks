package com.udnahc.opentasks.domain.action.settings

import androidx.room.immediateTransaction
import androidx.room.useWriterConnection
import com.udnahc.opentasks.data.attachment.AttachmentFileStorage
import com.udnahc.opentasks.data.database.AppDatabase
import com.udnahc.opentasks.data.model.Category
import com.udnahc.opentasks.data.sync.SyncService
import org.lighthousegames.logging.logging

private val log = logging("ClearLocalDataAction")

class ClearLocalDataAction(
    private val database: AppDatabase,
    private val attachmentFileStorage: AttachmentFileStorage,
    private val syncService: SyncService,
    private val triggerSyncAction: TriggerSyncAction,
) {
    suspend operator fun invoke() {
        log.d { "Clearing all local data" }
        syncService.runExclusiveReset(
            cancelPendingSync = triggerSyncAction::cancelPendingSync,
        ) {
            database.useWriterConnection { connection ->
                connection.immediateTransaction {
                    // Delete in FK dependency order using DAOs directly to avoid sync triggers.
                    database.tagDao().deleteAllTaskTags()
                    database.attachmentDao().deleteAll()
                    database.countdownDao().deleteAll()
                    database.tagDao().deleteAllTags()
                    database.taskDao().deleteAll()
                    database.noteDao().deleteAll()
                    database.categoryDao().deleteAll()
                    database.appSettingsDao().deleteAll()

                    // Re-insert the stable default used for import/export and sync lookup.
                    database.categoryDao().insert(
                        Category(
                            id = INBOX_ID,
                            name = "Inbox",
                            icon = "inbox",
                            sortOrder = 0,
                            createdAt = 0L,
                        )
                    )
                }
            }
            attachmentFileStorage.clearAll()
        }
    }

    companion object {
        private const val INBOX_ID = "00000000-0000-0000-0000-000000000001"
    }
}
