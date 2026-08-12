package com.udnahc.opentasks.data.sync

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.HttpMethod
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith
import com.udnahc.opentasks.data.auth.CacheBinding

class PocketBaseRecordGatewayTest {
    private val ownerBinding = CacheBinding(
        canonicalEndpoint = "https://example.test",
        serverInstanceId = "server",
        accountId = "account-a",
        capabilityVersion = 2,
        boundaryEpoch = 3,
    )

    private fun inventoryWithTaskTitle(title: String) = PocketBaseServerInventory(
        serverInstanceId = "server",
        accountId = "account-a",
        recordsByCollection = PocketBaseServerInventoryReader.COLLECTIONS.associateWith { collection ->
            if (collection == "tasks") {
                listOf(
                    buildJsonObject {
                        put("id", "remote-task")
                        put("localId", "task")
                        put("account", "account-a")
                        put("isDeleted", false)
                        put("title", title)
                    },
                )
            } else {
                emptyList()
            }
        },
    )

    @Test
    fun `inventory reads every page of all seven collections`() = runTest {
        val requests = mutableListOf<String>()
        val gateway = PocketBaseRecordGateway(
            HttpClient(MockEngine { request ->
                val path = request.url.encodedPath
                requests += request.url.toString()
                when {
                    path.endsWith("opentasks_sync_meta/records") -> respond(
                        content = """{"items":[{"capabilityVersion":2,"serverInstanceId":"server"}]}""",
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                    )
                    path.endsWith("categories/records") && request.url.parameters["page"] == "1" -> respond(
                        content = """{"items":[{"id":"category-1","localId":"category-1"}],"page":1,"totalPages":2}""",
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                    )
                    path.endsWith("categories/records") -> respond(
                        content = """{"items":[{"id":"category-2","localId":"category-2"}],"page":2,"totalPages":2}""",
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                    )
                    else -> respond(
                        content = """{"items":[],"page":1,"totalPages":1}""",
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                    )
                }
            }),
            "https://example.test",
        )

        val inventory = PocketBaseServerInventoryReader(gateway).read()

        assertEquals("server", inventory.serverInstanceId)
        assertEquals(PocketBaseServerInventoryReader.COLLECTIONS.toSet(), inventory.recordsByCollection.keys)
        assertEquals(2, inventory.recordsByCollection.getValue("categories").size)
        PocketBaseServerInventoryReader.COLLECTIONS.forEach { collection ->
            assertTrue(requests.any { it.contains("/$collection/records?") })
        }
        assertTrue(requests.any { it.contains("/categories/records?page=2") })
    }

    @Test
    fun `local id filter escapes quotes and backslashes`() {
        assertEquals("localId='a\\\\b\\'c'", PocketBaseFilter.localIdEquals("a\\b'c"))
    }

    @Test
    fun `replacement fingerprint is stable across collection row and object key ordering`() {
        val first = PocketBaseServerInventory(
            serverInstanceId = "server",
            accountId = "account-a",
            recordsByCollection = PocketBaseServerInventoryReader.COLLECTIONS.associateWith { collection ->
                if (collection == "tasks") {
                    listOf(
                        buildJsonObject {
                            put("id", "b")
                            put("localId", "task-b")
                            put("isDeleted", true)
                            put("title", "second")
                        },
                        buildJsonObject {
                            put("id", "a")
                            put("localId", "task-a")
                            put("isDeleted", false)
                            put("title", "first")
                        },
                    )
                } else {
                    emptyList()
                }
            },
        )
        val reordered = first.copy(
            recordsByCollection = first.recordsByCollection.entries.reversed().associate { (collection, rows) ->
                collection to rows.reversed().map { row ->
                    JsonObject(row.entries.reversed().associate { it.toPair() })
                }
            },
        )

        val tasks = first.replacementCounts().first { it.collection == "tasks" }
        assertEquals(1, tasks.active)
        assertEquals(1, tasks.tombstones)
        assertEquals(
            first.replacementFingerprint("https://example.test", "account-a"),
            reordered.replacementFingerprint("https://example.test", "account-a"),
        )
        assertFalse(
            first.replacementFingerprint("https://example.test", "account-a") ==
                first.copy(accountId = "account-b")
                    .replacementFingerprint("https://example.test", "account-b"),
        )
    }

    @Test
    fun `replacement fingerprint changes when a non-identity payload field changes`() {
        val initial = inventoryWithTaskTitle("Before")
        val updated = inventoryWithTaskTitle("After")

        assertNotEquals(
            initial.replacementFingerprint("https://example.test", "account-a"),
            updated.replacementFingerprint("https://example.test", "account-a"),
        )
    }

    @Test
    fun `failure summary exposes only validation field names and codes`() {
        val rawBody = """
            {
              "message":"must-not-be-logged",
              "data":{
                "content":{"code":"validation_invalid_value","message":"secret note content"},
                "localUpdatedAt":{"code":"validation_invalid_number","message":"secret timestamp detail"}
              }
            }
        """.trimIndent()

        assertEquals(
            "validation=content:validation_invalid_value,localUpdatedAt:validation_invalid_number",
            safePocketBaseFailureSummary(rawBody),
        )
        assertEquals("validation=unavailable", safePocketBaseFailureSummary("not-json"))
    }

    @Test
    fun `new attachment tombstone is JSON only and requires a blank returned file`() = runTest {
        val engine = MockEngine {
            assertTrue(it.url.encodedPath.endsWith("/api/collections/attachments/records"))
            val body = it.body as TextContent
            assertFalse(body.text.contains("must-not-be-sent"))
            respond(
                content = """{"id":"remote-id","file":""}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
        val gateway = PocketBaseRecordGateway(HttpClient(engine), "https://example.test")

        val response = gateway.createAttachmentTombstone(
            buildJsonObject {
                put("localId", "local-id")
                put("isDeleted", true)
                put("localUpdatedAt", 3)
                put("file", "must-not-be-sent")
                put("file-", "must-not-be-sent")
            }
        )

        assertTrue(response.isSuccess)
    }

    @Test
    fun `nonblank attachment tombstone response fails closed`() = runTest {
        val gateway = PocketBaseRecordGateway(
            HttpClient(MockEngine {
                respond(
                    content = """{"id":"remote-id","file":"still-present.png"}""",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                )
            }),
            "https://example.test",
        )

        val response = gateway.createAttachmentTombstone(
            buildJsonObject {
                put("localId", "local-id")
                put("isDeleted", true)
                put("localUpdatedAt", 3)
            }
        )

        assertEquals(HttpStatusCode.Conflict, response.status)
        assertFalse(response.isSuccess)
    }

    @Test
    fun `active attachment create uses guarded multipart transport`() = runTest {
        val gateway = PocketBaseRecordGateway(
            HttpClient(MockEngine { request ->
                assertEquals(HttpMethod.Post, request.method)
                assertTrue(request.body is MultiPartFormDataContent)
                respond(
                    content = """{"id":"remote-id","file":"stored.jpg"}""",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                )
            }),
            "https://example.test",
        )

        val response = gateway.createAttachment(
            buildJsonObject {
                put("localId", "local-id")
                put("localUpdatedAt", 3)
                put("isDeleted", false)
            },
            "image.jpg",
            byteArrayOf(1, 2, 3),
        )

        assertTrue(response.isSuccess)
        assertEquals("remote-id", response.body?.get("id")?.toString()?.trim('"'))
    }

    @Test
    fun `active attachment update uses PocketBase patch transport`() = runTest {
        val gateway = PocketBaseRecordGateway(
            HttpClient(MockEngine { request ->
                assertEquals(HttpMethod.Patch, request.method)
                assertTrue(request.body is MultiPartFormDataContent)
                respond(
                    content = """{"id":"remote-id","file":"stored.jpg"}""",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                )
            }),
            "https://example.test",
        )

        val response = gateway.updateAttachment(
            recordId = "remote-id",
            body = buildJsonObject {
                put("localId", "local-id")
                put("localUpdatedAt", 4)
                put("isDeleted", false)
            },
            fileName = "image.jpg",
            bytes = byteArrayOf(1, 2, 3),
        )

        assertTrue(response.isSuccess)
    }

    @Test
    fun `owner scoped JSON writes inject the active account and reject another owner`() = runTest {
        val gateway = PocketBaseRecordGateway(
            HttpClient(MockEngine { request ->
                val body = request.body as TextContent
                assertTrue(body.text.contains("\"account\":\"account-a\""))
                respond(
                    content = """{"id":"remote-id","account":"account-a"}""",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                )
            }),
            "https://example.test",
            ownerBinding = ownerBinding,
        )

        assertTrue(gateway.createJson("tasks", buildJsonObject { put("localId", "task") }).isSuccess)
        assertFailsWith<PocketBaseOwnerMismatchException> {
            gateway.createJson(
                "tasks",
                buildJsonObject {
                    put("localId", "task")
                    put("account", "account-b")
                },
            )
        }
    }

    @Test
    fun `owner scoped reads reject raw records from another account`() = runTest {
        val gateway = PocketBaseRecordGateway(
            HttpClient(MockEngine {
                respond(
                    content = """{"items":[{"id":"task","account":"account-b"}],"page":1,"totalPages":1}""",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                )
            }),
            "https://example.test",
            ownerBinding = ownerBinding,
        )

        assertFailsWith<PocketBaseOwnerMismatchException> {
            gateway.getRecords("tasks", page = 1, perPage = 30)
        }
    }

    @Test
    fun `hard delete requires an owner scoped inventory row and never accepts another owner`() = runTest {
        val requests = mutableListOf<String>()
        val gateway = PocketBaseRecordGateway(
            HttpClient(MockEngine { request ->
                requests += request.url.encodedPath
                assertEquals(HttpMethod.Delete, request.method)
                respond(content = "", status = HttpStatusCode.NoContent)
            }),
            "https://example.test",
            ownerBinding = ownerBinding,
        )

        val response = gateway.deleteOwnedInventoryRecord(
            "attachments",
            buildJsonObject {
                put("id", "attachment-a")
                put("account", "account-a")
            },
        )

        assertTrue(response.isSuccess)
        assertEquals(
            listOf("/api/collections/attachments/records/attachment-a"),
            requests,
        )
        assertFailsWith<PocketBaseOwnerMismatchException> {
            gateway.deleteOwnedInventoryRecord(
                "attachments",
                buildJsonObject {
                    put("id", "attachment-b")
                    put("account", "account-b")
                },
            )
        }
        assertFailsWith<IllegalArgumentException> {
            gateway.deleteOwnedInventoryRecord(
                "users",
                buildJsonObject {
                    put("id", "account-a")
                    put("account", "account-a")
                },
            )
        }
    }
}
