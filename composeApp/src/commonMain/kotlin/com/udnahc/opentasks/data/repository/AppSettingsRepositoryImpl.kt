package com.udnahc.opentasks.data.repository

import com.udnahc.opentasks.data.dao.AppSettingsDao
import com.udnahc.opentasks.data.model.AppSettings
import kotlinx.coroutines.flow.Flow

class AppSettingsRepositoryImpl(
    private val appSettingsDao: AppSettingsDao,
) : AppSettingsRepository {

    override suspend fun getValue(key: String): String? =
        appSettingsDao.getValue(key)

    override fun observeValue(key: String): Flow<String?> =
        appSettingsDao.observeValue(key)

    override suspend fun setValue(key: String, value: String) =
        appSettingsDao.setValue(AppSettings(key, value))

    override suspend fun removeValue(key: String) =
        appSettingsDao.removeValue(key)

    override suspend fun deleteAll() =
        appSettingsDao.deleteAll()
}
