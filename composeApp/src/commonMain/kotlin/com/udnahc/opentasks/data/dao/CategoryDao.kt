package com.udnahc.opentasks.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import com.udnahc.opentasks.data.model.Category
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {

    @Insert
    suspend fun insert(category: Category): Long

    @Update
    suspend fun update(category: Category)

    @Delete
    suspend fun delete(category: Category)

    @Query("SELECT * FROM categories WHERE isDeleted = 0 ORDER BY sortOrder ASC, id ASC")
    fun getAllCategories(): Flow<List<Category>>

    @Query("SELECT * FROM categories WHERE id = :id AND isDeleted = 0")
    suspend fun getCategoryById(id: String): Category?

    /** Unfiltered lookup including soft-deleted rows. For sync use only. */
    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun findCategoryByIdAnyState(id: String): Category?

    @Query("SELECT * FROM categories WHERE name = :name AND isDeleted = 0 LIMIT 1")
    suspend fun getCategoryByName(name: String): Category?

    @Query("SELECT * FROM categories WHERE isSynced = 0")
    suspend fun getUnsynced(): List<Category>

    @Query("UPDATE categories SET isSynced = 1 WHERE id = :id")
    suspend fun markSynced(id: String)

    @Query("UPDATE categories SET pbId = :pbId WHERE id = :id")
    suspend fun updatePbId(id: String, pbId: String)

    @Upsert
    suspend fun upsert(category: Category)

    @Query("SELECT * FROM categories")
    suspend fun getAllCategoriesOnce(): List<Category>

    @Query("DELETE FROM categories")
    suspend fun deleteAll()
}
