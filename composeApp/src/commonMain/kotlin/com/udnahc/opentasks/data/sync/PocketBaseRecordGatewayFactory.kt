package com.udnahc.opentasks.data.sync

import io.github.agrevster.pocketbaseKotlin.PocketbaseClient

class PocketBaseRecordGatewayFactory {
    fun create(client: PocketbaseClient): PocketBaseRecordGateway {
        val endpoint = PocketBaseClientProvider.endpointFor(client)
            ?: error("PocketBase client has no canonical endpoint")
        return create(client, endpoint)
    }

    fun create(client: PocketbaseClient, endpoint: PocketBaseEndpoint): PocketBaseRecordGateway =
        PocketBaseRecordGateway(client.httpClient, endpoint.canonicalUrl)
}

val PocketBaseEndpoint.canonicalUrl: String
    get() = "${protocol.name.lowercase()}://$host:$port"
