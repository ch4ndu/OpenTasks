package com.udnahc.opentasks.data.sync

import com.udnahc.opentasks.data.auth.AccountBoundary

class SyncAdapterException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

class SyncDegradedException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

/**
 * A structured authenticated PocketBase request received HTTP 401. This is
 * deliberately distinct from forbidden, timeout, and degraded-sync failures:
 * only a confirmed authentication rejection may transition the account shell
 * to reauthentication.
 */
class SyncAuthenticationRejectedException(
    cause: Throwable? = null,
) : Exception("PocketBase authentication was rejected during sync", cause)

data class SyncCollectionFailure(
    val collectionName: String,
    val operation: String,
    val cause: Throwable,
    val boundary: AccountBoundary? = null,
)

class SyncException(
    val failures: List<SyncCollectionFailure>,
    val boundary: AccountBoundary? = failures.firstOrNull()?.boundary,
) : Exception(
    failures.joinToString(
        prefix = "Sync failed: ",
        separator = "; ",
    ) { it.safeDiagnosticLabel() },
    failures.firstOrNull()?.cause,
)

class PocketBaseConnectionException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

class PocketBaseOwnerMismatchException(message: String) : IllegalStateException(message)

/** Preserves a confirmed authentication rejection through adapter/service wrappers. */
internal fun Throwable.findSyncAuthenticationRejected(): SyncAuthenticationRejectedException? =
    generateSequence(this) { it.cause }
        .filterIsInstance<SyncAuthenticationRejectedException>()
        .firstOrNull()

internal fun Throwable.rethrowSyncAuthenticationRejected() {
    findSyncAuthenticationRejected()?.let { throw it }
}

private fun SyncCollectionFailure.safeDiagnosticLabel(): String {
    val safeOperation = operation.takeIf(SAFE_SYNC_OPERATIONS::contains) ?: "operation"
    val safeCollection = collectionName.takeIf(SAFE_SYNC_COLLECTIONS::contains) ?: "collection"
    return "$safeOperation $safeCollection"
}

private val SAFE_SYNC_OPERATIONS = setOf("initial_pull", "pull", "push", "verify")
private val SAFE_SYNC_COLLECTIONS = setOf(
    "categories",
    "tags",
    "tasks",
    "attachments",
    "task_tags",
    "notes",
    "countdowns",
)
