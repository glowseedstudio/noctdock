package com.glowseed.noctdock.core

import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

enum class NoctLogLevel {
    DEBUG,
    INFO,
    WARN,
}

data class NoctLogEntry(val epochMillis: Long, val level: NoctLogLevel, val tag: String, val message: String, val throwableSummary: String? = null)

data class NoctSupportReportMetadata(
    val role: String,
    val appVersion: String,
    val buildType: String,
    val debugLogsEnabled: Boolean,
    val deviceManufacturer: String,
    val deviceModel: String,
    val sdkInt: Int,
    val generatedAtEpochMillis: Long = System.currentTimeMillis(),
)

private val supportReportTimestampFormatter =
    DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC)

fun formatSupportReport(metadata: NoctSupportReportMetadata, diagnosticsSection: String, recentLogs: List<NoctLogEntry>): String = buildString {
    appendLine("NoctDock support report")
    appendLine("Generated (UTC): ${supportReportTimestampFormatter.format(Instant.ofEpochMilli(metadata.generatedAtEpochMillis))}")
    appendLine()
    appendLine("=== Device ===")
    appendLine("Role: ${metadata.role}")
    appendLine("App version: ${metadata.appVersion}")
    appendLine("Build type: ${metadata.buildType}")
    appendLine("Debug logs enabled: ${metadata.debugLogsEnabled}")
    appendLine("Device: ${metadata.deviceManufacturer} ${metadata.deviceModel}")
    appendLine("Android SDK: ${metadata.sdkInt}")
    appendLine()
    appendLine("=== System status ===")
    appendLine(diagnosticsSection.trimEnd())
    appendLine()
    appendLine("=== Recent app logs (${recentLogs.size} lines, newest last) ===")
    if (recentLogs.isEmpty()) {
        appendLine("(No buffered logs yet. Reproduce the issue, then copy again.)")
    } else {
        recentLogs.forEach { entry ->
            append(entry.formatLine())
            appendLine()
        }
    }
    appendLine()
    appendLine("=== Notes ===")
    appendLine("No ROM paths, save files, or account data are included.")
    appendLine("Paste this report into a GitHub issue when reporting bugs or crashes.")
}

private fun NoctLogEntry.formatLine(): String {
    val time = supportReportTimestampFormatter.format(Instant.ofEpochMilli(epochMillis))
    val levelLabel = level.name.first()
    val base = "$time $levelLabel/$tag: $message"
    return if (throwableSummary.isNullOrBlank()) base else "$base\n$throwableSummary"
}
