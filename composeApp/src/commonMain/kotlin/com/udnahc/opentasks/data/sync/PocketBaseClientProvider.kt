package com.udnahc.opentasks.data.sync

import com.udnahc.opentasks.data.auth.AccountBoundary
import com.udnahc.opentasks.data.auth.CacheBinding
import com.udnahc.opentasks.data.auth.CacheMode
import com.udnahc.opentasks.data.auth.canonicalizeAccountEndpoint
import com.udnahc.opentasks.data.auth.isValidPocketBaseBinding
import io.github.agrevster.pocketbaseKotlin.PocketbaseClient
import io.ktor.http.URLProtocol
import kotlinx.coroutines.flow.MutableStateFlow
import org.lighthousegames.logging.logging

private val log = logging("PocketBaseClientProvider")

class PocketBaseClientProvider {
    private val activeMetadataState = MutableStateFlow<PocketBaseClientMetadata?>(null)
    val client: PocketbaseClient? get() = activeMetadataState.value?.client
    val endpoint: PocketBaseEndpoint? get() = activeMetadataState.value?.endpoint
    val activeBinding: CacheBinding? get() = activeMetadataState.value?.binding

    val isConfigured: Boolean get() = activeMetadataState.value != null

    fun configure(url: String) {
        val endpoint = parsePocketBaseEndpoint(url)
        configure(endpoint)
    }

    fun configure(endpoint: PocketBaseEndpoint) {
        val activeBinding = activeMetadataState.value?.binding
        if (activeBinding != null) {
            if (activeBinding.canonicalEndpoint != endpoint.canonicalUrl) {
                throw IllegalStateException("Cannot replace an active account client without a durable account transition")
            }
            return
        }
        val candidate = createClient(endpoint)
        val candidateMetadata = metadataFor(candidate) ?: run {
            releaseClient(candidate)
            error("PocketBase candidate has no registered metadata")
        }
        val publication = publishConfiguredCandidate(candidateMetadata)
        if (!publication.published) {
            releaseClient(candidate)
            val blockingBinding = publication.previous?.binding
                ?: error("PocketBase candidate publication was rejected without an active binding")
            if (blockingBinding.canonicalEndpoint != endpoint.canonicalUrl) {
                throw IllegalStateException("Cannot replace an active account client without a durable account transition")
            }
            return
        }
        publication.previous?.client
            ?.takeIf { it !== candidate }
            ?.let(::releaseClient)
        log.d { "PocketBase client configured" }
    }

    fun createClient(url: String): PocketbaseClient = createClient(parsePocketBaseEndpoint(url))

    internal fun createClient(endpoint: PocketBaseEndpoint): PocketbaseClient {
        val client = PocketbaseClient({
            protocol = endpoint.protocol
            host = endpoint.host
            port = endpoint.port
        })
        registerClient(PocketBaseClientMetadata(client, endpoint, null))
        return client
    }

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
            bindRegisteredClient(client, binding)
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
        val client = createDetachedBoundClient(binding, token)
        val candidateMetadata = try {
            val metadata = metadataFor(client)
                ?: error("PocketBase candidate has no registered metadata")
            check(metadata.endpoint == endpoint && metadata.binding == binding) {
                "PocketBase candidate metadata does not match the activation boundary"
            }
            metadata
        } catch (error: Throwable) {
            releaseClient(client)
            throw error
        }
        val previous = publishActive(candidateMetadata)
        previous?.client?.takeIf { it !== client }?.let(::releaseClient)
        return client
    }

    fun activeClientMetadata(): PocketBaseClientMetadata? = activeMetadataState.value

    fun activeBoundary(): AccountBoundary? = activeMetadataState.value?.boundary

    fun requireActiveClientMetadata(client: PocketbaseClient): PocketBaseClientMetadata {
        val metadata = activeMetadataState.value
        if (metadata?.client !== client) {
            throw IllegalStateException("PocketBase client is not the active account client")
        }
        if (metadata.binding == null) {
            throw IllegalStateException("PocketBase client has no active authenticated account")
        }
        return metadata
    }

    fun requireActiveBinding(client: PocketbaseClient): CacheBinding {
        return requireActiveClientMetadata(client).binding
            ?: error("Active PocketBase metadata has no authenticated binding")
    }

    fun disconnect() {
        clearActiveClient()?.client?.let(::releaseClient)
    }

    private fun releaseClient(client: PocketbaseClient) {
        clearActiveClient(client)
        if (unregisterClient(client)) client.httpClient.close()
    }

    private fun publishConfiguredCandidate(candidate: PocketBaseClientMetadata): ActivePublication {
        while (true) {
            val current = activeMetadataState.value
            if (current?.binding != null) return ActivePublication(published = false, previous = current)
            if (activeMetadataState.compareAndSet(current, candidate)) {
                return ActivePublication(published = true, previous = current)
            }
        }
    }

    private fun publishActive(candidate: PocketBaseClientMetadata): PocketBaseClientMetadata? {
        while (true) {
            val current = activeMetadataState.value
            if (activeMetadataState.compareAndSet(current, candidate)) return current
        }
    }

    private fun clearActiveClient(): PocketBaseClientMetadata? {
        while (true) {
            val current = activeMetadataState.value ?: return null
            if (activeMetadataState.compareAndSet(current, null)) return current
        }
    }

    private fun clearActiveClient(client: PocketbaseClient) {
        while (true) {
            val current = activeMetadataState.value
            if (current?.client !== client) return
            if (activeMetadataState.compareAndSet(current, null)) return
        }
    }

    companion object {
        private val registeredClients = MutableStateFlow<List<PocketBaseClientMetadata>>(emptyList())

        private fun registerClient(metadata: PocketBaseClientMetadata) {
            while (true) {
                val current = registeredClients.value
                check(current.none { it.client === metadata.client }) {
                    "PocketBase client is already registered"
                }
                val updated = current + metadata
                if (registeredClients.compareAndSet(current, updated)) return
            }
        }

        private fun bindRegisteredClient(
            client: PocketbaseClient,
            binding: CacheBinding,
        ): PocketBaseClientMetadata {
            while (true) {
                val current = registeredClients.value
                val existing = current.firstOrNull { it.client === client }
                    ?: error("PocketBase client is not registered")
                val updatedMetadata = PocketBaseClientMetadata(
                    client = existing.client,
                    endpoint = existing.endpoint,
                    binding = binding,
                )
                val updated = current.map { metadata ->
                    if (metadata.client === client) updatedMetadata else metadata
                }
                if (registeredClients.compareAndSet(current, updated)) return updatedMetadata
            }
        }

        private fun unregisterClient(client: PocketbaseClient): Boolean {
            while (true) {
                val current = registeredClients.value
                if (current.none { it.client === client }) return false
                val updated = current.filterNot { it.client === client }
                if (registeredClients.compareAndSet(current, updated)) return true
            }
        }

        /** One-read endpoint and binding metadata for active or detached clients. */
        fun metadataFor(client: PocketbaseClient): PocketBaseClientMetadata? =
            registeredClients.value.firstOrNull { it.client === client }

        /** The canonical endpoint used to construct a client, including detached candidates. */
        fun endpointFor(client: PocketbaseClient): PocketBaseEndpoint? = metadataFor(client)?.endpoint

        /** The owner boundary for the currently activated client, if any. */
        fun bindingFor(client: PocketbaseClient): CacheBinding? = metadataFor(client)?.binding
    }

    private data class ActivePublication(
        val published: Boolean,
        val previous: PocketBaseClientMetadata?,
    )
}

class PocketBaseClientMetadata(
    val client: PocketbaseClient,
    val endpoint: PocketBaseEndpoint,
    val binding: CacheBinding?,
) {
    val boundary: AccountBoundary?
        get() = binding?.let { cacheBinding ->
            AccountBoundary(
                canonicalEndpoint = cacheBinding.canonicalEndpoint,
                serverInstanceId = cacheBinding.serverInstanceId,
                accountId = cacheBinding.accountId,
                capabilityVersion = cacheBinding.capabilityVersion,
                boundaryEpoch = cacheBinding.boundaryEpoch,
                mode = cacheBinding.mode,
            )
        }
}

data class PocketBaseEndpoint(
    val protocol: URLProtocol,
    val host: String,
    val port: Int,
)

internal fun parsePocketBaseEndpoint(url: String): PocketBaseEndpoint =
    canonicalizeAccountEndpoint(url)
