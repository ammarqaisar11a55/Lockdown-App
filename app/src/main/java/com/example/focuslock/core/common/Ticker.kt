package com.example.focuslock.core.common

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Emits the wall-clock time aligned to [periodMs] boundaries.
 *
 * Only for UI: collected with lifecycle awareness, so it stops when the screen is not visible.
 * Background timing always uses AlarmManager.
 */
fun wallClockTicker(periodMs: Long): Flow<Long> = flow {
    while (true) {
        val now = System.currentTimeMillis()
        emit(now)
        delay(periodMs - now % periodMs)
    }
}

const val TICK_SECOND_MS = 1_000L
const val TICK_MINUTE_MS = 60_000L
