package com.udnahc.opentasks

object ExternalInputPolicy {
    const val MAX_IMPORT_BYTES = 5 * 1024 * 1024
    const val MAX_SHARE_PAYLOAD_BYTES = 32 * 1024
    const val MAX_SHARE_ITEMS = 8
    const val MAX_IOS_SHARE_URL_BYTES = 64 * 1024
    const val MAX_ICS_FILENAME_BYTES = 255

    fun validateImportByteCount(byteCount: Int): ExternalInputFailure? =
        if (byteCount > MAX_IMPORT_BYTES) ExternalInputFailure.TOO_LARGE else null

    fun validateShareItemCount(itemCount: Int): ExternalInputFailure? =
        if (itemCount > MAX_SHARE_ITEMS) ExternalInputFailure.TOO_MANY_ITEMS else null

    fun validateSharePayload(
        description: String,
        url: String,
        icsContent: String,
        icsFileName: String,
    ): ExternalInputFailure? {
        if (utf8ByteCountUpTo(icsFileName, MAX_ICS_FILENAME_BYTES) > MAX_ICS_FILENAME_BYTES) {
            return ExternalInputFailure.TOO_LARGE
        }

        var remaining = MAX_SHARE_PAYLOAD_BYTES
        for (value in listOf(description, url, icsContent, icsFileName)) {
            val byteCount = utf8ByteCountUpTo(value, remaining)
            if (byteCount > remaining) return ExternalInputFailure.TOO_LARGE
            remaining -= byteCount
        }
        return null
    }

    fun utf8ByteCountUpTo(value: String, limit: Int): Int {
        require(limit >= 0 && limit < Int.MAX_VALUE)
        var byteCount = 0
        var index = 0
        while (index < value.length) {
            val first = value[index]
            val firstCode = first.code
            val characterBytes = when {
                firstCode <= 0x7F -> 1
                firstCode <= 0x7FF -> 2
                firstCode in 0xD800..0xDBFF &&
                    index + 1 < value.length &&
                    value[index + 1].code in 0xDC00..0xDFFF -> {
                    index++
                    4
                }
                else -> 3
            }
            byteCount += characterBytes
            if (byteCount > limit) return limit + 1
            index++
        }
        return byteCount
    }

    fun isStrictUtf8(bytes: ByteArray): Boolean {
        var index = 0
        while (index < bytes.size) {
            val first = bytes[index].toInt() and 0xFF
            when {
                first <= 0x7F -> index += 1
                first in 0xC2..0xDF -> {
                    if (!hasContinuation(bytes, index + 1)) return false
                    index += 2
                }
                first == 0xE0 -> {
                    if (!hasContinuationInRange(bytes, index + 1, 0xA0, 0xBF) ||
                        !hasContinuation(bytes, index + 2)
                    ) return false
                    index += 3
                }
                first in 0xE1..0xEC || first in 0xEE..0xEF -> {
                    if (!hasContinuation(bytes, index + 1) ||
                        !hasContinuation(bytes, index + 2)
                    ) return false
                    index += 3
                }
                first == 0xED -> {
                    if (!hasContinuationInRange(bytes, index + 1, 0x80, 0x9F) ||
                        !hasContinuation(bytes, index + 2)
                    ) return false
                    index += 3
                }
                first == 0xF0 -> {
                    if (!hasContinuationInRange(bytes, index + 1, 0x90, 0xBF) ||
                        !hasContinuation(bytes, index + 2) ||
                        !hasContinuation(bytes, index + 3)
                    ) return false
                    index += 4
                }
                first in 0xF1..0xF3 -> {
                    if (!hasContinuation(bytes, index + 1) ||
                        !hasContinuation(bytes, index + 2) ||
                        !hasContinuation(bytes, index + 3)
                    ) return false
                    index += 4
                }
                first == 0xF4 -> {
                    if (!hasContinuationInRange(bytes, index + 1, 0x80, 0x8F) ||
                        !hasContinuation(bytes, index + 2) ||
                        !hasContinuation(bytes, index + 3)
                    ) return false
                    index += 4
                }
                else -> return false
            }
        }
        return true
    }

    private fun hasContinuation(bytes: ByteArray, index: Int): Boolean =
        index < bytes.size && bytes[index].toInt() and 0xC0 == 0x80

    private fun hasContinuationInRange(
        bytes: ByteArray,
        index: Int,
        minimum: Int,
        maximum: Int,
    ): Boolean {
        if (index >= bytes.size) return false
        val value = bytes[index].toInt() and 0xFF
        return value in minimum..maximum
    }
}

enum class ExternalInputFailure(val wireValue: String) {
    TOO_LARGE("too_large"),
    TOO_MANY_ITEMS("too_many_items"),
    INVALID_UTF8("invalid_utf8"),
    INVALID_FILE_TYPE("invalid_file_type"),
    UNREADABLE("unreadable"),
    ;

    companion object {
        fun fromWireValue(value: String): ExternalInputFailure? =
            entries.firstOrNull { it.wireValue == value }
    }
}
