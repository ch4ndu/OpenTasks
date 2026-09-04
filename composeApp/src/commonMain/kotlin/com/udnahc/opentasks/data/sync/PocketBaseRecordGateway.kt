package com.udnahc.opentasks.data.sync

import com.udnahc.opentasks.ExternalInputPolicy
import com.udnahc.opentasks.data.attachment.AttachmentFilePolicy
import com.udnahc.opentasks.data.attachment.AttachmentFileTooLargeException
import com.udnahc.opentasks.data.auth.CacheBinding
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.delete
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.path
import io.ktor.utils.io.readAvailable
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
            val page = json.decodeFromString<PocketBaseRecordPage<PocketBaseSyncMeta>>(text)
            validatePocketBasePage(requestedPage = 1, perPage = 1, response = page)
            page.items.firstOrNull()
        }

    suspend fun getRecords(
        collection: String,
        page: Int,
        perPage: Int,
    ): GatewayResponse<PocketBaseRecordPage<JsonObject>> {
        require(page >= 1) { "PocketBase page request must be positive" }
        require(perPage in 1..POCKETBASE_MAX_PAGE_SIZE) {
            "PocketBase page size is outside the supported range"
        }
        val response: GatewayResponse<PocketBaseRecordPage<JsonObject>> = decode(
            client.get("$baseUrl/api/collections/$collection/records?page=$page&perPage=$perPage&sort=id" + ownerFilter()),
        ) { text -> json.decodeFromString<PocketBaseRecordPage<JsonObject>>(text) }
        val pageBody = response.body?.let { body ->
            validatePocketBasePage(page, perPage, body)
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
        ) { text ->
            json.decodeFromString<PocketBaseRecordPage<JsonObject>>(text).also { page ->
                validatePocketBasePage(requestedPage = 1, perPage = 1, response = page)
            }
        }
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
     * Deletes only a record obtained from this owner-scoped gateway. The
     * structured inventory row is required so UI state can never supply an
     * arbitrary unscoped record id.
     */
    suspend fun deleteOwnedInventoryRecord(
        collection: String,
        inventoryRecord: JsonObject,
    ): GatewayResponse<Unit> {
        if (ownerBinding == null) {
            throw IllegalStateException("PocketBase hard deletion requires an authenticated owner boundary")
        }
        if (collection !in PocketBaseServerInventoryReader.COLLECTIONS) {
            throw IllegalArgumentException("PocketBase hard deletion rejected an unknown sync collection")
        }
        val owned = requireOwnedRecord(inventoryRecord)
        val recordId = owned["id"]?.jsonPrimitive?.contentOrNull
            ?.takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException("PocketBase inventory record has no id")
        val response = client.delete("$baseUrl/api/collections/$collection/records/$recordId")
        if (response.status == HttpStatusCode.Unauthorized) {
            throw SyncAuthenticationRejectedException()
        }
        val rawBody = readPocketBaseUtf8Body(response, POCKETBASE_SMALL_BODY_MAX_BYTES)
        return GatewayResponse(
            status = response.status,
            body = Unit.takeIf { response.status.value in 200..299 },
            rawBody = rawBody,
        )
    }

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
        val bytes = if (response.status.value in 200..299) {
            readBoundedResponseBytes(response)
        } else {
            readPocketBaseUtf8Body(response, POCKETBASE_SMALL_BODY_MAX_BYTES)
            null
        }
        return GatewayResponse(response.status, bytes, "")
    }

    private suspend fun <T> decode(response: HttpResponse, decode: (String) -> T?): GatewayResponse<T> {
        if (response.status == HttpStatusCode.Unauthorized) {
            throw SyncAuthenticationRejectedException()
        }
        val text = readPocketBaseUtf8Body(
            response = response,
            maxBytes = if (response.status.value in 200..299) {
                POCKETBASE_JSON_BODY_MAX_BYTES
            } else {
                POCKETBASE_SMALL_BODY_MAX_BYTES
            },
        )
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

    /** Reads at most the policy cap plus one sentinel byte without buffering an unbounded response. */
    private suspend fun readBoundedResponseBytes(response: HttpResponse): ByteArray {
        val maxBytes = AttachmentFilePolicy.MAX_UPLOAD_BYTES
        val output = ByteArray((maxBytes + 1L).toInt())
        val channel = response.bodyAsChannel()
        var total = 0
        while (total < output.size) {
            val read = channel.readAvailable(output, total, output.size - total)
            if (read < 0) break
            if (read == 0) continue
            total += read
        }
        if (total > maxBytes) throw AttachmentFileTooLargeException(maxBytes)
        return output.copyOf(total)
    }

    private fun ownedBody(body: JsonObject): JsonObject {
        val binding = ownerBinding ?: return body
        val supplied = body["account"]
        if (supplied != null && supplied !is JsonPrimitive) {
            throw PocketBaseOwnerMismatchException("PocketBase mutation owner is not a scalar account id")
        }
        val suppliedId = supplied?.contentOrNull
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
    return "validation=present,count=${validation.size}"
}

@Serializable
data class PocketBaseRecordPage<T>(val items: List<T> = emptyList(), val page: Int = 1, val totalPages: Int = 1)

internal const val POCKETBASE_JSON_BODY_MAX_BYTES = 16 * 1024 * 1024
internal const val POCKETBASE_SMALL_BODY_MAX_BYTES = 64 * 1024
internal const val POCKETBASE_MAX_PAGE_SIZE = 200
internal const val POCKETBASE_MAX_TOTAL_PAGES = 100
internal const val POCKETBASE_MAX_CUMULATIVE_ROWS = 50_000

internal class PocketBasePaginationBudget {
    private var acceptedRows = 0

    fun reserve(rowCount: Int) {
        if (rowCount > POCKETBASE_MAX_CUMULATIVE_ROWS - acceptedRows) {
            throw PocketBaseConnectionException("PocketBase pagination row limit was exceeded")
        }
        acceptedRows += rowCount
    }
}

internal class PocketBasePaginationGuard(
    private val perPage: Int,
    private val budget: PocketBasePaginationBudget = PocketBasePaginationBudget(),
) {
    private var stableTotalPages: Int? = null

    init {
        require(perPage in 1..POCKETBASE_MAX_PAGE_SIZE) {
            "PocketBase page size is outside the supported range"
        }
    }

    fun accept(requestedPage: Int, response: PocketBaseRecordPage<*>) {
        validatePocketBasePage(requestedPage, perPage, response)
        val expected = stableTotalPages
        if (expected != null && expected != response.totalPages) {
            throw PocketBaseConnectionException("PocketBase pagination changed during the read")
        }
        budget.reserve(response.items.size)
        if (expected == null) stableTotalPages = response.totalPages
    }
}

internal fun validatePocketBasePage(
    requestedPage: Int,
    perPage: Int,
    response: PocketBaseRecordPage<*>,
) {
    if (response.page != requestedPage ||
        response.items.size > perPage ||
        response.totalPages !in 0..POCKETBASE_MAX_TOTAL_PAGES ||
        response.totalPages == 0 && (requestedPage != 1 || response.items.isNotEmpty()) ||
        response.totalPages > 0 && requestedPage > response.totalPages
    ) {
        throw PocketBaseConnectionException("PocketBase pagination response was invalid")
    }
}

internal suspend fun readPocketBaseUtf8Body(
    response: HttpResponse,
    maxBytes: Int,
): String {
    require(maxBytes >= 0 && maxBytes < Int.MAX_VALUE) {
        "PocketBase response byte limit is invalid"
    }
    val output = ByteArray(maxBytes + 1)
    val channel = response.bodyAsChannel()
    var total = 0
    while (total < output.size) {
        val read = channel.readAvailable(output, total, output.size - total)
        if (read < 0) break
        if (read == 0) continue
        total += read
    }
    if (total > maxBytes) {
        throw PocketBaseConnectionException("PocketBase response body exceeded its byte limit")
    }
    val bytes = output.copyOf(total)
    if (!ExternalInputPolicy.isStrictUtf8(bytes)) {
        throw PocketBaseConnectionException("PocketBase response body was not valid UTF-8")
    }
    return bytes.decodeToString()
}

@Serializable
data class PocketBaseSyncMeta(
    val capabilityVersion: Int = 0,
    val authoritativeReplaceVersion: Int = 0,
    val serverInstanceId: String = "",
    val legacyOwnerAccount: String? = null,
    val legacyEndpoint: String? = null,
)
