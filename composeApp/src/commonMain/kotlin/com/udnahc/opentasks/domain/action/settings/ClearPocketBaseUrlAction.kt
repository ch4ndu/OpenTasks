package com.udnahc.opentasks.domain.action.settings

import com.udnahc.opentasks.data.dao.AppSettingsDao
import com.udnahc.opentasks.data.sync.PocketBaseClientProvider
import com.udnahc.opentasks.domain.usecase.settings.ObservePocketBaseUrlUseCase.Companion.KEY_POCKETBASE_URL

class ClearPocketBaseUrlAction(
    private val appSettingsDao: AppSettingsDao,
    private val pbProvider: PocketBaseClientProvider,
) {
    suspend operator fun invoke() {
        appSettingsDao.removeValue(KEY_POCKETBASE_URL)
        pbProvider.disconnect()
    }
}
