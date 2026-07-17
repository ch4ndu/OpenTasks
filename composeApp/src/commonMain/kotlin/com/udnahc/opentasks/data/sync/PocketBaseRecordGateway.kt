package com.udnahc.opentasks.data.sync

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Narrow HTTP seam for sync-critical PocketBase operations. It deliberately
 * returns status with the decoded body so callers never infer a 404 from an
 * exception message and never need to round-trip a record through JSON.
 */
class PocketBaseRecordGateway(
    private val client: HttpClient,
    private val baseUrl: String,
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    suspend fun getCapability(): GatewayResponse<PocketBaseSyncMeta> =
        decode(client.get("$baseUrl/api/collections/opentasks_sync_meta/records?perPage=1")) { text ->
            json.decodeFromString<PocketBaseRecordPage<PocketBaseSyncMeta>>(text).items.firstOrNull()
        }

    suspend fun getRecords(collection: String, page: Int, perPage: Int): GatewayResponse<PocketBaseRecordPage<JsonObject>> =
        decode(client.get("$baseUrl/api/collections/$collection/records?page=$page&perPage=$perPage")) { text ->
            json.decodeFromString(text)
        }

    suspend fun findByLocalId(collection: String, localId: String): GatewayResponse<JsonObject?> =
        decode(
            client.get(
                "$baseUrl/api/collections/$collection/records?perPage=1&filter=" +
                    encodeQuery(PocketBaseFilter.localIdEquals(localId))
            )
        ) { text -> json.decodeFromString<PocketBaseRecordPage<JsonObject>>(text).items.firstOrNull() }

    suspend fun getRecord(collection: String, recordId: String): GatewayResponse<JsonObject> =
        decode(client.get("$baseUrl/api/collections/$collection/records/$recordId")) { text ->
            json.decodeFromString(text)
        }

    suspend fun createJson(collection: String, body: JsonObject): GatewayResponse<JsonObject> =
        decode(client.post("$baseUrl/api/collections/$collection/records") {
            headers.append(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(body.toString())
        }) { json.decodeFromString(it) }

    suspend fun updateJson(collection: String, recordId: String, body: JsonObject): GatewayResponse<JsonObject> =
        decode(client.put("$baseUrl/api/collections/$collection/records/$recordId") {
            headers.append(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(body.toString())
        }) { json.decodeFromString(it) }

    /**
     * Guarded multipart create for an active attachment.  This intentionally
     * lives beside the tombstone methods so attachment writes never fall back
     * to the SDK's exception-only multipart API.
     */
    suspend fun createAttachment(
        body: JsonObject,
        fileName: String,
        bytes: ByteArray,
    ): GatewayResponse<JsonObject> =
        decode(client.post("$baseUrl/api/collections/attachments/records") {
            setBody(attachmentContent(body, fileName, bytes))
        }) { json.decodeFromString(it) }

    /** Guarded multipart update for an active attachment. */
    suspend fun updateAttachment(
        recordId: String,
        body: JsonObject,
        fileName: String,
        bytes: ByteArray,
    ): GatewayResponse<JsonObject> =
        decode(client.put("$baseUrl/api/collections/attachments/records/$recordId") {
            setBody(attachmentContent(body, fileName, bytes))
        }) { json.decodeFromString(it) }

    /** Creates a tombstone without emitting either a binary file or a file modifier. */
    suspend fun createAttachmentTombstone(body: JsonObject): GatewayResponse<JsonObject> =
        createJson("attachments", body.withoutFileFields()).requireBlankAttachmentFile()

    /**
     * Clears an existing remote attachment using PocketBase's field-removal modifier.
     * Metadata-only updates are used when the server already reports a blank file.
     */
    suspend fun updateAttachmentTombstone(
        recordId: String,
        body: JsonObject,
        currentRemoteFileName: String?,
    ): GatewayResponse<JsonObject> {
        val cleanBody = body.withoutFileFields()
        val response = if (currentRemoteFileName.isNullOrBlank()) {
            updateJson("attachments", recordId, cleanBody)
        } else {
            decode(client.put("$baseUrl/api/collections/attachments/records/$recordId") {
                setBody(
                    MultiPartFormDataContent(
                        formData {
                            cleanBody.forEach { (key, value) -> append(key, value.jsonPrimitive.content) }
                            append("file-", currentRemoteFileName)
                        }
                    )
                )
            }) { json.decodeFromString(it) }
        }
        return response.requireBlankAttachmentFile()
    }

    private suspend fun <T> decode(response: HttpResponse, decode: (String) -> T?): GatewayResponse<T> {
        val text = response.bodyAsText()
        return GatewayResponse(
            status = response.status,
            body = if (response.status.value in 200..299) decode(text) else null,
            rawBody = text,
        )
    }

    private fun attachmentContent(
        body: JsonObject,
        fileName: String,
        bytes: ByteArray,
    ): MultiPartFormDataContent = MultiPartFormDataContent(
        formData {
            body.withoutFileFields().forEach { (key, value) ->
                append(key, value.jsonPrimitive.content)
            }
            append(
                "file",
                bytes,
                Headers.build {
                    append(HttpHeaders.ContentType, ContentType.Application.OctetStream.toString())
                    append(HttpHeaders.ContentDisposition, "form-data; name=\"file\"; filename=\"$fileName\"")
                },
            )
        }
    )

    private fun JsonObject.withoutFileFields(): JsonObject =
        JsonObject(filterKeys { it != "file" && it != "file-" })

    private fun GatewayResponse<JsonObject>.requireBlankAttachmentFile(): GatewayResponse<JsonObject> {
        val returnedFile = body?.get("file")?.jsonPrimitive?.contentOrNull
        if (isSuccess && !returnedFile.isNullOrBlank()) {
            return copy(status = HttpStatusCode.Conflict, body = null, rawBody = rawBody)
        }
        return this
    }

    private fun encodeQuery(value: String): String =
        value.encodeToByteArray().joinToString("") { byte ->
            val char = byte.toInt().toChar()
            if (char.isLetterOrDigit() || char in "-_.~") char.toString() else "%${byte.toInt().and(0xff).toString(16).padStart(2, '0')}"
        }
}

data class GatewayResponse<T>(
    val status: HttpStatusCode,
    val body: T?,
    val rawBody: String,
) {
    val isSuccess: Boolean get() = status.value in 200..299
    val isNotFound: Boolean get() = status == HttpStatusCode.NotFound
}

@Serializable
data class PocketBaseRecordPage<T>(val items: List<T> = emptyList(), val page: Int = 1, val totalPages: Int = 1)

@Serializable
data class PocketBaseSyncMeta(
    val capabilityVersion: Int = 0,
    val serverInstanceId: String = "",
)
