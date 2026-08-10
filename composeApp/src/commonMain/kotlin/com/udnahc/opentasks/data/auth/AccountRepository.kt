package com.udnahc.opentasks.data.auth

import io.github.agrevster.pocketbaseKotlin.PocketbaseClient
import kotlinx.coroutines.flow.StateFlow

interface AccountRepository {
    val sessionState: StateFlow<AccountSessionState>

    suspend fun restoreSession(): AccountSessionState

    suspend fun login(
        endpoint: String,
        email: String,
        password: String,
    ): AccountSessionState

    suspend fun reauthenticate(
        email: String,
        password: String,
    ): AccountSessionState

    suspend fun switchAccount(
        endpoint: String,
        email: String,
        password: String,
    ): AccountSessionState

    suspend fun logout(): AccountSessionState
}

interface AccountSyncCoordinator {
    suspend fun syncAllWithinMutation(client: PocketbaseClient)

    suspend fun initialPullWithinMutation(client: PocketbaseClient)
}
