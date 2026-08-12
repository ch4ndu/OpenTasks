package com.udnahc.opentasks.data.sync

import com.udnahc.opentasks.data.auth.AccountBoundary
import com.udnahc.opentasks.data.auth.CacheBinding
import com.udnahc.opentasks.data.auth.CacheMode
import com.udnahc.opentasks.data.auth.isValidPocketBaseBinding
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
        val previous = _client
        val candidate = createClient(endpoint)
        _client = candidate
        _endpoint = endpoint
        _activeBinding = null
        previous?.let(::releaseClient)
        log.d { "PocketBase client configured" }
    }

    fun createClient(url: String): PocketbaseClient = createClient(parsePocketBaseEndpoint(url))

    internal fun createClient(endpoint: PocketBaseEndpoint): PocketbaseClient =
        PocketbaseClient({
            protocol = endpoint.protocol
            host = endpoint.host
            port = endpoint.port
        }).also { knownEndpoints[it] = endpoint }

    /**
     * Creates an authenticated, owner-bound candidate without publishing it as
     * the active application client. Used while a durable replacement marker
     * keeps task UI and ordinary sync disabled.
     */
    internal fun createDetachedBoundClient(
        binding: CacheBinding,
        token: String,
    ): PocketbaseClient {
        require(binding.isValidPocketBaseBinding()) {
            "Detached PocketBase client requires a valid remote cache binding"
        }
        require(token.isNotBlank()) { "PocketBase auth token must not be blank" }
        val client = createClient(parsePocketBaseEndpoint(binding.canonicalEndpoint))
        try {
            client.authStore.save(token)
            knownBindings[client] = binding
        } catch (error: Throwable) {
            releaseClient(client)
            throw error
        }
        return client
    }

    internal fun releaseDetachedClient(client: PocketbaseClient) {
        releaseClient(client)
    }

    /** Opens a temporary account session whose client is released by close(). */
    internal fun openAccountClientSession(
        endpoint: PocketBaseEndpoint,
    ): com.udnahc.opentasks.data.auth.AccountClientSession {
        val client = createClient(endpoint)
        return try {
            com.udnahc.opentasks.data.auth.PocketBaseAccountClientSession(
                client = client,
                release = ::releaseClient,
            )
        } catch (error: Throwable) {
            releaseClient(client)
            throw error
        }
    }

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
        require(binding.mode == CacheMode.POCKETBASE && binding.isValidPocketBaseBinding()) {
            "PocketBase activation requires a valid remote cache binding"
        }
        val endpoint = parsePocketBaseEndpoint(binding.canonicalEndpoint)
        val client = createClient(endpoint)
        try {
            client.authStore.save(token)
            knownBindings[client] = binding
        } catch (error: Throwable) {
            releaseClient(client)
            throw error
        }
        val previous = _client
        _client = client
        _endpoint = endpoint
        _activeBinding = binding
        previous?.takeIf { it !== client }?.let(::releaseClient)
        return client
    }

    fun activeBoundary(): AccountBoundary? = _activeBinding?.let { binding ->
        AccountBoundary(
            canonicalEndpoint = binding.canonicalEndpoint,
            serverInstanceId = binding.serverInstanceId,
            accountId = binding.accountId,
            capabilityVersion = binding.capabilityVersion,
            boundaryEpoch = binding.boundaryEpoch,
            mode = binding.mode,
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
        val client = _client
        if (client != null) {
            releaseClient(client)
        } else {
            _endpoint = null
            _activeBinding = null
        }
    }

    private fun releaseClient(client: PocketbaseClient) {
        val wasActive = _client === client
        val hadEndpoint = knownEndpoints.remove(client) != null
        val hadBinding = knownBindings.remove(client) != null
        if (!wasActive && !hadEndpoint && !hadBinding) return

        if (wasActive) {
            _client = null
            _endpoint = null
            _activeBinding = null
        }
        client.httpClient.close()
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
