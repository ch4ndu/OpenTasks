package com.udnahc.opentasks.domain.attachment

import com.udnahc.opentasks.data.attachment.PickedImage
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Transfers retryable image input from the create result to the edit form. */
class PendingTaskImageHandoff {
    private val imagesByTaskId = mutableMapOf<String, List<PickedImage>>()
    private val mutex = Mutex()

    suspend fun put(taskId: String, images: List<PickedImage>) = mutex.withLock {
        if (images.isEmpty()) {
            imagesByTaskId.remove(taskId)
        } else {
            imagesByTaskId[taskId] = images
        }
    }

    suspend fun take(taskId: String): List<PickedImage> = mutex.withLock {
        imagesByTaskId.remove(taskId).orEmpty()
    }
}
