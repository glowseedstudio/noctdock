package com.glowseed.noctdock.sender

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.glowseed.noctdock.core.GameHubControllerLayout
import com.glowseed.noctdock.core.NoctColors
import com.glowseed.noctdock.core.NoctGlassCard
import com.glowseed.noctdock.core.NoctPrimaryConsoleButton
import com.glowseed.noctdock.core.NoctSelectableCard
import com.glowseed.noctdock.core.NoctSpacing

internal val LocalGameHubControllerLayout =
    compositionLocalOf { GameHubControllerLayout.Xbox }

internal enum class GameHubHintButton {
    Accept,
    Back,
    Options,
    Favourite,
    Up,
    Down,
}

internal fun GameHubHintButton.displayLabel(): String =
    when (this) {
        GameHubHintButton.Accept -> "A"
        GameHubHintButton.Back -> "B"
        GameHubHintButton.Options -> "X"
        GameHubHintButton.Favourite -> "Y"
        GameHubHintButton.Up -> "↑"
        GameHubHintButton.Down -> "↓"
    }

private data class GameHubFaceButtonPlacement(val label: String, val xBias: Float, val yBias: Float)

private fun gameHubFaceButtonPlacements(layout: GameHubControllerLayout): List<GameHubFaceButtonPlacement> =
    when (layout) {
        GameHubControllerLayout.Xbox ->
            listOf(
                GameHubFaceButtonPlacement("Y", 0f, -1f),
                GameHubFaceButtonPlacement("X", -1f, 0f),
                GameHubFaceButtonPlacement("B", 1f, 0f),
                GameHubFaceButtonPlacement("A", 0f, 1f),
            )

        GameHubControllerLayout.Nintendo ->
            listOf(
                GameHubFaceButtonPlacement("X", 0f, -1f),
                GameHubFaceButtonPlacement("Y", -1f, 0f),
                GameHubFaceButtonPlacement("A", 1f, 0f),
                GameHubFaceButtonPlacement("B", 0f, 1f),
            )
    }

internal fun gameHubControllerButtonColors(label: String): Pair<Color, Color> =
    when (label.uppercase()) {
        "A" -> Color(0xFFE53935) to Color.White
        "B" -> Color(0xFFFFB300) to Color(0xFF1A1200)
        "X" -> Color(0xFF1E88E5) to Color.White
        "Y" -> Color(0xFF43A047) to Color.White
        else -> NoctColors.GlassBorder.copy(alpha = 0.55f) to NoctColors.TextSecondary
    }

@Composable
internal fun GameHubControllerButtonGlyph(label: String, size: Dp = 15.dp, fontSize: androidx.compose.ui.unit.TextUnit = 10.sp, modifier: Modifier = Modifier) {
    val (fill, letterColor) = gameHubControllerButtonColors(label)
    Box(
        modifier =
        modifier
            .size(size)
            .clip(CircleShape)
            .background(fill),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = letterColor,
            fontSize = fontSize,
            fontWeight = FontWeight.Bold,
            lineHeight = fontSize,
        )
    }
}

@Composable
private fun GameHubControllerFaceDiagram(
    layout: GameHubControllerLayout,
    buttonSize: Dp,
    spread: Dp,
    emphasized: Boolean,
    modifier: Modifier = Modifier,
) {
    val placements = remember(layout) { gameHubFaceButtonPlacements(layout) }
    BoxWithConstraints(modifier = modifier.size(spread * 2 + buttonSize), contentAlignment = Alignment.Center) {
        placements.forEach { placement ->
            val (fill, letterColor) = gameHubControllerButtonColors(placement.label)
            Box(
                modifier =
                Modifier
                    .offset(x = spread * placement.xBias, y = spread * placement.yBias)
                    .size(buttonSize)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors =
                            listOf(
                                fill.copy(alpha = if (emphasized) 1f else 0.92f),
                                fill.copy(alpha = if (emphasized) 0.78f else 0.68f),
                            ),
                        ),
                    )
                    .border(1.dp, Color.White.copy(alpha = if (emphasized) 0.18f else 0.12f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = placement.label,
                    color = letterColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = (buttonSize.value * 0.38f).sp,
                )
            }
        }
    }
}

@Composable
private fun GameHubControllerLayoutOptionCard(
    layout: GameHubControllerLayout,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    NoctSelectableCard(
        selected = selected,
        onClick = onClick,
        modifier = modifier.widthIn(min = 168.dp, max = 220.dp),
        contentPadding = PaddingValues(horizontal = NoctSpacing.lg, vertical = NoctSpacing.md),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = layout.label,
                style = MaterialTheme.typography.titleMedium,
                color = NoctColors.TextPrimary,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            Text(
                text = layout.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = NoctColors.TextSecondary,
                textAlign = TextAlign.Center,
            )
            GameHubControllerFaceDiagram(
                layout = layout,
                buttonSize = 42.dp,
                spread = 34.dp,
                emphasized = selected,
            )
        }
    }
}

@Composable
internal fun GameHubControllerLayoutPicker(
    onConfirm: (GameHubControllerLayout) -> Unit,
    modifier: Modifier = Modifier,
) {
    val layouts = GameHubControllerLayout.entries
    var selectedIndex by remember { mutableIntStateOf(0) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Box(
        modifier =
        modifier
            .fillMaxSize()
            .background(Color(0xE6000810))
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.DirectionLeft -> {
                        selectedIndex = (selectedIndex - 1).coerceAtLeast(0)
                        true
                    }

                    Key.DirectionRight -> {
                        selectedIndex = (selectedIndex + 1).coerceAtMost(layouts.lastIndex)
                        true
                    }

                    Key.ButtonA, Key.Enter, Key.DirectionCenter -> {
                        onConfirm(layouts[selectedIndex])
                        true
                    }

                    else -> false
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        NoctGlassCard(
            modifier =
            Modifier
                .padding(horizontal = 24.dp)
                .widthIn(max = 720.dp)
                .fillMaxWidth(),
            contentPadding = PaddingValues(24.dp),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Text(
                    text = "Match your controls",
                    style = MaterialTheme.typography.headlineSmall,
                    color = NoctColors.TextPrimary,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = "Pick the face-button layout printed on your handheld. NoctDock uses this for on-screen hints.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = NoctColors.TextSecondary,
                    textAlign = TextAlign.Center,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    layouts.forEachIndexed { index, layout ->
                        GameHubControllerLayoutOptionCard(
                            layout = layout,
                            selected = index == selectedIndex,
                            onClick = { selectedIndex = index },
                        )
                    }
                }
                NoctPrimaryConsoleButton(
                    text = "Use ${layouts[selectedIndex].label}",
                    onClick = { onConfirm(layouts[selectedIndex]) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    GameHubControllerButtonGlyph(label = "A")
                    Text("Confirm", color = NoctColors.TextSecondary, style = MaterialTheme.typography.labelMedium)
                    Text("← →", color = NoctColors.TextSecondary, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    Text("Choose", color = NoctColors.TextSecondary, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
internal fun GameHubControllerLayoutHost(
    layout: GameHubControllerLayout,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalGameHubControllerLayout provides layout, content = content)
}
