package com.udnahc.opentasks.data.settings

import androidx.room.immediateTransaction
import androidx.room.useWriterConnection
import com.udnahc.opentasks.data.auth.AccountTransition
import com.udnahc.opentasks.data.auth.CacheBinding
import com.udnahc.opentasks.data.auth.AccountTransitionPhase
import com.udnahc.opentasks.data.auth.AccountTransitionPurpose
import com.udnahc.opentasks.data.auth.CacheMode
import com.udnahc.opentasks.data.auth.LOCAL_CACHE_OWNER_ID
import com.udnahc.opentasks.data.auth.isValidActiveBinding
import com.udnahc.opentasks.data.auth.SecureTokenStoreException
import com.udnahc.opentasks.data.dao.AppSettingsDao
import com.udnahc.opentasks.data.database.AppDatabase
import com.udnahc.opentasks.data.model.AppSettings
import com.udnahc.opentasks.data.sync.SyncSettingsKeys
import com.udnahc.opentasks.domain.usecase.settings.ObservePocketBaseUrlUseCase.Companion.KEY_POCKETBASE_URL
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

data class LegacyCacheIdentity(
    val canonicalEndpoint: String?,
    val serverInstanceId: String?,
)

interface AccountStateStore {
    suspend fun readCacheBinding(): CacheBinding?
    suspend fun writeCacheBinding(binding: CacheBinding)
    suspend fun clearCacheBinding()

    suspend fun readTransition(): AccountTransition?
    suspend fun writeTransition(transition: AccountTransition)
    suspend fun clearTransition()

    suspend fun persistBindingAndTransition(
        binding: CacheBinding?,
        transition: AccountTransition?,
    )

    /** Replaces account-owned Room content and its durable boundary marker atomically. */
    suspend fun <T> replaceCacheAndPersist(
        binding: CacheBinding?,
        transition: AccountTransition?,
        clearCache: suspend () -> T,
    ): T

    suspend fun readLegacyCacheIdentity(): LegacyCacheIdentity

    suspend fun readLastBoundaryEpoch(): Long
}

/** Persists account boundary state in Room settings, with one writer transaction for handoffs. */
class RoomAccountStateStore(
    private val database: AppDatabase,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val json: Json = Json { ignoreUnknownKeys = true; explicitNulls = false },
) : AccountStateStore {
    override suspend fun readCacheBinding(): CacheBinding? =
        readSerialized<CacheBinding>(CACHE_BINDING_KEY)?.also { it.validateStored() }

    override suspend fun writeCacheBinding(binding: CacheBinding) {
        withContext(ioDispatcher) {
            database.useWriterConnection { connection ->
                connection.immediateTransaction {
                    val settings = database.appSettingsDao()
                    settings.setValue(
                        AppSettings(
                            CACHE_BINDING_KEY,
                            json.encodeToString(CacheBinding.serializer(), binding),
                        )
                    )
                    if (binding.mode == CacheMode.POCKETBASE) {
                        settings.setValue(AppSettings(KEY_POCKETBASE_URL, binding.canonicalEndpoint))
                    } else {
                        settings.removeValue(KEY_POCKETBASE_URL)
                    }
                    removeLegacyServerIdentity(settings)
                    updateLastBoundaryEpoch(settings, binding.boundaryEpoch)
                }
            }
        }
    }

    override suspend fun clearCacheBinding() {
        withContext(ioDispatcher) { database.appSettingsDao().removeValue(CACHE_BINDING_KEY) }
    }

    override suspend fun readTransition(): AccountTransition? =
        readSerialized<AccountTransition>(ACCOUNT_TRANSITION_KEY)?.also { it.validateStored() }

    override suspend fun writeTransition(transition: AccountTransition) {
        withContext(ioDispatcher) {
            database.useWriterConnection { connection ->
                connection.immediateTransaction {
                    val settings = database.appSettingsDao()
                    settings.setValue(
                        AppSettings(
                            ACCOUNT_TRANSITION_KEY,
                            json.encodeToString(AccountTransition.serializer(), transition),
                        )
                    )
                    updateLastBoundaryEpoch(settings, transition.boundaryEpoch)
                }
            }
        }
    }

    override suspend fun clearTransition() {
        withContext(ioDispatcher) { database.appSettingsDao().removeValue(ACCOUNT_TRANSITION_KEY) }
    }

    override suspend fun persistBindingAndTransition(
        binding: CacheBinding?,
        transition: AccountTransition?,
    ) = withContext(ioDispatcher) {
        database.useWriterConnection { connection ->
            connection.immediateTransaction {
                persistBoundarySettings(database.appSettingsDao(), binding, transition)
            }
        }
    }

    override suspend fun <T> replaceCacheAndPersist(
        binding: CacheBinding?,
        transition: AccountTransition?,
        clearCache: suspend () -> T,
    ): T = withContext(ioDispatcher) {
        database.useWriterConnection { connection ->
            connection.immediateTransaction {
                val result = clearCache()
                persistBoundarySettings(database.appSettingsDao(), binding, transition)
                result
            }
        }
    }

    override suspend fun readLegacyCacheIdentity(): LegacyCacheIdentity = withContext(ioDispatcher) {
        val settings = database.appSettingsDao()
        LegacyCacheIdentity(
            canonicalEndpoint = settings.getValue(KEY_POCKETBASE_URL),
            serverInstanceId = settings.getValue(SyncSettingsKeys.SERVER_INSTANCE_ID),
        )
    }

    override suspend fun readLastBoundaryEpoch(): Long = withContext(ioDispatcher) {
        val raw = database.appSettingsDao().getValue(BOUNDARY_EPOCH_KEY) ?: return@withContext 0L
        raw.toLongOrNull()?.takeIf { it >= 0L }
            ?: throw SecureTokenStoreException("Stored account boundary epoch is invalid")
    }

    private suspend inline fun <reified T> readSerialized(key: String): T? = withContext(ioDispatcher) {
        val value = database.appSettingsDao().getValue(key) ?: return@withContext null
        try {
            json.decodeFromString<T>(value)
        } catch (error: SerializationException) {
            throw SecureTokenStoreException("Stored account boundary state is invalid", error)
        } catch (error: IllegalArgumentException) {
            throw SecureTokenStoreException("Stored account boundary state is invalid", error)
        }
    }

    private fun CacheBinding.validateStored() {
        if (!isValidActiveBinding()) {
            throw SecureTokenStoreException("Stored account cache binding is invalid")
        }
    }

    private fun AccountTransition.validateStored() {
        val isValid = when (purpose) {
            AccountTransitionPurpose.ACCOUNT_CHANGE ->
                (sourceAccountId.isNotBlank() || destinationAccountId.isNotBlank()) &&
                    canonicalEndpoint.isNotBlank() &&
                    serverInstanceId.isNotBlank() &&
                    capabilityVersion > 0 &&
                    boundaryEpoch > 0L &&
                    phase in setOf(AccountTransitionPhase.PREPARED, AccountTransitionPhase.NEEDS_ACTIVATION)

            AccountTransitionPurpose.LOCAL_CLEAR ->
                sourceAccountId == LOCAL_CACHE_OWNER_ID &&
                    destinationAccountId.isBlank() &&
                    canonicalEndpoint.isEmpty() &&
                    serverInstanceId.isEmpty() &&
                    capabilityVersion == 0 &&
                    boundaryEpoch > 0L &&
                    phase in setOf(AccountTransitionPhase.PRE_RESET, AccountTransitionPhase.FILES_PENDING)

            AccountTransitionPurpose.LOCAL_AUTHORITATIVE_REPLACEMENT ->
                sourceAccountId == LOCAL_CACHE_OWNER_ID &&
                    destinationAccountId.isNotBlank() &&
                    destinationAccountId != LOCAL_CACHE_OWNER_ID &&
                    canonicalEndpoint.isNotBlank() &&
                    serverInstanceId.isNotBlank() &&
                    capabilityVersion > 0 &&
                    boundaryEpoch > 0L &&
                    phase in setOf(
                        AccountTransitionPhase.REMOTE_DELETE_PENDING,
                        AccountTransitionPhase.EXACT_SEED_PENDING,
                        AccountTransitionPhase.NEEDS_ACTIVATION,
                    )
        }
        if (!isValid) {
            throw SecureTokenStoreException("Stored account transition is invalid")
        }
    }

    private suspend fun removeLegacyServerIdentity(settings: AppSettingsDao) {
        settings.removeValue(SyncSettingsKeys.SERVER_INSTANCE_ID)
    }

    private suspend fun persistBoundarySettings(
        settings: AppSettingsDao,
        binding: CacheBinding?,
        transition: AccountTransition?,
    ) {
        if (binding == null) {
            settings.removeValue(CACHE_BINDING_KEY)
        } else {
            settings.setValue(
                AppSettings(
                    CACHE_BINDING_KEY,
                    json.encodeToString(CacheBinding.serializer(), binding),
                )
            )
            if (binding.mode == CacheMode.POCKETBASE) {
                settings.setValue(AppSettings(KEY_POCKETBASE_URL, binding.canonicalEndpoint))
            } else {
                settings.removeValue(KEY_POCKETBASE_URL)
            }
        }
        removeLegacyServerIdentity(settings)
        updateLastBoundaryEpoch(
            settings,
            maxOf(binding?.boundaryEpoch ?: 0L, transition?.boundaryEpoch ?: 0L),
        )
        if (transition == null) {
            settings.removeValue(ACCOUNT_TRANSITION_KEY)
        } else {
            settings.setValue(
                AppSettings(
                    ACCOUNT_TRANSITION_KEY,
                    json.encodeToString(AccountTransition.serializer(), transition),
                )
            )
        }
        if (binding == null && transition?.destinationAccountId.isNullOrBlank()) {
            settings.removeValue(SyncSettingsKeys.MODE)
        }
    }

    private suspend fun updateLastBoundaryEpoch(settings: AppSettingsDao, candidate: Long) {
        if (candidate <= 0L) return
        val current = settings.getValue(BOUNDARY_EPOCH_KEY)?.let { raw ->
            raw.toLongOrNull()?.takeIf { it >= 0L }
                ?: throw SecureTokenStoreException("Stored account boundary epoch is invalid")
        } ?: 0L
        if (candidate > current) {
            settings.setValue(AppSettings(BOUNDARY_EPOCH_KEY, candidate.toString()))
        }
    }

    companion object {
        const val CACHE_BINDING_KEY = "account_cache_binding_v1"
        const val ACCOUNT_TRANSITION_KEY = "account_transition_v1"
        private const val BOUNDARY_EPOCH_KEY = "account_boundary_epoch_v1"
    }
}
