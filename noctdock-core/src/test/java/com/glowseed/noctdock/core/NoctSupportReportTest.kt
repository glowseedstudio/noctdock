package com.glowseed.noctdock.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NoctSupportReportTest {
    @Test
    fun formatSupportReport_includesMetadataDiagnosticsAndLogs() {
        val report =
            formatSupportReport(
                metadata =
                NoctSupportReportMetadata(
                    role = "Sender",
                    appVersion = "0.1.0",
                    buildType = "debug",
                    debugLogsEnabled = true,
                    deviceManufacturer = "Retroid",
                    deviceModel = "Pocket 6",
                    sdkInt = 34,
                    generatedAtEpochMillis = 1_700_000_000_000L,
                ),
                diagnosticsSection = "NoctDock diagnostics\nReceiver: TV",
                recentLogs =
                listOf(
                    NoctLogEntry(
                        epochMillis = 1_700_000_000_100L,
                        level = NoctLogLevel.WARN,
                        tag = "Sender",
                        message = "Encoder stalled",
                    ),
                ),
            )
        assertTrue(report.contains("NoctDock support report"))
        assertTrue(report.contains("Role: Sender"))
        assertTrue(report.contains("Receiver: TV"))
        assertTrue(report.contains("Encoder stalled"))
        assertTrue(report.contains("No ROM paths"))
    }

    @Test
    fun formatSupportReport_omitsRomPathsInPrivacyNote() {
        val report =
            formatSupportReport(
                metadata =
                NoctSupportReportMetadata(
                    role = "Receiver",
                    appVersion = "0.1.0",
                    buildType = "release",
                    debugLogsEnabled = false,
                    deviceManufacturer = "Google",
                    deviceModel = "TV",
                    sdkInt = 33,
                ),
                diagnosticsSection = "Receiver diagnostics",
                recentLogs = emptyList(),
            )
        assertFalse(report.contains("/storage/"))
        assertTrue(report.contains("(No buffered logs yet"))
    }
}
