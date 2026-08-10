package com.udnahc.opentasks

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner

internal class AccountEpochViewModelStoreOwner(
    val boundaryEpoch: Long,
) : ViewModelStoreOwner {
    init {
        require(boundaryEpoch > 0L) { "An account ViewModel store requires a positive boundary epoch" }
    }

    override val viewModelStore = ViewModelStore()

    fun clear() {
        viewModelStore.clear()
    }
}

@Composable
internal fun AccountEpochViewModelStoreProvider(
    boundaryEpoch: Long,
    content: @Composable () -> Unit,
) {
    val owner = remember(boundaryEpoch) { AccountEpochViewModelStoreOwner(boundaryEpoch) }
    DisposableEffect(owner) {
        onDispose(owner::clear)
    }
    CompositionLocalProvider(LocalViewModelStoreOwner provides owner, content = content)
}
