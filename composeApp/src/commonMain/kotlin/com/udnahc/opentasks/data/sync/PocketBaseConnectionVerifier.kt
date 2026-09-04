package com.udnahc.opentasks.data.sync

import io.github.agrevster.pocketbaseKotlin.PocketbaseClient
import kotlin.coroutines.cancellation.CancellationException
import org.lighthousegames.logging.logging

private val log = logging("PocketBaseConnectionVerifier")

class PocketBaseConnectionVerifier(
    private val pbProvider: PocketBaseClientProvider,
    private val adapters: List<BaseSyncAdapter<*, *>>,
    private val healthCheck: suspend (PocketbaseClient) -> Unit = { it.health.healthCheck() },
) {
    suspend fun verify() {
        val client = pbProvider.activeClientMetadata()?.client
            ?: throw PocketBaseConnectionException("PocketBase client is not configured")
        verify(client)
    }

    suspend fun verify(client: PocketbaseClient) {
        runCatching { healthCheck(client) }
            .onFailure {
                if (it is CancellationException) throw it
                log.e { "PocketBase health check failed" }
            }
            .getOrElse { throw PocketBaseConnectionException("PocketBase health check failed", it) }

        val failures = mutableListOf<SyncCollectionFailure>()
        val clientMetadata = PocketBaseClientProvider.metadataFor(client)
        adapters.sortedBy { it.order }.forEach { adapter ->
            runCatching {
                if (clientMetadata?.binding == null) {
                    adapter.verifyCollection(client)
                } else {
                    adapter.verifyCollectionForActiveBoundary(client)
                }
            }
                .onFailure { error ->
                    if (error is CancellationException) throw error
                    log.e { "PocketBase collection check failed: ${adapter.collectionName}" }
                    failures += SyncCollectionFailure(adapter.collectionName, "verify", error)
                }
        }

        if (failures.isNotEmpty()) {
            throw SyncException(failures)
        }
    }
}
