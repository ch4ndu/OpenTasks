package com.udnahc.opentasks.domain.action.settings

import com.udnahc.opentasks.data.repository.AppSettingsRepository
import com.udnahc.opentasks.data.sync.PocketBaseClientProvider
import com.udnahc.opentasks.domain.usecase.settings.ObservePocketBaseUrlUseCase.Companion.KEY_POCKETBASE_URL
import org.lighthousegames.logging.logging

private val log = logging("ClearPocketBaseUrlAction")

class ClearPocketBaseUrlAction(
    private val appSettingsRepository: AppSettingsRepository,
    private val pbProvider: PocketBaseClientProvider,
) {
    suspend operator fun invoke() {
        log.d { "Clearing PocketBase URL" }
        appSettingsRepository.removeValue(KEY_POCKETBASE_URL)
        pbProvider.disconnect()
    }
}
