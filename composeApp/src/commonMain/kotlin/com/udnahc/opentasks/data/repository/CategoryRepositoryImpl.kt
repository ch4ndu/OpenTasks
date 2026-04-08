package com.udnahc.opentasks.data.repository

import com.udnahc.opentasks.data.dao.CategoryDao
import com.udnahc.opentasks.data.extensions.localNow
import com.udnahc.opentasks.data.extensions.localToUtc
import com.udnahc.opentasks.data.extensions.utcToLocal
import com.udnahc.opentasks.data.model.Category
import com.udnahc.opentasks.domain.action.settings.TriggerSyncAction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import org.lighthousegames.logging.logging

private val log = logging("CategoryRepository")

class CategoryRepositoryImpl(
    private val categoryDao: CategoryDao,
    private val triggerSyncAction: TriggerSyncAction,
) : CategoryRepository {

    override fun getAllCategories(): Flow<List<Category>> =
        categoryDao.getAllCategories()
            .map { categories -> categories.map { it.withLocalTimestamps() } }
            .flowOn(Dispatchers.Default)

    override suspend fun getCategoryById(id: String): Category? =
        categoryDao.getCategoryById(id)?.withLocalTimestamps()

    override suspend fun getCategoryByName(name: String): Category? =
        categoryDao.getCategoryByName(name)?.withLocalTimestamps()

    override suspend fun insert(category: Category): Long {
        log.v { "Inserting category: ${category.id}" }
        val result = categoryDao.insert(category.withDefaultTimestamps().withUtcTimestamps())
        triggerSyncAction()
        return result
    }

    override suspend fun update(category: Category) {
        log.v { "Updating category: ${category.id}" }
        categoryDao.update(category.withUtcTimestamps().copy(isSynced = false))
        triggerSyncAction()
    }

    override suspend fun delete(category: Category) {
        log.v { "Soft-deleting category: ${category.id}" }
        categoryDao.update(category.withUtcTimestamps().copy(isDeleted = true, isSynced = false))
        triggerSyncAction()
    }

    private fun Category.withDefaultTimestamps(): Category {
        val now = localNow()
        return copy(
            createdAt = if (createdAt == 0L) now else createdAt,
            updatedAt = if (updatedAt == 0L) now else updatedAt,
        )
    }

    /** Converts UTC timestamps from the database to local time for presentation. */
    private fun Category.withLocalTimestamps() = copy(
        createdAt = utcToLocal(createdAt),
        updatedAt = utcToLocal(updatedAt),
    )

    /** Converts local-shifted timestamps to UTC for database storage. */
    private fun Category.withUtcTimestamps() = copy(
        createdAt = localToUtc(createdAt),
        updatedAt = localToUtc(updatedAt),
    )
}
