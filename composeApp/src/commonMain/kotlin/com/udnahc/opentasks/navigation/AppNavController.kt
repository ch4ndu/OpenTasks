package com.udnahc.opentasks.navigation

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.diamondedge.logging.logging
import kotlin.reflect.KClass

class AppNavController(private val backStack: NavBackStack<NavKey>) {

    val currentScreen: NavKey
        get() = backStack.last()

    val previousScreen: NavKey?
        get() = backStack.getOrNull(backStack.size - 2)

    val topOfBackStack: NavKey
        get() = backStack.first()

    fun navigate(key: NavKey) {
        log.i { "[Navigation] ${key.routeDiagnosticName()}" }
        if (backStack.contains(key)) {
            backStack.remove(key)
        }
        backStack.add(key)
    }

    fun replaceTop(key: NavKey) {
        log.i { "[Navigation replace] ${key.routeDiagnosticName()}" }
        if (backStack.isNotEmpty()) {
            backStack.removeAt(backStack.lastIndex)
        }
        navigate(key)
    }

    fun navigateWithBackStack(
        screen: NavKey,
        vararg backStackScreens: NavKey
    ) {
        log.d {
            "navigateWithBackStack: ${screen.routeDiagnosticName()} " +
                "backStack=${backStackScreens.joinToString { it.routeDiagnosticName() }}"
        }
        clearBackStack()
        backStackScreens.forEach { screen ->
            backStack.add(screen)
        }
        navigate(screen)
    }

    fun popBackStack(): Boolean {
        log.d {
            "Back: from ${currentScreen.routeDiagnosticName()} to " +
                (previousScreen?.routeDiagnosticName() ?: "none")
        }
        val index = backStack.lastIndex
        if (index > 0) {
            backStack.removeAt(index)
            return true
        } else {
            return false
        }
    }

    fun popBackStackTo(
        cls: KClass<*>,
        inclusive: Boolean = false
    ): Boolean {
        log.d { "Popping back stack to ${cls.routeDiagnosticName()}" }
        if (!contains(cls)) {
            log.d { "${cls.routeDiagnosticName()} not on back stack" }
            return false
        }
        var hasPopped = false
        while (backStack.last()::class != cls) {
            val popped = popBackStack()
            hasPopped = popped || hasPopped
        }
        if (inclusive) {
            val popped = popBackStack()
            hasPopped = popped || hasPopped
        }
        return hasPopped
    }

    fun popBackStackToTop() {
        while (backStack.size > 1) {
            popBackStack()
        }
    }

    /**
     * Clear the backStack and navigate to given tab
     */
    fun navigateToTab(tab: NavKey) {
        log.d { "Navigating to tab ${tab.routeDiagnosticName()}" }
        // Pop up to the start destination of the graph to
        // avoid building up a large stack of destinations
        // on the back stack as users select items
        setRoot(tab)
    }

    /**
     * Remove all entries of the backstack and navigate to the given key which will then be the top and only entry in the backstack.
     */
    fun setRoot(key: NavKey) {
        log.d { "setRoot(${key.routeDiagnosticName()})" }
        backStack.clear()
        navigate(key)
    }

    private fun clearBackStack() {
        log.d { "Clearing backStack" }
        backStack.clear()
    }

    fun contains(key: NavKey): Boolean {
        return backStack.contains(key)
    }

    fun contains(cls: KClass<*>): Boolean {
        return find(cls) != null
    }

    fun find(cls: KClass<*>): NavKey? {
        return backStack.find { it::class == cls }
    }

    /**
     * If the lifecycle is not resumed it means this NavBackStackEntry already processed a nav event.
     *
     * This is used to de-duplicate navigation events.
     */
    fun invokeIfCurrent(
        key: NavKey,
        onClick: () -> Unit
    ) = run {
        if (currentScreen == key) {
            onClick.invoke()
        } else {
            log.d {
                "invokeIfCurrent ${key.routeDiagnosticName()} is not current " +
                    currentScreen.routeDiagnosticName()
            }
        }
    }

    companion object {
        private val log = logging("AppNavController")
        fun previewController(): AppNavController = AppNavController(NavBackStack<NavKey>())
    }
}

private fun NavKey.routeDiagnosticName(): String =
    this::class.simpleName ?: "UnknownRoute"

private fun KClass<*>.routeDiagnosticName(): String =
    simpleName ?: "UnknownRoute"
