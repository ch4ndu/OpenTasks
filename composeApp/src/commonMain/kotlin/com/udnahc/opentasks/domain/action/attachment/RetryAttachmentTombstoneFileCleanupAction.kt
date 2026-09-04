package com.udnahc.opentasks.domain.action.attachment

import com.udnahc.opentasks.data.attachment.AttachmentTombstoneFileCleanup
import com.udnahc.opentasks.data.auth.AccountMutationGate

class RetryAttachmentTombstoneFileCleanupAction(
    private val cleanup: AttachmentTombstoneFileCleanup,
    private val mutationGate: AccountMutationGate,
) {
    suspend operator fun invoke() = mutationGate.withExclusive {
        cleanup.retryAllRetainingRows()
    }
}
