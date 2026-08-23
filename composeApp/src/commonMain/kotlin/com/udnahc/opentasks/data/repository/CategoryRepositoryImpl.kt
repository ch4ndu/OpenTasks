package com.udnahc.opentasks.data.repository

import com.udnahc.opentasks.data.dao.CategoryDao
import com.udnahc.opentasks.data.extensions.localNow
import com.udnahc.opentasks.data.extensions.localToUtc
import com.udnahc.opentasks.data.extensions.utcToLocal
import com.udnahc.opentasks.data.model.Category
import com.udnahc.opentasks.data.auth.AccountMutationGate
import com.udnahc.opentasks.data.sync.SyncTrigger
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.lighthousegames.logging.logging

private val log = logging("CategoryRepository")

class CategoryRepositoryImpl(
    private val categoryDao: CategoryDao,
    private val syncTrigger: SyncTrigger,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val mutationGate: AccountMutationGate,
) : CategoryRepository {

    override fun getAllCategories(): Flow<List<Category>> =
        categoryDao.getAllCategories()
            .map { categories -> categories.map { it.withLocalTimestamps() } }
            .distinctUntilChanged()
            .flowOn(Dispatchers.Default)

    override suspend fun getCategoryById(id: String): Category? =
        withContext(ioDispatcher) { categoryDao.getCategoryById(id)?.withLocalTimestamps() }

    override suspend fun getCategoryByName(name: String): Category? =
        withContext(ioDispatcher) { categoryDao.getCategoryByName(name)?.withLocalTimestamps() }

    override suspend fun insert(category: Category): Long = mutationGate.withExclusive {
        log.v { "Inserting category: ${category.id}" }
        val result = withContext(ioDispatcher) {
            categoryDao.insert(category.withDefaultTimestamps().withUtcTimestamps())
        }
        syncTrigger.triggerSync()
        result
    }

    override suspend fun update(category: Category) = mutationGate.withExclusive {
        log.v { "Updating category: ${category.id}" }
        val committed = category.copy(
            isSynced = false,
            updatedAt = maxOf(localNow(), category.updatedAt),
        )
        withContext(ioDispatcher) {
            categoryDao.update(committed.withUtcTimestamps())
        }
        syncTrigger.triggerSync()
    }

    override suspend fun delete(category: Category) = mutationGate.withExclusive {
        log.v { "Soft-deleting category: ${category.id}" }
        val committed = category.copy(
            isDeleted = true,
            isSynced = false,
            updatedAt = maxOf(localNow(), category.updatedAt),
        )
        withContext(ioDispatcher) {
            categoryDao.update(committed.withUtcTimestamps())
        }
        syncTrigger.triggerSync()
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
