package com.example.focuslock.feature.developer

import androidx.compose.runtime.Composable

/** Debug-only developer tools. The release source set provides an empty stand-in. */
object DeveloperEntry {
    const val AVAILABLE = true

    @Composable
    fun Screen(onBack: () -> Unit) = DeveloperScreen(onBack)
}
