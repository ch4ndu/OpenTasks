package com.udnahc.opentasks.domain.action.settings

import com.udnahc.opentasks.data.repository.AppSettingsRepository
import com.udnahc.opentasks.data.sync.PocketBaseClientProvider
import com.udnahc.opentasks.data.sync.PocketBaseConnectionVerifier
import com.udnahc.opentasks.data.sync.PocketBaseRecordGatewayFactory
import com.udnahc.opentasks.data.sync.PocketBaseServerInventoryReader
import com.udnahc.opentasks.data.sync.PocketBaseConnectionException
import com.udnahc.opentasks.data.sync.PocketBaseEndpoint
import com.udnahc.opentasks.data.sync.PocketBaseServerInventory
import com.udnahc.opentasks.data.sync.LocalStorageState
import com.udnahc.opentasks.data.sync.ServerMigrationCoordinator
import com.udnahc.opentasks.data.sync.SyncMode
import com.udnahc.opentasks.data.sync.SyncSettingsKeys
import com.udnahc.opentasks.data.sync.SyncService
import com.udnahc.opentasks.domain.usecase.settings.ObservePocketBaseUrlUseCase.Companion.KEY_POCKETBASE_URL
import org.lighthousegames.logging.logging

private val log = logging("SavePocketBaseUrlAction")

class SavePocketBaseUrlAction(
    private val appSettingsRepository: AppSettingsRepository,
    private val pbProvider: PocketBaseClientProvider,
    private val connectionVerifier: PocketBaseConnectionVerifier,
    private val syncService: SyncService,
    private val serverMigrationCoordinator: ServerMigrationCoordinator? = null,
    private val candidateInventoryReader: suspend (io.github.agrevster.pocketbaseKotlin.PocketbaseClient, PocketBaseEndpoint) -> PocketBaseServerInventory =
        { client, endpoint ->
            PocketBaseServerInventoryReader(
                PocketBaseRecordGatewayFactory().create(client, endpoint),
            ).read()
        },
) {
    /** Classifies a detached candidate before persisting its identity or replacing the active client. */
    suspend operator fun invoke(url: String) {
        log.d { "Saving PocketBase URL" }
        val coordinator = serverMigrationCoordinator
        if (coordinator == null) {
            // Compatibility path for isolated legacy unit tests; application DI always supplies the coordinator.
            val verifiedClient = pbProvider.createClient(url)
            connectionVerifier.verify(verifiedClient)
            syncService.syncAll(verifiedClient)
            appSettingsRepository.setValue(KEY_POCKETBASE_URL, url)
            pbProvider.configure(url)
            return
        }
        val endpoint = com.udnahc.opentasks.data.sync.parsePocketBaseEndpoint(url)
        val verifiedClient = pbProvider.createClient(url)
        connectionVerifier.verify(verifiedClient)
        val inventory = candidateInventoryReader(verifiedClient, endpoint)
        val localState = coordinator.classifyLocalStorage()
        val savedIdentity = appSettingsRepository.getValue(SyncSettingsKeys.SERVER_INSTANCE_ID)
        val isSameServer = savedIdentity == inventory.serverInstanceId ||
            (savedIdentity.isNullOrBlank() &&
                (localState == LocalStorageState.FRESH || coordinator.hasProvenRemoteIdentity(inventory)))
        val mode = when {
            isSameServer -> SyncMode.NORMAL
            inventory.isEmpty && localState == LocalStorageState.NONEMPTY -> SyncMode.EMPTY_SERVER_SEED_PENDING
            inventory.isEmpty -> SyncMode.NORMAL
            localState == LocalStorageState.FRESH -> SyncMode.NORMAL
            else -> throw PocketBaseConnectionException("Refusing to merge populated local data with a different populated PocketBase server")
        }
        coordinator.commit(endpoint, inventory.serverInstanceId, mode)
        pbProvider.configure(endpoint)
        // SyncService switches to the resumable seed executor when the committed
        // mode is pending; normal sync cannot reach no-pbId tombstone cleanup.
        syncService.syncAll()
    }
}
