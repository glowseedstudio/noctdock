package com.glowseed.noctdock.sender

import com.glowseed.noctdock.core.Smooth60HzMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GameHubConsoleModesTest {
    @Test
    fun itemCountIncludesProfilesPreferencesSmooth60AndConnection() {
        assertEquals(3 + 4 + Smooth60HzMode.entries.size + 1, gameHubConsoleModesItemCount(3))
        assertEquals(1 + 4 + Smooth60HzMode.entries.size + 1, gameHubConsoleModesItemCount(0))
    }

    @Test
    fun preferenceStartIndexSkipsProfileRows() {
        assertEquals(3, gameHubConsoleModesPreferenceStartIndex(3))
        assertEquals(1, gameHubConsoleModesPreferenceStartIndex(0))
    }

    @Test
    fun focusKindMapsProfilePreferenceSmooth60AndTestRows() {
        assertTrue(gameHubConsoleModesFocusKind(1, 3) is GameHubConsoleModesFocusKind.Profile)
        assertEquals(1, (gameHubConsoleModesFocusKind(1, 3) as GameHubConsoleModesFocusKind.Profile).index)

        val toggle = gameHubConsoleModesFocusKind(3, 3) as GameHubConsoleModesFocusKind.PreferenceToggle
        assertEquals(0, toggle.slot)

        val smooth = gameHubConsoleModesFocusKind(7, 3) as GameHubConsoleModesFocusKind.Smooth60Hz
        assertEquals(0, smooth.index)

        assertTrue(gameHubConsoleModesFocusKind(10, 3) is GameHubConsoleModesFocusKind.TestConnection)
    }

    @Test
    fun moveDownAndUpStayWithinBounds() {
        val count = gameHubConsoleModesItemCount(2)
        assertEquals(count - 1, gameHubConsoleModesMoveDown(count - 2, count))
        assertEquals(0, gameHubConsoleModesMoveUp(0, count))
    }
}
