package com.udnahc.opentasks.navigation

import androidx.navigation3.runtime.NavKey
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource

interface Tab : NavKey {
    val title: String
//    val icon: DrawableResource
}