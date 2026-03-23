package com.udnahc.opentasks.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import com.udnahc.opentasks.data.calendar.CalendarPermissionStatus
import com.udnahc.opentasks.data.calendar.IosCalendarProvider
import kotlinx.coroutines.launch

@Composable
actual fun rememberCalendarPermissionLauncher(onResult: (Boolean) -> Unit): () -> Unit {
    val scope = rememberCoroutineScope()
    val provider = IosCalendarProvider()
    return {
        scope.launch {
            val status = provider.requestPermission()
            onResult(status == CalendarPermissionStatus.GRANTED)
        }
    }
}
