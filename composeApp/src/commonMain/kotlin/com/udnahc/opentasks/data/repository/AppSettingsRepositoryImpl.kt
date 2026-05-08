package com.udnahc.opentasks.data.repository

import com.udnahc.opentasks.data.dao.AppSettingsDao
import com.udnahc.opentasks.data.model.AppSettings
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class AppSettingsRepositoryImpl(
    private val appSettingsDao: AppSettingsDao,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : AppSettingsRepository {

    override suspend fun getValue(key: String): String? =
        withContext(ioDispatcher) { appSettingsDao.getValue(key) }

    override fun observeValue(key: String): Flow<String?> =
        appSettingsDao.observeValue(key)

    override suspend fun setValue(key: String, value: String) =
        withContext(ioDispatcher) { appSettingsDao.setValue(AppSettings(key, value)) }

    override suspend fun removeValue(key: String) =
        withContext(ioDispatcher) { appSettingsDao.removeValue(key) }

    override suspend fun deleteAll() =
        withContext(ioDispatcher) { appSettingsDao.deleteAll() }
}
