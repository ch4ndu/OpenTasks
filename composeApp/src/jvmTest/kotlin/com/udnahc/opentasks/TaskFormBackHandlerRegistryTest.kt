package com.udnahc.opentasks

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import com.udnahc.opentasks.navigation.Screen

class TaskFormBackHandlerRegistryTest {
    @Test
    fun outgoingFormCannotClearTheHandlerRegisteredByTheIncomingForm() {
        val registry = TaskFormBackHandlerRegistry()
        val firstOwner = Any()
        val secondOwner = Any()
        var firstCalls = 0
        var secondCalls = 0
        var fallbackCalls = 0

        registry.register(firstOwner) { firstCalls++ }
        registry.register(secondOwner) { secondCalls++ }
        registry.register(firstOwner, null)
        registry.handle { fallbackCalls++ }

        assertEquals(0, firstCalls)
        assertEquals(1, secondCalls)
        assertEquals(0, fallbackCalls)
    }

    @Test
    fun fallbackRunsAfterTheActiveFormUnregisters() {
        val registry = TaskFormBackHandlerRegistry()
        val owner = Any()
        var fallbackCalls = 0

        registry.register(owner) {}
        registry.register(owner, null)
        registry.handle { fallbackCalls++ }

        assertEquals(1, fallbackCalls)
    }

    @Test
    fun twoTaskEditorsUseDistinctEntryViewModelKeys() {
        assertNotEquals(
            taskFormViewModelKey(Screen.EditTask("first-task")),
            taskFormViewModelKey(Screen.EditTask("second-task")),
        )
    }
}
