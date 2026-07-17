package com.udnahc.opentasks.data.sync

import androidx.room.immediateTransaction
import androidx.room.useWriterConnection
import com.udnahc.opentasks.data.database.AppDatabase
import com.udnahc.opentasks.data.model.AppSettings
import io.github.agrevster.pocketbaseKotlin.PocketbaseClient

/**
 * Resumable one-way migration for a committed empty replacement server.
 *
 * The marker is intentionally retained on every failure.  Each adapter uses
 * the guarded gateway and conditional local acknowledgement, which makes a
 * process death after create safe to resume through `localId` recovery.
 */
class ServerSeedExecutor(
    private val database: AppDatabase,
    private val adapters: List<BaseSyncAdapter<*, *>>,
    private val inventoryReader: suspend (PocketbaseClient) -> PocketBaseServerInventory = { client ->
        PocketBaseServerInventoryReader(PocketBaseRecordGatewayFactory().create(client)).read()
    },
) {
    suspend fun isPending(): Boolean =
        database.appSettingsDao().getValue(SyncSettingsKeys.MODE) == SyncMode.EMPTY_SERVER_SEED_PENDING.name

    suspend fun resume(client: PocketbaseClient) {
        if (!isPending()) return
        val inventory = inventoryReader(client)
        val expectedIdentity = database.appSettingsDao().getValue(SyncSettingsKeys.SERVER_INSTANCE_ID)
        if (expectedIdentity.isNullOrBlank() || expectedIdentity != inventory.serverInstanceId) {
            throw SyncAdapterException("Seed resume rejected: PocketBase identity changed")
        }

        val ordered = adapters.sortedBy { it.order }
        ordered.forEach { adapter ->
            val rows = inventory.recordsByCollection[adapter.collectionName]
                ?: throw SyncAdapterException("Seed resume inventory is missing ${adapter.collectionName}")
            if (!adapter.validateSeedInventory(rows)) {
                throw SyncAdapterException("Seed resume rejected by ${adapter.collectionName} inventory")
            }
        }
        ordered.forEach { it.seedAll(client) }

        val finalInventory = inventoryReader(client)
        if (finalInventory.serverInstanceId != expectedIdentity) {
            throw SyncAdapterException("Seed completion rejected: PocketBase identity changed")
        }
        database.useWriterConnection { connection ->
            connection.immediateTransaction {
                if (database.appSettingsDao().getValue(SyncSettingsKeys.MODE) != SyncMode.EMPTY_SERVER_SEED_PENDING.name) {
                    return@immediateTransaction
                }
                val complete = ordered.all { adapter ->
                    adapter.isSeedComplete(finalInventory.recordsByCollection[adapter.collectionName].orEmpty())
                }
                if (!complete) throw SyncAdapterException("Seed completion invariant failed; migration remains pending")
                database.appSettingsDao().setValue(AppSettings(SyncSettingsKeys.MODE, SyncMode.NORMAL.name))
            }
        }
    }
}
