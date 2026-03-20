package com.udnahc.opentasks.navigation

import androidx.navigation3.runtime.NavKey

open class NavClass : NavKey {
    override fun toString(): String = this::class.simpleName ?: "NavClass"
}
