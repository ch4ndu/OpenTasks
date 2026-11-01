package com.udnahc.opentasks.ui.util

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner

/** One retained host entry, with independently disposable stores for native picker call sites. */
internal class PickerStateRegistry : ViewModel() {
    private val owners = mutableMapOf<String, ViewModelStoreOwner>()

    val retainedOwnerCount: Int get() = owners.size

    fun owner(token: String): ViewModelStoreOwner = owners.getOrPut(token) {
        object : ViewModelStoreOwner {
            override val viewModelStore = ViewModelStore()
        }
    }

    fun retire(token: String, expected: ViewModelStoreOwner) {
        if (owners[token] !== expected) return
        owners.remove(token)
        expected.viewModelStore.clear()
    }

    override fun onCleared() {
        val retired = owners.values.toList()
        owners.clear()
        retired.forEach { it.viewModelStore.clear() }
    }
}
