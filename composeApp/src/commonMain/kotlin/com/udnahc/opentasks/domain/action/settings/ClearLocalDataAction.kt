package com.udnahc.opentasks.domain.action.settings

import com.udnahc.opentasks.data.dao.CategoryDao
import com.udnahc.opentasks.data.dao.NoteDao
import com.udnahc.opentasks.data.dao.TagDao
import com.udnahc.opentasks.data.dao.TaskDao
import com.udnahc.opentasks.data.model.Category
import com.udnahc.opentasks.data.repository.AppSettingsRepository
import org.lighthousegames.logging.logging

private val log = logging("ClearLocalDataAction")

class ClearLocalDataAction(
    private val taskDao: TaskDao,
    private val categoryDao: CategoryDao,
    private val noteDao: NoteDao,
    private val tagDao: TagDao,
    private val appSettingsRepository: AppSettingsRepository,
) {
    suspend operator fun invoke() {
        log.d { "Clearing all local data" }
        // Delete in FK dependency order
        // Uses DAOs directly to avoid triggering sync on bulk delete
        tagDao.deleteAllTaskTags()
        tagDao.deleteAllTags()
        taskDao.deleteAll()
        noteDao.deleteAll()
        categoryDao.deleteAll()
        appSettingsRepository.deleteAll()

        // Re-insert default Inbox category
        categoryDao.insert(
            Category(
                id = INBOX_ID,
                name = "Inbox",
                icon = "inbox",
                sortOrder = 0,
                createdAt = 0L,
            )
        )
    }

    companion object {
        private const val INBOX_ID = "00000000-0000-0000-0000-000000000001"
    }
}
