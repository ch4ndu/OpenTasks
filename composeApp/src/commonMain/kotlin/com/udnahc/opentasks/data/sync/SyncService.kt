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
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.encodeToString
import io.github.agrevster.pocketbaseKotlin.dsl.query.Filter
import org.lighthousegames.logging.logging

private val log = logging("SyncService")

class SyncService(
    private val pbProvider: PocketBaseClientProvider,
    private val taskDao: TaskDao,
    private val categoryDao: CategoryDao,
    private val noteDao: NoteDao,
) {
    private val syncMutex = Mutex()

    /** Strip the BaseModel `id` field from the JSON body to prevent PocketBase "pk_change" errors. */
    private fun stripId(jsonBody: String): String =
        buildJsonObject {
            Json.parseToJsonElement(jsonBody).jsonObject.entries.forEach { (key, value) ->
                if (key != "id") put(key, value)
            }
        }.toString()

    suspend fun syncAll() {
        val client = pbProvider.client ?: run {
            log.d { "Sync skipped: no PocketBase client" }
            return
        }
        if (!syncMutex.tryLock()) {
            log.d { "Sync skipped: another sync in progress" }
            return
        }
        try {
            log.d { "Sync started" }
            runCatching { pushCategories(client) }.onFailure { log.e { "Push categories failed: ${it.message}" } }
            runCatching { pushTasks(client) }.onFailure { log.e { "Push tasks failed: ${it.message}" } }
            runCatching { pushNotes(client) }.onFailure { log.e { "Push notes failed: ${it.message}" } }
            runCatching { pullCategories(client) }.onFailure { log.e { "Pull categories failed: ${it.message}" } }
            runCatching { pullTasks(client) }.onFailure { log.e { "Pull tasks failed: ${it.message}" } }
            runCatching { pullNotes(client) }.onFailure { log.e { "Pull notes failed: ${it.message}" } }
            log.d { "Sync completed" }
        } finally {
            syncMutex.unlock()
        }
    }

    // --- Push: local unsynced -> PocketBase ---

    private suspend fun pushTasks(client: PocketbaseClient) {
        val unsynced = taskDao.getUnsynced()
        log.d { "Pushing ${unsynced.size} tasks" }
        for (task in unsynced) {
            try {
                if (task.isDeleted) {
                    val pbId = task.pbId
                    if (pbId != null) {
                        val deleted = runCatching { client.records.delete("tasks", pbId) }
                        if (deleted.isSuccess) taskDao.delete(task)
                    } else {
                        taskDao.delete(task) // Never synced, just remove locally
                    }
                } else {
                    val body = stripId(Json.encodeToString(task.toTaskRecord()))
                    val pbId = task.pbId
                    if (pbId != null) {
                        client.records.update<TaskRecord>("tasks", pbId, body)
                        taskDao.markSynced(task.id)
                    } else {
                        val created = runCatching {
                            client.records.create<TaskRecord>("tasks", body)
                        }
                        if (created.isSuccess) {
                            created.getOrNull()?.id?.let { taskDao.updatePbId(task.id, it) }
                            taskDao.markSynced(task.id)
                        } else {
                            // Create failed — likely duplicate localId. Look up existing record.
                            val existing = runCatching {
                                client.records.getList<TaskRecord>("tasks", 1, 1, filterBy = Filter("localId='${task.id}'"))
                            }.getOrNull()?.items?.firstOrNull()
                            val serverId = existing?.id
                            if (serverId != null) {
                                taskDao.updatePbId(task.id, serverId)
                                client.records.update<TaskRecord>("tasks", serverId, body)
                                taskDao.markSynced(task.id)
                            } else {
                                log.e { "Failed to push task ${task.id}: ${created.exceptionOrNull()?.message}" }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                log.e { "Failed to push task ${task.id}: ${e.message}" }
            }
        }
    }

    private suspend fun pushCategories(client: PocketbaseClient) {
        val unsynced = categoryDao.getUnsynced()
        log.d { "Pushing ${unsynced.size} categories" }
        for (category in unsynced) {
            try {
                if (category.isDeleted) {
                    val pbId = category.pbId
                    if (pbId != null) {
                        val deleted = runCatching { client.records.delete("categories", pbId) }
                        if (deleted.isSuccess) categoryDao.delete(category)
                    } else {
                        categoryDao.delete(category)
                    }
                } else {
                    val body = stripId(Json.encodeToString(category.toCategoryRecord()))
                    val pbId = category.pbId
                    if (pbId != null) {
                        client.records.update<CategoryRecord>("categories", pbId, body)
                        categoryDao.markSynced(category.id)
                    } else {
                        val created = runCatching {
                            client.records.create<CategoryRecord>("categories", body)
                        }
                        if (created.isSuccess) {
                            created.getOrNull()?.id?.let { categoryDao.updatePbId(category.id, it) }
                            categoryDao.markSynced(category.id)
                        } else {
                            // Create failed — likely duplicate localId. Look up existing record.
                            val existing = runCatching {
                                client.records.getList<CategoryRecord>("categories", 1, 1, filterBy = Filter("localId='${category.id}'"))
                            }.getOrNull()?.items?.firstOrNull()
                            val serverId = existing?.id
                            if (serverId != null) {
                                categoryDao.updatePbId(category.id, serverId)
                                client.records.update<CategoryRecord>("categories", serverId, body)
                                categoryDao.markSynced(category.id)
                            } else {
                                log.e { "Failed to push category ${category.id}: ${created.exceptionOrNull()?.message}" }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                log.e { "Failed to push category ${category.id}: ${e.message}" }
            }
        }
    }

    private suspend fun pushNotes(client: PocketbaseClient) {
        val unsynced = noteDao.getUnsynced()
        log.d { "Pushing ${unsynced.size} notes" }
        for (note in unsynced) {
            try {
                if (note.isDeleted) {
                    val pbId = note.pbId
                    if (pbId != null) {
                        val deleted = runCatching { client.records.delete("notes", pbId) }
                        if (deleted.isSuccess) noteDao.delete(note)
                    } else {
                        noteDao.delete(note)
                    }
                } else {
                    val body = stripId(Json.encodeToString(note.toNoteRecord()))
                    val pbId = note.pbId
                    if (pbId != null) {
                        client.records.update<NoteRecord>("notes", pbId, body)
                        noteDao.markSynced(note.id)
                    } else {
                        val created = runCatching {
                            client.records.create<NoteRecord>("notes", body)
                        }
                        if (created.isSuccess) {
                            created.getOrNull()?.id?.let { noteDao.updatePbId(note.id, it) }
                            noteDao.markSynced(note.id)
                        } else {
                            // Create failed — likely duplicate localId. Look up existing record.
                            val existing = runCatching {
                                client.records.getList<NoteRecord>("notes", 1, 1, filterBy = Filter("localId='${note.id}'"))
                            }.getOrNull()?.items?.firstOrNull()
                            val serverId = existing?.id
                            if (serverId != null) {
                                noteDao.updatePbId(note.id, serverId)
                                client.records.update<NoteRecord>("notes", serverId, body)
                                noteDao.markSynced(note.id)
                            } else {
                                log.e { "Failed to push note ${note.id}: ${created.exceptionOrNull()?.message}" }
                            }
                        }
                    }
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
            log.d { "Pulled ${remoteRecords.size} tasks" }
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
            log.d { "Pulled ${remoteRecords.size} categories" }
            for (record in remoteRecords) {
                val local = categoryDao.getCategoryById(record.localId)
                if (record.isDeleted) {
                    if (local != null) categoryDao.delete(local)
                } else if (local == null || record.localUpdatedAt > local.updatedAt) {
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
            log.d { "Pulled ${remoteRecords.size} notes" }
            for (record in remoteRecords) {
                val local = noteDao.getNoteById(record.localId)
                if (record.isDeleted) {
                    if (local != null) noteDao.delete(local)
                } else if (local == null || record.localUpdatedAt > local.updatedAt) {
                    noteDao.upsert(record.toNote())
                    log.v { "Pulled note ${record.localId}: content length=${record.content.length}, has newlines=${'\n' in record.content}" }
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
