package com.udnahc.opentasks.data.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.udnahc.opentasks.data.model.AppSettings
import kotlinx.coroutines.flow.Flow

@Dao
interface AppSettingsDao {
    @Query("SELECT value FROM app_settings WHERE `key` = :key")
    suspend fun getValue(key: String): String?

    @Query("SELECT value FROM app_settings WHERE `key` = :key")
    fun observeValue(key: String): Flow<String?>

    @Upsert
    suspend fun setValue(setting: AppSettings)

    @Query("DELETE FROM app_settings WHERE `key` = :key")
    suspend fun removeValue(key: String)

    @Query("DELETE FROM app_settings")
    suspend fun deleteAll()
}
