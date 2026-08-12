package com.udnahc.opentasks.data.auth

import com.udnahc.opentasks.data.sync.PocketBaseServerInventoryReader
import com.udnahc.opentasks.data.sync.PocketBaseClientProvider
import com.udnahc.opentasks.data.sync.PocketBaseEndpoint
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpResponseData
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.header
import io.ktor.client.request.HttpRequestData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.URLProtocol
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class PocketBaseAccountAuthenticatorTest {

    private val endpoint = PocketBaseEndpoint(URLProtocol.HTTPS, "tasks.example.test", 443)

    @Test
    fun passwordAuthenticationVerifiesRequestPromotionAndAllOwnerInventories() = runTest {
        val fixture = authFixture(AuthScript.PASSWORD)

        val credential = fixture.authenticator.authenticate(
            endpoint = endpoint,
            email = "person@example.test",
            password = "password-value",
        )

        assertEquals("account-a", credential.account.accountId)
        assertEquals("password-token", credential.token)
        assertEquals(2, credential.capability.capabilityVersion)
        assertEquals(7, credential.capability.authoritativeReplaceVersion)
        assertEquals(2, credential.capability.scopedRecordCounts.getValue("tasks"))
        assertEquals(listOf("password-token"), fixture.session.updatedTokens)

        val authRequest = fixture.session.requests.single {
            it.url.encodedPath.endsWith("/auth-with-password")
        }
        assertEquals(HttpMethod.Post, authRequest.method)
        assertEquals("/api/collections/users/auth-with-password", authRequest.url.encodedPath)
        assertEquals("", authRequest.headers[HttpHeaders.Authorization])
        val authBody = authRequest.body as TextContent
        assertEquals(ContentType.Application.Json, authBody.contentType)
        val body = Json.parseToJsonElement(authBody.text).jsonObject
        assertEquals("person@example.test", body.getValue("identity").jsonPrimitive.content)
        assertEquals("password-value", body.getValue("password").jsonPrimitive.content)

        val capabilityRequests = fixture.session.requests.filter {
            it.url.encodedPath.endsWith("/opentasks_sync_meta/records")
        }
        assertEquals(1, capabilityRequests.size)
        val inventoryRequests = fixture.session.requests.filter {
            it.url.encodedPath.contains("/api/collections/") &&
                it.url.encodedPath.endsWith("/records") &&
                !it.url.encodedPath.endsWith("/opentasks_sync_meta/records")
        }
        assertEquals(8, inventoryRequests.size)
        PocketBaseServerInventoryReader.COLLECTIONS.forEach { collection ->
            val requests = inventoryRequests.filter {
                it.url.encodedPath.endsWith("/$collection/records")
            }
            assertEquals(if (collection == "tasks") 2 else 1, requests.size)
            assertTrue(requests.all { it.headers[HttpHeaders.Authorization] == "Bearer password-token" })
        }
        assertEquals(
            setOf("1", "2"),
            inventoryRequests.filter { it.url.encodedPath.endsWith("/tasks/records") }
                .mapNotNull { it.url.parameters["page"] }
                .toSet(),
        )
        assertEquals(1, fixture.session.closeCalls)
    }

    @Test
    fun refreshPromotesTheReplacementTokenForFollowUpRequests() = runTest {
        val fixture = authFixture(AuthScript.REFRESH)

        val credential = fixture.authenticator.refresh(endpoint, token = "old-token")

        assertEquals("replacement-token", credential.token)
        assertEquals(listOf("old-token", "replacement-token"), fixture.session.updatedTokens)
        val refreshRequest = fixture.session.requests.single {
            it.url.encodedPath.endsWith("/auth-refresh")
        }
        assertEquals("Bearer old-token", refreshRequest.headers[HttpHeaders.Authorization])
        fixture.session.requests.filter {
            it.url.encodedPath.endsWith("/opentasks_sync_meta/records") ||
                it.url.encodedPath.endsWith("/records")
        }.forEach { request ->
            assertEquals("Bearer replacement-token", request.headers[HttpHeaders.Authorization])
        }
        assertEquals(1, fixture.session.closeCalls)
    }

    @Test
    fun ownerRejectionClosesTheAccountSession() = runTest {
        val fixture = authFixture(AuthScript.OWNER_REJECTION)

        assertFailsWith<AccountCapabilityRejectedException> {
            fixture.authenticator.authenticate(endpoint, "person@example.test", "password-value")
        }

        assertEquals(1, fixture.session.closeCalls)
    }

    @Test
    fun failureAndCancellationBothCloseTheAccountSession() = runTest {
        val failureFixture = authFixture(AuthScript.FAILURE)
        assertFailsWith<AccountAuthenticationRejectedException> {
            failureFixture.authenticator.authenticate(endpoint, "person@example.test", "password-value")
        }
        assertEquals(1, failureFixture.session.closeCalls)

        val cancellationFixture = authFixture(AuthScript.CANCELLATION)
        assertFailsWith<CancellationException> {
            cancellationFixture.authenticator.authenticate(endpoint, "person@example.test", "password-value")
        }
        assertEquals(1, cancellationFixture.session.closeCalls)
    }

    @Test
    fun retryableHttpStatusesAreConnectivityFailuresInEveryAccountRequestPhase() {
        val retryableStatuses = listOf(408, 425, 429, 503)

        AccountHttpRequestPhase.entries.forEach { phase ->
            retryableStatuses.forEach { statusCode ->
                val failure = classifyAccountHttpFailure(phase, statusCode)

                assertIs<AccountConnectivityException>(failure)
                assertTrue(failure.cause?.message.orEmpty().contains(phase.diagnosticName))
                assertTrue(failure.cause?.message.orEmpty().contains("HTTP $statusCode"))
            }
        }
    }

    @Test
    fun explicitCredentialDenialRemainsAuthenticationRejection() {
        val failure = classifyAccountHttpFailure(
            AccountHttpRequestPhase.AUTHENTICATION,
            statusCode = 401,
        )

        assertIs<AccountAuthenticationRejectedException>(failure)
    }

    @Test
    fun nonTransientInvalidCapabilityResponsesRemainCapabilityRejections() {
        listOf(AccountHttpRequestPhase.CAPABILITY, AccountHttpRequestPhase.OWNER_INVENTORY)
            .forEach { phase ->
                assertIs<AccountCapabilityRejectedException>(
                    classifyAccountHttpFailure(phase, statusCode = 404),
                )
                assertIs<AccountCapabilityRejectedException>(
                    classifyAccountHttpFailure(phase, statusCode = 200),
                )
        }
    }

    private fun authFixture(script: AuthScript): AuthFixture {
        val session = ScriptedAccountSession { request, token ->
            val path = request.url.encodedPath
            when {
                path.endsWith("/auth-with-password") -> when (script) {
                    AuthScript.FAILURE -> jsonResponse("", HttpStatusCode.Unauthorized)
                    AuthScript.CANCELLATION -> throw CancellationException("cancelled")
                    else -> jsonResponse(authResponse("password-token"))
                }

                path.endsWith("/auth-refresh") -> {
                    assertEquals("old-token", token)
                    jsonResponse(authResponse("replacement-token"))
                }

                path.endsWith("/opentasks_sync_meta/records") -> jsonResponse(
                    """
                    {"items":[{"capabilityVersion":2,"authoritativeReplaceVersion":7,"serverInstanceId":"server-a","legacyOwnerAccount":"legacy-owner","legacyEndpoint":"https://legacy.example.test"}]}
                    """.trimIndent(),
                )

                path.endsWith("/records") -> {
                    val collection = path
                        .substringAfter("/api/collections/")
                        .substringBefore("/records")
                    val page = request.url.parameters["page"]?.toIntOrNull() ?: 1
                    val owner = if (script == AuthScript.OWNER_REJECTION) "account-b" else "account-a"
                    val items = if (collection == "tasks") {
                        val id = if (page == 1) "task-one" else "task-two"
                        """[{"id":"$id","localId":"$id","account":"$owner"}]"""
                    } else {
                        "[]"
                    }
                    val totalPages = if (collection == "tasks") 2 else 1
                    jsonResponse(
                        """{"items":$items,"page":$page,"totalPages":$totalPages}""",
                    )
                }

                else -> error("Unexpected account request path: $path")
            }
        }
        return AuthFixture(
            session = session,
            authenticator = PocketBaseAccountAuthenticator(
                pbProvider = PocketBaseClientProvider(),
                sessionFactory = AccountClientSessionFactory { session },
            ),
        )
    }

    private fun authResponse(token: String): String =
        """{"token":"$token","record":{"id":"account-a","email":"person@example.test","name":"Person"}}"""

    private fun MockRequestHandleScope.jsonResponse(
        body: String,
        status: HttpStatusCode = HttpStatusCode.OK,
    ) = respond(
        content = body,
        status = status,
        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
    )

    private enum class AuthScript {
        PASSWORD,
        REFRESH,
        OWNER_REJECTION,
        FAILURE,
        CANCELLATION,
    }

    private data class AuthFixture(
        val session: ScriptedAccountSession,
        val authenticator: PocketBaseAccountAuthenticator,
    )

    private class ScriptedAccountSession(
        private val responder: suspend MockRequestHandleScope.(HttpRequestData, String?) -> HttpResponseData,
    ) : AccountClientSession {
        private var currentToken: String? = null
        private var closed = false
        val requests = mutableListOf<HttpRequestData>()
        val updatedTokens = mutableListOf<String>()
        var closeCalls = 0
            private set

        private val engine = MockEngine { request ->
            requests += request
            responder(request, currentToken)
        }

        override val httpClient: HttpClient = HttpClient(engine) {
            defaultRequest {
                currentToken?.let { header(HttpHeaders.Authorization, "Bearer $it") }
            }
        }

        override fun updateToken(token: String) {
            updatedTokens += token
            currentToken = token
        }

        override fun close() {
            if (closed) return
            closed = true
            closeCalls += 1
            httpClient.close()
        }
    }
}
