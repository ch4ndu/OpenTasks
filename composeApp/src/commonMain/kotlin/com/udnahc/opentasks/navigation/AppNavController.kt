package com.udnahc.opentasks.navigation

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.diamondedge.logging.logging

class AppNavController(private val backStack: NavBackStack<NavKey>) {

    val currentScreen: NavKey
        get() = backStack.last()

    val previousScreen: NavKey?
        get() = backStack.getOrNull(backStack.size - 2)

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

    companion object {
        private val log = logging("AppNavController")
    }
}

private fun NavKey.routeDiagnosticName(): String =
    this::class.simpleName ?: "UnknownRoute"
