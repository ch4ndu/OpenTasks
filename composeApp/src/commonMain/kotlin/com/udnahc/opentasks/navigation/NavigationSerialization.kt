package com.udnahc.opentasks.navigation

import androidx.navigation3.runtime.NavKey
import androidx.savedstate.serialization.SavedStateConfiguration
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass

/**
 * The navigation back stack is persisted as a polymorphic NavKey list. Keep
 * this registration next to the route model so adding a destination cannot
 * silently make restored stacks fail to decode.
 */
val screenNavSerializersModule: SerializersModule = SerializersModule {
    polymorphic(NavKey::class) {
        subclass(Screen.Matrix::class)
        subclass(Screen.TaskList::class)
        subclass(Screen.Calendar::class)
        subclass(Screen.Notes::class)
        subclass(Screen.QuadrantDetail::class)
        subclass(Screen.CreateTask::class)
        subclass(Screen.QuickAddTask::class)
        subclass(Screen.EditTask::class)
        subclass(Screen.Settings::class)
        subclass(Screen.Countdown::class)
        subclass(Screen.CreateCountdown::class)
        subclass(Screen.CountdownDetail::class)
        subclass(Screen.EditCountdown::class)
    }
}

val screenNavSavedStateConfiguration: SavedStateConfiguration = SavedStateConfiguration {
    serializersModule = screenNavSerializersModule
}
