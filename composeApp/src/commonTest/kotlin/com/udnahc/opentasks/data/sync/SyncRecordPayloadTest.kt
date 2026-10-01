package com.udnahc.opentasks.data.sync

import com.udnahc.opentasks.data.model.RecurrenceType
import com.udnahc.opentasks.data.sync.records.toCategoryRecord
import com.udnahc.opentasks.data.sync.records.toCountdownRecord
import com.udnahc.opentasks.data.sync.records.toNoteRecord
import com.udnahc.opentasks.data.sync.records.toTagRecord
import com.udnahc.opentasks.data.sync.records.toTaskRecord
import com.udnahc.opentasks.data.sync.records.toTaskTagRecord
import com.udnahc.opentasks.data.sync.records.toTag
import com.udnahc.opentasks.data.sync.records.toTask
import com.udnahc.opentasks.testutil.testCategory
import com.udnahc.opentasks.testutil.testCountdown
import com.udnahc.opentasks.testutil.testNote
import com.udnahc.opentasks.testutil.testTag
import com.udnahc.opentasks.testutil.testTask
import com.udnahc.opentasks.testutil.testTaskTag
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put

class SyncRecordPayloadTest {
    @Test
    fun ordinarySerializersEmitTheCompleteApplicationKeySet() {
        assertComplete(
            categoryRecordPayloadSchema,
            syncRecordJson.encodeToString(testCategory().toCategoryRecord()),
            setOf("localId", "name", "icon", "sortOrder", "isDeleted", "localCreatedAt", "localUpdatedAt"),
        )
        assertComplete(
            tagRecordPayloadSchema,
            syncRecordJson.encodeToString(testTag(color = null).toTagRecord()),
            setOf("localId", "name", "color", "isDeleted", "localCreatedAt", "localUpdatedAt"),
        )
        assertComplete(
            taskRecordPayloadSchema,
            syncRecordJson.encodeToString(testTask().toTaskRecord()),
            setOf(
                "localId", "title", "content", "subtasks", "priority", "deadline", "endDeadline",
                "notifyBeforeValue", "notifyBeforeUnit", "recurrenceType", "recurrenceInterval",
                "recurrenceAnchorDay", "status", "completedAt", "isStarred", "isShortlisted", "section", "isUrgent",
                "isImportant", "categoryId", "isAllDay", "sourceExternalId", "location", "url",
                "organizer", "eventStatus", "attendees", "durationReminders", "dateReminders", "isDeleted",
                "localCreatedAt", "localUpdatedAt",
            ),
        )
        assertComplete(
            taskTagRecordPayloadSchema,
            syncRecordJson.encodeToString(testTaskTag().toTaskTagRecord()),
            setOf("localId", "taskId", "tagId", "isDeleted", "localCreatedAt", "localUpdatedAt"),
        )
        assertComplete(
            noteRecordPayloadSchema,
            syncRecordJson.encodeToString(testNote().toNoteRecord()),
            setOf("localId", "title", "content", "isDeleted", "localCreatedAt", "localUpdatedAt"),
        )
        assertComplete(
            countdownRecordPayloadSchema,
            syncRecordJson.encodeToString(testCountdown().toCountdownRecord()),
            setOf(
                "localId", "title", "targetDate", "countdownType", "countingMode", "reminders",
                "recurrenceType", "recurrenceInterval", "recurrenceDaysOfWeek", "smartListVisibility",
                "isCompleted", "isDeleted", "localCreatedAt", "localUpdatedAt",
            ),
        )
    }

    @Test
    fun clearedValuesUseWireDefaultsAndRoundTripBackToApplicationEmpty() {
        val task = testTask().copy(
            subtasks = "",
            deadline = null,
            endDeadline = null,
            recurrenceType = RecurrenceType.NONE,
            recurrenceAnchorDay = null,
            completedAt = null,
            section = null,
            sourceExternalId = null,
        )
        val taskRecord = task.toTaskRecord()
        val taskJson = Json.parseToJsonElement(syncRecordJson.encodeToString(taskRecord)).jsonObject

        assertEquals(0L, taskJson.getValue("deadline").jsonPrimitive.long)
        assertEquals(0L, taskJson.getValue("endDeadline").jsonPrimitive.long)
        assertEquals(0L, taskJson.getValue("recurrenceAnchorDay").jsonPrimitive.long)
        assertEquals(0L, taskJson.getValue("completedAt").jsonPrimitive.long)
        assertEquals("", taskJson.getValue("section").jsonPrimitive.content)
        assertEquals("", taskJson.getValue("sourceExternalId").jsonPrimitive.content)
        assertEquals("", taskJson.getValue("subtasks").jsonPrimitive.content)
        assertEquals(task.copy(isSynced = true), taskRecord.toTask())

        val tagJson = Json.parseToJsonElement(
            syncRecordJson.encodeToString(testTag(color = null).toTagRecord()),
        ).jsonObject
        assertEquals("", tagJson.getValue("color").jsonPrimitive.content)
        assertEquals(null, testTag(color = null).toTagRecord().toTag().color)
    }

    @Test
    fun explicitNullAndNativeEmptySubtasksAreAcceptedButMissingOrUnsupportedValuesFail() {
        val body = Json.parseToJsonElement(syncRecordJson.encodeToString(testTask().toTaskRecord())).jsonObject
        val withNullSubtasks = buildJsonObject {
            body.forEach { (key, value) -> put(key, value) }
            put("id", JsonPrimitive("pb-task"))
            put("account", JsonPrimitive("account-a"))
            put("subtasks", JsonNull)
        }
        assertEquals("", taskRecordPayloadSchema.project(withNullSubtasks, requireId = true)["subtasks"]?.jsonPrimitive?.content)

        val missingTitle = JsonObject(withNullSubtasks.filterKeys { it != "title" })
        assertFailsWith<SyncRecordPayloadException> {
            taskRecordPayloadSchema.project(missingTitle, requireId = true)
        }

        val nativeEmptySubtasks = buildJsonObject {
            withNullSubtasks.forEach { (key, value) -> put(key, value) }
            put("subtasks", JsonArray(emptyList()))
        }
        assertEquals("", taskRecordPayloadSchema.project(nativeEmptySubtasks, requireId = true)["subtasks"]?.jsonPrimitive?.content)

        val unsupportedSubtasks = buildJsonObject {
            withNullSubtasks.forEach { (key, value) -> put(key, value) }
            put("subtasks", JsonPrimitive("not-an-array"))
        }
        assertFailsWith<SyncRecordPayloadException> {
            taskRecordPayloadSchema.project(unsupportedSubtasks, requireId = true)
        }
    }

    @Test
    fun wrongTypesUnknownEnumsAndMissingIdentityFailBeforeMutation() {
        val body = Json.parseToJsonElement(syncRecordJson.encodeToString(testTask().toTaskRecord())).jsonObject
        val withIdentity = buildJsonObject {
            body.forEach { (key, value) -> put(key, value) }
            put("id", JsonPrimitive("pb-task"))
        }

        val wrongTitleType = buildJsonObject {
            withIdentity.forEach { (key, value) -> put(key, value) }
            put("title", JsonPrimitive(7))
        }
        assertFailsWith<SyncRecordPayloadException> {
            taskRecordPayloadSchema.project(wrongTitleType, requireId = true)
        }

        val unknownPriority = buildJsonObject {
            withIdentity.forEach { (key, value) -> put(key, value) }
            put("priority", JsonPrimitive("UNKNOWN"))
        }
        assertFailsWith<SyncRecordPayloadException> {
            taskRecordPayloadSchema.project(unknownPriority, requireId = true)
        }

        val missingIdentity = JsonObject(withIdentity.filterKeys { it != "id" })
        assertFailsWith<SyncRecordPayloadException> {
            taskRecordPayloadSchema.project(missingIdentity, requireId = true)
        }
    }

    @Test
    fun serverEnvelopeIsIgnoredWhileApplicationKeysRemainComplete() {
        val body = Json.parseToJsonElement(syncRecordJson.encodeToString(testCategory().toCategoryRecord())).jsonObject
        val response = buildJsonObject {
            body.forEach { (key, value) -> put(key, value) }
            put("id", JsonPrimitive("pb-category"))
            put("account", JsonPrimitive("account-a"))
            put("collectionId", JsonPrimitive("categories"))
            put("collectionName", JsonPrimitive("categories"))
            put("created", JsonPrimitive("2026-09-10T00:00:00Z"))
            put("updated", JsonPrimitive("2026-09-10T00:00:00Z"))
        }
        val projected = categoryRecordPayloadSchema.project(response, requireId = true)
        assertEquals(body.keys - "id", projected.keys)
        assertFalse("id" in projected)
        assertTrue("account" !in projected)
    }

    @Test
    fun nonzeroNegativeValuesArePreserved() {
        val task = testTask().copy(
            deadline = -9L,
            endDeadline = -8L,
            recurrenceAnchorDay = -3,
            completedAt = -2L,
            section = "section",
            sourceExternalId = "external",
        )
        val record = task.toTaskRecord()
        assertEquals(-9L, record.deadline)
        assertEquals(-8L, record.endDeadline)
        assertEquals(-3, record.recurrenceAnchorDay)
        assertEquals(-2L, record.completedAt)
        assertEquals(task.copy(isSynced = true), record.toTask())
    }

    private fun assertComplete(
        schema: SyncRecordPayloadSchema,
        encoded: String,
        expectedKeys: Set<String>,
    ) {
        val encodedObject = Json.parseToJsonElement(encoded).jsonObject
        val body = JsonObject(encodedObject.filterKeys { it != "id" })
        assertEquals(expectedKeys, body.keys)
        assertEquals(expectedKeys, schema.project(body, requireId = false).keys)
        assertFalse("account" in body)
    }
}
