package com.udnahc.opentasks.data.sync

import org.lighthousegames.logging.logging
import io.github.agrevster.pocketbaseKotlin.PocketbaseClient

private val log = logging("PocketBaseConnectionVerifier")

class PocketBaseConnectionVerifier(
    private val pbProvider: PocketBaseClientProvider,
    private val adapters: List<BaseSyncAdapter<*, *>>,
    private val healthCheck: suspend (PocketbaseClient) -> Unit = { it.health.healthCheck() },
) {
    suspend fun verify() {
        val client = pbProvider.client ?: throw PocketBaseConnectionException("PocketBase client is not configured")

        runCatching { healthCheck(client) }
            .onFailure { log.e(it) { "PocketBase health check failed" } }
            .getOrElse { throw PocketBaseConnectionException("PocketBase health check failed", it) }

        val failures = mutableListOf<SyncCollectionFailure>()
        adapters.sortedBy { it.order }.forEach { adapter ->
            runCatching { adapter.verifyCollection(client) }
                .onFailure { error ->
                    log.e(error) { "PocketBase collection check failed: ${adapter.collectionName}" }
                    failures += SyncCollectionFailure(adapter.collectionName, "verify", error)
                }
        }

        if (failures.isNotEmpty()) {
            throw SyncException(failures)
        }
    }
}
