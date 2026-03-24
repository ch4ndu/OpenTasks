package com.udnahc.opentasks.data.sync

import com.udnahc.opentasks.data.dao.CategoryDao
import com.udnahc.opentasks.data.dao.NoteDao
import com.udnahc.opentasks.data.dao.TaskDao
import com.udnahc.opentasks.data.sync.records.TaskRecord
import com.udnahc.opentasks.data.sync.records.CategoryRecord
import com.udnahc.opentasks.data.sync.records.NoteRecord
import com.udnahc.opentasks.data.sync.records.toTaskRecord
import com.udnahc.opentasks.data.sync.records.toCategoryRecord
import com.udnahc.opentasks.data.sync.records.toNoteRecord
import com.udnahc.opentasks.data.sync.records.toTask
import com.udnahc.opentasks.data.sync.records.toCategory
import com.udnahc.opentasks.data.sync.records.toNote
import io.github.agrevster.pocketbaseKotlin.PocketbaseClient
import kotlinx.coroutines.sync.Mutex
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import org.lighthousegames.logging.logging

private val log = logging("SyncService")

class SyncService(
    private val pbProvider: PocketBaseClientProvider,
    private val taskDao: TaskDao,
    private val categoryDao: CategoryDao,
    private val noteDao: NoteDao,
) {
    private val syncMutex = Mutex()

    suspend fun syncAll() {
        val client = pbProvider.client ?: return
        if (!syncMutex.tryLock()) return
        try {
            pushCategories(client)
            pushTasks(client)
            pushNotes(client)
            pullCategories(client)
            pullTasks(client)
            pullNotes(client)
        } catch (e: Exception) {
            log.e { "Sync failed: ${e.message}" }
        } finally {
            syncMutex.unlock()
        }
    }

    // --- Push: local unsynced -> PocketBase ---

    private suspend fun pushTasks(client: PocketbaseClient) {
        val unsynced = taskDao.getUnsynced()
        for (task in unsynced) {
            try {
                val body = Json.encodeToString(task.toTaskRecord())
                if (task.isDeleted) {
                    val deleted = runCatching { client.records.delete("tasks", task.id) }
                    if (deleted.isSuccess) taskDao.delete(task)
                } else {
                    try {
                        client.records.update<TaskRecord>("tasks", task.id, body)
                    } catch (_: Exception) {
                        client.records.create<TaskRecord>("tasks", body)
                    }
                    taskDao.markSynced(task.id)
                }
            } catch (e: Exception) {
                log.e { "Failed to push task ${task.id}: ${e.message}" }
            }
        }
    }

    private suspend fun pushCategories(client: PocketbaseClient) {
        val unsynced = categoryDao.getUnsynced()
        for (category in unsynced) {
            try {
                val body = Json.encodeToString(category.toCategoryRecord())
                if (category.isDeleted) {
                    val deleted = runCatching { client.records.delete("categories", category.id) }
                    if (deleted.isSuccess) categoryDao.delete(category)
                } else {
                    try {
                        client.records.update<CategoryRecord>("categories", category.id, body)
                    } catch (_: Exception) {
                        client.records.create<CategoryRecord>("categories", body)
                    }
                    categoryDao.markSynced(category.id)
                }
            } catch (e: Exception) {
                log.e { "Failed to push category ${category.id}: ${e.message}" }
            }
        }
    }

    private suspend fun pushNotes(client: PocketbaseClient) {
        val unsynced = noteDao.getUnsynced()
        for (note in unsynced) {
            try {
                val body = Json.encodeToString(note.toNoteRecord())
                if (note.isDeleted) {
                    val deleted = runCatching { client.records.delete("notes", note.id) }
                    if (deleted.isSuccess) noteDao.delete(note)
                } else {
                    try {
                        client.records.update<NoteRecord>("notes", note.id, body)
                    } catch (_: Exception) {
                        client.records.create<NoteRecord>("notes", body)
                    }
                    noteDao.markSynced(note.id)
                }
            } catch (e: Exception) {
                log.e { "Failed to push note ${note.id}: ${e.message}" }
            }
        }
    }

    // --- Pull: PocketBase -> local ---

    private suspend fun pullTasks(client: PocketbaseClient) {
        try {
            val remoteRecords = client.records.getFullList<TaskRecord>("tasks", 200)
            for (record in remoteRecords) {
                val localTask = taskDao.getTaskById(record.localId)
                if (record.isDeleted) {
                    if (localTask != null) taskDao.delete(localTask)
                } else if (localTask == null || record.localUpdatedAt > localTask.updatedAt) {
                    taskDao.upsert(record.toTask())
                }
            }
            // Remove local synced tasks that no longer exist on server
            val remoteIds = remoteRecords.filter { !it.isDeleted }.map { it.localId }.toSet()
            for (local in taskDao.getAllTasksOnce()) {
                if (local.isSynced && local.id !in remoteIds && !local.isDeleted) {
                    taskDao.delete(local)
                }
            }
        } catch (e: Exception) {
            log.e { "Failed to pull tasks: ${e.message}" }
        }
    }

    private suspend fun pullCategories(client: PocketbaseClient) {
        try {
            val remoteRecords = client.records.getFullList<CategoryRecord>("categories", 200)
            for (record in remoteRecords) {
                val local = categoryDao.getCategoryById(record.localId)
                if (record.isDeleted) {
                    if (local != null) categoryDao.delete(local)
                } else if (local == null || record.localCreatedAt > local.createdAt) {
                    categoryDao.upsert(record.toCategory())
                }
            }
            // Remove local synced categories that no longer exist on server
            val remoteIds = remoteRecords.filter { !it.isDeleted }.map { it.localId }.toSet()
            for (local in categoryDao.getAllCategoriesOnce()) {
                if (local.isSynced && local.id !in remoteIds && !local.isDeleted) {
                    categoryDao.delete(local)
                }
            }
        } catch (e: Exception) {
            log.e { "Failed to pull categories: ${e.message}" }
        }
    }

    private suspend fun pullNotes(client: PocketbaseClient) {
        try {
            val remoteRecords = client.records.getFullList<NoteRecord>("notes", 200)
            for (record in remoteRecords) {
                val local = noteDao.getNoteById(record.localId)
                if (record.isDeleted) {
                    if (local != null) noteDao.delete(local)
                } else if (local == null || record.localUpdatedAt > local.updatedAt) {
                    noteDao.upsert(record.toNote())
                }
            }
            // Remove local synced notes that no longer exist on server
            val remoteIds = remoteRecords.filter { !it.isDeleted }.map { it.localId }.toSet()
            for (local in noteDao.getAllNotesOnce()) {
                if (local.isSynced && local.id !in remoteIds && !local.isDeleted) {
                    noteDao.delete(local)
                }
            }
        } catch (e: Exception) {
            log.e { "Failed to pull notes: ${e.message}" }
        }
    }
}
