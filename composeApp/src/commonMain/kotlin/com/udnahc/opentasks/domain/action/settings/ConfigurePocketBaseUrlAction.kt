package com.udnahc.opentasks.domain.action.settings

import com.udnahc.opentasks.data.repository.AppSettingsRepository
import com.udnahc.opentasks.data.sync.PocketBaseClientProvider
import com.udnahc.opentasks.domain.usecase.settings.ObservePocketBaseUrlUseCase.Companion.KEY_POCKETBASE_URL
import org.lighthousegames.logging.logging

private val log = logging("ConfigurePocketBaseUrlAction")

class ConfigurePocketBaseUrlAction(
    private val appSettingsRepository: AppSettingsRepository,
    private val pbProvider: PocketBaseClientProvider,
) {
    /** Configures PocketBase from the saved URL. Returns false when no URL is saved. */
    suspend operator fun invoke(): Boolean {
        val url = appSettingsRepository.getValue(KEY_POCKETBASE_URL)
        if (url.isNullOrBlank()) {
            log.d { "PocketBase URL not configured" }
            return false
        }
        pbProvider.configure(url)
        return true
    }
}
