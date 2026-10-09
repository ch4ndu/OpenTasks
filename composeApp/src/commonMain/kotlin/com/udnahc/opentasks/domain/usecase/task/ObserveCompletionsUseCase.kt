package com.udnahc.opentasks.domain.usecase.task

import com.udnahc.opentasks.data.repository.CompletionRepository

class ObserveCompletionsUseCase(private val repository: CompletionRepository) {
    operator fun invoke() = repository.observeCompletions()
}
