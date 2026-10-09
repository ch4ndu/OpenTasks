package com.udnahc.opentasks.data.sync

import com.udnahc.opentasks.data.model.TaskCompletion
import com.udnahc.opentasks.data.sync.records.*
import com.udnahc.opentasks.testutil.testTask
import kotlinx.serialization.json.*
import kotlin.test.*

class CompletionRecordTest {
    @Test
    fun completionWirePayloadPreservesSnapshotAndExplicitEmptyOccurrence() {
        val event = TaskCompletion("entry", "deleted-task", "Original title", "deleted-list", "Original list", 42L, createdAt = 42L, updatedAt = 42L)
        val body = Json.parseToJsonElement(syncRecordJson.encodeToString(event.toTaskCompletionRecord())).jsonObject
        assertEquals(0L, body.getValue("occurrenceAt").jsonPrimitive.long)
        assertEquals(JsonObject(body - "id"), taskCompletionRecordPayloadSchema.project(body, requireId = false))
        val decoded = syncRecordJson.decodeFromJsonElement<TaskCompletionRecord>(body).toTaskCompletion()
        assertEquals(event.copy(pbId = decoded.pbId, isSynced = true), decoded)
        assertFailsWith<SyncRecordPayloadException> {
            taskCompletionRecordPayloadSchema.project(JsonObject(body - "taskId"), requireId = false)
        }
    }

    @Test
    fun taskNoteLinkRoundTripsAndClearsExplicitlyOnWire() {
        val linked = testTask().copy(sourceNoteId = "note-id")
        assertEquals("note-id", linked.toTaskRecord().toTask().sourceNoteId)
        val cleared = Json.parseToJsonElement(syncRecordJson.encodeToString(linked.copy(sourceNoteId = null).toTaskRecord())).jsonObject
        assertEquals("", cleared.getValue("sourceNoteId").jsonPrimitive.content)
    }
}
