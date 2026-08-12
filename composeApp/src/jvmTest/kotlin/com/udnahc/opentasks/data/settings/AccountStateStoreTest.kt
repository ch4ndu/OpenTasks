package com.udnahc.opentasks.data.settings

import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.udnahc.opentasks.data.auth.AccountTransition
import com.udnahc.opentasks.data.auth.AccountTransitionPhase
import com.udnahc.opentasks.data.auth.AccountTransitionPurpose
import com.udnahc.opentasks.data.auth.CacheMode
import com.udnahc.opentasks.data.auth.LOCAL_CACHE_OWNER_ID
import com.udnahc.opentasks.data.auth.CacheBinding
import com.udnahc.opentasks.data.auth.SecureTokenStoreException
import com.udnahc.opentasks.data.database.AppDatabase
import com.udnahc.opentasks.data.model.AppSettings
import com.udnahc.opentasks.domain.usecase.settings.ObservePocketBaseUrlUseCase.Companion.KEY_POCKETBASE_URL
import java.io.File
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class AccountStateStoreTest {
    private lateinit var databaseFile: File
    private lateinit var database: AppDatabase

    @BeforeTest
    fun createDatabase() {
        databaseFile = File.createTempFile("opentasks-account-state", ".db")
        database = Room.databaseBuilder<AppDatabase>(name = databaseFile.absolutePath)
            .setDriver(BundledSQLiteDriver())
            .setJournalMode(RoomDatabase.JournalMode.TRUNCATE)
            .build()
    }

    @AfterTest
    fun closeDatabase() {
        database.close()
        databaseFile.delete()
    }

    @Test
    fun bindingWritesCanonicalEndpointAndClearingBindingRetainsIt() = runTest {
        val store = RoomAccountStateStore(database)
        val binding = binding()

        store.writeCacheBinding(binding)

        assertEquals(binding, store.readCacheBinding())
        assertEquals(binding.canonicalEndpoint, database.appSettingsDao().getValue(KEY_POCKETBASE_URL))

        store.clearCacheBinding()

        assertNull(store.readCacheBinding())
        assertEquals(binding.canonicalEndpoint, database.appSettingsDao().getValue(KEY_POCKETBASE_URL))
    }

    @Test
    fun atomicBoundaryWritesRoundTripAndLogoutCleanupDoesNotEraseEndpoint() = runTest {
        val store = RoomAccountStateStore(database)
        val binding = binding()
        val transition = transition(AccountTransitionPhase.NEEDS_ACTIVATION)

        store.persistBindingAndTransition(binding, transition)

        assertEquals(binding, store.readCacheBinding())
        assertEquals(transition, store.readTransition())
        assertEquals(binding.canonicalEndpoint, database.appSettingsDao().getValue(KEY_POCKETBASE_URL))

        val logoutTransition = transition(AccountTransitionPhase.NEEDS_ACTIVATION).copy(
            destinationAccountId = "",
        )
        store.replaceCacheAndPersist(binding = null, transition = logoutTransition) { Unit }

        assertNull(store.readCacheBinding())
        assertEquals(logoutTransition, store.readTransition())
        assertEquals(binding.canonicalEndpoint, database.appSettingsDao().getValue(KEY_POCKETBASE_URL))
    }

    @Test
    fun legacySerializedBindingAndTransitionDefaultToPocketBaseAccountChange() = runTest {
        database.appSettingsDao().setValue(
            AppSettings(
                RoomAccountStateStore.CACHE_BINDING_KEY,
                """{"canonicalEndpoint":"https://tasks.example.com:443","serverInstanceId":"server","accountId":"account-a","capabilityVersion":2,"boundaryEpoch":4}""",
            )
        )
        database.appSettingsDao().setValue(
            AppSettings(
                RoomAccountStateStore.ACCOUNT_TRANSITION_KEY,
                """{"sourceAccountId":"account-a","destinationAccountId":"account-b","canonicalEndpoint":"https://tasks.example.com:443","serverInstanceId":"server","capabilityVersion":2,"boundaryEpoch":5,"phase":"PREPARED"}""",
            )
        )

        val store = RoomAccountStateStore(database)

        assertEquals(CacheMode.POCKETBASE, store.readCacheBinding()?.mode)
        assertEquals(AccountTransitionPurpose.ACCOUNT_CHANGE, store.readTransition()?.purpose)
    }

    @Test
    fun localBindingAndLocalClearPhasesRoundTripWithoutWritingAFakeEndpoint() = runTest {
        val store = RoomAccountStateStore(database)
        database.appSettingsDao().setValue(AppSettings(KEY_POCKETBASE_URL, "https://stale.example.com"))
        val localBinding = CacheBinding(
            canonicalEndpoint = "",
            serverInstanceId = "",
            accountId = LOCAL_CACHE_OWNER_ID,
            capabilityVersion = 0,
            boundaryEpoch = 7L,
            mode = CacheMode.LOCAL_ONLY,
        )
        val filesPending = AccountTransition(
            sourceAccountId = LOCAL_CACHE_OWNER_ID,
            destinationAccountId = "",
            canonicalEndpoint = "",
            serverInstanceId = "",
            capabilityVersion = 0,
            boundaryEpoch = 8L,
            phase = AccountTransitionPhase.FILES_PENDING,
            purpose = AccountTransitionPurpose.LOCAL_CLEAR,
        )

        store.persistBindingAndTransition(localBinding, filesPending)

        assertEquals(localBinding, store.readCacheBinding())
        assertEquals(filesPending, store.readTransition())
        assertNull(database.appSettingsDao().getValue(KEY_POCKETBASE_URL))
    }

    @Test
    fun authoritativeReplacementPersistsOnlyExecutableRecoveryPhases() = runTest {
        val store = RoomAccountStateStore(database)
        val replacement = AccountTransition(
            sourceAccountId = LOCAL_CACHE_OWNER_ID,
            destinationAccountId = "account-a",
            canonicalEndpoint = "https://tasks.example.com:443",
            serverInstanceId = "server",
            capabilityVersion = 2,
            boundaryEpoch = 8L,
            phase = AccountTransitionPhase.REMOTE_DELETE_PENDING,
            purpose = AccountTransitionPurpose.LOCAL_AUTHORITATIVE_REPLACEMENT,
        )

        listOf(
            AccountTransitionPhase.REMOTE_DELETE_PENDING,
            AccountTransitionPhase.EXACT_SEED_PENDING,
            AccountTransitionPhase.NEEDS_ACTIVATION,
        ).forEach { phase ->
            store.writeTransition(replacement.copy(phase = phase))
            assertEquals(phase, store.readTransition()?.phase)
        }

        store.writeTransition(replacement.copy(phase = AccountTransitionPhase.PREPARED))
        assertFailsWith<SecureTokenStoreException> { store.readTransition() }
    }

    private fun binding() = CacheBinding(
        canonicalEndpoint = "https://tasks.example.com:443",
        serverInstanceId = "server",
        accountId = "account-a",
        capabilityVersion = 2,
        boundaryEpoch = 4L,
    )

    private fun transition(phase: AccountTransitionPhase) = AccountTransition(
        sourceAccountId = "account-a",
        destinationAccountId = "account-b",
        canonicalEndpoint = "https://tasks.example.com:443",
        serverInstanceId = "server",
        capabilityVersion = 2,
        boundaryEpoch = 5L,
        phase = phase,
    )
}
