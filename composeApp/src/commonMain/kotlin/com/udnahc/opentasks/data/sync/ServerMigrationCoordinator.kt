package com.udnahc.opentasks.data.sync

import androidx.room.immediateTransaction
import androidx.room.useWriterConnection
import com.udnahc.opentasks.data.database.AppDatabase
import com.udnahc.opentasks.data.model.AppSettings
import com.udnahc.opentasks.data.model.AppConstants
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext

/** Owns the one-transaction handoff from the old server identity to a validated candidate. */
class ServerMigrationCoordinator(
    private val database: AppDatabase,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    suspend fun classifyLocalStorage(): LocalStorageState = withContext(ioDispatcher) {
        val categories = database.categoryDao().getAllCategoriesOnce()
        val hasOnlyPristineInbox = categories.singleOrNull()?.let { category ->
            category.id == AppConstants.DEFAULT_INBOX_ID &&
                category.name == "Inbox" &&
                category.icon == "inbox" &&
                category.sortOrder == 0 &&
                category.pbId == null &&
                !category.isSynced &&
                !category.isDeleted &&
                category.createdAt == 0L &&
                category.updatedAt == 0L
        } == true
        val noOtherRows = database.taskDao().getAllTasksOnce().isEmpty() &&
            database.tagDao().getAllTagsOnce().isEmpty() &&
            database.tagDao().getAllTaskTagsOnce().isEmpty() &&
            database.attachmentDao().getAllOnce().isEmpty() &&
            database.noteDao().getAllNotesOnce().isEmpty() &&
            database.countdownDao().getAllCountdownsOnce().isEmpty()
        if (hasOnlyPristineInbox && noOtherRows) LocalStorageState.FRESH else LocalStorageState.NONEMPTY
    }

    suspend fun commit(endpoint: PocketBaseEndpoint, identity: String, mode: SyncMode) = withContext(ioDispatcher) {
        database.useWriterConnection { connection ->
            connection.immediateTransaction {
                val settings = database.appSettingsDao()
                settings.setValue(AppSettings(POCKETBASE_URL_KEY, endpoint.canonicalUrl))
                settings.setValue(AppSettings(SyncSettingsKeys.SERVER_INSTANCE_ID, identity))
                settings.setValue(AppSettings(SyncSettingsKeys.MODE, mode.name))
                if (mode == SyncMode.EMPTY_SERVER_SEED_PENDING) resetForSeed()
            }
        }
    }

    suspend fun hasProvenRemoteIdentity(inventory: PocketBaseServerInventory): Boolean = withContext(ioDispatcher) {
        val remoteIdsByLocalId = inventory.recordsByCollection.values
            .flatten()
            .mapNotNull { row ->
                val localId = row["localId"]?.toString()?.trim('"')
                val remoteId = row["id"]?.toString()?.trim('"')
                if (localId.isNullOrBlank() || remoteId.isNullOrBlank()) null else localId to remoteId
            }
            .toMap()
        database.categoryDao().getAllCategoriesOnce().any { remoteIdsByLocalId[it.id] == it.pbId } ||
            database.tagDao().getAllTagsOnce().any { remoteIdsByLocalId[it.id] == it.pbId } ||
            database.taskDao().getAllTasksOnce().any { remoteIdsByLocalId[it.id] == it.pbId } ||
            database.attachmentDao().getAllOnce().any { remoteIdsByLocalId[it.id] == it.pbId } ||
            database.noteDao().getAllNotesOnce().any { remoteIdsByLocalId[it.id] == it.pbId } ||
            database.countdownDao().getAllCountdownsOnce().any { remoteIdsByLocalId[it.id] == it.pbId } ||
            database.tagDao().getAllTaskTagsOnce().any { taskTag ->
                remoteIdsByLocalId["${taskTag.taskId}:${taskTag.tagId}"] == taskTag.pbId
            }
    }

    private suspend fun resetForSeed() {
        database.categoryDao().resetSyncMetadataForServerSeed()
        database.tagDao().resetTagSyncMetadataForServerSeed()
        database.taskDao().resetSyncMetadataForServerSeed()
        database.attachmentDao().resetSyncMetadataForServerSeed()
        database.tagDao().resetTaskTagSyncMetadataForServerSeed()
        database.noteDao().resetSyncMetadataForServerSeed()
        database.countdownDao().resetSyncMetadataForServerSeed()
    }

    companion object {
        private const val POCKETBASE_URL_KEY = "pocketbase_url"
    }
}

enum class LocalStorageState { FRESH, NONEMPTY }
