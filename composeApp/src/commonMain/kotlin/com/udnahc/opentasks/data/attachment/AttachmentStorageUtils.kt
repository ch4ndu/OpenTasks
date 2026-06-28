package com.udnahc.opentasks.data.attachment

fun attachmentExtension(fileName: String, bytes: ByteArray): String =
    when {
        bytes.size >= 12 &&
                bytes[0] == 'R'.code.toByte() &&
                bytes[1] == 'I'.code.toByte() &&
                bytes[2] == 'F'.code.toByte() &&
                bytes[8] == 'W'.code.toByte() &&
                bytes[9] == 'E'.code.toByte() &&
                bytes[10] == 'B'.code.toByte() &&
                bytes[11] == 'P'.code.toByte() -> "webp"
        bytes.size >= 3 &&
                bytes[0] == 0xFF.toByte() &&
                bytes[1] == 0xD8.toByte() &&
                bytes[2] == 0xFF.toByte() -> "jpg"
        bytes.size >= 8 &&
                bytes[0] == 0x89.toByte() &&
                bytes[1] == 'P'.code.toByte() &&
                bytes[2] == 'N'.code.toByte() &&
                bytes[3] == 'G'.code.toByte() -> "png"
        fileName.substringAfterLast('.', "").lowercase() in setOf("webp", "jpg", "jpeg", "png") ->
            fileName.substringAfterLast('.').lowercase().let { if (it == "jpeg") "jpg" else it }
        else -> "jpg"
    }

fun attachmentMimeType(fileName: String, bytes: ByteArray): String =
    when (attachmentExtension(fileName, bytes)) {
        "webp" -> "image/webp"
        "png" -> "image/png"
        else -> "image/jpeg"
    }
