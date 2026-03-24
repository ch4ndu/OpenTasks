package com.udnahc.opentasks.domain.usecase.tag

import com.udnahc.opentasks.data.model.Tag
import com.udnahc.opentasks.data.repository.TagRepository
import kotlinx.coroutines.flow.Flow

class ObserveTagsForTaskUseCase(private val repository: TagRepository) {
    operator fun invoke(taskId: String): Flow<List<Tag>> = repository.getTagsForTask(taskId)
}
