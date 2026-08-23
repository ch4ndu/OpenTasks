package com.udnahc.opentasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.udnahc.opentasks.viewmodel.MainDispatcherRule
import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class AccountEpochViewModelStoreOwnerTest : MainDispatcherRule() {

    @Test
    fun accountEpochChangeClearsThePreviousStoreAndCancelsItsViewModelScope() = runTest(dispatcher) {
        val factory = viewModelFactory { initializer { ProbeViewModel() } }
        val accountAOwner = AccountEpochViewModelStoreOwner(boundaryEpoch = 7L)
        val accountAViewModel = ViewModelProvider.create(accountAOwner, factory)[ProbeViewModel::class]
        accountAViewModel.startWork()
        runCurrent()

        accountAOwner.clear()
        runCurrent()

        val accountBOwner = AccountEpochViewModelStoreOwner(boundaryEpoch = 8L)
        val accountBViewModel = ViewModelProvider.create(accountBOwner, factory)[ProbeViewModel::class]

        assertTrue(accountAViewModel.wasCancelled)
        assertNotSame(accountAViewModel, accountBViewModel)
        accountBOwner.clear()
    }

    @Test
    fun accountOwnedViewModelsOutsideNavigationAreInsideTheEpochOwnedSubtree() {
        val source = locateAppSource().readText()
        val providerStart = source.indexOf("AccountEpochViewModelStoreProvider(")
        val mainScreenStart = source.indexOf("MainScreen(", startIndex = providerStart)

        assertTrue(providerStart >= 0)
        assertTrue(mainScreenStart > providerStart)
        listOf(
            "val noteViewModel: NoteViewModel = koinViewModel()",
            "val matrixViewModel: MatrixViewModel = koinViewModel()",
            "val taskNotificationViewModel: TaskNotificationViewModel = koinViewModel()",
            "val appViewModel: AppViewModel = koinViewModel()",
            "val importCalendarViewModel: ImportCalendarViewModel = koinViewModel()",
            "val importIcsViewModel: ImportIcsViewModel = koinViewModel()",
            "val importCsvViewModel: ImportCsvViewModel = koinViewModel()",
        ).forEach { resolution ->
            assertContains(source, resolution)
        }
        assertEquals(
            1,
            Regex("val matrixViewModel: MatrixViewModel = koinViewModel\\(\\)")
                .findAll(source)
                .count(),
        )
    }

    private fun locateAppSource(): File = listOf(
        File("src/commonMain/kotlin/com/udnahc/opentasks/App.kt"),
        File("composeApp/src/commonMain/kotlin/com/udnahc/opentasks/App.kt"),
    ).firstOrNull(File::isFile) ?: error("Could not locate App.kt for the ViewModel ownership inventory")

    private class ProbeViewModel : ViewModel() {
        var wasCancelled = false
            private set

        fun startWork() {
            viewModelScope.launch {
                try {
                    awaitCancellation()
                } finally {
                    wasCancelled = true
                }
            }
        }
    }
}
