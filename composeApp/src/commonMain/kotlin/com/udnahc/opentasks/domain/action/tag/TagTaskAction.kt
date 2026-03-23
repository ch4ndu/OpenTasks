package com.udnahc.opentasks.domain.action.tag

import com.udnahc.opentasks.data.model.TaskTag
import com.udnahc.opentasks.data.repository.TagRepository

class TagTaskAction(private val repository: TagRepository) {
    suspend operator fun invoke(taskId: Long, tagId: Long) {
        repository.insertTaskTag(TaskTag(taskId = taskId, tagId = tagId))
    }
}
