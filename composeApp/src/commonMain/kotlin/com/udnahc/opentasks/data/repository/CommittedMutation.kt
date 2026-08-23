package com.udnahc.opentasks.data.repository

/**
 * The value returned by a Room write is authoritative once the writer
 * transaction has committed.  Work performed after that commit (for example
 * a sync trigger or reminder maintenance) may still report a warning without
 * changing the committed result into a failed write.
 */
enum class PostCommitWarningPhase {
    SYNC,
    REMINDER_MAINTENANCE,
    COMBINED,
    OTHER,
}

data class PostCommitWarning(
    val cause: Throwable,
    val phase: PostCommitWarningPhase,
)

data class CommittedMutation<T>(
    val value: T,
    val postCommitWarning: PostCommitWarning? = null,
) {
    fun withPostCommitWarning(
        warning: Throwable?,
        phase: PostCommitWarningPhase = PostCommitWarningPhase.OTHER,
    ): CommittedMutation<T> {
        if (warning == null) return this
        val merged = postCommitWarning?.let { current ->
            PostCommitWarning(
                cause = IllegalStateException(
                    "Multiple post-commit maintenance warnings",
                    current.cause,
                ).also {
                    it.addSuppressed(warning)
                },
                phase = if (current.phase == phase) phase else PostCommitWarningPhase.COMBINED,
            )
        } ?: PostCommitWarning(warning, phase)
        return copy(postCommitWarning = merged)
    }
}

fun <T, R> CommittedMutation<T>.mapValue(transform: (T) -> R): CommittedMutation<R> =
    CommittedMutation(transform(value), postCommitWarning)
