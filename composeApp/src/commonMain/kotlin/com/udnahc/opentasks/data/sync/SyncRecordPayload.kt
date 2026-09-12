package com.udnahc.opentasks.data.sync

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.long

/**
 * Sync payloads must include default values because PocketBase PATCH requests
 * merge fields that are present and retain fields that are omitted.  Keep this
 * Json instance local to the sync boundary; the rest of the application keeps
 * its existing serialization defaults.
 */
internal val syncRecordJson = Json {
    encodeDefaults = true
    explicitNulls = true
}

internal enum class SyncRecordFieldKind {
    String,
    Number,
    Boolean,
    JsonString,
}

internal data class SyncRecordField(
    val kind: SyncRecordFieldKind,
    val nullableAsEmpty: Boolean = false,
    val allowedValues: Set<String> = emptySet(),
    val emptyEnumDefault: String? = null,
    val requireNonBlank: Boolean = false,
)

internal class SyncRecordPayloadException(message: String) : IllegalArgumentException(message)

private const val INVALID_SUBTASK_ARRAY_MESSAGE = "tasks.subtasks has an invalid array value"
private val subtaskKeys = setOf("id", "text", "isChecked")

/** Normalizes the legacy string wire form and the native JSON-array form. */
internal object SubtaskArrayStringSerializer : KSerializer<String?> {
    override val descriptor = PrimitiveSerialDescriptor("SubtaskArrayString", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: String?) {
        encoder.encodeString(normalizeSubtaskArrayText(value.orEmpty()))
    }

    override fun deserialize(decoder: Decoder): String? {
        val jsonDecoder = decoder as? JsonDecoder
            ?: return normalizeSubtaskArrayText(decoder.decodeString())
        return normalizeSubtaskArrayElement(jsonDecoder.decodeJsonElement())
    }
}

internal fun normalizeSubtaskArrayElement(value: JsonElement): String = when (value) {
    JsonNull -> ""
    is JsonArray -> canonicalSubtaskArray(value)
    is JsonPrimitive -> if (value.isString) {
        normalizeSubtaskArrayText(value.content)
    } else {
        invalidSubtaskArray()
    }
    else -> invalidSubtaskArray()
}

private fun normalizeSubtaskArrayText(value: String): String {
    if (value.isBlank()) return ""
    val parsed = runCatching { Json.parseToJsonElement(value) }
        .getOrElse { invalidSubtaskArray() }
    return if (parsed is JsonArray) canonicalSubtaskArray(parsed) else invalidSubtaskArray()
}

private fun canonicalSubtaskArray(value: JsonArray): String {
    if (value.isEmpty()) return ""
    val canonical = value.map { item ->
        val objectValue = item as? JsonObject ?: invalidSubtaskArray()
        if (objectValue.keys != subtaskKeys) invalidSubtaskArray()
        val id = objectValue["id"].requiredSubtaskString()
        val text = objectValue["text"].requiredSubtaskString()
        val isChecked = objectValue["isChecked"].requiredSubtaskBoolean()
        buildJsonObject {
            put("id", id)
            put("text", text)
            put("isChecked", isChecked)
        }
    }
    return JsonArray(canonical).toString()
}

private fun JsonElement?.requiredSubtaskString(): String {
    val primitive = this as? JsonPrimitive
        ?: invalidSubtaskArray()
    if (!primitive.isString) invalidSubtaskArray()
    return primitive.content
}

private fun JsonElement?.requiredSubtaskBoolean(): Boolean {
    val primitive = this as? JsonPrimitive
        ?: invalidSubtaskArray()
    if (primitive.isString || primitive.booleanOrNull == null) invalidSubtaskArray()
    return primitive.boolean
}

private fun invalidSubtaskArray(): Nothing = throw SyncRecordPayloadException(INVALID_SUBTASK_ARRAY_MESSAGE)

/**
 * Projects raw PocketBase JSON to the complete application payload.  The
 * projection deliberately ignores only PocketBase's documented envelope
 * fields; missing application keys and unsupported shapes remain failures.
 */
class SyncRecordPayloadSchema internal constructor(
    private val collectionName: String,
    private val fields: Map<String, SyncRecordField>,
    private val legacyExtraKeys: Set<String> = emptySet(),
) {
    private val envelopeFields = setOf("account", "collectionId", "collectionName", "created", "updated")

    fun project(
        raw: JsonObject,
        requireId: Boolean,
        ignoredFields: Set<String> = emptySet(),
    ): JsonObject {
        val allowedKeys = fields.keys + envelopeFields + "id" + legacyExtraKeys
        val unknownKeys = raw.keys - allowedKeys
        if (unknownKeys.isNotEmpty()) {
            throw SyncRecordPayloadException(
                "$collectionName record contains unknown application keys: ${unknownKeys.sorted().joinToString(",")}",
            )
        }
        if (requireId) {
            val id = (raw["id"] ?: throw SyncRecordPayloadException("$collectionName record is missing id"))
                .asText("id")
            if (id.isBlank()) {
                throw SyncRecordPayloadException("$collectionName record has a blank id")
            }
        }
        return buildJsonObject {
            fields.forEach { (name, field) ->
                if (name in ignoredFields) return@forEach
                val value = raw[name]
                    ?: throw SyncRecordPayloadException("$collectionName record is missing application key $name")
                put(name, field.normalize(collectionName, name, value))
            }
        }
    }

    private fun SyncRecordField.normalize(
        collectionName: String,
        name: String,
        value: JsonElement,
    ): JsonPrimitive {
        if (value is JsonNull) {
            if (!nullableAsEmpty) {
                throw SyncRecordPayloadException("$collectionName.$name cannot be null")
            }
            return JsonPrimitive("")
        }
        if (kind == SyncRecordFieldKind.JsonString) {
            return JsonPrimitive(normalizeSubtaskArrayElement(value))
        }
        val primitive = value as? JsonPrimitive
            ?: throw SyncRecordPayloadException("$collectionName.$name has an unsupported JSON shape")
        return when (kind) {
            SyncRecordFieldKind.String,
            SyncRecordFieldKind.JsonString,
            -> {
                if (!primitive.isString) {
                    throw SyncRecordPayloadException("$collectionName.$name must be a JSON string")
                }
                val content = primitive.content
                if (requireNonBlank && content.isBlank()) {
                    throw SyncRecordPayloadException("$collectionName.$name must be nonblank")
                }
                val normalizedContent = if (content.isEmpty() && emptyEnumDefault != null) {
                    emptyEnumDefault
                } else {
                    content
                }
                if (allowedValues.isNotEmpty() && normalizedContent !in allowedValues) {
                    val shape = if (content.isEmpty()) "empty" else "non-empty"
                    throw SyncRecordPayloadException(
                        "$collectionName.$name has an unknown $shape enum value",
                    )
                }
                JsonPrimitive(normalizedContent)
            }

            SyncRecordFieldKind.Number -> {
                if (primitive.isString || primitive.longOrNull == null) {
                    throw SyncRecordPayloadException("$collectionName.$name must be an integer JSON number")
                }
                JsonPrimitive(primitive.long)
            }

            SyncRecordFieldKind.Boolean -> {
                if (primitive.isString || primitive.booleanOrNull == null) {
                    throw SyncRecordPayloadException("$collectionName.$name must be a JSON boolean")
                }
                JsonPrimitive(primitive.boolean)
            }
        }
    }
}

private fun stringField(
    allowedValues: Set<String> = emptySet(),
    emptyEnumDefault: String? = null,
    requireNonBlank: Boolean = false,
) = SyncRecordField(
    kind = SyncRecordFieldKind.String,
    allowedValues = allowedValues,
    emptyEnumDefault = emptyEnumDefault,
    requireNonBlank = requireNonBlank,
)

private fun nullableStringField() = SyncRecordField(
    kind = SyncRecordFieldKind.String,
    nullableAsEmpty = true,
)

private fun jsonStringField() = SyncRecordField(
    kind = SyncRecordFieldKind.JsonString,
    nullableAsEmpty = true,
)

private fun numberField() = SyncRecordField(SyncRecordFieldKind.Number)
private fun booleanField() = SyncRecordField(SyncRecordFieldKind.Boolean)

private val taskPriorityValues = setOf("HIGH", "MEDIUM", "LOW", "NONE")
private val notifyBeforeUnitValues = setOf("NONE", "DAYS", "WEEKS", "MONTHS")
private val recurrenceValues = setOf("NONE", "DAILY", "WEEKLY", "MONTHLY", "YEARLY", "EVERY_WEEKDAY")
private val taskStatusValues = setOf("TODO", "IN_PROGRESS", "DONE")
private val countdownTypeValues = setOf("HOLIDAY", "BIRTHDAY", "ANNIVERSARY", "COUNTDOWN")
private val countingModeValues = setOf("COUNTDOWN", "COUNT_UP")
private val smartListVisibilityValues = setOf("ON_THE_DAY", "THREE_DAYS_EARLY", "SEVEN_DAYS_EARLY", "ALWAYS", "DO_NOT_SHOW")

internal val categoryRecordPayloadSchema = SyncRecordPayloadSchema(
    collectionName = "categories",
    fields = linkedMapOf(
        "localId" to stringField(requireNonBlank = true),
        "name" to stringField(),
        "icon" to stringField(),
        "sortOrder" to numberField(),
        "isDeleted" to booleanField(),
        "localCreatedAt" to numberField(),
        "localUpdatedAt" to numberField(),
    ),
)

internal val tagRecordPayloadSchema = SyncRecordPayloadSchema(
    collectionName = "tags",
    fields = linkedMapOf(
        "localId" to stringField(requireNonBlank = true),
        "name" to stringField(),
        "color" to stringField(),
        "isDeleted" to booleanField(),
        "localCreatedAt" to numberField(),
        "localUpdatedAt" to numberField(),
    ),
)

internal val taskRecordPayloadSchema = SyncRecordPayloadSchema(
    collectionName = "tasks",
    legacyExtraKeys = setOf("isCompleted"),
    fields = linkedMapOf(
        "localId" to stringField(requireNonBlank = true),
        "title" to stringField(),
        "content" to stringField(),
        "subtasks" to jsonStringField(),
        "priority" to stringField(allowedValues = taskPriorityValues, emptyEnumDefault = "NONE"),
        "deadline" to numberField(),
        "endDeadline" to numberField(),
        "notifyBeforeValue" to numberField(),
        "notifyBeforeUnit" to stringField(allowedValues = notifyBeforeUnitValues, emptyEnumDefault = "NONE"),
        "recurrenceType" to stringField(allowedValues = recurrenceValues, emptyEnumDefault = "NONE"),
        "recurrenceInterval" to numberField(),
        "recurrenceAnchorDay" to numberField(),
        "status" to stringField(allowedValues = taskStatusValues, emptyEnumDefault = "TODO"),
        "completedAt" to numberField(),
        "isStarred" to booleanField(),
        "section" to stringField(),
        "isUrgent" to booleanField(),
        "isImportant" to booleanField(),
        "categoryId" to stringField(),
        "isAllDay" to booleanField(),
        "sourceExternalId" to stringField(),
        "location" to stringField(),
        "url" to stringField(),
        "organizer" to stringField(),
        "eventStatus" to stringField(),
        "attendees" to stringField(),
        "durationReminders" to stringField(),
        "dateReminders" to stringField(),
        "isDeleted" to booleanField(),
        "localCreatedAt" to numberField(),
        "localUpdatedAt" to numberField(),
    ),
)

internal val taskTagRecordPayloadSchema = SyncRecordPayloadSchema(
    collectionName = "task_tags",
    fields = linkedMapOf(
        "localId" to stringField(requireNonBlank = true),
        "taskId" to stringField(),
        "tagId" to stringField(),
        "isDeleted" to booleanField(),
        "localCreatedAt" to numberField(),
        "localUpdatedAt" to numberField(),
    ),
)

internal val noteRecordPayloadSchema = SyncRecordPayloadSchema(
    collectionName = "notes",
    fields = linkedMapOf(
        "localId" to stringField(requireNonBlank = true),
        "title" to stringField(),
        "content" to stringField(),
        "isDeleted" to booleanField(),
        "localCreatedAt" to numberField(),
        "localUpdatedAt" to numberField(),
    ),
)

internal val countdownRecordPayloadSchema = SyncRecordPayloadSchema(
    collectionName = "countdowns",
    fields = linkedMapOf(
        "localId" to stringField(requireNonBlank = true),
        "title" to stringField(),
        "targetDate" to numberField(),
        "countdownType" to stringField(allowedValues = countdownTypeValues, emptyEnumDefault = "COUNTDOWN"),
        "countingMode" to stringField(allowedValues = countingModeValues, emptyEnumDefault = "COUNTDOWN"),
        "reminders" to stringField(),
        "recurrenceType" to stringField(allowedValues = recurrenceValues, emptyEnumDefault = "NONE"),
        "recurrenceInterval" to numberField(),
        "recurrenceDaysOfWeek" to stringField(),
        "smartListVisibility" to stringField(allowedValues = smartListVisibilityValues, emptyEnumDefault = "ON_THE_DAY"),
        "isCompleted" to booleanField(),
        "isDeleted" to booleanField(),
        "localCreatedAt" to numberField(),
        "localUpdatedAt" to numberField(),
    ),
)

internal val attachmentRecordPayloadSchema = SyncRecordPayloadSchema(
    collectionName = "attachments",
    fields = linkedMapOf(
        "localId" to stringField(requireNonBlank = true),
        "ownerType" to stringField(),
        "ownerId" to stringField(),
        "kind" to stringField(),
        "file" to nullableStringField(),
        "mimeType" to stringField(),
        "fileName" to stringField(),
        "fileSizeBytes" to numberField(),
        "width" to numberField(),
        "height" to numberField(),
        "sortOrder" to numberField(),
        "isDeleted" to booleanField(),
        "localCreatedAt" to numberField(),
        "localUpdatedAt" to numberField(),
    ),
)

private fun JsonElement.asText(name: String): String {
    val primitive = this as? JsonPrimitive
        ?: throw SyncRecordPayloadException("PocketBase $name must be a JSON string")
    if (primitive is JsonNull || !primitive.isString) {
        throw SyncRecordPayloadException("PocketBase $name must be a JSON string")
    }
    return primitive.content
}
