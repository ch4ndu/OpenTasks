package com.udnahc.opentasks.data.sync

import com.udnahc.opentasks.data.auth.CacheBinding
import io.github.agrevster.pocketbaseKotlin.PocketbaseClient

open class PocketBaseRecordGatewayFactory {
    open fun create(client: PocketbaseClient): PocketBaseRecordGateway {
        val metadata = PocketBaseClientProvider.metadataFor(client)
            ?: error("PocketBase client has no registered metadata")
        val endpoint = metadata.endpoint
        val binding = metadata.binding
        return if (binding == null) {
            create(client, endpoint)
        } else {
            create(client, endpoint, binding)
        }
    }

    open fun create(client: PocketbaseClient, endpoint: PocketBaseEndpoint): PocketBaseRecordGateway =
        PocketBaseRecordGateway(client.httpClient, endpoint.canonicalUrl)

    open fun create(
        client: PocketbaseClient,
        endpoint: PocketBaseEndpoint,
        binding: CacheBinding,
    ): PocketBaseRecordGateway = PocketBaseRecordGateway(
        client = client.httpClient,
        baseUrl = endpoint.canonicalUrl,
        ownerBinding = binding,
    )
}

val PocketBaseEndpoint.canonicalUrl: String
    get() = "${protocol.name.lowercase()}://$host:$port"
