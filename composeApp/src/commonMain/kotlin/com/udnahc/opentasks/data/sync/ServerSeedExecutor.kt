package com.udnahc.opentasks.data.sync

import androidx.room.immediateTransaction
import androidx.room.useWriterConnection
import com.udnahc.opentasks.data.database.AppDatabase
import com.udnahc.opentasks.data.model.AppSettings
import com.udnahc.opentasks.data.settings.AccountStateStore
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
    private val accountStateStore: AccountStateStore? = null,
) {
    suspend fun isPending(): Boolean =
        database.appSettingsDao().getValue(SyncSettingsKeys.MODE) == SyncMode.EMPTY_SERVER_SEED_PENDING.name

    suspend fun isAuthoritativePending(): Boolean =
        database.appSettingsDao().getValue(SyncSettingsKeys.MODE) == SyncMode.AUTHORITATIVE_REPLACE_PENDING.name

    suspend fun resume(client: PocketbaseClient) {
        if (!isPending()) return
        resumeSeed(client, authoritative = false)
    }

    suspend fun resumeAuthoritative(client: PocketbaseClient) {
        if (!isAuthoritativePending()) {
            throw SyncAdapterException("Authoritative seed resume rejected: replacement mode is not pending")
        }
        resumeSeed(client, authoritative = true)
    }

    private suspend fun resumeSeed(client: PocketbaseClient, authoritative: Boolean) {
        val inventory = inventoryReader(client)
        val expectedIdentity = accountStateStore?.readCacheBinding()?.serverInstanceId
            ?: database.appSettingsDao().getValue(SyncSettingsKeys.SERVER_INSTANCE_ID)
        if (expectedIdentity.isNullOrBlank() || expectedIdentity != inventory.serverInstanceId) {
            throw SyncAdapterException("Seed resume rejected: PocketBase identity changed")
        }
        val activeBinding = PocketBaseClientProvider.bindingFor(client)
        if (activeBinding != null && inventory.accountId != activeBinding.accountId) {
            throw SyncAdapterException("Seed resume rejected: PocketBase account boundary changed")
        }

        val ordered = adapters.sortedBy { it.order }
        ordered.forEach { adapter ->
            val rows = inventory.recordsByCollection[adapter.collectionName]
                ?: throw SyncAdapterException("Seed resume inventory is missing ${adapter.collectionName}")
            if (!adapter.validateSeedInventory(rows)) {
                if (authoritative) {
                    throw AuthoritativeSeedConflictException(
                        "Authoritative seed diverged in ${adapter.collectionName}",
                    )
                }
                throw SyncAdapterException("Seed resume rejected by ${adapter.collectionName} inventory")
            }
        }
        ordered.forEach { adapter ->
            if (authoritative) adapter.seedAllAuthoritative(client) else adapter.seedAll(client)
        }

        val finalInventory = inventoryReader(client)
        if (finalInventory.serverInstanceId != expectedIdentity ||
            (activeBinding != null && finalInventory.accountId != activeBinding.accountId)
        ) {
            throw SyncAdapterException("Seed completion rejected: PocketBase identity changed")
        }
        database.useWriterConnection { connection ->
            connection.immediateTransaction {
                val expectedMode = if (authoritative) {
                    SyncMode.AUTHORITATIVE_REPLACE_PENDING.name
                } else {
                    SyncMode.EMPTY_SERVER_SEED_PENDING.name
                }
                if (database.appSettingsDao().getValue(SyncSettingsKeys.MODE) != expectedMode) {
                    return@immediateTransaction
                }
                val complete = ordered.all { adapter ->
                    adapter.isSeedComplete(finalInventory.recordsByCollection[adapter.collectionName].orEmpty())
                }
                if (!complete) {
                    if (authoritative) {
                        throw AuthoritativeSeedConflictException(
                            "Authoritative seed completion inventory diverged",
                        )
                    }
                    throw SyncAdapterException("Seed completion invariant failed; migration remains pending")
                }
                if (!authoritative) {
                    database.appSettingsDao().setValue(AppSettings(SyncSettingsKeys.MODE, SyncMode.NORMAL.name))
                }
            }
        }
    }
}

class AuthoritativeSeedConflictException(message: String) : IllegalStateException(message)

class AuthoritativeLocalSeedSourceException : IllegalStateException(
    "The local authoritative seed source is not uploadable",
)
