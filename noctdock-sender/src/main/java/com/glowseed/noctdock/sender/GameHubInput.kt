package com.glowseed.noctdock.sender

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type

/**
 * D-pad / A / B routing targets for Game Hub.
 * Top bar focus is the default on cold start so horizontal tab navigation works immediately.
 */
internal enum class GameHubFocusZone {
    TopBar,
    LibraryFilters,
    ScreensList,
    ConsoleModesList,
    SettingsPanel,
    Grid,
    Portal,
}

private val gameHubAcceptKeys =
    setOf(
        Key.Enter,
        Key.NumPadEnter,
        Key.DirectionCenter,
        Key.ButtonA,
        Key.Spacebar,
    )

private val gameHubBackKeys =
    setOf(
        Key.Back,
        Key.Escape,
        Key.ButtonB,
    )

internal fun KeyEvent.gameHubIsAcceptDown(): Boolean = type == KeyEventType.KeyDown && key in gameHubAcceptKeys

internal fun KeyEvent.gameHubIsBackDown(): Boolean = type == KeyEventType.KeyDown && key in gameHubBackKeys

internal fun gameHubTopBarIndexForFilter(filter: GameHubLibraryFilter): Int = GameHubLibraryFilter.entries.indexOf(filter).coerceAtLeast(0)

/** Gamepad A / Enter on a hub control — does not rely on [androidx.compose.foundation.clickable] alone. */
internal fun Modifier.gameHubActivateOnAccept(onActivate: () -> Unit): Modifier = onKeyEvent { event ->
    if (event.gameHubIsAcceptDown()) {
        onActivate()
        true
    } else {
        false
    }
}
