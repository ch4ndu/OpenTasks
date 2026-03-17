package com.udnahc.opentasks.data.repository

import com.udnahc.opentasks.data.dao.TaskListDao
import com.udnahc.opentasks.data.model.TaskList
import kotlinx.coroutines.flow.Flow

class TaskListRepositoryImpl(
    private val taskListDao: TaskListDao
) : TaskListRepository {

    override fun getAllLists(): Flow<List<TaskList>> = taskListDao.getAllLists()

    override suspend fun getListById(id: Long): TaskList? = taskListDao.getListById(id)

    override suspend fun insert(taskList: TaskList): Long = taskListDao.insert(taskList)

    override suspend fun update(taskList: TaskList) = taskListDao.update(taskList)

    override suspend fun delete(taskList: TaskList) = taskListDao.delete(taskList)
}
