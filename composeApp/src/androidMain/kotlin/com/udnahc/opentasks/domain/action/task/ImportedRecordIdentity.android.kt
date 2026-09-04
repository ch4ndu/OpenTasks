package com.udnahc.opentasks.domain.action.task

import java.security.MessageDigest

internal actual fun sha256(input: ByteArray): ByteArray =
    MessageDigest.getInstance("SHA-256").digest(input)
