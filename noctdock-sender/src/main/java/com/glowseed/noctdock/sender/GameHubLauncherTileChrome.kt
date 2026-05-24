package com.glowseed.noctdock.sender

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.glowseed.noctdock.core.NoctColors

internal fun DrawScope.drawGameHubLauncherTileGlass(
    focused: Boolean,
    accent: Color,
    glowAlpha: Float,
    cornerRadiusPx: Float,
) {
    val corner = CornerRadius(cornerRadiusPx)
    val dropY = 3.dp.toPx()
    drawRoundRect(
        color = Color.Black.copy(alpha = if (focused) 0.42f else 0.28f),
        topLeft = Offset(1.5.dp.toPx(), dropY),
        size = Size(size.width - 3.dp.toPx(), size.height - 1.5.dp.toPx()),
        cornerRadius = corner,
    )
    if (focused) {
        drawRoundRect(
            brush =
            Brush.verticalGradient(
                colors =
                listOf(
                    Color(0xFF243448),
                    Color(0xFF1A2434),
                    Color(0xFF101820),
                    Color(0xFF0A0E14),
                ),
                startY = 0f,
                endY = size.height,
            ),
            cornerRadius = corner,
        )
        drawRoundRect(
            brush =
            Brush.radialGradient(
                colors =
                listOf(
                    accent.copy(alpha = 0.22f),
                    NoctColors.Cyan.copy(alpha = 0.12f),
                    Color.Transparent,
                ),
                center = Offset(size.width * 0.12f, size.height * 0.48f),
                radius = size.maxDimension * 0.46f,
            ),
            cornerRadius = corner,
        )
        drawRoundRect(
            brush =
            Brush.radialGradient(
                colors =
                listOf(
                    NoctColors.Magenta.copy(alpha = 0.18f),
                    NoctColors.Violet.copy(alpha = 0.10f),
                    Color.Transparent,
                ),
                center = Offset(size.width * 0.88f, size.height * 0.36f),
                radius = size.maxDimension * 0.44f,
            ),
            cornerRadius = corner,
        )
        drawRoundRect(
            brush =
            Brush.radialGradient(
                colors =
                listOf(
                    accent.copy(alpha = glowAlpha * 0.42f),
                    NoctColors.Magenta.copy(alpha = glowAlpha * 0.28f),
                    NoctColors.Cyan.copy(alpha = glowAlpha * 0.14f),
                    Color(0x77101820),
                ),
                center = Offset(size.width * 0.5f, size.height * 0.38f),
                radius = size.maxDimension * 1.02f,
            ),
            cornerRadius = corner,
        )
        drawRoundRect(
            brush =
            Brush.linearGradient(
                colors =
                listOf(
                    Color.White.copy(alpha = 0.14f),
                    Color.Transparent,
                ),
                start = Offset(0f, 0f),
                end = Offset(size.width * 0.55f, size.height * 0.22f),
            ),
            cornerRadius = corner,
        )
    } else {
        drawRoundRect(
            brush =
            Brush.verticalGradient(
                colors =
                listOf(
                    Color(0xFF1E2A3A),
                    Color(0xFF141C28),
                    Color(0xFF0C1018),
                    Color(0xFF080C12),
                ),
                startY = 0f,
                endY = size.height,
            ),
            cornerRadius = corner,
        )
        val soft = glowAlpha * 0.30f
        drawRoundRect(
            brush =
            Brush.radialGradient(
                colors =
                listOf(
                    accent.copy(alpha = soft * 0.52f),
                    NoctColors.Cyan.copy(alpha = soft * 0.30f),
                    Color.Transparent,
                ),
                center = Offset(size.width * 0.12f, size.height * 0.48f),
                radius = size.maxDimension * 0.44f,
            ),
            cornerRadius = corner,
        )
        drawRoundRect(
            brush =
            Brush.radialGradient(
                colors =
                listOf(
                    NoctColors.Magenta.copy(alpha = soft * 0.42f),
                    NoctColors.Violet.copy(alpha = soft * 0.24f),
                    Color.Transparent,
                ),
                center = Offset(size.width * 0.88f, size.height * 0.36f),
                radius = size.maxDimension * 0.42f,
            ),
            cornerRadius = corner,
        )
        drawRoundRect(
            brush =
            Brush.radialGradient(
                colors =
                listOf(
                    accent.copy(alpha = soft * 0.98f),
                    NoctColors.Magenta.copy(alpha = soft * 0.64f),
                    NoctColors.Cyan.copy(alpha = soft * 0.30f),
                    Color(0x88101820),
                ),
                center = Offset(size.width * 0.5f, size.height * 0.38f),
                radius = size.maxDimension * 0.95f,
            ),
            cornerRadius = corner,
        )
    }
    drawRoundRect(
        brush =
        Brush.verticalGradient(
            colors =
            listOf(
                Color.White.copy(alpha = if (focused) 0.12f else 0.10f),
                Color.White.copy(alpha = 0.03f),
                Color.Transparent,
            ),
            startY = 0f,
            endY = size.height * 0.32f,
        ),
        cornerRadius = corner,
    )
    drawRoundRect(
        brush =
        Brush.verticalGradient(
            colors =
            listOf(
                Color.Transparent,
                Color.Black.copy(alpha = if (focused) 0.20f else 0.16f),
            ),
            startY = size.height * 0.55f,
            endY = size.height,
        ),
        cornerRadius = corner,
    )
    if (!focused) {
        drawRoundRect(
            brush =
            Brush.linearGradient(
                colors =
                listOf(
                    NoctColors.Cyan.copy(alpha = 0.22f),
                    accent.copy(alpha = 0.16f),
                    NoctColors.Violet.copy(alpha = 0.14f),
                    NoctColors.Magenta.copy(alpha = 0.12f),
                ),
                start = Offset(0f, 0f),
                end = Offset(size.width, size.height),
            ),
            cornerRadius = corner,
            style = Stroke(width = 1.15.dp.toPx()),
        )
    }
}

internal fun DrawScope.drawGameHubLauncherTileFocusBloom(
    accent: Color,
    glowAlpha: Float,
    cornerRadiusPx: Float,
    gradientPhase: Float,
) {
    val bloomColors =
        listOf(
            accent.copy(alpha = glowAlpha * 0.40f),
            NoctColors.Cyan.copy(alpha = glowAlpha * 0.30f),
            NoctColors.Magenta.copy(alpha = glowAlpha * 0.24f),
            NoctColors.Violet.copy(alpha = glowAlpha * 0.20f),
            accent.copy(alpha = glowAlpha * 0.40f),
        )
    val brush = gameHubRotatingSweepBrush(bloomColors, center, gradientPhase)
    drawRoundRect(
        brush = brush,
        topLeft = Offset(-3.dp.toPx(), -3.dp.toPx()),
        size = Size(size.width + 6.dp.toPx(), size.height + 6.dp.toPx()),
        cornerRadius = CornerRadius(cornerRadiusPx + 3.dp.toPx()),
        style = Stroke(width = 5.dp.toPx()),
        alpha = 0.34f,
    )
    drawRoundRect(
        brush =
        Brush.radialGradient(
            colors =
            listOf(
                accent.copy(alpha = glowAlpha * 0.16f),
                Color.Transparent,
            ),
            center = center,
            radius = size.maxDimension * 0.48f,
        ),
        topLeft = Offset(-5.dp.toPx(), -5.dp.toPx()),
        size = Size(size.width + 10.dp.toPx(), size.height + 10.dp.toPx()),
        cornerRadius = CornerRadius(cornerRadiusPx + 5.dp.toPx()),
        alpha = 0.20f,
    )
}

@Composable
internal fun GameHubFavouriteBadge(
    accent: Color,
    modifier: Modifier = Modifier,
    star: @Composable () -> Unit,
) {
    Box(
        modifier =
        modifier
            .size(28.dp)
            .clip(CircleShape)
            .drawBehind {
                drawCircle(
                    brush =
                    Brush.radialGradient(
                        colors =
                        listOf(
                            Color(0xE6182434),
                            Color(0xCC0E141E),
                        ),
                        center = center,
                        radius = size.minDimension * 0.55f,
                    ),
                )
            }
            .border(
                width = 1.dp,
                brush =
                Brush.linearGradient(
                    listOf(
                        accent.copy(alpha = 0.45f),
                        NoctColors.Magenta.copy(alpha = 0.32f),
                    ),
                ),
                shape = CircleShape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        star()
    }
}
