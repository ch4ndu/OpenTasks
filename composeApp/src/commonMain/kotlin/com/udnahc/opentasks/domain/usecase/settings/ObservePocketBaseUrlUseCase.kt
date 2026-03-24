package com.udnahc.opentasks.domain.usecase.settings

import com.udnahc.opentasks.data.dao.AppSettingsDao
import kotlinx.coroutines.flow.Flow

class ObservePocketBaseUrlUseCase(private val appSettingsDao: AppSettingsDao) {
    operator fun invoke(): Flow<String?> =
        appSettingsDao.observeValue(KEY_POCKETBASE_URL)

    companion object {
        const val KEY_POCKETBASE_URL = "pocketbase_url"
    }
}
