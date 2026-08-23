package com.udnahc.opentasks.data.auth

import com.udnahc.opentasks.data.sync.PocketBaseClientProvider
import com.udnahc.opentasks.data.sync.PocketBaseEndpoint
import com.udnahc.opentasks.data.sync.PocketBaseOwnerMismatchException
import com.udnahc.opentasks.data.sync.PocketBaseRecordGateway
import com.udnahc.opentasks.data.sync.PocketBaseServerInventoryReader
import com.udnahc.opentasks.data.sync.PocketBaseServerInventory
import com.udnahc.opentasks.data.sync.SyncAuthenticationRejectedException
import com.udnahc.opentasks.data.sync.canonicalUrl
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.path
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlin.coroutines.cancellation.CancellationException
import org.lighthousegames.logging.logging

private val log = logging("PocketBaseAccountAuthenticator")

data class AccountCapability(
    val capabilityVersion: Int,
    val serverInstanceId: String,
    val legacyOwnerAccount: String,
    val legacyEndpoint: String,
    val scopedRecordCounts: Map<String, Int>,
    val authoritativeReplaceVersion: Int = 0,
    internal val ownerInventory: PocketBaseServerInventory? = null,
)

internal interface AccountAuthenticator {
    suspend fun authenticate(
        endpoint: PocketBaseEndpoint,
        email: String,
        password: String,
    ): AccountCredential

    suspend fun refresh(
        endpoint: PocketBaseEndpoint,
        token: String,
    ): AccountCredential

    suspend fun readOwnerInventory(credential: AccountCredential): PocketBaseServerInventory =
        credential.capability.ownerInventory
            ?: throw AccountCapabilityRejectedException("PocketBase owner inventory is unavailable")
}

internal class AccountCredential(
    val account: AuthenticatedAccount,
    val endpoint: PocketBaseEndpoint,
    val token: String,
    val capability: AccountCapability,
) {
    fun withCapability(capability: AccountCapability): AccountCredential =
        AccountCredential(account, endpoint, token, capability)

    override fun toString(): String = "AccountCredential(accountId=${account.accountId})"
}

class AccountAuthenticationRejectedException(cause: Throwable? = null) : IllegalStateException(
    "PocketBase authentication was rejected",
    cause,
)

class AccountConnectivityException(cause: Throwable) : IllegalStateException(
    "PocketBase account service is unavailable",
    cause,
)

class AccountCapabilityRejectedException(message: String) : IllegalStateException(message)

internal enum class AccountHttpRequestPhase(val diagnosticName: String) {
    AUTHENTICATION("authentication request"),
    CAPABILITY("capability request"),
    OWNER_INVENTORY("owner inventory request"),
}

internal fun classifyAccountHttpFailure(
    phase: AccountHttpRequestPhase,
    statusCode: Int,
): IllegalStateException {
    val diagnostic = IllegalStateException(
        "PocketBase ${phase.diagnosticName} failed with HTTP $statusCode",
    )
    if (statusCode == 408 || statusCode == 425 || statusCode == 429 || statusCode in 500..599) {
        return AccountConnectivityException(diagnostic)
    }
    return when {
        phase == AccountHttpRequestPhase.AUTHENTICATION && statusCode in 400..499 ->
            AccountAuthenticationRejectedException(diagnostic)
        phase == AccountHttpRequestPhase.AUTHENTICATION -> AccountConnectivityException(diagnostic)
        else -> AccountCapabilityRejectedException(diagnostic.message.orEmpty())
    }
}

class AccountSwitchNotAvailableException : IllegalStateException(
    "Changing the active account is not available until the cache-transition phase",
)

class AccountTransitionBlockedException(message: String) : IllegalStateException(message)

class LegacyCacheOwnershipException(
    val account: AuthenticatedAccount,
    val hasUnsyncedRows: Boolean,
) : IllegalStateException(
    if (hasUnsyncedRows) {
        "The existing local cache cannot be proven to belong to this account and contains pending changes"
    } else {
        "The existing local cache cannot be proven to belong to this account"
    },
)

internal class PocketBaseAccountAuthenticator(
    private val pbProvider: PocketBaseClientProvider,
    private val json: Json = Json { ignoreUnknownKeys = true },
    private val sessionFactory: AccountClientSessionFactory =
        AccountClientSessionFactory { endpoint -> pbProvider.accountClientSession(endpoint) },
) : AccountAuthenticator {
    override suspend fun authenticate(
        endpoint: PocketBaseEndpoint,
        email: String,
        password: String,
    ): AccountCredential {
        require(email.isNotBlank()) { "Account email must not be blank" }
        require(password.isNotBlank()) { "Account password must not be blank" }
        log.d { "Password authentication started" }
        val session = sessionFactory.open(endpoint)
        return try {
            val response = requestAuthWithPassword(session.httpClient, email, password)
            val credential = parseCredential(response, endpoint)
            log.d { "Password authentication accepted; validating server capability" }
            session.updateToken(credential.token)
            val capability = validateCapability(
                session.httpClient,
                endpoint,
                credential.account.accountId,
            )
            log.d { "Password authentication capability validation completed" }
            credential.withCapability(capability)
        } finally {
            session.close()
        }
    }

    override suspend fun refresh(
        endpoint: PocketBaseEndpoint,
        token: String,
    ): AccountCredential {
        log.d { "Token refresh started" }
        val session = sessionFactory.open(endpoint)
        return try {
            session.updateToken(token)
            val response = requestAuthRefresh(session.httpClient)
            val credential = parseCredential(response, endpoint)
            log.d { "Token refresh accepted; validating server capability" }
            session.updateToken(credential.token)
            val capability = validateCapability(
                session.httpClient,
                endpoint,
                credential.account.accountId,
            )
            log.d { "Token refresh capability validation completed" }
            credential.withCapability(capability)
        } finally {
            session.close()
        }
    }

    private suspend fun requestAuthWithPassword(
        httpClient: HttpClient,
        email: String,
        password: String,
    ): AuthResponseEnvelope {
        val body = JsonObject(
            mapOf(
                "identity" to JsonPrimitive(email),
                "password" to JsonPrimitive(password),
            )
        )
        val response = try {
            httpClient.post {
                url { path("api", "collections", "users", "auth-with-password") }
                header("Authorization", "")
                contentType(ContentType.Application.Json)
                setBody(body.toString())
            }
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            throw AccountConnectivityException(error)
        }
        return decodeAuthResponse(response)
    }

    private suspend fun requestAuthRefresh(httpClient: HttpClient): AuthResponseEnvelope {
        val response = try {
            httpClient.post {
                url { path("api", "collections", "users", "auth-refresh") }
                contentType(ContentType.Application.Json)
            }
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            throw AccountConnectivityException(error)
        }
        return decodeAuthResponse(response)
    }

    private suspend fun decodeAuthResponse(
        response: io.ktor.client.statement.HttpResponse,
    ): AuthResponseEnvelope {
        if (response.status.value !in 200..299) {
            throw classifyAccountHttpFailure(
                AccountHttpRequestPhase.AUTHENTICATION,
                response.status.value,
            )
        }
        val rawBody = try {
            response.bodyAsText()
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            throw AccountConnectivityException(error)
        }
        return runCatching { json.decodeFromString<AuthResponseEnvelope>(rawBody) }
            .getOrElse { throw AccountCapabilityRejectedException("PocketBase account response was invalid") }
    }

    private fun parseCredential(
        response: AuthResponseEnvelope,
        endpoint: PocketBaseEndpoint,
    ): AccountCredential {
        if (response.token.isBlank()) {
            throw AccountCapabilityRejectedException("PocketBase account response did not contain a token")
        }
        val accountId = response.record["id"]?.jsonPrimitive?.contentOrNull
            ?.takeIf { it.isNotBlank() }
            ?: throw AccountCapabilityRejectedException("PocketBase account response did not contain an account id")
        val email = response.record["email"]?.jsonPrimitive?.contentOrNull
        val displayName = response.record["name"]?.jsonPrimitive?.contentOrNull
            ?: response.record["displayName"]?.jsonPrimitive?.contentOrNull
        return AccountCredential(
            account = AuthenticatedAccount(accountId, email, displayName),
            endpoint = endpoint,
            token = response.token,
            capability = AccountCapability(
                capabilityVersion = 0,
                serverInstanceId = "",
                legacyOwnerAccount = "",
                legacyEndpoint = "",
                scopedRecordCounts = emptyMap(),
                authoritativeReplaceVersion = 0,
            ),
        )
    }

    private suspend fun validateCapability(
        httpClient: HttpClient,
        endpoint: PocketBaseEndpoint,
        accountId: String,
    ): AccountCapability {
        val gateway = PocketBaseRecordGateway(
            client = httpClient,
            baseUrl = endpoint.canonicalUrl,
        )
        val metaResponse = try {
            gateway.getCapability()
        } catch (error: SyncAuthenticationRejectedException) {
            throw AccountAuthenticationRejectedException(error)
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            throw AccountConnectivityException(error)
        }
        val meta = metaResponse.body
            ?: throw classifyAccountHttpFailure(
                AccountHttpRequestPhase.CAPABILITY,
                metaResponse.status.value,
            )
        if (meta.capabilityVersion != CAPABILITY_VERSION || meta.serverInstanceId.isBlank()) {
            throw AccountCapabilityRejectedException("PocketBase capability version is unsupported")
        }
        if (meta.legacyOwnerAccount.isNullOrBlank() || meta.legacyEndpoint.isNullOrBlank()) {
            throw AccountCapabilityRejectedException("PocketBase legacy ownership metadata is incomplete")
        }
        log.d { "PocketBase capability metadata accepted; validating owner-scoped inventories" }

        // The detached candidate is authenticated but not yet active. Give
        // its inventory the same owner-scoped gateway contract as production
        // sync before the durable binding is committed.
        val ownerGateway = PocketBaseRecordGateway(
            client = httpClient,
            baseUrl = endpoint.canonicalUrl,
            ownerBinding = CacheBinding(
                canonicalEndpoint = endpoint.canonicalUrl,
                serverInstanceId = meta.serverInstanceId,
                accountId = accountId,
                capabilityVersion = meta.capabilityVersion,
                boundaryEpoch = 0L,
            ),
        )
        val recordsByCollection = linkedMapOf<String, List<JsonObject>>()
        for (collection in PocketBaseServerInventoryReader.COLLECTIONS) {
            val rows = readAll(ownerGateway, collection)
            rows.forEach { row ->
                val owner = row.accountIdOrNull()
                    ?: throw AccountCapabilityRejectedException("PocketBase $collection row has no account owner")
                if (owner != accountId) {
                    throw AccountCapabilityRejectedException("PocketBase returned a row owned by another account")
                }
            }
            recordsByCollection[collection] = rows
            log.d { "PocketBase owner-scoped inventory validated: collection=$collection, rows=${rows.size}" }
        }
        val inventory = PocketBaseServerInventory(
            serverInstanceId = meta.serverInstanceId,
            recordsByCollection = recordsByCollection,
            accountId = accountId,
        )
        return AccountCapability(
            capabilityVersion = meta.capabilityVersion,
            serverInstanceId = meta.serverInstanceId,
            legacyOwnerAccount = meta.legacyOwnerAccount,
            legacyEndpoint = meta.legacyEndpoint,
            scopedRecordCounts = recordsByCollection.mapValues { it.value.size },
            authoritativeReplaceVersion = meta.authoritativeReplaceVersion,
            ownerInventory = inventory,
        )
    }

    override suspend fun readOwnerInventory(credential: AccountCredential): PocketBaseServerInventory {
        val session = sessionFactory.open(credential.endpoint)
        return try {
            session.updateToken(credential.token)
            val gateway = PocketBaseRecordGateway(
                client = session.httpClient,
                baseUrl = credential.endpoint.canonicalUrl,
                ownerBinding = CacheBinding(
                    canonicalEndpoint = credential.endpoint.canonicalUrl,
                    serverInstanceId = credential.capability.serverInstanceId,
                    accountId = credential.account.accountId,
                    capabilityVersion = credential.capability.capabilityVersion,
                    boundaryEpoch = 0L,
                ),
            )
            val inventory = try {
                PocketBaseServerInventoryReader(gateway).read()
            } catch (error: SyncAuthenticationRejectedException) {
                throw AccountAuthenticationRejectedException(error)
            } catch (error: PocketBaseOwnerMismatchException) {
                throw AccountCapabilityRejectedException(
                    "PocketBase returned a row outside the authenticated account boundary",
                )
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                throw AccountConnectivityException(error)
            }
            if (inventory.serverInstanceId != credential.capability.serverInstanceId ||
                inventory.accountId != credential.account.accountId
            ) {
                throw AccountCapabilityRejectedException("PocketBase owner inventory boundary changed")
            }
            inventory
        } finally {
            session.close()
        }
    }

    private suspend fun readAll(
        gateway: PocketBaseRecordGateway,
        collection: String,
    ): List<JsonObject> {
        val rows = mutableListOf<JsonObject>()
        var page = 1
        do {
            val response = try {
                gateway.getRecords(collection, page, INVENTORY_PAGE_SIZE)
            } catch (error: SyncAuthenticationRejectedException) {
                throw AccountAuthenticationRejectedException(error)
            } catch (error: PocketBaseOwnerMismatchException) {
                throw AccountCapabilityRejectedException(
                    "PocketBase returned a row outside the authenticated account boundary",
                )
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                throw AccountConnectivityException(error)
            }
            val result = response.body
                ?: throw classifyAccountHttpFailure(
                    AccountHttpRequestPhase.OWNER_INVENTORY,
                    response.status.value,
                )
            rows += result.items
            page += 1
        } while (page <= result.totalPages)
        return rows
    }

    private fun JsonObject.accountIdOrNull(): String? {
        val value = this["account"] ?: return null
        return when {
            value is JsonPrimitive -> value.contentOrNull
            value is JsonObject -> value["id"]?.jsonPrimitive?.contentOrNull
            else -> null
        }?.takeIf { it.isNotBlank() }
    }

    @Serializable
    private data class AuthResponseEnvelope(
        val token: String = "",
        val record: JsonObject = JsonObject(emptyMap()),
    )

    private companion object {
        const val CAPABILITY_VERSION = 2
        const val INVENTORY_PAGE_SIZE = 200
    }
}
