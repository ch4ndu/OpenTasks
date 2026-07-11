package com.udnahc.opentasks.viewmodel

import com.udnahc.opentasks.ui.util.FileExportResult
import kotlin.test.Test
import kotlin.test.assertEquals

class ExportResultTest {
    @Test
    fun cancellationIsNeutralWhileCompletionAndFailureAreReported() {
        assertEquals(ExportResult.Idle, FileExportResult.Cancelled.toUiResult(count = 3))
        assertEquals(ExportResult.Success(3), FileExportResult.Completed.toUiResult(count = 3))
        assertEquals(ExportResult.Error, FileExportResult.Error().toUiResult(count = 3))
    }
}
