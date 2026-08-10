package com.udnahc.opentasks.data.sync

/** Read-only, complete server snapshot used before adopting a candidate endpoint. */
data class PocketBaseServerInventory(
    val serverInstanceId: String,
    val recordsByCollection: Map<String, List<kotlinx.serialization.json.JsonObject>>,
    val accountId: String? = null,
) {
    val isEmpty: Boolean get() = recordsByCollection.values.all { it.isEmpty() }
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
        val inventories = COLLECTIONS.associateWith { collection -> readAll(collection) }
        return PocketBaseServerInventory(meta.serverInstanceId, inventories, gateway.ownerAccountId)
    }

    private suspend fun readAll(collection: String): List<kotlinx.serialization.json.JsonObject> {
        val rows = mutableListOf<kotlinx.serialization.json.JsonObject>()
        var page = 1
        do {
            val response = gateway.getRecords(collection, page, PAGE_SIZE)
            val result = response.body
                ?: throw PocketBaseConnectionException("Unable to inventory $collection (HTTP ${response.status.value})")
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
