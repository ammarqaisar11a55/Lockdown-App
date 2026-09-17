package com.example.focuslock.core.common

import android.content.BroadcastReceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** Broadcast receivers get roughly 10 s after goAsync(); stay safely inside that budget. */
private const val RECEIVER_BUDGET_MS = 9_000L

/** Runs [block] off the main thread while keeping the broadcast alive until it completes. */
fun BroadcastReceiver.goAsync(scope: CoroutineScope, block: suspend () -> Unit) {
    val pending = goAsync()
    scope.launch {
        try {
            withTimeoutOrNull(RECEIVER_BUDGET_MS) { block() }
        } finally {
            pending.finish()
        }
    }
}
