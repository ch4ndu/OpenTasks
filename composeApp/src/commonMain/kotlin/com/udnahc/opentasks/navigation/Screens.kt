package com.udnahc.opentasks.navigation

import kotlinx.serialization.Serializable

sealed class Screens {
    @Serializable
    data object Home : NavClass()
}
