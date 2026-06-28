package com.udnahc.opentasks.domain.usecase.attachment

import com.udnahc.opentasks.data.model.ATTACHMENT_KIND_IMAGE
import com.udnahc.opentasks.data.model.ATTACHMENT_OWNER_TASK
import com.udnahc.opentasks.data.repository.AttachmentRepository

class ObserveTaskImagesUseCase(
    private val repository: AttachmentRepository,
) {
    operator fun invoke(taskId: String) =
        repository.observeForOwner(ATTACHMENT_OWNER_TASK, taskId, ATTACHMENT_KIND_IMAGE)
}
