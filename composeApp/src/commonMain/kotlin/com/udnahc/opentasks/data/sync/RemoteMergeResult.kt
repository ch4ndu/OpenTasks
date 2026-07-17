package com.udnahc.opentasks.data.sync

/** Result of a single Room-writer remote merge. */
enum class RemoteMergeResult {
    Applied,
    KeptLocal,
    MissingParent,
}
