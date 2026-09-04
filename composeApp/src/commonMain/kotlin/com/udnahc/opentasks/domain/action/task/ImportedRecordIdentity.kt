package com.udnahc.opentasks.domain.action.task

import com.udnahc.opentasks.data.calendar.CsvTask
import com.udnahc.opentasks.data.model.CalendarEvent
import com.udnahc.opentasks.data.model.CalendarEventSourceKind

internal data class ImportedExternalIdentity(
    val canonicalId: String,
    val legacyAlias: String,
)

internal class ImportedIdentityBatch {
    private val missingUidOrdinals = mutableMapOf<String, Int>()
    private val csvOrdinals = mutableMapOf<String, Int>()

    fun nextCalendar(event: CalendarEvent): ImportedExternalIdentity {
        if (event.sourceKind == CalendarEventSourceKind.LEGACY) {
            return ImportedExternalIdentity(event.externalId, event.externalId)
        }

        val encoder = BinaryIdentityEncoder()
        val rawUid = event.rawUid?.takeIf { it.isNotBlank() }
        if (rawUid != null) {
            encoder.writeString("calendar-uid-v1")
            encoder.writeString(event.sourceKind.name)
            encoder.writeString(rawUid)
            encoder.writeNullableLong(event.occurrenceToken)
            encoder.writeBoolean(event.isAllDay)
        } else {
            val record = calendarRecordBytes(event)
            val fingerprint = sha256(record).toCanonicalHex()
            val ordinal = missingUidOrdinals[fingerprint] ?: 0
            missingUidOrdinals[fingerprint] = ordinal + 1
            encoder.writeString("calendar-record-v1")
            encoder.writeBytes(record)
            encoder.writeInt(ordinal)
        }

        return ImportedExternalIdentity(
            canonicalId = "calendar_v2_${sha256(encoder.toByteArray()).toCanonicalHex()}",
            legacyAlias = event.externalId,
        )
    }

    fun nextCsv(task: CsvTask): ImportedExternalIdentity {
        val record = csvRecordBytes(task)
        val fingerprint = sha256(record).toCanonicalHex()
        val ordinal = csvOrdinals[fingerprint] ?: 0
        csvOrdinals[fingerprint] = ordinal + 1
        val encoder = BinaryIdentityEncoder().apply {
            writeString("csv-record-v1")
            writeBytes(record)
            writeInt(ordinal)
        }
        return ImportedExternalIdentity(
            canonicalId = "csv_v2_${sha256(encoder.toByteArray()).toCanonicalHex()}",
            legacyAlias = "csv_${task.title.hashCode()}_${task.createdAt}",
        )
    }

    private fun calendarRecordBytes(event: CalendarEvent): ByteArray = BinaryIdentityEncoder().apply {
        writeString("calendar-record-fields-v1")
        writeString(event.sourceKind.name)
        writeString(event.title)
        writeString(event.description)
        writeLong(event.startTimeUtcMillis)
        writeNullableLong(event.endTimeUtcMillis)
        writeString(event.calendarName)
        writeBoolean(event.isAllDay)
        writeString(event.location)
        writeString(event.url)
        writeString(event.organizer)
        writeString(event.status)
        writeStrings(event.attendees.sorted())
        writeNullableLong(event.occurrenceToken)
    }.toByteArray()

    private fun csvRecordBytes(task: CsvTask): ByteArray = BinaryIdentityEncoder().apply {
        writeString("csv-record-fields-v1")
        writeString(task.title)
        writeString(task.content)
        writeString(task.listName)
        writeNullableLong(task.startDate)
        writeNullableLong(task.dueDate)
        writeBoolean(task.isAllDay)
        writeString(task.priority.name)
        writeBoolean(task.isCompleted)
        writeNullableLong(task.completedAt)
        writeString(task.durationReminders)
        writeString(task.recurrenceType.name)
        writeLong(task.createdAt)
    }.toByteArray()
}

internal expect fun sha256(input: ByteArray): ByteArray

private class BinaryIdentityEncoder {
    private var bytes = ByteArray(INITIAL_CAPACITY)
    private var size = 0

    fun writeBoolean(value: Boolean) {
        writeByte(if (value) 1 else 0)
    }

    fun writeInt(value: Int) {
        ensureCapacity(4)
        for (shift in 24 downTo 0 step 8) {
            bytes[size++] = (value ushr shift).toByte()
        }
    }

    fun writeLong(value: Long) {
        ensureCapacity(8)
        for (shift in 56 downTo 0 step 8) {
            bytes[size++] = (value ushr shift).toByte()
        }
    }

    fun writeNullableLong(value: Long?) {
        writeBoolean(value != null)
        if (value != null) writeLong(value)
    }

    fun writeString(value: String) {
        writeBytes(value.encodeToByteArray())
    }

    fun writeStrings(values: List<String>) {
        writeInt(values.size)
        values.forEach(::writeString)
    }

    fun writeBytes(value: ByteArray) {
        writeInt(value.size)
        ensureCapacity(value.size)
        value.copyInto(bytes, destinationOffset = size)
        size += value.size
    }

    fun toByteArray(): ByteArray = bytes.copyOf(size)

    private fun writeByte(value: Int) {
        ensureCapacity(1)
        bytes[size++] = value.toByte()
    }

    private fun ensureCapacity(additional: Int) {
        val required = size + additional
        if (required <= bytes.size) return
        var capacity = bytes.size
        while (capacity < required) {
            capacity = (capacity * 2).coerceAtLeast(required)
        }
        bytes = bytes.copyOf(capacity)
    }

    private companion object {
        const val INITIAL_CAPACITY = 256
    }
}

private fun ByteArray.toCanonicalHex(): String {
    require(size == SHA256_DIGEST_BYTES) { "Canonical identity requires SHA-256" }
    return buildString(CANONICAL_DIGEST_BYTES * 2) {
        for (index in 0 until CANONICAL_DIGEST_BYTES) {
            val byte = this@toCanonicalHex[index]
            val value = byte.toInt() and 0xff
            append(HEX_DIGITS[value ushr 4])
            append(HEX_DIGITS[value and 0x0f])
        }
    }
}

private const val SHA256_DIGEST_BYTES = 32
private const val CANONICAL_DIGEST_BYTES = 16

private const val HEX_DIGITS = "0123456789abcdef"
