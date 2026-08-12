package com.udnahc.opentasks.data.auth

import com.udnahc.opentasks.data.sync.PocketBaseClientProvider
import com.udnahc.opentasks.data.sync.PocketBaseEndpoint
import io.github.agrevster.pocketbaseKotlin.PocketbaseClient
import io.ktor.client.HttpClient

/**
 * Owns one temporary account client for the duration of an authentication or
 * inventory operation. The Ktor client is intentionally the only transport
 * surface exposed to the authenticator.
 */
internal interface AccountClientSession {
    val httpClient: HttpClient

    fun updateToken(token: String)

    fun close()
}

internal fun interface AccountClientSessionFactory {
    fun open(endpoint: PocketBaseEndpoint): AccountClientSession
}

internal class PocketBaseAccountClientSession(
    private val client: PocketbaseClient,
    private val release: (PocketbaseClient) -> Unit,
) : AccountClientSession {
    override val httpClient: HttpClient = client.httpClient
    private var closed = false

    override fun updateToken(token: String) {
        client.authStore.save(token)
    }

    override fun close() {
        if (closed) return
        closed = true
        release(client)
    }
}

internal fun PocketBaseClientProvider.accountClientSession(
    endpoint: PocketBaseEndpoint,
): AccountClientSession = openAccountClientSession(endpoint)
