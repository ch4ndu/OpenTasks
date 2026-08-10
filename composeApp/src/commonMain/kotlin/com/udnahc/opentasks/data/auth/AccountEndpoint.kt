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
    val port = match.groupValues[3].takeIf { it.isNotBlank() }?.toIntOrNull()
        ?: if (protocol == URLProtocol.HTTPS) 443 else 80
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

private fun String.isPrivateIpv4Host(): Boolean {
    val octets = split('.').map { it.toIntOrNull() ?: return false }
    if (octets.size != 4 || octets.any { it !in 0..255 }) return false
    return when (octets[0]) {
        10 -> true
        172 -> octets[1] in 16..31
        192 -> octets[1] == 168
        else -> false
    }
}

private val ENDPOINT_PATTERN =
    Regex("^(https?)://(\\[[0-9A-Fa-f:.]+\\]|[A-Za-z0-9.-]+)(?::([0-9]+))?/?$")
