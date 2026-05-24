package com.glowseed.noctdock.sender

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import com.glowseed.noctdock.core.NoctColors

@Composable
internal fun GameHubCollapseChevron(modifier: Modifier = Modifier, tint: Color = NoctColors.TextSecondary) {
    Canvas(modifier = modifier.size(22.dp)) {
        val strokeWidth = 2.2.dp.toPx()
        val centerX = size.width / 2f
        val topY = size.height * 0.34f
        val bottomY = size.height * 0.66f
        val wing = size.width * 0.22f
        drawLine(
            color = tint,
            start = Offset(centerX - wing, topY),
            end = Offset(centerX, bottomY),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = tint,
            start = Offset(centerX + wing, topY),
            end = Offset(centerX, bottomY),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
        )
    }
}
