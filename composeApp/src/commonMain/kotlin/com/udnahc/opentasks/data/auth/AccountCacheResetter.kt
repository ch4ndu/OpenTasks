package com.udnahc.opentasks.data.auth

import com.udnahc.opentasks.data.attachment.AttachmentFileStorage
import com.udnahc.opentasks.data.database.AppDatabase
import com.udnahc.opentasks.data.model.AppConstants
import com.udnahc.opentasks.data.model.Category
import com.udnahc.opentasks.data.settings.AccountStateStore
import com.udnahc.opentasks.data.sync.SyncService
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext

data class LegacyCacheSnapshot(
    val unsyncedRowCount: Int,
    val isPristineInboxOnly: Boolean,
) {
    val hasUnsyncedRows: Boolean get() = unsyncedRowCount > 0
}

internal interface AccountCacheInspectorContract {
    suspend fun inspect(): LegacyCacheSnapshot
}

internal class AccountCacheInspector(
    private val database: AppDatabase,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : AccountCacheInspectorContract {
    override suspend fun inspect(): LegacyCacheSnapshot = withContext(ioDispatcher) {
        val categoryDao = database.categoryDao()
        val tagDao = database.tagDao()
        val taskDao = database.taskDao()
        val attachmentDao = database.attachmentDao()
        val noteDao = database.noteDao()
        val countdownDao = database.countdownDao()

        val unsyncedRowCount = categoryDao.getUnsynced().size +
            tagDao.getUnsynced().size +
            tagDao.getUnsyncedTaskTags().size +
            taskDao.getUnsynced().size +
            attachmentDao.getUnsynced().size +
            noteDao.getUnsynced().size +
            countdownDao.getUnsynced().size

        val categories = categoryDao.getAllCategoriesOnce()
        val isPristineInboxOnly = categories.singleOrNull()?.let { category ->
            category.id == AppConstants.DEFAULT_INBOX_ID &&
                category.name == "Inbox" &&
                category.icon == "inbox" &&
                category.sortOrder == 0 &&
                category.pbId == null &&
                !category.isSynced &&
                !category.isDeleted &&
                category.createdAt == 0L &&
                category.updatedAt == 0L
        } == true &&
            taskDao.getAllTasksOnce().isEmpty() &&
            tagDao.getAllTagsOnce().isEmpty() &&
            tagDao.getAllTaskTagsOnce().isEmpty() &&
            attachmentDao.getAllOnce().isEmpty() &&
            noteDao.getAllNotesOnce().isEmpty() &&
            countdownDao.getAllCountdownsOnce().isEmpty()

        LegacyCacheSnapshot(unsyncedRowCount, isPristineInboxOnly)
    }
}

internal interface AccountCacheResetterContract {
    suspend fun resetWithinMutation()

    suspend fun replaceCacheWithinMutation(
        binding: CacheBinding?,
        transition: AccountTransition?,
    )

    suspend fun clearAttachmentFilesWithinMutation()
}

/** Clears only account-owned content; installation-wide settings remain intact. */
internal class AccountCacheResetter(
    private val database: AppDatabase,
    private val attachmentFileStorage: AttachmentFileStorage,
    private val syncService: SyncService,
    private val mutationGate: AccountMutationGate,
    private val stateStore: AccountStateStore,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : AccountCacheResetterContract {
    suspend fun reset() {
        mutationGate.withExclusive { resetWithinMutation() }
    }

    override suspend fun resetWithinMutation() {
        syncService.runExclusiveResetWithinAccountMutation {
            withContext(ioDispatcher) {
                // A legacy reset has no established source binding to recover.
                // Clear files first so a later first login can never activate a
                // new account while files from the unbound cache remain.
                attachmentFileStorage.clearAll()
                replaceDatabaseAndPersist(binding = null, transition = null)
            }
        }
    }

    /**
     * Atomically installs the empty Room cache and its durable transition
     * marker. Callers then clear account-owned files as a distinct recoverable
     * step while task UI remains gated by the marker.
     */
    override suspend fun replaceCacheWithinMutation(
        binding: CacheBinding?,
        transition: AccountTransition?,
    ) {
        syncService.runExclusiveResetWithinAccountMutation {
            withContext(ioDispatcher) {
                replaceDatabaseAndPersist(binding, transition)
            }
        }
    }

    /** Retries the post-commit file boundary during transition recovery. */
    override suspend fun clearAttachmentFilesWithinMutation() {
        syncService.runExclusiveResetWithinAccountMutation {
            withContext(ioDispatcher) { attachmentFileStorage.clearAll() }
        }
    }

    private suspend fun replaceDatabaseAndPersist(
        binding: CacheBinding?,
        transition: AccountTransition?,
    ) {
        stateStore.replaceCacheAndPersist(binding, transition) {
            database.tagDao().deleteAllTaskTags()
            database.attachmentDao().deleteAll()
            database.countdownDao().deleteAll()
            database.tagDao().deleteAllTags()
            database.taskDao().deleteAll()
            database.noteDao().deleteAll()
            database.categoryDao().deleteAll()
            database.categoryDao().insert(
                Category(
                    id = AppConstants.DEFAULT_INBOX_ID,
                    name = "Inbox",
                    icon = "inbox",
                    sortOrder = 0,
                    createdAt = 0L,
                )
            )
        }
    }
}
