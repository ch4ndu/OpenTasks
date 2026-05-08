package com.udnahc.opentasks.data.sync

class SyncAdapterException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

class SyncDegradedException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

data class SyncCollectionFailure(
    val collectionName: String,
    val operation: String,
    val cause: Throwable,
)

class SyncException(
    val failures: List<SyncCollectionFailure>,
) : Exception(
    failures.joinToString(
        prefix = "Sync failed: ",
        separator = "; ",
    ) { "${it.operation} ${it.collectionName}: ${it.cause.message ?: it.cause::class.simpleName}" },
    failures.firstOrNull()?.cause,
)

class PocketBaseConnectionException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)
