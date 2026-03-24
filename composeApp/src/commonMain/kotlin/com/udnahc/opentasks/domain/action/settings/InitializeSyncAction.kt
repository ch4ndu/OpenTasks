package com.udnahc.opentasks.domain.action.settings

import com.udnahc.opentasks.data.dao.AppSettingsDao
import com.udnahc.opentasks.data.sync.PocketBaseClientProvider
import com.udnahc.opentasks.data.sync.SyncService
import com.udnahc.opentasks.domain.usecase.settings.ObservePocketBaseUrlUseCase.Companion.KEY_POCKETBASE_URL

class InitializeSyncAction(
    private val appSettingsDao: AppSettingsDao,
    private val pbProvider: PocketBaseClientProvider,
    private val syncService: SyncService,
) {
    /** Reads the stored URL, configures the client if present, and syncs. */
    suspend operator fun invoke() {
        val url = appSettingsDao.getValue(KEY_POCKETBASE_URL) ?: return
        pbProvider.configure(url)
        syncService.syncAll()
    }
}
