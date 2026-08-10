package com.udnahc.opentasks.data.sync

import com.udnahc.opentasks.data.auth.CacheBinding
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsBytes
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.path
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
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
    private val ownerBinding: CacheBinding? = null,
) {
    val ownerAccountId: String? get() = ownerBinding?.accountId
    private val fileTokenProvider = ownerBinding?.let {
        PocketBaseFileTokenProvider(client, baseUrl, json)
    }
    suspend fun getCapability(): GatewayResponse<PocketBaseSyncMeta> =
        decode(client.get("$baseUrl/api/collections/opentasks_sync_meta/records?perPage=1")) { text ->
            json.decodeFromString<PocketBaseRecordPage<PocketBaseSyncMeta>>(text).items.firstOrNull()
        }

    suspend fun getRecords(
        collection: String,
        page: Int,
        perPage: Int,
    ): GatewayResponse<PocketBaseRecordPage<JsonObject>> {
        val response: GatewayResponse<PocketBaseRecordPage<JsonObject>> = decode(
            client.get("$baseUrl/api/collections/$collection/records?page=$page&perPage=$perPage" + ownerFilter()),
        ) { text -> json.decodeFromString<PocketBaseRecordPage<JsonObject>>(text) }
        val pageBody = response.body?.let { body ->
            body.copy(items = body.items.map(::requireOwnedRecord))
        }
        return response.copy(body = pageBody)
    }

    suspend fun findByLocalId(collection: String, localId: String): GatewayResponse<JsonObject?> {
        val response: GatewayResponse<PocketBaseRecordPage<JsonObject>> = decode(
            client.get(
                "$baseUrl/api/collections/$collection/records?perPage=1&filter=" +
                    encodeQuery(
                        ownerBinding?.let {
                            PocketBaseFilter.ownerAndLocalIdEquals(it.accountId, localId)
                        } ?: PocketBaseFilter.localIdEquals(localId)
                    )
            )
        ) { text -> json.decodeFromString<PocketBaseRecordPage<JsonObject>>(text) }
        return GatewayResponse(
            status = response.status,
            body = response.body?.items?.firstOrNull()?.let(::requireOwnedRecord),
            rawBody = response.rawBody,
        )
    }

    suspend fun getRecord(collection: String, recordId: String): GatewayResponse<JsonObject> =
        decodeRecord(client.get("$baseUrl/api/collections/$collection/records/$recordId"))

    suspend fun createJson(collection: String, body: JsonObject): GatewayResponse<JsonObject> =
        decodeRecord(client.post("$baseUrl/api/collections/$collection/records") {
            headers.append(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(ownedBody(body).toString())
        })

    suspend fun updateJson(collection: String, recordId: String, body: JsonObject): GatewayResponse<JsonObject> =
        decodeRecord(client.patch("$baseUrl/api/collections/$collection/records/$recordId") {
            headers.append(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(ownedBody(body).toString())
        })

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
        decodeRecord(client.post("$baseUrl/api/collections/attachments/records") {
            setBody(attachmentContent(ownedBody(body), fileName, bytes))
        })

    /** Guarded multipart update for an active attachment. */
    suspend fun updateAttachment(
        recordId: String,
        body: JsonObject,
        fileName: String,
        bytes: ByteArray,
    ): GatewayResponse<JsonObject> =
        decodeRecord(client.patch("$baseUrl/api/collections/attachments/records/$recordId") {
            setBody(attachmentContent(ownedBody(body), fileName, bytes))
        })

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
        val cleanBody = ownedBody(body).withoutFileFields()
        val response = if (currentRemoteFileName.isNullOrBlank()) {
            updateJson("attachments", recordId, cleanBody)
        } else {
            decodeRecord(client.patch("$baseUrl/api/collections/attachments/records/$recordId") {
                setBody(
                    MultiPartFormDataContent(
                        formData {
                            cleanBody.forEach { (key, value) -> append(key, value.jsonPrimitive.content) }
                            append("file-", currentRemoteFileName)
                        }
                    )
                )
            })
        }
        return response.requireBlankAttachmentFile()
    }

    /**
     * Downloads a protected attachment with one retry only after the server
     * confirms that the current file token is no longer accepted.
     */
    suspend fun downloadProtectedFile(recordId: String, fileName: String): GatewayResponse<ByteArray> {
        if (ownerBinding == null) {
            throw IllegalStateException("Protected attachment downloads require an active account boundary")
        }
        val tokenProvider = fileTokenProvider
            ?: throw IllegalStateException("Protected attachment downloads require an active account boundary")
        val first = downloadFile(recordId, fileName, tokenProvider.currentToken())
        if (first.isSuccess || !isConfirmedFileTokenExpiry(first.status)) return first
        return downloadFile(recordId, fileName, tokenProvider.refreshToken())
    }

    private suspend fun downloadFile(
        recordId: String,
        fileName: String,
        token: String,
    ): GatewayResponse<ByteArray> {
        val response = client.get {
            url {
                path("api", "files", "attachments", recordId, fileName)
                parameters.append("token", token)
            }
        }
        val bytes = if (response.status.value in 200..299) response.bodyAsBytes() else null
        return GatewayResponse(response.status, bytes, "")
    }

    private suspend fun <T> decode(response: HttpResponse, decode: (String) -> T?): GatewayResponse<T> {
        val text = response.bodyAsText()
        return GatewayResponse(
            status = response.status,
            body = if (response.status.value in 200..299) decode(text) else null,
            rawBody = text,
        )
    }

    private suspend fun decodeRecord(response: HttpResponse): GatewayResponse<JsonObject> {
        val decoded: GatewayResponse<JsonObject> = decode(response) { text ->
            json.decodeFromString<JsonObject>(text)
        }
        return decoded.copy(body = decoded.body?.let(::requireOwnedRecord))
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

    private fun ownedBody(body: JsonObject): JsonObject {
        val binding = ownerBinding ?: return body
        val supplied = body["account"]
        if (supplied != null && supplied !is JsonPrimitive) {
            throw PocketBaseOwnerMismatchException("PocketBase mutation owner is not a scalar account id")
        }
        val suppliedId = (supplied as? JsonPrimitive)?.contentOrNull
        if (suppliedId != null && suppliedId != binding.accountId) {
            throw PocketBaseOwnerMismatchException("PocketBase mutation owner does not match the active account")
        }
        return kotlinx.serialization.json.buildJsonObject {
            body.forEach { (key, value) -> put(key, value) }
            put("account", JsonPrimitive(binding.accountId))
        }
    }

    private fun requireOwnedRecord(record: JsonObject): JsonObject {
        val binding = ownerBinding ?: return record
        val owner = record["account"]
        val ownerId = when (owner) {
            is JsonPrimitive -> owner.contentOrNull
            is JsonObject -> owner["id"]?.jsonPrimitive?.contentOrNull
            else -> null
        }
        if (ownerId != binding.accountId) {
            throw PocketBaseOwnerMismatchException(
                "PocketBase returned a record outside the active account boundary",
            )
        }
        return record
    }

    private fun ownerFilter(): String = ownerBinding?.let {
        "&filter=${encodeQuery(PocketBaseFilter.accountEquals(it.accountId))}"
    }.orEmpty()

    private fun isConfirmedFileTokenExpiry(status: HttpStatusCode): Boolean =
        status == HttpStatusCode.Unauthorized || status == HttpStatusCode.Forbidden

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

internal fun safePocketBaseFailureSummary(rawBody: String): String {
    val response = runCatching {
        Json.parseToJsonElement(rawBody) as? JsonObject
    }.getOrNull() ?: return "validation=unavailable"
    val validation = response["data"] as? JsonObject
        ?: return "validation=none"
    if (validation.isEmpty()) return "validation=none"
    val fieldCodes = validation.entries
        .sortedBy { it.key }
        .joinToString(",") { (field, error) ->
            val code = (error as? JsonObject)
                ?.get("code")
                ?.jsonPrimitive
                ?.contentOrNull
                ?: "unknown"
            "$field:$code"
        }
    return "validation=$fieldCodes"
}

@Serializable
data class PocketBaseRecordPage<T>(val items: List<T> = emptyList(), val page: Int = 1, val totalPages: Int = 1)

@Serializable
data class PocketBaseSyncMeta(
    val capabilityVersion: Int = 0,
    val serverInstanceId: String = "",
    val legacyOwnerAccount: String? = null,
    val legacyEndpoint: String? = null,
)
