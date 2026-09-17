package com.example.focuslock.feature.developer

import androidx.compose.runtime.Composable

/** Release builds contain no developer tooling at all. */
object DeveloperEntry {
    const val AVAILABLE = false

    @Suppress("UNUSED_PARAMETER")
    @Composable
    fun Screen(onBack: () -> Unit) = Unit
}
