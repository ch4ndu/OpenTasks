package com.udnahc.opentasks.data.repository

import com.udnahc.opentasks.data.dao.CategoryDao
import com.udnahc.opentasks.data.model.Category
import kotlinx.coroutines.flow.Flow

class CategoryRepositoryImpl(
    private val categoryDao: CategoryDao
) : CategoryRepository {

    override fun getAllCategories(): Flow<List<Category>> = categoryDao.getAllCategories()

    override suspend fun getCategoryById(id: Long): Category? = categoryDao.getCategoryById(id)

    override suspend fun getCategoryByName(name: String): Category? = categoryDao.getCategoryByName(name)

    override suspend fun insert(category: Category): Long = categoryDao.insert(category)

    override suspend fun update(category: Category) = categoryDao.update(category)

    override suspend fun delete(category: Category) = categoryDao.delete(category)
}
