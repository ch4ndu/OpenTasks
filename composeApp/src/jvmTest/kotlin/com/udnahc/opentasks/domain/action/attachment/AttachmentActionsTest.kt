package com.udnahc.opentasks.domain.action.attachment

import com.udnahc.opentasks.data.attachment.AttachmentFilePolicy
import com.udnahc.opentasks.data.attachment.AttachmentImageDecodeException
import com.udnahc.opentasks.data.attachment.PickedImage
import com.udnahc.opentasks.data.auth.MutexAccountMutationGate
import com.udnahc.opentasks.data.model.AttachmentSyncState
import com.udnahc.opentasks.testutil.FakeAttachmentFileStorage
import com.udnahc.opentasks.testutil.FakeAttachmentRepository
import com.udnahc.opentasks.testutil.testAttachment
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AttachmentActionsTest {

    @Test
    fun addTaskImageDeletesStoredFilesWhenValidationFails() = runTest {
        val repository = FakeAttachmentRepository()
        val storage = FakeAttachmentFileStorage()
        val action = AddTaskImageAction(repository, storage, MutexAccountMutationGate())
        val fileName = "large.jpg"

        assertFailsWith<IllegalArgumentException> {
            action(
                taskId = "task",
                image = PickedImage(fileName, ByteArray(AttachmentFilePolicy.MAX_UPLOAD_BYTES.toInt() + 1)),
            )
        }

        assertFalse(storage.exists("/tmp/$fileName"))
        assertFalse(storage.exists("/tmp/thumb_$fileName"))
        assertTrue(repository.inserted.isEmpty())
    }

    @Test
    fun addTaskImageDeletesStoredFilesWhenInsertFails() = runTest {
        val repository = FakeAttachmentRepository().apply {
            insertError = IllegalStateException("insert failed")
        }
        val storage = FakeAttachmentFileStorage()
        val action = AddTaskImageAction(repository, storage, MutexAccountMutationGate())
        val fileName = "image.jpg"

        assertFailsWith<IllegalStateException> {
            action(taskId = "task", image = PickedImage(fileName, ByteArray(16)))
        }

        assertFalse(storage.exists("/tmp/$fileName"))
        assertFalse(storage.exists("/tmp/thumb_$fileName"))
        assertTrue(repository.inserted.isEmpty())
    }

    @Test
    fun addTaskImageDoesNotInsertWhenImageDecodeFails() = runTest {
        val repository = FakeAttachmentRepository()
        val storage = FakeAttachmentFileStorage().apply {
            storePickedImageError = AttachmentImageDecodeException()
        }
        val action = AddTaskImageAction(repository, storage, MutexAccountMutationGate())

        assertFailsWith<AttachmentImageDecodeException> {
            action(taskId = "task", image = PickedImage("corrupt.jpg", ByteArray(16)))
        }

        assertTrue(repository.inserted.isEmpty())
    }

    @Test
    fun removeTaskImageDeletesSyncedLocalFilesAfterTombstone() = runTest {
        val attachment = testAttachment(
            id = "synced-image",
            localPath = "/tmp/synced-image.jpg",
            thumbnailPath = "/tmp/synced-image-thumb.jpg",
            pbId = "pb-synced-image",
            syncState = AttachmentSyncState.SYNCED,
            isSynced = true,
        )
        val repository = FakeAttachmentRepository(listOf(attachment))
        val storage = FakeAttachmentFileStorage().apply {
            addFile(attachment.localPath)
            addFile(attachment.thumbnailPath)
        }
        val action = RemoveTaskImageAction(repository, storage, MutexAccountMutationGate())

        action(attachment)

        val tombstone = repository.updated.single()
        assertEquals(attachment.id, tombstone.id)
        assertTrue(tombstone.isDeleted)
        assertEquals(AttachmentSyncState.LOCAL_ONLY, tombstone.syncState)
        assertFalse(storage.exists(attachment.localPath))
        assertFalse(storage.exists(attachment.thumbnailPath))
    }
}
