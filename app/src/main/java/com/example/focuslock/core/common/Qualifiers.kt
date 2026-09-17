package com.example.focuslock.core.common

import javax.inject.Qualifier

/** Process-lifetime scope for work that must outlive a screen (e.g. broadcast handling). */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher

/** True only in debug builds: keeps ADB available during Strict sessions on test devices. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AllowDebugging

/** The runtime application id (differs between debug and release). */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class OwnPackage
