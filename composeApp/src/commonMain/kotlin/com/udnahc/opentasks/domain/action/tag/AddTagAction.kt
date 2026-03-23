package com.udnahc.opentasks.domain.action.tag

import com.udnahc.opentasks.data.extensions.utcNow
import com.udnahc.opentasks.data.model.Tag
import com.udnahc.opentasks.data.repository.TagRepository

class AddTagAction(private val repository: TagRepository) {
    suspend operator fun invoke(name: String, color: String? = null): Long {
        return repository.insertTag(
            Tag(
                name = name,
                color = color,
                createdAt = utcNow(),
            )
        )
    }
}
