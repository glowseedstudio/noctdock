package com.glowseed.noctdock.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class NoctLogTest {
    @Before
    fun resetBuffer() {
        NoctLog.clearBufferForTests()
        NoctLog.configure(debugLogs = true, infoLogs = true)
    }

    @Test
    fun recentEntries_capsAtMaxBufferSize() {
        repeat(250) { NoctLog.recordForTests(NoctLogLevel.WARN, "Cap", "line-$it") }
        val entries = NoctLog.recentEntries()
        assertEquals(200, entries.size)
        assertTrue(entries.last().message.contains("line-249"))
    }

    @Test
    fun recordForTests_preservesWarnLevel() {
        NoctLog.recordForTests(NoctLogLevel.WARN, "Sender", "pairing failed")
        val entries = NoctLog.recentEntries()
        assertTrue(entries.any { it.message == "pairing failed" && it.level == NoctLogLevel.WARN })
    }
}
