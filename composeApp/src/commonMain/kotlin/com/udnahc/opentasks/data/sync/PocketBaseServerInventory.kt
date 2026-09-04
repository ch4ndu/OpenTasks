package com.udnahc.opentasks.data.sync

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** Read-only, complete server snapshot used before adopting a candidate endpoint. */
data class PocketBaseServerInventory(
    val serverInstanceId: String,
    val recordsByCollection: Map<String, List<JsonObject>>,
    val accountId: String? = null,
) {
    val isEmpty: Boolean get() = recordsByCollection.values.all { it.isEmpty() }

    fun replacementCounts(): List<ReplacementCollectionCount> = COLLECTIONS.map { collection ->
        val rows = recordsByCollection[collection].orEmpty()
        ReplacementCollectionCount(
            collection = collection,
            active = rows.count { !it.isDeletedRecord() },
            tombstones = rows.count { it.isDeletedRecord() },
        )
    }

    fun replacementFingerprint(canonicalEndpoint: String, accountId: String): String {
        val parts = buildList {
            add(canonicalEndpoint)
            add(serverInstanceId)
            add(accountId)
            for (collection in COLLECTIONS) {
                add(collection)
                recordsByCollection[collection].orEmpty()
                    .map { it.canonicalFingerprintText() }
                    .sorted()
                    .forEach(::add)
            }
        }
        return opaqueFingerprint(parts)
    }

    companion object {
        val COLLECTIONS: List<String> get() = PocketBaseServerInventoryReader.COLLECTIONS
    }
}

data class ReplacementCollectionCount(
    val collection: String,
    val active: Int,
    val tombstones: Int,
) {
    val total: Int get() = active + tombstones
}

internal fun opaqueFingerprint(parts: Iterable<String>): String {
    var first = 0xcbf29ce484222325UL
    var second = 0x9e3779b97f4a7c15UL
    for (part in parts) {
        for (byte in part.encodeToByteArray()) {
            first = (first xor byte.toUByte().toULong()) * 0x100000001b3UL
            second = (second xor (byte.toUByte().toULong() + first)) * 0x9e3779b185ebca87UL
        }
        first = (first xor 0xffUL) * 0x100000001b3UL
        second = (second xor first) * 0x9e3779b185ebca87UL
    }
    return first.toString(16).padStart(16, '0') + second.toString(16).padStart(16, '0')
}

private fun JsonObject.isDeletedRecord(): Boolean {
    val raw = this["isDeleted"] as? JsonPrimitive ?: return false
    return raw.contentOrNull?.toBooleanStrictOrNull() == true
}

class PocketBaseServerInventoryReader(
    private val gateway: PocketBaseRecordGateway,
) {
    suspend fun read(): PocketBaseServerInventory {
        val capability = gateway.getCapability()
        val meta = capability.body
            ?: throw PocketBaseConnectionException("PocketBase sync capability is unavailable (HTTP ${capability.status.value})")
        if (meta.capabilityVersion != CAPABILITY_VERSION || meta.serverInstanceId.isBlank()) {
            throw PocketBaseConnectionException("PocketBase sync capability is unsupported")
        }
        val inventories = COLLECTIONS.associateWith { collection ->
            readAll(collection)
        }
        return PocketBaseServerInventory(meta.serverInstanceId, inventories, gateway.ownerAccountId)
    }

    private suspend fun readAll(collection: String): List<JsonObject> {
        val rows = mutableListOf<JsonObject>()
        val pagination = PocketBasePaginationGuard(PAGE_SIZE)
        var page = 1
        do {
            val response = gateway.getRecords(collection, page, PAGE_SIZE)
            val result = response.body
                ?: throw PocketBaseConnectionException("Unable to inventory $collection (HTTP ${response.status.value})")
            pagination.accept(page, result)
            rows += result.items
            page += 1
        } while (page <= result.totalPages)
        return rows
    }

    companion object {
        const val CAPABILITY_VERSION = 2
        const val PAGE_SIZE = 200
        val COLLECTIONS = listOf("categories", "tags", "tasks", "attachments", "task_tags", "notes", "countdowns")
    }
}
