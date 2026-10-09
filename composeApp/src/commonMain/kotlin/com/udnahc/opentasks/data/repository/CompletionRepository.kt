package com.udnahc.opentasks.data.repository

import com.udnahc.opentasks.data.dao.TaskCompletionDao
import com.udnahc.opentasks.data.extensions.utcToLocal
import com.udnahc.opentasks.data.model.TaskCompletion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

interface CompletionRepository {
    fun observeCompletions(): Flow<List<TaskCompletion>>
}

class CompletionRepositoryImpl(private val dao: TaskCompletionDao) : CompletionRepository {
    override fun observeCompletions(): Flow<List<TaskCompletion>> = dao.getAllTaskCompletions()
        .map { rows -> rows.map { row -> row.copy(
            completedAt = utcToLocal(row.completedAt),
            occurrenceAt = row.occurrenceAt?.let(::utcToLocal),
            createdAt = utcToLocal(row.createdAt), updatedAt = utcToLocal(row.updatedAt),
        ) } }
        .distinctUntilChanged()
        .flowOn(Dispatchers.Default)
}
