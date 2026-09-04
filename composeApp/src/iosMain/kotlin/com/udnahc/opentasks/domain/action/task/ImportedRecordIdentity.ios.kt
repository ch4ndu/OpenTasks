package com.udnahc.opentasks.domain.action.task

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.CoreCrypto.CC_SHA256

@OptIn(ExperimentalForeignApi::class, ExperimentalUnsignedTypes::class)
internal actual fun sha256(input: ByteArray): ByteArray {
    val digest = UByteArray(SHA256_BYTES)
    digest.usePinned { digestPinned ->
        if (input.isEmpty()) {
            CC_SHA256(null, 0u, digestPinned.addressOf(0))
        } else {
            input.usePinned { inputPinned ->
                CC_SHA256(inputPinned.addressOf(0), input.size.toUInt(), digestPinned.addressOf(0))
            }
        }
    }
    return ByteArray(SHA256_BYTES) { index -> digest[index].toByte() }
}

private const val SHA256_BYTES = 32
