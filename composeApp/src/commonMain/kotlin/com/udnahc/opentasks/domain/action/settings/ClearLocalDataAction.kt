package com.udnahc.opentasks.domain.action.settings

import com.udnahc.opentasks.data.dao.AppSettingsDao
import com.udnahc.opentasks.data.dao.CategoryDao
import com.udnahc.opentasks.data.dao.NoteDao
import com.udnahc.opentasks.data.dao.TagDao
import com.udnahc.opentasks.data.dao.TaskDao
import com.udnahc.opentasks.data.model.Category

class ClearLocalDataAction(
    private val taskDao: TaskDao,
    private val categoryDao: CategoryDao,
    private val noteDao: NoteDao,
    private val tagDao: TagDao,
    private val appSettingsDao: AppSettingsDao,
) {
    suspend operator fun invoke() {
        // Delete in FK dependency order
        tagDao.deleteAllTaskTags()
        tagDao.deleteAllTags()
        taskDao.deleteAll()
        noteDao.deleteAll()
        categoryDao.deleteAll()
        appSettingsDao.deleteAll()

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
