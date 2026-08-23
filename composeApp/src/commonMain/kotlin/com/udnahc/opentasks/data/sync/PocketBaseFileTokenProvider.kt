package com.udnahc.opentasks.data.sync

import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.coroutines.cancellation.CancellationException

/**
 * Holds a protected-file token only in memory for one gateway instance.  The
 * token is never part of a Room model, account state, exception message, or
 * log entry.
 */
internal class PocketBaseFileTokenProvider(
    private val client: HttpClient,
    private val baseUrl: String,
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    private var cachedToken: String? = null

    suspend fun currentToken(): String = cachedToken ?: fetchToken().also { cachedToken = it }

    suspend fun refreshToken(): String {
        cachedToken = null
        return currentToken()
    }

    private suspend fun fetchToken(): String {
        val response = client.post("$baseUrl/api/files/token")
        if (response.status == HttpStatusCode.Unauthorized) {
            throw SyncAuthenticationRejectedException()
        }
        if (response.status.value !in 200..299) {
            throw PocketBaseConnectionException(
                "PocketBase protected-file token request failed with HTTP ${response.status.value}",
            )
        }
        val rawBody = try {
            response.bodyAsText()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            throw PocketBaseConnectionException("PocketBase protected-file token response was unreadable", error)
        }
        val token = try {
            json.parseToJsonElement(rawBody)
                .jsonObject["token"]
                ?.jsonPrimitive
                ?.content
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            null
        }?.takeIf { it.isNotBlank() }
            ?: throw PocketBaseConnectionException("PocketBase protected-file token response was invalid")
        return token
    }
}
