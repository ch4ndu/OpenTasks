package com.udnahc.opentasks.data.sync

import androidx.room.immediateTransaction
import androidx.room.useWriterConnection
import com.udnahc.opentasks.data.auth.isValidPocketBaseBinding
import com.udnahc.opentasks.data.database.AppDatabase
import io.github.agrevster.pocketbaseKotlin.PocketbaseClient

/** Runs only local missing-row recovery at a Room writer boundary. */
fun interface SyncWriterTransactionRunner {
    suspend fun run(block: suspend () -> Unit)
}

/**
 * Resources owned by one sync pass. The guarded gateway retains its protected
 * file token cache for the complete pass; no remote I/O belongs in the writer
 * transaction runner.
 */
class SyncPassContext internal constructor(
    val client: PocketbaseClient,
    internal val gateway: PocketBaseRecordGateway?,
    private val writerTransactionRunner: SyncWriterTransactionRunner,
) {
    suspend fun runWriterTransaction(block: suspend () -> Unit) {
        writerTransactionRunner.run(block)
    }

    suspend fun runMissingRowTransaction(block: suspend () -> Unit) {
        runWriterTransaction(block)
    }

    internal companion object {
        fun standalone(
            client: PocketbaseClient,
            gateway: PocketBaseRecordGateway?,
        ): SyncPassContext = SyncPassContext(
            client = client,
            gateway = gateway,
            writerTransactionRunner = SyncWriterTransactionRunner { block -> block() },
        )
    }
}

/** Creates the single validated owner-scoped gateway and writer runner for a pass. */
class SyncPassContextFactory(
    private val database: AppDatabase? = null,
    private val gatewayFactory: PocketBaseRecordGatewayFactory = PocketBaseRecordGatewayFactory(),
) {
    fun create(client: PocketbaseClient): SyncPassContext {
        val binding = PocketBaseClientProvider.bindingFor(client)
            ?: throw IllegalStateException("Sync pass requires an active authenticated account boundary")
        require(binding.isValidPocketBaseBinding()) {
            "Sync pass requires a valid authenticated account boundary"
        }
        val endpoint = PocketBaseClientProvider.endpointFor(client)
            ?: throw IllegalStateException("Sync pass requires a canonical PocketBase endpoint")
        require(endpoint.canonicalUrl == binding.canonicalEndpoint) {
            "Sync pass endpoint does not match the active account boundary"
        }
        val runner = database?.let { appDatabase ->
            SyncWriterTransactionRunner { block ->
                appDatabase.useWriterConnection { connection ->
                    connection.immediateTransaction {
                        block()
                    }
                }
            }
        } ?: SyncWriterTransactionRunner { block -> block() }
        return SyncPassContext(
            client = client,
            gateway = gatewayFactory.create(client, endpoint, binding),
            writerTransactionRunner = runner,
        )
    }
}
