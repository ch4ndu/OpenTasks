package com.udnahc.opentasks.data.sync

import com.udnahc.opentasks.data.auth.CacheBinding
import io.github.agrevster.pocketbaseKotlin.PocketbaseClient

class PocketBaseRecordGatewayFactory {
    fun create(client: PocketbaseClient): PocketBaseRecordGateway {
        val endpoint = PocketBaseClientProvider.endpointFor(client)
            ?: error("PocketBase client has no canonical endpoint")
        val binding = PocketBaseClientProvider.bindingFor(client)
        return if (binding == null) {
            create(client, endpoint)
        } else {
            create(client, endpoint, binding)
        }
    }

    fun create(client: PocketbaseClient, endpoint: PocketBaseEndpoint): PocketBaseRecordGateway =
        PocketBaseRecordGateway(client.httpClient, endpoint.canonicalUrl)

    fun create(
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
