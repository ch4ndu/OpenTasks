package com.udnahc.opentasks.data.repository

import kotlinx.coroutines.flow.Flow

interface AppSettingsRepository {
    suspend fun getValue(key: String): String?
    fun observeValue(key: String): Flow<String?>
    suspend fun setValue(
        key: String,
        value: String
    )

    suspend fun removeValue(key: String)
    suspend fun deleteAll()
}
