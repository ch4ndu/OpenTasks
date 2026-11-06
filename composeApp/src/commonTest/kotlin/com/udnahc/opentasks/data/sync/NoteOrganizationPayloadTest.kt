package com.udnahc.opentasks.data.sync

import com.udnahc.opentasks.data.sync.records.*
import com.udnahc.opentasks.testutil.testNote
import kotlinx.serialization.json.*
import kotlin.test.*

class NoteOrganizationPayloadTest {
    @Test
    fun organizationRoundTripsAndClearedValuesAreExplicit() {
        val note = testNote().copy(isPinned = true, isArchived = true, tagIds = listOf("work", "personal"), linkedNoteIds = listOf("other"))
        val json = syncRecordJson.encodeToString(note.toNoteRecord())
        val raw = Json.parseToJsonElement(json).jsonObject
        noteRecordPayloadSchema.project(raw, requireId = false)
        val restored = syncRecordJson.decodeFromString<NoteRecord>(json).toNote()
        assertEquals(note.tagIds, restored.tagIds)
        assertEquals(note.linkedNoteIds, restored.linkedNoteIds)
        assertTrue(restored.isPinned && restored.isArchived)
        val cleared = Json.parseToJsonElement(syncRecordJson.encodeToString(testNote().toNoteRecord())).jsonObject
        assertEquals(JsonPrimitive(false), cleared["isArchived"])
        assertEquals(JsonPrimitive("[]"), cleared["tagIds"])
        val migrated = JsonObject(cleared + mapOf("tagIds" to JsonPrimitive(""), "linkedNoteIds" to JsonPrimitive("")))
        assertEquals(noteRecordPayloadSchema.project(cleared, false), noteRecordPayloadSchema.project(migrated, false))
        assertTrue(syncRecordJson.decodeFromJsonElement<NoteRecord>(migrated).tagIds.isEmpty())
    }

    @Test
    fun incompleteOrMalformedRemoteOrganizationFailsClosed() {
        val raw = Json.parseToJsonElement(syncRecordJson.encodeToString(testNote().toNoteRecord())).jsonObject
        assertFailsWith<SyncRecordPayloadException> { noteRecordPayloadSchema.project(JsonObject(raw - "isArchived"), false) }
        listOf(JsonNull, JsonPrimitive("[1]"), JsonPrimitive("[\"a\",\"a\"]"), JsonPrimitive("[\"\"]"), JsonPrimitive("{}"), JsonPrimitive(false)).forEach { invalid ->
            assertFailsWith<SyncRecordPayloadException> { noteRecordPayloadSchema.project(JsonObject(raw + ("tagIds" to invalid)), false) }
        }
    }
}
