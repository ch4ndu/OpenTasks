package com.udnahc.opentasks.domain.action.tag

import com.udnahc.opentasks.data.model.TaskTag
import com.udnahc.opentasks.data.repository.TagRepository
import org.lighthousegames.logging.logging

private val log = logging("TagTaskAction")

class TagTaskAction(private val repository: TagRepository) {
    suspend operator fun invoke(taskId: String, tagId: String) {
        log.d { "Tagging task $taskId with tag $tagId" }
        repository.insertTaskTag(TaskTag(taskId = taskId, tagId = tagId))
    }
}
