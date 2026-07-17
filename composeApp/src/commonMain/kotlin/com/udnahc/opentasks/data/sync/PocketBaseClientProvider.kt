package com.udnahc.opentasks.data.sync

import io.github.agrevster.pocketbaseKotlin.PocketbaseClient
import io.ktor.http.URLProtocol
import org.lighthousegames.logging.logging

private val log = logging("PocketBaseClientProvider")

class PocketBaseClientProvider {
    private var _client: PocketbaseClient? = null
    val client: PocketbaseClient? get() = _client
    private var _endpoint: PocketBaseEndpoint? = null
    val endpoint: PocketBaseEndpoint? get() = _endpoint

    val isConfigured: Boolean get() = _client != null

    fun configure(url: String) {
        val endpoint = parsePocketBaseEndpoint(url)
        configure(endpoint)
    }

    fun configure(endpoint: PocketBaseEndpoint) {
        _client = createClient(endpoint)
        _endpoint = endpoint
        log.d { "PocketBase client configured: ${endpoint.protocol.name.lowercase()}://${endpoint.host}:${endpoint.port}" }
    }

    fun createClient(url: String): PocketbaseClient = createClient(parsePocketBaseEndpoint(url))

    private fun createClient(endpoint: PocketBaseEndpoint): PocketbaseClient =
        PocketbaseClient({
            protocol = endpoint.protocol
            host = endpoint.host
            port = endpoint.port
        }).also { knownEndpoints[it] = endpoint }

    fun disconnect() {
        _client = null
        _endpoint = null
    }

    companion object {
        private val knownEndpoints = mutableMapOf<PocketbaseClient, PocketBaseEndpoint>()

        /** The canonical endpoint used to construct a client, including detached candidates. */
        fun endpointFor(client: PocketbaseClient): PocketBaseEndpoint? = knownEndpoints[client]
    }
}

data class PocketBaseEndpoint(
    val protocol: URLProtocol,
    val host: String,
    val port: Int,
)

internal fun parsePocketBaseEndpoint(url: String): PocketBaseEndpoint {
    val cleaned = url.trim().trimEnd('/')
    require(cleaned.isNotBlank()) { "PocketBase URL is blank" }

    val useHttps = cleaned.startsWith("https://")
    val useHttp = cleaned.startsWith("http://")
    val protocol = if (useHttps) URLProtocol.HTTPS else URLProtocol.HTTP
    val withoutProtocol = cleaned
        .removePrefix("https://")
        .removePrefix("http://")
        .substringBefore('/')
    val separator = withoutProtocol.lastIndexOf(':')
    val host = if (separator > 0) withoutProtocol.substring(0, separator) else withoutProtocol
    val explicitPort =
        if (separator > 0) withoutProtocol.substring(separator + 1).toIntOrNull() else null
    val port = explicitPort ?: when {
        useHttps -> 443
        useHttp -> 80
        else -> 80
    }

    require(host.isNotBlank()) { "PocketBase URL host is blank" }
    return PocketBaseEndpoint(protocol, host, port)
}
