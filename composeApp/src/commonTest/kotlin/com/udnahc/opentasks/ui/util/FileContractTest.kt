package com.udnahc.opentasks.ui.util

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FileContractTest {
    @Test
    fun importTypeAcceptsOnlyItsExtensionCaseInsensitively() {
        assertTrue(ImportFileType.CSV.accepts("tasks.CSV"))
        assertTrue(ImportFileType.ICS.accepts("calendar.ics"))
        assertFalse(ImportFileType.CSV.accepts("calendar.ics"))
        assertFalse(ImportFileType.ICS.accepts("calendar.txt"))
    }
}
