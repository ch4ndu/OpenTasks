package com.udnahc.opentasks.ui.screens

import kotlin.test.Test
import kotlin.test.assertEquals

class SyncPullToRefreshContractTest {
    @Test
    fun remoteModeRetainsTheRefreshWrapperAndProgressState() {
        assertEquals(
            SyncPullToRefreshContract(wrapsContent = true, isRefreshing = true),
            syncPullToRefreshContract(enabled = true, isRefreshing = true),
        )
    }

    @Test
    fun localModeRendersContentWithoutRefreshGestureOrProgressState() {
        assertEquals(
            SyncPullToRefreshContract(wrapsContent = false, isRefreshing = false),
            syncPullToRefreshContract(enabled = false, isRefreshing = true),
        )
    }
}
