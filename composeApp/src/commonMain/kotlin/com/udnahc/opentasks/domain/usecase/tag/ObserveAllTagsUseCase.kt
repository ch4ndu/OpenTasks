package com.udnahc.opentasks.domain.usecase.tag

import com.udnahc.opentasks.data.repository.TagRepository

class ObserveAllTagsUseCase(private val repository: TagRepository) {
    operator fun invoke() = repository.getAllTags()
}
