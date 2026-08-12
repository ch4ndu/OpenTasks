package com.udnahc.opentasks.ui.screens

import androidx.compose.material3.SnackbarHostState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@OptIn(ExperimentalCoroutinesApi::class)
class QuickAddTaskScreenTest {
    @Test
    fun saveFailureIsConsumedOnlyAfterSnackbarPresentationCompletes() = runTest {
        val hostState = SnackbarHostState()
        var consumedCount = 0

        val presentation = launch {
            showQuickAddSaveFailure(hostState, "Save failed") {
                consumedCount += 1
            }
        }
        runCurrent()

        val snackbar = assertNotNull(hostState.currentSnackbarData)
        assertEquals(0, consumedCount)

        snackbar.dismiss()
        presentation.join()

        assertEquals(1, consumedCount)
    }
}
