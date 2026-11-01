package com.udnahc.opentasks.domain.time

import com.udnahc.opentasks.data.extensions.todayLocal
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.isActive
import kotlinx.datetime.LocalDate
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

class LocalDaySignal(
    private val currentDate: () -> LocalDate = ::todayLocal,
    private val checkInterval: Duration = 1.minutes,
) {
    private val refreshes = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    private val checks: Flow<Unit> = merge(
        flow {
            emit(Unit)
            while (currentCoroutineContext().isActive) {
                delay(checkInterval)
                emit(Unit)
            }
        },
        refreshes,
    )

    val dates: Flow<LocalDate> = checks
        .map { currentDate() }
        .distinctUntilChanged()

    /** Recheck locale/hour-format on resume even when the civil date has not changed. */
    fun formattingContexts(currentContext: () -> String): Flow<String> = checks
        .map { currentContext() }
        .distinctUntilChanged()

    /** Initial composition snapshot from the same clock source as [dates]. */
    fun snapshot(): LocalDate = currentDate()

    fun refresh() {
        refreshes.tryEmit(Unit)
    }
}
