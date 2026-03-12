package com.udnahc.opentasks.navigation

import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.EntryProviderScope
import kotlin.jvm.JvmSuppressWildcards
import kotlin.reflect.KClass

@Suppress("UNCHECKED_CAST")
inline fun <reified T : Any> EntryProviderScope<*>.entry(
    noinline clazzContentKey: (key: @JvmSuppressWildcards T) -> Any = { it.toString() },
    metadata: Map<String, Any> = emptyMap(),
    noinline content: @Composable (T) -> Unit,
) {
    addEntryProvider(clazz = T::class as KClass<out Nothing>, clazzContentKey, metadata, content)
}
