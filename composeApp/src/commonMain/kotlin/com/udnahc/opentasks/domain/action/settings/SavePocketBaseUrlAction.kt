package com.udnahc.opentasks.domain.action.settings

import com.udnahc.opentasks.data.repository.AppSettingsRepository
import com.udnahc.opentasks.data.sync.PocketBaseClientProvider
import com.udnahc.opentasks.data.sync.PocketBaseConnectionVerifier
import com.udnahc.opentasks.data.sync.SyncService
import com.udnahc.opentasks.domain.usecase.settings.ObservePocketBaseUrlUseCase.Companion.KEY_POCKETBASE_URL
import org.lighthousegames.logging.logging

private val log = logging("SavePocketBaseUrlAction")

class SavePocketBaseUrlAction(
    private val appSettingsRepository: AppSettingsRepository,
    private val pbProvider: PocketBaseClientProvider,
    private val connectionVerifier: PocketBaseConnectionVerifier,
    private val syncService: SyncService,
) {
    /** Verifies the URL and initial sync before saving it or swapping the active client. */
    suspend operator fun invoke(url: String) {
        log.d { "Saving PocketBase URL" }
        val verifiedClient = pbProvider.createClient(url)
        connectionVerifier.verify(verifiedClient)
        syncService.syncAll(verifiedClient)
        appSettingsRepository.setValue(KEY_POCKETBASE_URL, url)
        pbProvider.configure(url)
    }
}
