package com.example.focuslock.core.common

import android.util.Log
import com.example.focuslock.BuildConfig
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.ArrayDeque
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Structured logging. Debug builds log verbosely and keep a small in-memory buffer for the
 * developer screen; release builds only emit warnings and errors. Callers must never pass
 * personal data (app lists, session names) in messages.
 */
interface FocusLogger {
    fun debug(tag: String, message: String)
    fun info(tag: String, message: String)
    fun warn(tag: String, message: String)
    fun error(tag: String, message: String, throwable: Throwable? = null)
    fun recentEntries(): List<String>
}

@Singleton
class AndroidFocusLogger @Inject constructor() : FocusLogger {
    private val verbose = BuildConfig.DEBUG
    private val buffer = ArrayDeque<String>(BUFFER_SIZE)
    private val timeFormat = DateTimeFormatter.ofPattern("HH:mm:ss")

    override fun debug(tag: String, message: String) {
        if (!verbose) return
        Log.d(tag, message)
        remember(tag, message)
    }

    override fun info(tag: String, message: String) {
        if (!verbose) return
        Log.i(tag, message)
        remember(tag, message)
    }

    override fun warn(tag: String, message: String) {
        Log.w(tag, message)
        remember(tag, message)
    }

    override fun error(tag: String, message: String, throwable: Throwable?) {
        Log.e(tag, message, throwable)
        remember(tag, "$message ${throwable?.javaClass?.simpleName.orEmpty()}".trim())
    }

    override fun recentEntries(): List<String> = synchronized(buffer) { buffer.toList() }

    private fun remember(tag: String, message: String) {
        if (!verbose) return
        synchronized(buffer) {
            if (buffer.size == BUFFER_SIZE) buffer.removeFirst()
            buffer.addLast("[${LocalTime.now().format(timeFormat)}] $tag  $message")
        }
    }

    private companion object {
        const val BUFFER_SIZE = 200
    }
}
