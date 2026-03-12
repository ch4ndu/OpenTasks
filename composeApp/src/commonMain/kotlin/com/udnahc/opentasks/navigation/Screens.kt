package com.udnahc.opentasks.navigation

import kotlinx.serialization.Serializable

sealed class Screens {
    // BottomNav Tabs
    @Serializable
    data class Matrix(

        override val title: String,
//        override val icon: DrawableResource
    ) : Tab

    @Serializable
    data class Inbox(

        override val title: String,
//        override val icon: DrawableResource
    ) : Tab

    @Serializable
    data class Calendar(

        override val title: String,
//        override val icon: DrawableResource
    ) : Tab
}