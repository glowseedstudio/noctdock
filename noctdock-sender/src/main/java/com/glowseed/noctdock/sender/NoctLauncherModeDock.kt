package com.glowseed.noctdock.sender

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.glowseed.noctdock.core.LocalNoctAccent
import com.glowseed.noctdock.core.NoctColors

internal enum class NoctLauncherModeDockLayout {
    Horizontal,
    Vertical,
}

internal data class NoctLauncherModeDockItem(
    val mode: GameHubHomePanel,
    val index: Int,
    val contentDescription: String,
)

enum class NoctLauncherModeDockSelectAction {
    GoHome,
    OpenLibrary,
    OpenScreens,
    OpenConsoleModes,
    OpenSettings,
}

internal object NoctLauncherModeDockDefaults {
    val defaultMode: GameHubHomePanel = GameHubHomePanel.Launcher

    val modes: List<NoctLauncherModeDockItem> =
        listOf(
            NoctLauncherModeDockItem(GameHubHomePanel.Launcher, GAME_HUB_TOP_BAR_HOME, "Home"),
            NoctLauncherModeDockItem(GameHubHomePanel.Library, GAME_HUB_TOP_BAR_LIBRARY, "Library"),
            NoctLauncherModeDockItem(GameHubHomePanel.Screens, GAME_HUB_TOP_BAR_SCREENS, "Screens"),
            NoctLauncherModeDockItem(GameHubHomePanel.ConsoleModes, GAME_HUB_TOP_BAR_CONSOLE_MODES, "Console Modes"),
            NoctLauncherModeDockItem(GameHubHomePanel.Settings, GAME_HUB_TOP_BAR_SETTINGS, "Settings"),
        )
}

internal fun noctLauncherModeDockIndexForMode(mode: GameHubHomePanel): Int = gameHubTopBarIndexForPanel(mode)

internal fun noctLauncherModeDockModeForIndex(index: Int): GameHubHomePanel =
    when (index) {
        GAME_HUB_TOP_BAR_HOME -> GameHubHomePanel.Launcher
        GAME_HUB_TOP_BAR_LIBRARY -> GameHubHomePanel.Library
        GAME_HUB_TOP_BAR_SCREENS -> GameHubHomePanel.Screens
        GAME_HUB_TOP_BAR_CONSOLE_MODES -> GameHubHomePanel.ConsoleModes
        GAME_HUB_TOP_BAR_SETTINGS -> GameHubHomePanel.Settings
        else -> GameHubHomePanel.Launcher
    }

internal fun noctLauncherModeDockButtonSelected(
    buttonIndex: Int,
    dockFocused: Boolean,
    focusedIndex: Int,
): Boolean = dockFocused && focusedIndex == buttonIndex

internal fun noctLauncherModeDockSelectAction(
    currentPanel: GameHubHomePanel,
    index: Int,
): NoctLauncherModeDockSelectAction =
    when (index) {
        GAME_HUB_TOP_BAR_HOME -> NoctLauncherModeDockSelectAction.GoHome
        GAME_HUB_TOP_BAR_LIBRARY ->
            if (currentPanel == GameHubHomePanel.Library) {
                NoctLauncherModeDockSelectAction.GoHome
            } else {
                NoctLauncherModeDockSelectAction.OpenLibrary
            }

        GAME_HUB_TOP_BAR_SCREENS ->
            if (currentPanel == GameHubHomePanel.Screens) {
                NoctLauncherModeDockSelectAction.GoHome
            } else {
                NoctLauncherModeDockSelectAction.OpenScreens
            }

        GAME_HUB_TOP_BAR_CONSOLE_MODES ->
            if (currentPanel == GameHubHomePanel.ConsoleModes) {
                NoctLauncherModeDockSelectAction.GoHome
            } else {
                NoctLauncherModeDockSelectAction.OpenConsoleModes
            }

        else ->
            if (currentPanel == GameHubHomePanel.Settings) {
                NoctLauncherModeDockSelectAction.GoHome
            } else {
                NoctLauncherModeDockSelectAction.OpenSettings
            }
    }

@Composable
internal fun NoctLauncherModeDock(
    modes: List<NoctLauncherModeDockItem>,
    selectedMode: GameHubHomePanel,
    focusedIndex: Int,
    dockFocused: Boolean,
    buttonSize: Dp,
    reducedMotion: Boolean,
    focusRequesters: List<FocusRequester>,
    onModeSelected: (Int) -> Unit,
    onFocusChanged: (index: Int, focused: Boolean) -> Unit,
    modifier: Modifier = Modifier,
    layout: NoctLauncherModeDockLayout = NoctLauncherModeDockLayout.Horizontal,
) {
    val content: @Composable () -> Unit = {
        modes.forEach { item ->
            val selected =
                noctLauncherModeDockButtonSelected(
                    buttonIndex = item.index,
                    dockFocused = dockFocused,
                    focusedIndex = focusedIndex,
                )
            val focusRequester = focusRequesters.getOrNull(item.index) ?: FocusRequester()
            NoctLauncherModeDockButton(
                mode = item.mode,
                onClick = { onModeSelected(item.index) },
                buttonSize = buttonSize,
                reducedMotion = reducedMotion,
                selected = selected,
                modifier =
                Modifier
                    .focusRequester(focusRequester)
                    .focusable(dockFocused),
                onFocusChanged = { focused -> onFocusChanged(item.index, focused) },
            )
        }
    }

    when (layout) {
        NoctLauncherModeDockLayout.Horizontal ->
            Row(
                modifier = modifier,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                content = { content() },
            )

        NoctLauncherModeDockLayout.Vertical ->
            Column(
                modifier = modifier,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                content = { content() },
            )
    }
}

@Composable
private fun NoctLauncherModeDockButton(
    mode: GameHubHomePanel,
    onClick: () -> Unit,
    buttonSize: Dp,
    reducedMotion: Boolean,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onFocusChanged: (Boolean) -> Unit = {},
) {
    when (mode) {
        GameHubHomePanel.Launcher ->
            NoctLauncherModeDockHomeButton(
                onClick = onClick,
                buttonSize = buttonSize,
                reducedMotion = reducedMotion,
                selected = selected,
                modifier = modifier,
                onFocusChanged = onFocusChanged,
            )

        GameHubHomePanel.Library ->
            NoctLauncherModeDockLibraryButton(
                onClick = onClick,
                buttonSize = buttonSize,
                reducedMotion = reducedMotion,
                selected = selected,
                modifier = modifier,
                onFocusChanged = onFocusChanged,
            )

        GameHubHomePanel.Screens ->
            NoctLauncherModeDockScreensButton(
                onClick = onClick,
                buttonSize = buttonSize,
                reducedMotion = reducedMotion,
                selected = selected,
                modifier = modifier,
                onFocusChanged = onFocusChanged,
            )

        GameHubHomePanel.ConsoleModes ->
            NoctLauncherModeDockConsoleModesButton(
                onClick = onClick,
                buttonSize = buttonSize,
                reducedMotion = reducedMotion,
                selected = selected,
                modifier = modifier,
                onFocusChanged = onFocusChanged,
            )

        GameHubHomePanel.Settings ->
            NoctLauncherModeDockSettingsButton(
                onClick = onClick,
                buttonSize = buttonSize,
                reducedMotion = reducedMotion,
                selected = selected,
                modifier = modifier,
                onFocusChanged = onFocusChanged,
            )
    }
}

private fun noctLauncherModeDockIconColor(ringActive: Boolean): androidx.compose.ui.graphics.Color =
    NoctColors.TextPrimary.copy(alpha = if (ringActive) 1f else 0.9f)

private fun noctLauncherModeDockIconStroke(): Float = 2.9f

private fun noctLauncherModeDockRingStroke(selected: Boolean): Float = if (selected) 4f else 3f

@Composable
private fun NoctLauncherModeDockChrome(
    onClick: () -> Unit,
    buttonSize: Dp,
    reducedMotion: Boolean,
    selected: Boolean,
    contentDescription: String,
    modifier: Modifier = Modifier,
    onFocusChanged: (Boolean) -> Unit = {},
    icon: @Composable (iconSize: Dp, ringActive: Boolean) -> Unit,
) {
    val accent = LocalNoctAccent.current
    val ringActive = selected
    val scale by animateFloatAsState(
        if (ringActive) 1.06f else 1f,
        animationSpec = tween(280, easing = FastOutSlowInEasing),
        label = "mode-dock-scale",
    )
    val iconSize = buttonSize * 0.54f
    Box(
        modifier =
        modifier
            .scale(scale)
            .size(buttonSize)
            .clip(CircleShape)
            .gameHubFocusRing(
                CircleShape,
                accent,
                focused = ringActive,
                strokeDp = noctLauncherModeDockRingStroke(selected),
                idleBorderDp = 2.dp,
                reducedMotion = reducedMotion,
            )
            .gameHubActivateOnAccept(onClick)
            .clickable(role = Role.Button, onClick = onClick)
            .onFocusChanged { onFocusChanged(it.isFocused) }
            .semantics {
                role = Role.Button
                this.contentDescription = contentDescription
            },
        contentAlignment = Alignment.Center,
    ) {
        icon(iconSize, ringActive)
    }
}

@Composable
private fun NoctLauncherModeDockHomeButton(
    onClick: () -> Unit,
    buttonSize: Dp,
    reducedMotion: Boolean,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onFocusChanged: (Boolean) -> Unit = {},
) {
    NoctLauncherModeDockChrome(
        onClick = onClick,
        buttonSize = buttonSize,
        reducedMotion = reducedMotion,
        selected = selected,
        contentDescription = "Home",
        modifier = modifier,
        onFocusChanged = onFocusChanged,
    ) { iconSize, ringActive ->
        Canvas(modifier = Modifier.size(iconSize)) {
            val color = noctLauncherModeDockIconColor(ringActive)
            val strokePx = noctLauncherModeDockIconStroke().dp.toPx()
            val cap = StrokeCap.Round
            val pad = size.minDimension * 0.14f
            val w = size.width
            val iconH = size.height - pad * 2f
            val top = pad
            val roofPeak = Offset(w * 0.5f, top + iconH * 0.08f)
            val roofLineY = top + iconH * 0.4f
            val roofLeft = Offset(w * 0.22f, roofLineY)
            val roofRight = Offset(w * 0.78f, roofLineY)
            drawLine(color, roofPeak, roofLeft, strokeWidth = strokePx, cap = cap)
            drawLine(color, roofPeak, roofRight, strokeWidth = strokePx, cap = cap)
            drawLine(color, roofLeft, roofRight, strokeWidth = strokePx, cap = cap)
            val bodyTop = roofLineY
            val bodyBottom = top + iconH * 0.9f
            val bodyLeft = w * 0.3f
            val bodyRight = w * 0.7f
            drawLine(color, Offset(bodyLeft, bodyTop), Offset(bodyLeft, bodyBottom), strokeWidth = strokePx, cap = cap)
            drawLine(color, Offset(bodyRight, bodyTop), Offset(bodyRight, bodyBottom), strokeWidth = strokePx, cap = cap)
            drawLine(color, Offset(bodyLeft, bodyBottom), Offset(bodyRight, bodyBottom), strokeWidth = strokePx, cap = cap)
            drawLine(color, Offset(w * 0.5f, bodyTop), Offset(w * 0.5f, bodyBottom), strokeWidth = strokePx * 0.85f, cap = cap)
        }
    }
}

@Composable
private fun NoctLauncherModeDockLibraryButton(
    onClick: () -> Unit,
    buttonSize: Dp,
    reducedMotion: Boolean,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onFocusChanged: (Boolean) -> Unit = {},
) {
    NoctLauncherModeDockChrome(
        onClick = onClick,
        buttonSize = buttonSize,
        reducedMotion = reducedMotion,
        selected = selected,
        contentDescription = "Library",
        modifier = modifier,
        onFocusChanged = onFocusChanged,
    ) { iconSize, ringActive ->
        Canvas(modifier = Modifier.size(iconSize)) {
            val stroke = Stroke(width = noctLauncherModeDockIconStroke().dp.toPx(), cap = StrokeCap.Round)
            val inset = size.minDimension * 0.14f
            val gap = size.minDimension * 0.09f
            val cell = (size.minDimension - inset * 2f - gap) / 2f
            val grid = cell * 2f + gap
            val originX = (size.width - grid) / 2f
            val originY = (size.height - grid) / 2f
            val color = noctLauncherModeDockIconColor(ringActive)
            val corner = CornerRadius(3.dp.toPx(), 3.dp.toPx())
            for (row in 0..1) {
                for (col in 0..1) {
                    drawRoundRect(
                        color = color,
                        topLeft = Offset(originX + col * (cell + gap), originY + row * (cell + gap)),
                        size = Size(cell, cell),
                        cornerRadius = corner,
                        style = stroke,
                    )
                }
            }
        }
    }
}

@Composable
private fun NoctLauncherModeDockScreensButton(
    onClick: () -> Unit,
    buttonSize: Dp,
    reducedMotion: Boolean,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onFocusChanged: (Boolean) -> Unit = {},
) {
    NoctLauncherModeDockChrome(
        onClick = onClick,
        buttonSize = buttonSize,
        reducedMotion = reducedMotion,
        selected = selected,
        contentDescription = "Screens",
        modifier = modifier,
        onFocusChanged = onFocusChanged,
    ) { iconSize, ringActive ->
        Canvas(modifier = Modifier.size(iconSize)) {
            val strokePx = noctLauncherModeDockIconStroke().dp.toPx()
            val stroke = Stroke(width = strokePx, cap = StrokeCap.Round)
            val color = noctLauncherModeDockIconColor(ringActive)
            val cap = StrokeCap.Round
            val screenW = size.width * 0.74f
            val screenH = size.height * 0.42f
            val standGap = size.height * 0.07f
            val totalH = screenH + standGap + strokePx
            val top = (size.height - totalH) / 2f
            val left = (size.width - screenW) / 2f
            drawRoundRect(
                color = color,
                topLeft = Offset(left, top),
                size = Size(screenW, screenH),
                cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()),
                style = stroke,
            )
            val standY = top + screenH + standGap
            val standW = screenW * 0.42f
            drawLine(
                color = color,
                start = Offset(center.x - standW / 2f, standY),
                end = Offset(center.x + standW / 2f, standY),
                strokeWidth = strokePx,
                cap = cap,
            )
        }
    }
}

@Composable
private fun NoctLauncherModeDockConsoleModesButton(
    onClick: () -> Unit,
    buttonSize: Dp,
    reducedMotion: Boolean,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onFocusChanged: (Boolean) -> Unit = {},
) {
    NoctLauncherModeDockChrome(
        onClick = onClick,
        buttonSize = buttonSize,
        reducedMotion = reducedMotion,
        selected = selected,
        contentDescription = "Console Modes",
        modifier = modifier,
        onFocusChanged = onFocusChanged,
    ) { iconSize, ringActive ->
        Canvas(modifier = Modifier.size(iconSize)) {
            val stroke = Stroke(width = noctLauncherModeDockIconStroke().dp.toPx(), cap = StrokeCap.Round)
            val color = noctLauncherModeDockIconColor(ringActive)
            val outerR = size.minDimension * 0.38f
            val innerR = size.minDimension * 0.22f
            drawCircle(color = color, radius = outerR, center = center, style = stroke)
            drawCircle(color = color.copy(alpha = 0.88f), radius = innerR, center = center, style = stroke)
            drawCircle(color = color, radius = size.minDimension * 0.07f, center = center)
        }
    }
}

@Composable
private fun NoctLauncherModeDockSettingsButton(
    onClick: () -> Unit,
    buttonSize: Dp,
    reducedMotion: Boolean,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onFocusChanged: (Boolean) -> Unit = {},
) {
    NoctLauncherModeDockChrome(
        onClick = onClick,
        buttonSize = buttonSize,
        reducedMotion = reducedMotion,
        selected = selected,
        contentDescription = "Settings",
        modifier = modifier,
        onFocusChanged = onFocusChanged,
    ) { iconSize, ringActive ->
        Canvas(modifier = Modifier.size(iconSize)) {
            val iconColor = noctLauncherModeDockIconColor(ringActive)
            val stroke = Stroke(width = noctLauncherModeDockIconStroke().dp.toPx(), cap = StrokeCap.Round)
            val c = center
            val r = size.minDimension * 0.34f
            drawCircle(color = iconColor.copy(alpha = if (ringActive) 0.22f else 0.12f), radius = r * 1.35f, center = c)
            drawCircle(color = iconColor, radius = r, center = c, style = stroke)
            val toothStroke = noctLauncherModeDockIconStroke().dp.toPx()
            val cap = StrokeCap.Round
            for (i in 0 until 8) {
                val angle = (i * 45f) * (Math.PI / 180f).toFloat()
                val inner = Offset(c.x + kotlin.math.cos(angle) * r * 0.55f, c.y + kotlin.math.sin(angle) * r * 0.55f)
                val outer = Offset(c.x + kotlin.math.cos(angle) * r * 0.92f, c.y + kotlin.math.sin(angle) * r * 0.92f)
                drawLine(color = iconColor, start = inner, end = outer, strokeWidth = toothStroke, cap = cap)
            }
        }
    }
}
