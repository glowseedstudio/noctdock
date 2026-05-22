package com.glowseed.noctdock.sender

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.glowseed.noctdock.core.ConnectionRecommendation
import com.glowseed.noctdock.core.DeviceCapabilityDetector
import com.glowseed.noctdock.core.DeviceSupportLevel
import com.glowseed.noctdock.core.HandheldPerformanceTier
import com.glowseed.noctdock.core.NoctColors
import com.glowseed.noctdock.core.NoctOrb
import com.glowseed.noctdock.core.NoctSelectableCard
import com.glowseed.noctdock.core.NoctSpacing
import com.glowseed.noctdock.core.NoctStatusPill
import com.glowseed.noctdock.core.StreamProfile
import com.glowseed.noctdock.core.StreamProfiles
import com.glowseed.noctdock.core.noctSpace

object ConsoleModeProfiles {
    fun available(uiState: SenderUiState): List<StreamProfile> {
        val receiverCaps = uiState.defaultReceiver?.videoCapabilities
        val test = uiState.connectionTestResult?.recommendation
        val fullHdAllowed =
            DeviceCapabilityDetector.allowsFullHdAfterConnectionTest(
                profile = uiState.deviceProfile,
                encoderSummary = uiState.encoderCapabilitySummary,
                connectionTestPassed = test == ConnectionRecommendation.CINEMA || test == ConnectionRecommendation.SHARP,
            )
        val hevcAllowed = DeviceCapabilityDetector.allowsHevc(uiState.deviceProfile, uiState.encoderCapabilitySummary)
        return StreamProfiles.visible.filter { profile ->
            when (profile.id) {
                StreamProfiles.Performance.id -> true

                StreamProfiles.Balanced.id -> uiState.deviceProfile.supportLevel != DeviceSupportLevel.RECEIVER_OR_LIGHT_ONLY

                StreamProfiles.Quality.id ->
                    uiState.deviceProfile.qualityAllowed &&
                        uiState.deviceProfile.handheldTier !in listOf(HandheldPerformanceTier.MID, HandheldPerformanceTier.LIGHT) &&
                        (
                            uiState.deviceProfile.handheldTier != HandheldPerformanceTier.UNKNOWN ||
                                uiState.connectionTestResult != null
                            )

                StreamProfiles.Sharp.id ->
                    receiverCaps?.let { caps ->
                        fullHdAllowed &&
                            profile.width <= caps.maxWidth &&
                            profile.height <= caps.maxHeight &&
                            ((hevcAllowed && caps.supportsHevc) || caps.supportsAvc)
                    } == true

                StreamProfiles.Cinema.id ->
                    receiverCaps?.let { caps ->
                        fullHdAllowed &&
                            profile.width <= caps.maxWidth &&
                            profile.height <= caps.maxHeight &&
                            ((hevcAllowed && caps.supportsHevc) || caps.supportsAvc) &&
                            (
                                test == ConnectionRecommendation.CINEMA ||
                                    (caps.shieldOptimized && test == ConnectionRecommendation.SHARP)
                                )
                    } == true

                else -> true
            }
        }
    }
}

fun consoleModeAccent(profile: StreamProfile): Color = when (profile.id) {
    "performance" -> NoctColors.Cyan
    "balanced" -> NoctColors.Violet
    "quality" -> NoctColors.Magenta
    "sharp" -> NoctColors.Green
    "cinema" -> NoctColors.Cyan
    else -> NoctColors.Green
}

fun consoleModeDescription(profile: StreamProfile): String = when (profile.id) {
    "performance" -> "Smooth and reliable."
    "balanced" -> "Recommended for most sessions."
    "quality" -> "Cleaner picture."
    "sharp" -> "Sharper image for strong handhelds."
    "cinema" -> "Full HD for excellent Wi-Fi."
    "1080_boost" -> "Compatibility test mode."
    else -> "Experimental."
}

fun consoleModeChips(profile: StreamProfile): List<String> = when (profile.id) {
    "performance" -> listOf("Reliable")
    "balanced" -> listOf("Smooth")
    "quality" -> listOf("Clean")
    "sharp" -> listOf("Sharper")
    "cinema" -> listOf("Full HD")
    "1080_boost" -> listOf("Test")
    else -> listOf("Experimental")
}

@Composable
fun ConsoleModeProfileCard(profile: StreamProfile, selected: Boolean, onSelect: () -> Unit, modifier: Modifier = Modifier, cardHeight: Dp = 212.dp) {
    val accent = consoleModeAccent(profile)
    NoctSelectableCard(
        selected = selected,
        onClick = onSelect,
        modifier = modifier.height(cardHeight),
        contentPadding = PaddingValues(noctSpace(NoctSpacing.md)),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(noctSpace(NoctSpacing.sm)),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(noctSpace(NoctSpacing.md)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ConsoleModeOrbBadge(profile = profile, accent = accent, modifier = Modifier.size(50.dp))
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(noctSpace(NoctSpacing.xs))) {
                    Text(
                        profile.title,
                        color = NoctColors.TextPrimary,
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(consoleModeDescription(profile), color = NoctColors.TextSecondary)
                }
            }
            Spacer(modifier = Modifier.weight(1f))
            Row(
                horizontalArrangement = Arrangement.spacedBy(noctSpace(NoctSpacing.sm), Alignment.CenterHorizontally),
                modifier = Modifier.fillMaxWidth(),
            ) {
                consoleModeChips(profile).forEach { chip -> NoctStatusPill(chip, accent) }
            }
        }
    }
}

@Composable
fun ConsoleModeOrbBadge(profile: StreamProfile, accent: Color, modifier: Modifier = Modifier, iconSize: Dp = 24.dp) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        NoctOrb(modifier = Modifier.fillMaxSize(), color = accent, reducedMotion = true)
        Canvas(modifier = Modifier.size(iconSize)) {
            val iconColor = NoctColors.TextPrimary.copy(alpha = 0.96f)
            val strokeWidth = 2.2.dp.toPx()
            val stroke = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            when (profile.id) {
                "performance" -> {
                    val path = androidx.compose.ui.graphics.Path().apply {
                        moveTo(size.width * 0.56f, size.height * 0.06f)
                        lineTo(size.width * 0.24f, size.height * 0.54f)
                        lineTo(size.width * 0.50f, size.height * 0.54f)
                        lineTo(size.width * 0.40f, size.height * 0.94f)
                        lineTo(size.width * 0.78f, size.height * 0.42f)
                        lineTo(size.width * 0.52f, size.height * 0.42f)
                        close()
                    }
                    drawPath(path, iconColor)
                }

                "balanced" -> {
                    drawLine(
                        iconColor,
                        Offset(size.width * 0.50f, size.height * 0.16f),
                        Offset(size.width * 0.50f, size.height * 0.78f),
                        strokeWidth = strokeWidth,
                        cap = StrokeCap.Round,
                    )
                    drawLine(
                        iconColor,
                        Offset(size.width * 0.20f, size.height * 0.34f),
                        Offset(size.width * 0.80f, size.height * 0.34f),
                        strokeWidth = strokeWidth,
                        cap = StrokeCap.Round,
                    )
                    drawLine(
                        iconColor,
                        Offset(size.width * 0.32f, size.height * 0.34f),
                        Offset(size.width * 0.22f, size.height * 0.58f),
                        strokeWidth = strokeWidth,
                        cap = StrokeCap.Round,
                    )
                    drawLine(
                        iconColor,
                        Offset(size.width * 0.32f, size.height * 0.34f),
                        Offset(size.width * 0.42f, size.height * 0.58f),
                        strokeWidth = strokeWidth,
                        cap = StrokeCap.Round,
                    )
                    drawLine(
                        iconColor,
                        Offset(size.width * 0.68f, size.height * 0.34f),
                        Offset(size.width * 0.58f, size.height * 0.58f),
                        strokeWidth = strokeWidth,
                        cap = StrokeCap.Round,
                    )
                    drawLine(
                        iconColor,
                        Offset(size.width * 0.68f, size.height * 0.34f),
                        Offset(size.width * 0.78f, size.height * 0.58f),
                        strokeWidth = strokeWidth,
                        cap = StrokeCap.Round,
                    )
                    drawLine(
                        iconColor,
                        Offset(size.width * 0.22f, size.height * 0.58f),
                        Offset(size.width * 0.42f, size.height * 0.58f),
                        strokeWidth = strokeWidth,
                        cap = StrokeCap.Round,
                    )
                    drawLine(
                        iconColor,
                        Offset(size.width * 0.58f, size.height * 0.58f),
                        Offset(size.width * 0.78f, size.height * 0.58f),
                        strokeWidth = strokeWidth,
                        cap = StrokeCap.Round,
                    )
                    drawLine(
                        iconColor,
                        Offset(size.width * 0.34f, size.height * 0.82f),
                        Offset(size.width * 0.66f, size.height * 0.82f),
                        strokeWidth = strokeWidth,
                        cap = StrokeCap.Round,
                    )
                }

                else -> {
                    drawRoundRect(
                        color = iconColor,
                        topLeft = Offset(size.width * 0.18f, size.height * 0.24f),
                        size = androidx.compose.ui.geometry.Size(size.width * 0.64f, size.height * 0.48f),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx(), 3.dp.toPx()),
                        style = stroke,
                    )
                    drawLine(
                        iconColor,
                        Offset(size.width * 0.30f, size.height * 0.82f),
                        Offset(size.width * 0.70f, size.height * 0.82f),
                        strokeWidth = strokeWidth,
                        cap = StrokeCap.Round,
                    )
                    drawLine(
                        iconColor,
                        Offset(size.width * 0.50f, size.height * 0.72f),
                        Offset(size.width * 0.50f, size.height * 0.82f),
                        strokeWidth = strokeWidth,
                        cap = StrokeCap.Round,
                    )
                    drawLine(
                        iconColor,
                        Offset(size.width * 0.70f, size.height * 0.16f),
                        Offset(size.width * 0.70f, size.height * 0.28f),
                        strokeWidth =
                        strokeWidth * 0.8f,
                        cap = StrokeCap.Round,
                    )
                    drawLine(
                        iconColor,
                        Offset(size.width * 0.64f, size.height * 0.22f),
                        Offset(size.width * 0.76f, size.height * 0.22f),
                        strokeWidth =
                        strokeWidth * 0.8f,
                        cap = StrokeCap.Round,
                    )
                }
            }
        }
    }
}
