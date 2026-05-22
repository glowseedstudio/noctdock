package com.glowseed.noctdock.core

import android.util.Log

/**
 * Central logging gate for NoctDock apps.
 *
 * Stream metric lines use [debug] and are enabled only when `BuildConfig.NOCT_DEBUG_LOGS` is true.
 * Release and perf builds keep [info] off by default to avoid recurring encoder/network spam in logcat.
 */
object NoctLog {
    private const val LOG_PREFIX = "NoctDock"
    private const val MAX_BUFFER_ENTRIES = 200
    private const val MAX_THROWABLE_CHARS = 2_048

    @Volatile
    private var debugEnabled = false

    @Volatile
    private var infoEnabled = true

    private val bufferLock = Any()
    private val buffer = ArrayDeque<NoctLogEntry>(MAX_BUFFER_ENTRIES)

    fun configure(debugLogs: Boolean, infoLogs: Boolean = true) {
        debugEnabled = debugLogs
        infoEnabled = infoLogs
    }

    fun recentEntries(): List<NoctLogEntry> = synchronized(bufferLock) { buffer.toList() }

    internal fun clearBufferForTests() {
        synchronized(bufferLock) { buffer.clear() }
    }

    fun debug(tag: String, message: String) {
        if (debugEnabled) {
            Log.d("$LOG_PREFIX/$tag", message)
            record(NoctLogLevel.DEBUG, tag, message)
        }
    }

    fun info(tag: String, message: String) {
        if (debugEnabled || infoEnabled) {
            Log.i("$LOG_PREFIX/$tag", message)
            record(NoctLogLevel.INFO, tag, message)
        }
    }

    fun warn(tag: String, message: String, throwable: Throwable? = null) {
        Log.w("$LOG_PREFIX/$tag", message, throwable)
        record(NoctLogLevel.WARN, tag, message, throwable)
    }

    private fun record(level: NoctLogLevel, tag: String, message: String, throwable: Throwable? = null) {
        val summary = throwable?.let { summarizeThrowable(it) }
        val entry =
            NoctLogEntry(
                epochMillis = System.currentTimeMillis(),
                level = level,
                tag = tag,
                message = message,
                throwableSummary = summary,
            )
        synchronized(bufferLock) {
            if (buffer.size >= MAX_BUFFER_ENTRIES) buffer.removeFirst()
            buffer.addLast(entry)
        }
    }

    internal fun recordForTests(level: NoctLogLevel, tag: String, message: String, throwable: Throwable? = null) {
        record(level, tag, message, throwable)
    }

    private fun summarizeThrowable(throwable: Throwable): String {
        val stack = throwable.stackTraceToString().trim()
        return if (stack.length <= MAX_THROWABLE_CHARS) stack else stack.take(MAX_THROWABLE_CHARS) + "\n…(truncated)"
    }
}
