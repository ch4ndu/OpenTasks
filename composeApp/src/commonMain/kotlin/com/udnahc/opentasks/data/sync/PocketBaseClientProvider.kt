package com.udnahc.opentasks.data.sync

import com.udnahc.opentasks.data.auth.AccountBoundary
import com.udnahc.opentasks.data.auth.CacheBinding
import io.github.agrevster.pocketbaseKotlin.PocketbaseClient
import io.ktor.http.URLProtocol
import org.lighthousegames.logging.logging

private val log = logging("PocketBaseClientProvider")

class PocketBaseClientProvider {
    private var _client: PocketbaseClient? = null
    val client: PocketbaseClient? get() = _client
    private var _endpoint: PocketBaseEndpoint? = null
    val endpoint: PocketBaseEndpoint? get() = _endpoint
    private var _activeBinding: CacheBinding? = null
    val activeBinding: CacheBinding? get() = _activeBinding

    val isConfigured: Boolean get() = _client != null

    fun configure(url: String) {
        val endpoint = parsePocketBaseEndpoint(url)
        configure(endpoint)
    }

    fun configure(endpoint: PocketBaseEndpoint) {
        val activeBinding = _activeBinding
        if (activeBinding != null) {
            if (activeBinding.canonicalEndpoint != endpoint.canonicalUrl) {
                throw IllegalStateException("Cannot replace an active account client without a durable account transition")
            }
            return
        }
        _client = createClient(endpoint)
        _endpoint = endpoint
        _activeBinding = null
        log.d { "PocketBase client configured: ${endpoint.protocol.name.lowercase()}://${endpoint.host}:${endpoint.port}" }
    }

    fun createClient(url: String): PocketbaseClient = createClient(parsePocketBaseEndpoint(url))

    internal fun createClient(endpoint: PocketBaseEndpoint): PocketbaseClient =
        PocketbaseClient({
            protocol = endpoint.protocol
            host = endpoint.host
            port = endpoint.port
        }).also { knownEndpoints[it] = endpoint }

    /**
     * Activates a client only after detached authentication and capability
     * validation have produced a durable cache binding.  Tokens remain in the
     * PocketBase auth store and are never exposed by this boundary contract.
     */
    fun activate(
        binding: CacheBinding,
        token: String,
    ): PocketbaseClient {
        require(token.isNotBlank()) { "PocketBase auth token must not be blank" }
        val endpoint = parsePocketBaseEndpoint(binding.canonicalEndpoint)
        val client = createClient(endpoint)
        client.authStore.save(token)
        _client?.takeIf { it !== client }?.let { previous ->
            knownEndpoints.remove(previous)
            knownBindings.remove(previous)
        }
        _client = client
        _endpoint = endpoint
        _activeBinding = binding
        knownBindings[client] = binding
        return client
    }

    fun activeBoundary(): AccountBoundary? = _activeBinding?.let { binding ->
        AccountBoundary(
            canonicalEndpoint = binding.canonicalEndpoint,
            serverInstanceId = binding.serverInstanceId,
            accountId = binding.accountId,
            capabilityVersion = binding.capabilityVersion,
            boundaryEpoch = binding.boundaryEpoch,
        )
    }

    fun requireActiveBinding(client: PocketbaseClient): CacheBinding {
        if (_client !== client) {
            throw IllegalStateException("PocketBase client is not the active account client")
        }
        return _activeBinding
            ?: throw IllegalStateException("PocketBase client has no active authenticated account")
    }

    fun disconnect() {
        _client?.let { client ->
            knownEndpoints.remove(client)
            knownBindings.remove(client)
        }
        _client = null
        _endpoint = null
        _activeBinding = null
    }

    companion object {
        private val knownEndpoints = mutableMapOf<PocketbaseClient, PocketBaseEndpoint>()
        private val knownBindings = mutableMapOf<PocketbaseClient, CacheBinding>()

        /** The canonical endpoint used to construct a client, including detached candidates. */
        fun endpointFor(client: PocketbaseClient): PocketBaseEndpoint? = knownEndpoints[client]

        /** The owner boundary for the currently activated client, if any. */
        fun bindingFor(client: PocketbaseClient): CacheBinding? = knownBindings[client]
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
