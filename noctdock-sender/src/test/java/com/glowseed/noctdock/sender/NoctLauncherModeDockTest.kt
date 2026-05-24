package com.glowseed.noctdock.sender

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NoctLauncherModeDockTest {
    @Test
    fun defaultModeIsLauncher() {
        assertEquals(GameHubHomePanel.Launcher, NoctLauncherModeDockDefaults.defaultMode)
    }

    @Test
    fun modesListContainsAllFivePanelsInOrder() {
        val modes = NoctLauncherModeDockDefaults.modes
        assertEquals(5, modes.size)
        assertEquals(GameHubHomePanel.Launcher, modes[0].mode)
        assertEquals(GameHubHomePanel.Library, modes[1].mode)
        assertEquals(GameHubHomePanel.Screens, modes[2].mode)
        assertEquals(GameHubHomePanel.ConsoleModes, modes[3].mode)
        assertEquals(GameHubHomePanel.Settings, modes[4].mode)
        assertEquals(GAME_HUB_TOP_BAR_HOME, modes[0].index)
        assertEquals(GAME_HUB_TOP_BAR_LIBRARY, modes[1].index)
        assertEquals(GAME_HUB_TOP_BAR_SCREENS, modes[2].index)
        assertEquals(GAME_HUB_TOP_BAR_CONSOLE_MODES, modes[3].index)
        assertEquals(GAME_HUB_TOP_BAR_SETTINGS, modes[4].index)
    }

    @Test
    fun indexForModeRoundTripsForEveryPanel() {
        GameHubHomePanel.entries.forEach { panel ->
            val index = noctLauncherModeDockIndexForMode(panel)
            assertEquals(panel, noctLauncherModeDockModeForIndex(index))
        }
    }

    @Test
    fun buttonSelectedWhenDockFocusedAndIndexMatches() {
        assertTrue(
            noctLauncherModeDockButtonSelected(
                buttonIndex = GAME_HUB_TOP_BAR_LIBRARY,
                dockFocused = true,
                focusedIndex = GAME_HUB_TOP_BAR_LIBRARY,
            ),
        )
    }

    @Test
    fun buttonNotSelectedWhenDockNotFocused() {
        assertFalse(
            noctLauncherModeDockButtonSelected(
                buttonIndex = GAME_HUB_TOP_BAR_LIBRARY,
                dockFocused = false,
                focusedIndex = GAME_HUB_TOP_BAR_LIBRARY,
            ),
        )
    }

    @Test
    fun buttonNotSelectedWhenFocusedIndexDiffers() {
        assertFalse(
            noctLauncherModeDockButtonSelected(
                buttonIndex = GAME_HUB_TOP_BAR_HOME,
                dockFocused = true,
                focusedIndex = GAME_HUB_TOP_BAR_SETTINGS,
            ),
        )
    }

    @Test
    fun homeTabAlwaysOpensHome() {
        GameHubHomePanel.entries.forEach { panel ->
            assertEquals(
                NoctLauncherModeDockSelectAction.GoHome,
                noctLauncherModeDockSelectAction(panel, GAME_HUB_TOP_BAR_HOME),
            )
        }
    }

    @Test
    fun libraryTabTogglesBetweenLibraryAndHome() {
        assertEquals(
            NoctLauncherModeDockSelectAction.OpenLibrary,
            noctLauncherModeDockSelectAction(GameHubHomePanel.Launcher, GAME_HUB_TOP_BAR_LIBRARY),
        )
        assertEquals(
            NoctLauncherModeDockSelectAction.GoHome,
            noctLauncherModeDockSelectAction(GameHubHomePanel.Library, GAME_HUB_TOP_BAR_LIBRARY),
        )
    }

    @Test
    fun screensTabTogglesBetweenScreensAndHome() {
        assertEquals(
            NoctLauncherModeDockSelectAction.OpenScreens,
            noctLauncherModeDockSelectAction(GameHubHomePanel.Launcher, GAME_HUB_TOP_BAR_SCREENS),
        )
        assertEquals(
            NoctLauncherModeDockSelectAction.GoHome,
            noctLauncherModeDockSelectAction(GameHubHomePanel.Screens, GAME_HUB_TOP_BAR_SCREENS),
        )
    }

    @Test
    fun consoleModesTabTogglesBetweenConsoleModesAndHome() {
        assertEquals(
            NoctLauncherModeDockSelectAction.OpenConsoleModes,
            noctLauncherModeDockSelectAction(GameHubHomePanel.Launcher, GAME_HUB_TOP_BAR_CONSOLE_MODES),
        )
        assertEquals(
            NoctLauncherModeDockSelectAction.GoHome,
            noctLauncherModeDockSelectAction(GameHubHomePanel.ConsoleModes, GAME_HUB_TOP_BAR_CONSOLE_MODES),
        )
    }

    @Test
    fun settingsTabTogglesBetweenSettingsAndHome() {
        assertEquals(
            NoctLauncherModeDockSelectAction.OpenSettings,
            noctLauncherModeDockSelectAction(GameHubHomePanel.Launcher, GAME_HUB_TOP_BAR_SETTINGS),
        )
        assertEquals(
            NoctLauncherModeDockSelectAction.GoHome,
            noctLauncherModeDockSelectAction(GameHubHomePanel.Settings, GAME_HUB_TOP_BAR_SETTINGS),
        )
    }
}
