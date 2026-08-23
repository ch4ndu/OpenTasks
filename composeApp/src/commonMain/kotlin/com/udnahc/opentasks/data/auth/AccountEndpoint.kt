package com.udnahc.opentasks.data.auth

import com.udnahc.opentasks.data.sync.PocketBaseEndpoint
import io.ktor.http.URLProtocol

/** Canonicalizes an account endpoint and rejects clear-text public servers. */
fun canonicalizeAccountEndpoint(raw: String): PocketBaseEndpoint {
    val value = raw.trim()
    val match = ENDPOINT_PATTERN.matchEntire(value)
        ?: throw IllegalArgumentException("PocketBase endpoint must be an http(s) URL without a path")
    val protocol = if (match.groupValues[1].lowercase() == "https") URLProtocol.HTTPS else URLProtocol.HTTP
    val host = match.groupValues[2].lowercase()
    if (!host.isValidEndpointHost()) {
        throw IllegalArgumentException("PocketBase endpoint host is invalid")
    }
    val explicitPort = match.groupValues[3]
    val port = if (explicitPort.isBlank()) {
        if (protocol == URLProtocol.HTTPS) 443 else 80
    } else {
        explicitPort.toIntOrNull()
            ?: throw IllegalArgumentException("PocketBase endpoint port is invalid")
    }
    if (port !in 1..65535) {
        throw IllegalArgumentException("PocketBase endpoint port is invalid")
    }
    if (protocol == URLProtocol.HTTP && !host.isLoopbackHost() && !host.isPrivateIpv4Host()) {
        throw IllegalArgumentException("PocketBase requires HTTPS except for loopback or private LAN endpoints")
    }
    return PocketBaseEndpoint(protocol, host, port)
}

private fun String.isLoopbackHost(): Boolean =
    removePrefix("[").removeSuffix("]") in setOf("localhost", "127.0.0.1", "::1")

private fun String.isValidEndpointHost(): Boolean =
    if (startsWith("[") && endsWith("]")) {
        removePrefix("[").removeSuffix("]").isValidIpv6Address()
    } else {
        isValidDnsOrIpv4Host()
    }

private fun String.isValidDnsOrIpv4Host(): Boolean {
    if (isEmpty() || startsWith('.') || endsWith('.') || contains("..")) return false
    if (isDottedNumericHost()) return isValidIpv4Address()
    return length <= MAX_DNS_HOST_LENGTH && split('.').all(String::isValidDnsLabel)
}

private fun String.isDottedNumericHost(): Boolean =
    contains('.') && split('.').all { label ->
        label.isNotEmpty() && label.all { character -> character in '0'..'9' }
    }

private fun String.isValidDnsLabel(): Boolean =
    length in 1..MAX_DNS_LABEL_LENGTH &&
        first().isAsciiAlphaNumeric() &&
        last().isAsciiAlphaNumeric() &&
        all { character -> character.isAsciiAlphaNumeric() || character == '-' }

private fun Char.isAsciiAlphaNumeric(): Boolean =
    this in 'a'..'z' || this in '0'..'9'

private const val MAX_DNS_HOST_LENGTH = 253
private const val MAX_DNS_LABEL_LENGTH = 63

private fun String.isValidIpv4Address(): Boolean = validIpv4Octets() != null

private fun String.validIpv4Octets(): List<Int>? {
    if (isEmpty() || startsWith('.') || endsWith('.') || contains("..")) return null
    val octets = split('.')
    if (octets.size != 4 || octets.any { it.isEmpty() || it.any { character -> character !in '0'..'9' } }) {
        return null
    }
    return octets.map { octet ->
        octet.toIntOrNull()?.takeIf { it in 0..255 } ?: return null
    }
}

private fun String.isValidIpv6Address(): Boolean {
    if (isEmpty()) return false
    val compressionStart = indexOf("::")
    val hasCompression = compressionStart >= 0
    if (hasCompression && indexOf("::", startIndex = compressionStart + 2) >= 0) return false
    val segments = if (hasCompression) {
        val left = substring(0, compressionStart).takeIf { it.isNotEmpty() }?.split(':').orEmpty()
        val right = substring(compressionStart + 2).takeIf { it.isNotEmpty() }?.split(':').orEmpty()
        if (left.any(String::isEmpty) || right.any(String::isEmpty)) return false
        left + right
    } else {
        split(':').also { values -> if (values.any(String::isEmpty)) return false }
    }
    val units = segments.sumOf { segment -> if (segment.contains('.')) 2 else 1 }
    if (hasCompression) {
        if (units >= 8) return false
    } else if (units != 8) {
        return false
    }
    return segments.withIndex().all { (index, segment) ->
        if (segment.contains('.')) {
            index == segments.lastIndex && segment.isValidIpv4Address()
        } else {
            segment.length in 1..4 && segment.all(Char::isHexDigit)
        }
    }
}

private fun Char.isHexDigit(): Boolean =
    this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'

private fun String.isPrivateIpv4Host(): Boolean {
    val octets = validIpv4Octets() ?: return false
    return when (octets[0]) {
        10 -> true
        172 -> octets[1] in 16..31
        192 -> octets[1] == 168
        else -> false
    }
}

private val ENDPOINT_PATTERN =
    Regex("^(https?)://(\\[[0-9A-Fa-f:.]+\\]|[A-Za-z0-9.-]+)(?::([0-9]+))?/?$")
