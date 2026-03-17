package com.udnahc.opentasks.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.udnahc.opentasks.data.model.TaskList
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskListDao {

    @Insert
    suspend fun insert(taskList: TaskList): Long

    @Update
    suspend fun update(taskList: TaskList)

    @Delete
    suspend fun delete(taskList: TaskList)

    @Query("SELECT * FROM task_lists ORDER BY sortOrder ASC, id ASC")
    fun getAllLists(): Flow<List<TaskList>>

    @Query("SELECT * FROM task_lists WHERE id = :id")
    suspend fun getListById(id: Long): TaskList?
}
