package com.udnahc.opentasks.data.sync

import com.udnahc.opentasks.data.auth.AccountTransition
import com.udnahc.opentasks.data.auth.AccountTransitionPhase
import com.udnahc.opentasks.data.auth.AccountTransitionPurpose
import com.udnahc.opentasks.data.auth.CacheBinding
import com.udnahc.opentasks.data.settings.AccountStateStore
import io.github.agrevster.pocketbaseKotlin.PocketbaseClient
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlin.coroutines.cancellation.CancellationException

/** Destructive, owner-scoped pre-seed phase for a confirmed local-authoritative replacement. */
internal interface AuthoritativeServerReplaceContract {
    suspend fun persistConfirmedBoundary(binding: CacheBinding, transition: AccountTransition)
    suspend fun localInventoryFingerprint(): String
    suspend fun validateLocalSeedSource()
    suspend fun resume(
        client: PocketbaseClient,
        binding: CacheBinding,
        initialTransition: AccountTransition,
    ): AccountTransition
}

class AuthoritativeServerReplaceExecutor(
    private val adapters: List<BaseSyncAdapter<*, *>>,
    private val seedExecutor: ServerSeedExecutor,
    private val migrationCoordinator: ServerMigrationCoordinator,
    private val accountStateStore: AccountStateStore,
    private val inventoryReader: suspend (PocketbaseClient) -> PocketBaseServerInventory = { client ->
        PocketBaseServerInventoryReader(PocketBaseRecordGatewayFactory().create(client)).read()
    },
    private val deleteRecord: suspend (PocketbaseClient, String, JsonObject) -> GatewayResponse<Unit> =
        { client, collection, row ->
            PocketBaseRecordGatewayFactory().create(client)
                .deleteOwnedInventoryRecord(collection, row)
        },
) : AuthoritativeServerReplaceContract {
    override suspend fun persistConfirmedBoundary(
        binding: CacheBinding,
        transition: AccountTransition,
    ) {
        migrationCoordinator.persistAuthoritativePhase(binding, transition)
    }

    override suspend fun localInventoryFingerprint(): String {
        val parts = buildList {
            for (adapter in adapters.sortedWith(compareBy({ it.order }, { it.collectionName }))) {
                add(adapter.collectionName)
                addAll(adapter.localInventoryFingerprintRows())
            }
        }
        return opaqueFingerprint(parts)
    }

    override suspend fun validateLocalSeedSource() {
        for (adapter in adapters.sortedWith(compareBy({ it.order }, { it.collectionName }))) {
            adapter.validateLocalSeedSource()
        }
    }

    /** Returns the durable NEEDS_ACTIVATION marker after exact completion. */
    override suspend fun resume(
        client: PocketbaseClient,
        binding: CacheBinding,
        initialTransition: AccountTransition,
    ): AccountTransition {
        require(initialTransition.purpose == AccountTransitionPurpose.LOCAL_AUTHORITATIVE_REPLACEMENT)
        var transition = accountStateStore.readTransition() ?: initialTransition
        validateBoundary(binding, transition)

        if (transition.phase == AccountTransitionPhase.REMOTE_DELETE_PENDING) {
            deleteCompleteOwnerInventory(client, binding)
            val exactSeed = transition.copy(phase = AccountTransitionPhase.EXACT_SEED_PENDING)
            migrationCoordinator.resetForAuthoritativeSeed(binding, exactSeed)
            transition = exactSeed
        }

        if (transition.phase == AccountTransitionPhase.EXACT_SEED_PENDING) {
            try {
                seedExecutor.resumeAuthoritative(client)
            } catch (error: AuthoritativeSeedConflictException) {
                val deleteAgain = transition.copy(phase = AccountTransitionPhase.REMOTE_DELETE_PENDING)
                migrationCoordinator.persistAuthoritativePhase(binding, deleteAgain)
                throw AuthoritativeReplacementConflictException(error)
            }
            val needsActivation = transition.copy(phase = AccountTransitionPhase.NEEDS_ACTIVATION)
            migrationCoordinator.persistAuthoritativePhase(
                binding = binding,
                transition = needsActivation,
                mode = SyncMode.NORMAL,
            )
            transition = needsActivation
        }

        if (transition.phase != AccountTransitionPhase.NEEDS_ACTIVATION) {
            throw IllegalStateException("Authoritative replacement has an invalid recovery phase")
        }
        return transition
    }

    private suspend fun deleteCompleteOwnerInventory(
        client: PocketbaseClient,
        binding: CacheBinding,
    ) {
        val inventory = inventoryReader(client)
        requireInventoryBoundary(inventory, binding)
        val failures = mutableListOf<Throwable>()
        for (collection in DELETE_ORDER) {
            val rows = inventory.recordsByCollection[collection]
                ?: throw SyncAdapterException("Replacement inventory is missing $collection")
            rows.sortedBy { it["id"]?.jsonPrimitive?.contentOrNull.orEmpty() }.forEach { row ->
                try {
                    val response = deleteRecord(client, collection, row)
                    if (!response.isSuccess && response.status != HttpStatusCode.NotFound) {
                        failures += SyncAdapterException(
                            "Owner-scoped delete failed for $collection (HTTP ${response.status.value})",
                        )
                    }
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Throwable) {
                    failures += error
                }
            }
        }
        if (failures.isNotEmpty()) {
            throw SyncAdapterException("Authoritative owner deletion failed", failures.first())
        }

        val emptyInventory = inventoryReader(client)
        requireInventoryBoundary(emptyInventory, binding)
        if (!emptyInventory.isEmpty) {
            throw AuthoritativeReplacementConflictException(
                IllegalStateException("Destination owner inventory changed during deletion"),
            )
        }
    }

    private fun requireInventoryBoundary(
        inventory: PocketBaseServerInventory,
        binding: CacheBinding,
    ) {
        if (inventory.serverInstanceId != binding.serverInstanceId ||
            inventory.accountId != binding.accountId
        ) {
            throw SyncAdapterException("Authoritative replacement inventory boundary changed")
        }
    }

    private fun validateBoundary(binding: CacheBinding, transition: AccountTransition) {
        if (transition.destinationAccountId != binding.accountId ||
            transition.canonicalEndpoint != binding.canonicalEndpoint ||
            transition.serverInstanceId != binding.serverInstanceId ||
            transition.capabilityVersion != binding.capabilityVersion ||
            transition.boundaryEpoch != binding.boundaryEpoch
        ) {
            throw IllegalStateException("Authoritative replacement boundary does not match its durable transition")
        }
    }

    companion object {
        val DELETE_ORDER = listOf(
            "task_tags",
            "attachments",
            "tasks",
            "tags",
            "categories",
            "notes",
            "countdowns",
        )
    }
}

class AuthoritativeReplacementConflictException(cause: Throwable) : IllegalStateException(
    "PocketBase account changed during authoritative replacement; retry will delete and reseed the complete owner inventory",
    cause,
)
