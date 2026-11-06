package com.udnahc.opentasks.domain.action.note

import com.udnahc.opentasks.data.repository.TagRepository
import com.udnahc.opentasks.domain.action.tag.AddTagAction

/** Called under the foreground mutation boundary, including lookup and creation. */
class GetOrCreateNoteTagAction(private val repository: TagRepository, private val addTag: AddTagAction) {
    suspend operator fun invoke(name: String): String {
        val trimmed = name.trim()
        require(trimmed.isNotEmpty())
        return repository.getTagByName(trimmed)?.id ?: addTag(trimmed)
    }
}
