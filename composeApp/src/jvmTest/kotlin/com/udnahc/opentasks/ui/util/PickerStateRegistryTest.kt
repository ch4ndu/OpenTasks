package com.udnahc.opentasks.ui.util

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.udnahc.opentasks.viewmodel.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class PickerStateRegistryTest : MainDispatcherRule() {
    @Test
    fun retainedHostAndSavedTokenRecoverTheSamePendingPicker() = runTest(dispatcher) {
        val host = hostOwner()
        val registry = registry(host)
        val owner = registry.owner("saved-token")
        val picker = picker(owner).apply { pendingResult = "selected-file" }
        val work = picker.viewModelScope.launch { awaitCancellation() }
        runCurrent()

        // A recreated Activity keeps its ViewModelStore; only the saveable token is restored.
        val recreatedHost = object : ViewModelStoreOwner {
            override val viewModelStore = host.viewModelStore
        }
        val restoredRegistry = registry(recreatedHost)
        val restoredOwner = restoredRegistry.owner("saved-token")
        assertSame(registry, restoredRegistry)
        assertSame(owner, restoredOwner)
        assertSame(picker, picker(restoredOwner))
        assertEquals("selected-file", picker(restoredOwner).pendingResult)
        assertTrue(work.isActive)
        assertEquals(1, registry.retainedOwnerCount)
        host.viewModelStore.clear()
    }

    @Test
    fun repeatedVisitsRemoveAndClearEveryRetiredOwnerWithoutTouchingAnotherPicker() = runTest(dispatcher) {
        val registry = PickerStateRegistry()
        val otherOwner = registry.owner("other-dialog")
        val otherPicker = picker(otherOwner)
        val otherWork = otherPicker.viewModelScope.launch { awaitCancellation() }
        runCurrent()

        repeat(100) { visit ->
            val token = "visit-$visit"
            val owner = registry.owner(token)
            val picker = picker(owner)
            val work = picker.viewModelScope.launch { awaitCancellation() }
            runCurrent()

            registry.retire(token, owner)

            assertTrue(picker.wasCleared)
            assertTrue(work.isCancelled)
            assertEquals(1, registry.retainedOwnerCount)
            assertFalse(otherPicker.wasCleared)
            assertTrue(otherWork.isActive)
        }

        registry.retire("other-dialog", otherOwner)
        assertEquals(0, registry.retainedOwnerCount)
        assertTrue(otherPicker.wasCleared)
        assertTrue(otherWork.isCancelled)
    }

    @Test
    fun returningSavedTokenGetsNewStateAndOldDisposalCannotRetireIt() {
        val registry = PickerStateRegistry()
        val oldOwner = registry.owner("saved-token")
        val oldPicker = picker(oldOwner).apply { pendingResult = "old-file" }
        registry.retire("saved-token", oldOwner)

        val newOwner = registry.owner("saved-token")
        val newPicker = picker(newOwner)
        assertNotSame(oldOwner, newOwner)
        assertNotSame(oldPicker, newPicker)
        assertEquals(null, newPicker.pendingResult)

        registry.retire("saved-token", oldOwner)
        assertSame(newOwner, registry.owner("saved-token"))
        assertFalse(newPicker.wasCleared)
        assertEquals(1, registry.retainedOwnerCount)
        registry.retire("saved-token", newOwner)
    }

    @Test
    fun finalHostDestructionClearsAllRemainingPickersAndTheirScopes() = runTest(dispatcher) {
        val host = hostOwner()
        val registry = registry(host)
        val first = picker(registry.owner("import"))
        val second = picker(registry.owner("image"))
        val firstWork = first.viewModelScope.launch { awaitCancellation() }
        val secondWork = second.viewModelScope.launch { awaitCancellation() }
        runCurrent()

        host.viewModelStore.clear()

        assertEquals(0, registry.retainedOwnerCount)
        assertTrue(first.wasCleared)
        assertTrue(second.wasCleared)
        assertTrue(firstWork.isCancelled)
        assertTrue(secondWork.isCancelled)
    }

    private fun hostOwner() = object : ViewModelStoreOwner {
        override val viewModelStore = ViewModelStore()
    }

    private fun registry(owner: ViewModelStoreOwner): PickerStateRegistry = ViewModelProvider.create(
        owner,
        viewModelFactory { initializer { PickerStateRegistry() } },
    )[PickerStateRegistry::class]

    private fun picker(owner: ViewModelStoreOwner): ProbePicker = ViewModelProvider.create(
        owner,
        viewModelFactory { initializer { ProbePicker() } },
    )[ProbePicker::class]

    private class ProbePicker : ViewModel() {
        var pendingResult: String? = null
        var wasCleared = false
            private set

        override fun onCleared() {
            wasCleared = true
            pendingResult = null
        }
    }
}
