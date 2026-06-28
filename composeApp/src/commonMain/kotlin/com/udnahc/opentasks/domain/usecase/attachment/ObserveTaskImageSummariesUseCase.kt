package com.udnahc.opentasks.domain.usecase.attachment

import com.udnahc.opentasks.data.model.ATTACHMENT_OWNER_TASK
import com.udnahc.opentasks.data.model.AttachmentSummary
import com.udnahc.opentasks.data.repository.AttachmentRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ObserveTaskImageSummariesUseCase(
    private val repository: AttachmentRepository,
) {
    operator fun invoke(): Flow<Map<String, AttachmentSummary>> =
        repository.observeImageSummaries()
            .map { summaries ->
                summaries
                    .filter { it.ownerType == ATTACHMENT_OWNER_TASK }
                    .associateBy { it.ownerId }
            }
}
