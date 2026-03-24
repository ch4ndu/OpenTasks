package com.udnahc.opentasks.data.repository

import com.udnahc.opentasks.data.dao.CategoryDao
import com.udnahc.opentasks.data.model.Category
import com.udnahc.opentasks.domain.action.settings.TriggerSyncAction
import kotlinx.coroutines.flow.Flow

class CategoryRepositoryImpl(
    private val categoryDao: CategoryDao,
    private val triggerSyncAction: TriggerSyncAction,
) : CategoryRepository {

    override fun getAllCategories(): Flow<List<Category>> = categoryDao.getAllCategories()

    override suspend fun getCategoryById(id: String): Category? = categoryDao.getCategoryById(id)

    override suspend fun getCategoryByName(name: String): Category? = categoryDao.getCategoryByName(name)

    override suspend fun insert(category: Category): Long {
        val result = categoryDao.insert(category)
        triggerSyncAction()
        return result
    }

    override suspend fun update(category: Category) {
        categoryDao.update(category)
        triggerSyncAction()
    }

    override suspend fun delete(category: Category) {
        categoryDao.delete(category)
        triggerSyncAction()
    }
}
