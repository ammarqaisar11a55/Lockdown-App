package com.example.focuslock.domain.model

/** A package the user permitted during lockdown. */
data class AllowedApplication(
    val packageName: String,
    val label: String,
)

/** A launchable application installed on the device. */
data class InstalledApplication(
    val packageName: String,
    val label: String,
    /** True for functions Android keeps available regardless of the allowlist (e.g. the dialer). */
    val essential: Boolean,
)
