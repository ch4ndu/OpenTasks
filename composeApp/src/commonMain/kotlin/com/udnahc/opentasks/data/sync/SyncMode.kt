package com.udnahc.opentasks.data.sync

enum class SyncMode {
    NORMAL,
    EMPTY_SERVER_SEED_PENDING,
    AUTHORITATIVE_REPLACE_PENDING,
}

object SyncSettingsKeys {
    const val SERVER_INSTANCE_ID = "pocketbase_server_instance_id"
    const val MODE = "pocketbase_sync_mode"
}
