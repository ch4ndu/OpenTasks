package com.udnahc.opentasks.domain.action.tag

import com.udnahc.opentasks.data.extensions.utcNow
import com.udnahc.opentasks.data.model.Tag
import com.udnahc.opentasks.data.repository.TagRepository
import org.lighthousegames.logging.logging

private val log = logging("AddTagAction")

class AddTagAction(private val repository: TagRepository) {
    suspend operator fun invoke(name: String, color: String? = null): String {
        log.d { "Adding tag: '$name'" }
        val tag = Tag(
            name = name,
            color = color,
            createdAt = utcNow(),
        )
        repository.insertTag(tag)
        return tag.id
    }
}
