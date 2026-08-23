package com.udnahc.opentasks.navigation

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.serialization.NavBackStackSerializer
import kotlinx.serialization.PolymorphicSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class NavigationSerializationTest {
    @Test
    fun everyScreenSubtypeRoundTripsWithTheSaveableBackStackSerializer() {
        val screens: List<NavKey> = listOf(
            Screen.Matrix,
            Screen.TaskList,
            Screen.Calendar,
            Screen.Notes,
            Screen.QuadrantDetail(priorityOrdinal = 2),
            Screen.CreateTask(
                priorityOrdinal = 3,
                categoryId = "projects",
                day = 9,
                month = 8,
                year = 2030,
                title = "Saved title",
                description = "Saved description",
                url = "https://example.test/task",
            ),
            Screen.QuickAddTask(
                priorityOrdinal = 1,
                categoryId = "work",
                day = 10,
                month = 8,
                year = 2030,
            ),
            Screen.EditTask(taskId = "task-id"),
            Screen.Settings,
            Screen.Countdown,
            Screen.CreateCountdown(typeOrdinal = 1),
            Screen.CountdownDetail(countdownId = "countdown-id"),
            Screen.EditCountdown(countdownId = "other-countdown-id"),
        )
        val serializer = NavBackStackSerializer<NavKey>(PolymorphicSerializer(NavKey::class))
        val json = Json { serializersModule = screenNavSerializersModule }
        val encoded = json.encodeToJsonElement(serializer, NavBackStack(*screens.toTypedArray()))
        val restored = json.decodeFromJsonElement(serializer, encoded)

        assertEquals(screens, restored.toList())
    }

    @Test
    fun anUnregisteredNavKeyCannotBeSaved() {
        val serializer = NavBackStackSerializer<NavKey>(PolymorphicSerializer(NavKey::class))

        assertFailsWith<SerializationException> {
            Json.encodeToJsonElement(serializer, NavBackStack<NavKey>(Screen.Matrix))
        }
    }
}
