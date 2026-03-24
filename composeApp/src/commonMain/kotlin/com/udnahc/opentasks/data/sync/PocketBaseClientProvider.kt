package com.udnahc.opentasks.data.sync

import io.github.agrevster.pocketbaseKotlin.PocketbaseClient
import io.ktor.http.URLProtocol

class PocketBaseClientProvider {
    private var _client: PocketbaseClient? = null
    val client: PocketbaseClient? get() = _client

    val isConfigured: Boolean get() = _client != null

    fun configure(url: String) {
        val cleaned = url.trimEnd('/')
        val useHttps = cleaned.startsWith("https://")
        val withoutProtocol = cleaned
            .removePrefix("https://")
            .removePrefix("http://")
        val parts = withoutProtocol.split(":")
        val host = parts[0]
        val port = if (parts.size > 1) parts[1].toIntOrNull() ?: 8090 else 8090

        _client = PocketbaseClient({
            protocol = if (useHttps) URLProtocol.HTTPS else URLProtocol.HTTP
            this.host = host
            this.port = port
        })
    }

    fun disconnect() {
        _client = null
    }
}
