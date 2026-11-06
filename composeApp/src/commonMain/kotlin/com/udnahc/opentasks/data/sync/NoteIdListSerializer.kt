package com.udnahc.opentasks.data.sync

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.*

/** PocketBase text fields contain JSON ID arrays; empty fields on migrated rows mean no links. */
internal object NoteIdListSerializer : KSerializer<List<String>> {
    override val descriptor = PrimitiveSerialDescriptor("NoteIdList", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: List<String>) {
        encoder.encodeString(normalizeNoteIds(JsonArray(value.map(::JsonPrimitive))))
    }
    override fun deserialize(decoder: Decoder): List<String> {
        val value = (decoder as? JsonDecoder)?.decodeJsonElement() ?: JsonPrimitive(decoder.decodeString())
        return Json.decodeFromString(normalizeNoteIds(value))
    }
}

internal fun normalizeNoteIds(value: JsonElement): String {
    val parsed = if (value is JsonPrimitive && value.isString) {
        if (value.content.isEmpty()) JsonArray(emptyList()) else
            try { Json.parseToJsonElement(value.content) } catch (_: IllegalArgumentException) { invalidNoteIds() }
    } else value
    val array = parsed as? JsonArray ?: invalidNoteIds()
    val ids = array.map {
        val id = it as? JsonPrimitive ?: invalidNoteIds()
        if (!id.isString || id.content.isBlank()) invalidNoteIds()
        id.content
    }
    if (ids.distinct().size != ids.size) invalidNoteIds()
    return JsonArray(ids.map(::JsonPrimitive)).toString()
}

private fun invalidNoteIds(): Nothing = throw SyncRecordPayloadException("notes has an invalid ID array")
