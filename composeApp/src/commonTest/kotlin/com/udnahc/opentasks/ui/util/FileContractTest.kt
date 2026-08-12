package com.udnahc.opentasks.ui.util

import com.udnahc.opentasks.ExternalInputFailure
import com.udnahc.opentasks.ExternalInputPolicy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FileContractTest {
    @Test
    fun importTypeAcceptsOnlyItsExtensionCaseInsensitively() {
        assertTrue(ImportFileType.CSV.accepts("tasks.CSV"))
        assertTrue(ImportFileType.ICS.accepts("calendar.ics"))
        assertFalse(ImportFileType.CSV.accepts("calendar.ics"))
        assertFalse(ImportFileType.ICS.accepts("calendar.txt"))
    }

    @Test
    fun externalInputPolicyUsesExactUtf8ByteBoundaries() {
        assertEquals(2, ExternalInputPolicy.utf8ByteCountUpTo("é", 1))
        assertNull(ExternalInputPolicy.validateImportByteCount(ExternalInputPolicy.MAX_IMPORT_BYTES))
        assertEquals(
            ExternalInputFailure.TOO_LARGE,
            ExternalInputPolicy.validateImportByteCount(ExternalInputPolicy.MAX_IMPORT_BYTES + 1),
        )

        val exactShareDescription = "a".repeat(
            ExternalInputPolicy.MAX_SHARE_PAYLOAD_BYTES - "shared.ics".length,
        )
        assertNull(
            ExternalInputPolicy.validateSharePayload(
                description = exactShareDescription,
                url = "",
                icsContent = "",
                icsFileName = "shared.ics",
            ),
        )
        assertEquals(
            ExternalInputFailure.TOO_LARGE,
            ExternalInputPolicy.validateSharePayload(
                description = "$exactShareDescription a",
                url = "",
                icsContent = "",
                icsFileName = "shared.ics",
            ),
        )
    }

    @Test
    fun externalInputPolicyRejectsTooManyItemsAndMalformedUtf8() {
        assertNull(ExternalInputPolicy.validateShareItemCount(ExternalInputPolicy.MAX_SHARE_ITEMS))
        assertEquals(
            ExternalInputFailure.TOO_MANY_ITEMS,
            ExternalInputPolicy.validateShareItemCount(ExternalInputPolicy.MAX_SHARE_ITEMS + 1),
        )
        assertTrue(ExternalInputPolicy.isStrictUtf8("valid".encodeToByteArray()))
        assertFalse(ExternalInputPolicy.isStrictUtf8(byteArrayOf(0xC3.toByte(), 0x28)))
    }
}
