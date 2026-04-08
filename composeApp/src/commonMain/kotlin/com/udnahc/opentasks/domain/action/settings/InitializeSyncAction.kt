package com.udnahc.opentasks.domain.action.settings

import com.udnahc.opentasks.data.repository.AppSettingsRepository
import com.udnahc.opentasks.data.sync.PocketBaseClientProvider
import com.udnahc.opentasks.data.sync.SyncService
import com.udnahc.opentasks.domain.usecase.settings.ObservePocketBaseUrlUseCase.Companion.KEY_POCKETBASE_URL

class InitializeSyncAction(
    private val appSettingsRepository: AppSettingsRepository,
    private val pbProvider: PocketBaseClientProvider,
    private val syncService: SyncService,
) {
    /** Reads the stored URL, configures the client if present, and syncs. */
    suspend operator fun invoke() {
        val url = appSettingsRepository.getValue(KEY_POCKETBASE_URL) ?: return
        pbProvider.configure(url)
        syncService.syncAll()
    }
}
