package com.glowseed.noctdock.core

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Shapes
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object NoctColors {
    val Black = Color(0xFF000000)
    val Ink = Color(0xFF05060A)
    val Glass = Color(0xB20C1018)
    val GlassBorder = Color(0x33DDE7FF)
    val TextPrimary = Color(0xFFF3F7FF)
    val TextSecondary = Color(0xFF9DA9BA)
    val Cyan = Color(0xFF5DEBFF)
    val Magenta = Color(0xFFFF4FD8)
    val Violet = Color(0xFF826BFF)
    val Green = Color(0xFF83FFB3)
}

object NoctSpacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 16.dp
    val lg = 24.dp
    val xl = 32.dp
    val xxl = 48.dp
}

val LocalNoctAccent = staticCompositionLocalOf { NoctColors.Cyan }
val LocalNoctDensityScale = staticCompositionLocalOf { 1f }
private const val TWO_PI = 6.2831855f

@Composable
fun NoctAppearance(accentTheme: AccentTheme, uiDensity: UiDensity, content: @Composable () -> Unit) {
    val accent =
        when (accentTheme) {
            AccentTheme.Cyan -> NoctColors.Cyan
            AccentTheme.Magenta -> NoctColors.Magenta
            AccentTheme.Violet -> NoctColors.Violet
        }
    val densityScale =
        when (uiDensity) {
            UiDensity.Compact -> 0.86f
            UiDensity.Comfortable -> 1f
            UiDensity.Couch -> 1.16f
        }
    CompositionLocalProvider(
        LocalNoctAccent provides accent,
        LocalNoctDensityScale provides densityScale,
        content = content,
    )
}

@Composable
fun noctSpace(value: Dp): Dp = value * LocalNoctDensityScale.current

val NoctTypography =
    Typography(
        displayMedium =
        androidx.compose.ui.text.TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.SemiBold,
            fontSize = 44.sp,
            lineHeight = 50.sp,
        ),
        headlineMedium =
        androidx.compose.ui.text.TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.SemiBold,
            fontSize = 30.sp,
            lineHeight = 36.sp,
        ),
        titleLarge =
        androidx.compose.ui.text.TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.SemiBold,
            fontSize = 22.sp,
            lineHeight = 28.sp,
        ),
        bodyLarge =
        androidx.compose.ui.text.TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.Normal,
            fontSize = 17.sp,
            lineHeight = 24.sp,
        ),
        labelLarge =
        androidx.compose.ui.text.TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.SemiBold,
            fontSize = 15.sp,
            lineHeight = 20.sp,
        ),
    )

private val NoctScheme =
    darkColorScheme(
        background = NoctColors.Black,
        surface = NoctColors.Ink,
        primary = NoctColors.Cyan,
        secondary = NoctColors.Magenta,
        tertiary = NoctColors.Green,
        onBackground = NoctColors.TextPrimary,
        onSurface = NoctColors.TextPrimary,
        onPrimary = Color(0xFF001116),
    )

@Composable
fun NoctTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = NoctScheme,
        typography = NoctTypography,
        shapes =
        Shapes(
            extraSmall = RoundedCornerShape(6.dp),
            small = RoundedCornerShape(8.dp),
            medium = RoundedCornerShape(8.dp),
            large = RoundedCornerShape(18.dp),
            extraLarge = RoundedCornerShape(24.dp),
        ),
        content = content,
    )
}

@Composable
fun NoctBackground(
    modifier: Modifier = Modifier,
    ambientMotionEnabled: Boolean = true,
    dynamicNebula: Boolean = true,
    theme: NebulaTheme = NebulaTheme.CyanCore,
    motionMode: BackgroundMotionMode = BackgroundMotionMode.AnimatedNebula,
    reducedMotion: Boolean = false,
    batterySaver: Boolean = false,
    surface: BackgroundSurface = BackgroundSurface.Handheld,
    content: @Composable BoxScope.() -> Unit,
) {
    val animationBlocked = reducedMotion || !ambientMotionEnabled || !dynamicNebula
    val runtimeConfig = remember(motionMode, animationBlocked, batterySaver, surface) {
        BackgroundAmbiencePolicy.config(
            motionMode = motionMode,
            reducedMotion = animationBlocked,
            batterySaver = batterySaver,
            surface = surface,
        )
    }
    Box(
        modifier =
        modifier
            .fillMaxSize()
            .background(NoctColors.Black),
    ) {
        NoctAmbientCanvas(
            theme = theme,
            config = runtimeConfig,
            surface = surface,
            modifier = Modifier.fillMaxSize(),
        )
        content()
    }
}

@Composable
private fun NoctAmbientCanvas(theme: NebulaTheme, config: BackgroundRuntimeConfig, surface: BackgroundSurface, modifier: Modifier = Modifier) {
    val palette = remember(theme) { noctNebulaPalette(theme) }
    val particles =
        remember(config.particleCount, theme, surface) {
            AmbientParticleField.generate(
                count = config.particleCount,
                seed = 8191 + theme.ordinal * 193 + surface.ordinal * 977 + config.particleCount * 17,
            )
        }
    val drawPhase =
        if (config.animated) {
            val transition = rememberInfiniteTransition(label = "noct-background")
            val phase by
                transition.animateFloat(
                    initialValue = 0f,
                    targetValue = 1f,
                    animationSpec =
                    infiniteRepeatable(
                        animation = tween(durationMillis = noctBackgroundCycleMillis(surface, config), easing = LinearEasing),
                        repeatMode = RepeatMode.Restart,
                    ),
                    label = "nebula-phase",
                )
            phase
        } else {
            0f
        }

    Canvas(modifier = modifier) {
        drawRect(NoctColors.Black)
        val drift = drawPhase * config.driftSpeedMultiplier
        val primaryCenter =
            Offset(
                x = size.width * (0.13f + 0.028f * kotlin.math.sin(drift * TWO_PI)),
                y = size.height * (0.72f + 0.038f * kotlin.math.sin((drift + 0.18f) * TWO_PI)),
            )
        val secondaryCenter =
            Offset(
                x = size.width * (0.85f + 0.032f * kotlin.math.sin((drift + 0.47f) * TWO_PI)),
                y = size.height * (0.28f + 0.028f * kotlin.math.sin((drift + 0.64f) * TWO_PI)),
            )
        val quietCenter =
            Offset(
                x = size.width * (0.48f + 0.02f * kotlin.math.sin((drift + 0.32f) * TWO_PI)),
                y = size.height * (0.54f + 0.025f * kotlin.math.sin((drift + 0.71f) * TWO_PI)),
            )

        drawCircle(
            brush =
            Brush.radialGradient(
                colors = listOf(palette.first.copy(alpha = config.glowAlpha * 1.12f), Color.Transparent),
                center = primaryCenter,
                radius = size.maxDimension * if (surface == BackgroundSurface.Dock) 0.42f else 0.64f,
            ),
            radius = size.maxDimension,
            center = primaryCenter,
        )
        drawCircle(
            brush =
            Brush.radialGradient(
                colors = listOf(palette.second.copy(alpha = config.glowAlpha * 0.95f), Color.Transparent),
                center = secondaryCenter,
                radius = size.maxDimension * if (surface == BackgroundSurface.Dock) 0.36f else 0.60f,
            ),
            radius = size.maxDimension,
            center = secondaryCenter,
        )
        if (surface != BackgroundSurface.Dock) {
            drawCircle(
                brush =
                Brush.radialGradient(
                    colors = listOf(NoctColors.Magenta.copy(alpha = config.glowAlpha * 0.38f), Color.Transparent),
                    center = quietCenter,
                    radius = size.maxDimension * 0.50f,
                ),
                radius = size.maxDimension,
                center = quietCenter,
            )
            drawCircle(
                brush =
                Brush.radialGradient(
                    colors = listOf(NoctColors.Violet.copy(alpha = config.glowAlpha * 0.22f), Color.Transparent),
                    center = Offset(size.width * 0.76f, size.height * 0.20f),
                    radius = size.maxDimension * 0.34f,
                ),
                radius = size.maxDimension,
                center = Offset(size.width * 0.76f, size.height * 0.20f),
            )
        }

        particles.forEach { particle ->
            val position = AmbientParticleField.position(particle, drawPhase * config.driftSpeedMultiplier)
            drawCircle(
                color = Color.White.copy(alpha = position.alpha * if (surface == BackgroundSurface.Tv) 0.58f else 0.44f),
                radius = particle.size * particle.depth,
                center = Offset(position.x * size.width, position.y * size.height),
            )
        }

        if (config.showDockOrb) {
            val orbDrift = if (config.animated) drawPhase * 0.5f else 0f
            val orbCenter =
                Offset(
                    x = size.width * 0.86f + 4f * kotlin.math.sin(orbDrift * TWO_PI),
                    y = size.height * 0.18f + 5f * kotlin.math.sin((orbDrift + 0.25f) * TWO_PI),
                )
            drawCircle(
                brush =
                Brush.radialGradient(
                    colors = listOf(NoctColors.Cyan.copy(alpha = 0.13f), Color.Transparent),
                    center = orbCenter,
                    radius = size.minDimension * 0.16f,
                ),
                radius = size.minDimension * 0.20f,
                center = orbCenter,
            )
        }
    }
}

private fun noctNebulaPalette(theme: NebulaTheme): Pair<Color, Color> = when (theme) {
    NebulaTheme.CyanCore -> NoctColors.Cyan to NoctColors.Violet
    NebulaTheme.MagentaDrift -> NoctColors.Magenta to NoctColors.Cyan
    NebulaTheme.VioletNebula -> NoctColors.Violet to NoctColors.Magenta
    NebulaTheme.DeepSpace -> NoctColors.Cyan.copy(alpha = 0.52f) to NoctColors.Violet.copy(alpha = 0.38f)
}

private fun noctBackgroundCycleMillis(surface: BackgroundSurface, config: BackgroundRuntimeConfig): Int {
    val base =
        when (surface) {
            BackgroundSurface.Handheld -> 140_000
            BackgroundSurface.Dock -> 180_000
            BackgroundSurface.Tv -> 160_000
        }
    return if (config.driftSpeedMultiplier <= 0f) base else (base / config.driftSpeedMultiplier.coerceAtLeast(0.2f)).toInt()
}

@Composable
fun NoctCard(modifier: Modifier = Modifier, shape: Shape = RoundedCornerShape(22.dp), contentPadding: PaddingValues = PaddingValues(NoctSpacing.md), content: @Composable () -> Unit) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = NoctColors.Glass),
        border = BorderStroke(1.dp, NoctColors.GlassBorder),
        shape = shape,
    ) {
        Column(modifier = Modifier.padding(contentPadding)) { content() }
    }
}

@Composable
fun NoctGlassCard(modifier: Modifier = Modifier, contentPadding: PaddingValues = PaddingValues(NoctSpacing.lg), content: @Composable () -> Unit) {
    NoctCard(modifier = modifier, contentPadding = contentPadding, content = content)
}

@Composable
fun NoctSelectableCard(selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, contentPadding: PaddingValues = PaddingValues(NoctSpacing.lg), content: @Composable () -> Unit) {
    val accent = LocalNoctAccent.current
    val shape = RoundedCornerShape(24.dp)
    Surface(
        modifier =
        modifier
            .clip(shape)
            .clickable(role = Role.Button, onClick = onClick)
            .then(if (selected) Modifier.noctGradientBorder(shape) else Modifier),
        color = if (selected) Color(0xC2141A28) else NoctColors.Glass,
        contentColor = NoctColors.TextPrimary,
        border = BorderStroke(1.dp, if (selected) accent.copy(alpha = 0.18f) else NoctColors.GlassBorder),
        shape = shape,
    ) {
        Column(modifier = Modifier.padding(contentPadding)) { content() }
    }
}

@Composable
fun NoctOrb(modifier: Modifier = Modifier, reducedMotion: Boolean = false, color: Color = NoctColors.Cyan) {
    NoctAnimatedOrb(modifier = modifier.size(82.dp), color = color, reducedMotion = reducedMotion)
}

@Composable
fun NoctPrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, minHeight: Dp = 56.dp) {
    val accent = LocalNoctAccent.current
    val shape = RoundedCornerShape(50)
    val labelColor = Color(0xFF001116)
    Box(
        modifier =
        modifier
            .heightIn(min = minHeight)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics {
                role = Role.Button
                contentDescription = text
            },
        contentAlignment = Alignment.Center,
    ) {
        NoctAccentButtonChrome(
            modifier = Modifier.matchParentSize(),
            accent = accent,
            shape = shape,
            glowIntensity = NoctAccentGlowIntensity.Standard,
            fillBrush = noctPrimaryButtonFillBrush(accent),
            borderBrush = noctPrimaryButtonBorderBrush(accent),
        )
        Text(
            text = text,
            color = labelColor,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            lineHeight = MaterialTheme.typography.labelLarge.lineHeight * 1.05f,
            modifier =
            Modifier.padding(
                horizontal = noctSpace(NoctSpacing.lg),
                vertical = noctSpace(NoctSpacing.sm) + 2.dp,
            ),
        )
    }
}

@Composable
fun NoctSecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, minHeight: Dp = 48.dp) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = minHeight),
        shape = RoundedCornerShape(50),
        border = BorderStroke(1.dp, NoctColors.GlassBorder),
        colors =
        ButtonDefaults.outlinedButtonColors(
            contentColor = NoctColors.TextPrimary,
            containerColor = Color(0x330C1018),
        ),
        contentPadding = PaddingValues(horizontal = noctSpace(NoctSpacing.lg), vertical = noctSpace(NoctSpacing.md)),
    ) {
        Text(text = text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun NoctStatusPill(text: String, color: Color, modifier: Modifier = Modifier) {
    val verticalPadding = noctSpace(NoctSpacing.sm)
    val horizontalPadding = noctSpace(NoctSpacing.md)
    Surface(
        modifier = modifier,
        color = Color(0x66101824),
        contentColor = NoctColors.TextPrimary,
        border = BorderStroke(1.dp, color.copy(alpha = 0.34f)),
        shape = RoundedCornerShape(50),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = horizontalPadding, vertical = verticalPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier =
                Modifier
                    .size(14.dp)
                    .drawWithCache {
                        val halo =
                            Brush.radialGradient(
                                colors = listOf(color.copy(alpha = 0.72f), Color.Transparent),
                                center = size.center,
                                radius = size.maxDimension * 0.72f,
                            )
                        onDrawBehind {
                            drawCircle(brush = halo, radius = size.minDimension * 0.72f, center = size.center)
                            drawCircle(color = color, radius = size.minDimension * 0.26f, center = size.center)
                        }
                    },
            )
            Text(
                text = "  $text",
                color = NoctColors.TextPrimary,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
            )
        }
    }
}

@Composable
fun NoctBatteryPill(text: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = Color(0x66101824),
        contentColor = NoctColors.TextPrimary,
        border = BorderStroke(1.dp, NoctColors.Violet.copy(alpha = 0.34f)),
        shape = RoundedCornerShape(50),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = noctSpace(NoctSpacing.md), vertical = noctSpace(NoctSpacing.sm)),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Canvas(modifier = Modifier.size(width = 18.dp, height = 12.dp).semantics { contentDescription = "Battery" }) {
                val stroke = Stroke(width = 1.8.dp.toPx())
                drawRoundRect(
                    color = NoctColors.TextPrimary.copy(alpha = 0.88f),
                    topLeft = Offset(size.width * 0.02f, size.height * 0.10f),
                    size = androidx.compose.ui.geometry.Size(size.width * 0.82f, size.height * 0.80f),
                    cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx()),
                    style = stroke,
                )
                drawRoundRect(
                    color = NoctColors.TextPrimary.copy(alpha = 0.88f),
                    topLeft = Offset(size.width * 0.86f, size.height * 0.34f),
                    size = androidx.compose.ui.geometry.Size(size.width * 0.12f, size.height * 0.30f),
                    cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx()),
                    style = Fill,
                )
                drawRoundRect(
                    color = NoctColors.TextPrimary.copy(alpha = 0.72f),
                    topLeft = Offset(size.width * 0.12f, size.height * 0.24f),
                    size = androidx.compose.ui.geometry.Size(size.width * 0.40f, size.height * 0.52f),
                    cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx()),
                    style = Fill,
                )
            }
            Text(text, color = NoctColors.TextPrimary, style = MaterialTheme.typography.labelLarge, maxLines = 1)
        }
    }
}

@Composable
fun NoctWordmark(title: String = "NoctDock", subtitle: String = "Wireless Dock Mode", compact: Boolean = false, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(if (compact) 2.dp else noctSpace(NoctSpacing.xs)),
    ) {
        Text(
            text = title,
            style =
            (
                if (compact) {
                    MaterialTheme.typography.displayMedium.copy(
                        fontSize = 40.sp,
                        lineHeight = 44.sp,
                    )
                } else {
                    MaterialTheme.typography.displayMedium
                }
                ).merge(
                TextStyle(
                    brush =
                    Brush.linearGradient(
                        colorStops =
                        arrayOf(
                            0f to NoctColors.TextPrimary,
                            0.38f to Color(0xFFD6ECFF),
                            0.72f to Color(0xFFB1C6FF),
                            1f to Color(0xFFC7AEFF),
                        ),
                    ),
                ),
            ),
            maxLines = 1,
        )
        Text(
            text = subtitle,
            color = NoctColors.TextSecondary,
            style = if (compact) {
                MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Normal,
                )
            } else {
                MaterialTheme.typography.titleLarge
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun NoctSectionHeader(title: String, subtitle: String? = null, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(NoctSpacing.xs)) {
        Text(text = title, color = NoctColors.TextPrimary, style = MaterialTheme.typography.headlineMedium)
        if (subtitle != null) Text(text = subtitle, color = NoctColors.TextSecondary, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
fun NoctAnimatedOrb(modifier: Modifier = Modifier, color: Color = NoctColors.Cyan, reducedMotion: Boolean = false) {
    val transition = rememberInfiniteTransition(label = "noct-orb")
    val pulse by
        transition.animateFloat(
            initialValue = 0.55f,
            targetValue = if (reducedMotion) 0.55f else 0.9f,
            animationSpec =
            infiniteRepeatable(
                animation = tween(durationMillis = 7200, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "orb-pulse",
        )
    Box(
        modifier =
        modifier
            .drawWithCache {
                val outerHalo =
                    Brush.radialGradient(
                        colors = listOf(
                            color.copy(alpha = pulse * 0.58f),
                            NoctColors.Magenta.copy(alpha = pulse * 0.14f),
                            Color.Transparent,
                        ),
                        center = size.center,
                        radius = size.maxDimension * 0.70f,
                    )
                val shell =
                    Brush.radialGradient(
                        colors = listOf(Color(0xFF0D1120), Color(0xFF12162A), Color(0xFF06080F)),
                        center = Offset(size.width * 0.38f, size.height * 0.32f),
                        radius = size.maxDimension * 0.62f,
                    )
                val rim =
                    Brush.sweepGradient(
                        listOf(
                            NoctColors.Cyan.copy(alpha = 0.95f),
                            NoctColors.Violet.copy(alpha = 0.88f),
                            NoctColors.Magenta.copy(alpha = 0.92f),
                            NoctColors.Cyan.copy(alpha = 0.95f),
                        ),
                        center = size.center,
                    )
                onDrawBehind {
                    drawCircle(brush = outerHalo, radius = size.minDimension * 0.58f, center = size.center)
                    drawCircle(brush = shell, radius = size.minDimension * 0.30f, center = size.center)
                    drawCircle(
                        brush = rim,
                        radius = size.minDimension * 0.31f,
                        center = size.center,
                        style = Stroke(width = size.minDimension * 0.028f),
                    )
                    drawCircle(
                        color = Color.White.copy(alpha = 0.08f),
                        radius = size.minDimension * 0.06f,
                        center = Offset(size.width * 0.37f, size.height * 0.34f),
                    )
                }
            },
    )
}

/** Lens-style dock orb used on the sender portal and receiver waiting hero. */
@Composable
fun NoctDockHeroOrb(accent: Color, modifier: Modifier = Modifier, reducedMotion: Boolean = false, large: Boolean = false, sizeScale: Float = 1f) {
    val base = if (large) 132.dp else 96.dp
    Box(contentAlignment = Alignment.Center, modifier = modifier.size(base * sizeScale)) {
        NoctAnimatedOrb(
            modifier = Modifier.fillMaxSize(),
            color = accent,
            reducedMotion = reducedMotion,
        )
        Canvas(modifier = Modifier.fillMaxSize()) {
            val c = center
            val pulse = size.minDimension * 0.42f
            drawCircle(
                brush = Brush.radialGradient(listOf(NoctColors.Magenta.copy(alpha = 0.22f), Color.Transparent), center = c, radius = pulse),
                radius = pulse,
                center = c,
            )
            drawCircle(
                color = accent.copy(alpha = 0.55f),
                radius = pulse * 0.72f,
                center = c,
                style = Stroke(width = 2.dp.toPx()),
            )
        }
    }
}

@Composable
fun NoctPrimaryConsoleButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, minHeight: Dp = 68.dp) {
    val accent = LocalNoctAccent.current
    val shape = RoundedCornerShape(50)
    val fillAlpha = if (enabled) 1f else 0.38f
    Box(
        modifier =
        modifier
            .heightIn(min = minHeight)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics {
                role = Role.Button
                contentDescription = text
            },
        contentAlignment = Alignment.Center,
    ) {
        NoctAccentButtonChrome(
            modifier = Modifier.matchParentSize(),
            accent = accent,
            shape = shape,
            glowIntensity = if (enabled) NoctAccentGlowIntensity.Strong else null,
            fillBrush =
            Brush.linearGradient(
                listOf(
                    accent.copy(alpha = fillAlpha),
                    lerp(accent, Color.White, 0.22f).copy(alpha = fillAlpha),
                    Color(0xFF55B8FF).copy(alpha = fillAlpha),
                    NoctColors.Violet.copy(alpha = fillAlpha * 0.9f),
                    NoctColors.Magenta.copy(alpha = fillAlpha * 0.85f),
                ),
            ),
            borderBrush =
            if (enabled) {
                noctPrimaryButtonBorderBrush(accent)
            } else {
                Brush.linearGradient(
                    listOf(
                        accent.copy(alpha = 0.26f),
                        NoctColors.Magenta.copy(alpha = 0.20f),
                    ),
                )
            },
        )
        Row(
            modifier =
            Modifier.padding(
                horizontal = noctSpace(NoctSpacing.xl),
                vertical = noctSpace(NoctSpacing.md),
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Canvas(modifier = Modifier.size(18.dp).semantics { contentDescription = "Console Mode" }) {
                drawCircle(Color.White.copy(alpha = 0.95f), radius = size.minDimension * 0.18f, center = size.center)
                drawLine(
                    Color.White.copy(alpha = 0.74f),
                    Offset(size.width * 0.78f, size.height * 0.12f),
                    Offset(
                        size.width * 0.78f,
                        size.height * 0.34f,
                    ),
                    strokeWidth = 1.6.dp.toPx(),
                    cap = StrokeCap.Round,
                )
                drawLine(
                    Color.White.copy(alpha = 0.74f),
                    Offset(size.width * 0.67f, size.height * 0.23f),
                    Offset(
                        size.width * 0.89f,
                        size.height * 0.23f,
                    ),
                    strokeWidth = 1.6.dp.toPx(),
                    cap = StrokeCap.Round,
                )
                drawCircle(
                    Color.White.copy(alpha = 0.26f),
                    radius = size.minDimension * 0.46f,
                    center = size.center,
                    style = Stroke(width = 2.dp.toPx()),
                )
            }
            Spacer(Modifier.width(NoctSpacing.sm))
            Text(text = text, color = Color.White, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleLarge)
        }
    }
}

@Composable
fun NoctNavCard(title: String, icon: String, onClick: () -> Unit, compact: Boolean = false, modifier: Modifier = Modifier) {
    val accent = LocalNoctAccent.current
    val shape = RoundedCornerShape(20.dp)
    Surface(
        modifier =
        modifier
            .heightIn(min = if (compact) 74.dp else 82.dp)
            .drawWithCache {
                val glow =
                    Brush.radialGradient(
                        colors = listOf(accent.copy(alpha = 0.12f), Color.Transparent),
                        center = Offset(size.width * 0.12f, size.height * 0.82f),
                        radius = size.width * 0.52f,
                    )
                val corner = CornerRadius(20.dp.toPx(), 20.dp.toPx())
                onDrawBehind {
                    drawRoundRect(
                        brush = glow,
                        cornerRadius = corner,
                    )
                }
            }
            .clip(shape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics {
                role = Role.Button
                contentDescription = title
            },
        color = Color(0x8C080D16),
        contentColor = NoctColors.TextPrimary,
        border = BorderStroke(1.dp, accent.copy(alpha = 0.26f)),
        shape = shape,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(
                horizontal = if (compact) 18.dp else noctSpace(NoctSpacing.lg),
                vertical = if (compact) 14.dp else noctSpace(NoctSpacing.md),
            ),
            horizontalArrangement = Arrangement.spacedBy(noctSpace(NoctSpacing.md)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NoctNavIcon(icon = icon, color = accent, modifier = Modifier.size(if (compact) 34.dp else 38.dp))
            Text(
                title,
                color = NoctColors.TextPrimary,
                style = if (compact) {
                    MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.SemiBold,
                    )
                } else {
                    MaterialTheme.typography.labelLarge
                },
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Canvas(modifier = Modifier.size(18.dp).semantics { contentDescription = "Open $title" }) {
                val stroke = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round)
                drawLine(
                    NoctColors.TextSecondary.copy(alpha = 0.92f),
                    Offset(size.width * 0.35f, size.height * 0.18f),
                    Offset(size.width * 0.68f, size.height * 0.50f),
                    strokeWidth = stroke.width,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    NoctColors.TextSecondary.copy(alpha = 0.92f),
                    Offset(size.width * 0.68f, size.height * 0.50f),
                    Offset(size.width * 0.35f, size.height * 0.82f),
                    strokeWidth = stroke.width,
                    cap = StrokeCap.Round,
                )
            }
        }
    }
}

@Composable
private fun NoctNavIcon(icon: String, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.semantics { contentDescription = icon }) {
        val stroke = Stroke(width = 2.4.dp.toPx(), cap = StrokeCap.Round)
        val glow = color.copy(alpha = 0.22f)
        drawCircle(glow, radius = size.minDimension * 0.48f, center = size.center)
        when (icon) {
            "TV" -> {
                drawRoundRect(
                    color = color.copy(alpha = 0.95f),
                    topLeft = Offset(size.width * 0.12f, size.height * 0.18f),
                    size = androidx.compose.ui.geometry.Size(size.width * 0.76f, size.height * 0.50f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()),
                    style = stroke,
                )
                drawLine(
                    color,
                    Offset(size.width * 0.50f, size.height * 0.68f),
                    Offset(size.width * 0.50f, size.height * 0.84f),
                    strokeWidth = stroke.width,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color,
                    Offset(size.width * 0.30f, size.height * 0.86f),
                    Offset(size.width * 0.70f, size.height * 0.86f),
                    strokeWidth = stroke.width,
                    cap = StrokeCap.Round,
                )
            }

            "LIB" -> {
                drawRoundRect(
                    color.copy(alpha = 0.46f),
                    Offset(size.width * 0.28f, size.height * 0.16f),
                    androidx.compose.ui.geometry.Size(size.width * 0.52f, size.height * 0.52f),
                    androidx.compose.ui.geometry.CornerRadius(4.dp.toPx()),
                    style = stroke,
                )
                drawRoundRect(
                    color.copy(alpha = 0.68f),
                    Offset(size.width * 0.20f, size.height * 0.24f),
                    androidx.compose.ui.geometry.Size(size.width * 0.52f, size.height * 0.52f),
                    androidx.compose.ui.geometry.CornerRadius(4.dp.toPx()),
                    style = stroke,
                )
                drawRoundRect(
                    color,
                    Offset(size.width * 0.12f, size.height * 0.32f),
                    androidx.compose.ui.geometry.Size(
                        size.width * 0.52f,
                        size.height * 0.52f,
                    ),
                    androidx.compose.ui.geometry.CornerRadius(4.dp.toPx()),
                    style = stroke,
                )
                drawLine(
                    color,
                    Offset(size.width * 0.25f, size.height * 0.47f),
                    Offset(size.width * 0.51f, size.height * 0.47f),
                    strokeWidth = stroke.width,
                    cap = StrokeCap.Round,
                )
            }

            "MODE" -> {
                drawRoundRect(
                    color,
                    Offset(size.width * 0.10f, size.height * 0.34f),
                    androidx.compose.ui.geometry.Size(
                        size.width * 0.80f,
                        size.height * 0.38f,
                    ),
                    androidx.compose.ui.geometry.CornerRadius(12.dp.toPx()),
                    style = stroke,
                )
                drawLine(
                    color,
                    Offset(size.width * 0.26f, size.height * 0.45f),
                    Offset(size.width * 0.26f, size.height * 0.62f),
                    strokeWidth = stroke.width,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color,
                    Offset(size.width * 0.17f, size.height * 0.535f),
                    Offset(size.width * 0.35f, size.height * 0.535f),
                    strokeWidth = stroke.width,
                    cap = StrokeCap.Round,
                )
                drawCircle(color, radius = size.minDimension * 0.045f, center = Offset(size.width * 0.66f, size.height * 0.49f))
                drawCircle(color, radius = size.minDimension * 0.045f, center = Offset(size.width * 0.78f, size.height * 0.60f))
                drawLine(
                    color,
                    Offset(size.width * 0.23f, size.height * 0.34f),
                    Offset(size.width * 0.30f, size.height * 0.24f),
                    strokeWidth = stroke.width,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color,
                    Offset(size.width * 0.70f, size.height * 0.24f),
                    Offset(size.width * 0.77f, size.height * 0.34f),
                    strokeWidth = stroke.width,
                    cap = StrokeCap.Round,
                )
            }

            "SET" -> {
                drawCircle(color, radius = size.minDimension * 0.18f, center = size.center, style = stroke)
                drawCircle(color, radius = size.minDimension * 0.06f, center = size.center)
                repeat(8) { index ->
                    val angle = index * TWO_PI / 8f
                    val start = Offset(
                        size.center.x + kotlin.math.cos(angle) * size.minDimension * 0.29f,
                        size.center.y + kotlin.math.sin(angle) * size.minDimension * 0.29f,
                    )
                    val end = Offset(
                        size.center.x + kotlin.math.cos(angle) * size.minDimension * 0.42f,
                        size.center.y + kotlin.math.sin(angle) * size.minDimension * 0.42f,
                    )
                    drawLine(color, start, end, strokeWidth = stroke.width, cap = StrokeCap.Round)
                }
            }
        }
    }
}

@Composable
fun NoctPrivacyBar(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = Color(0x8A0A101B),
        contentColor = NoctColors.TextPrimary,
        border = BorderStroke(1.dp, NoctColors.Cyan.copy(alpha = 0.28f)),
        shape = RoundedCornerShape(22.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(noctSpace(NoctSpacing.md)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Canvas(modifier = Modifier.size(24.dp).semantics { contentDescription = "Local privacy" }) {
                val stroke = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round)
                drawRoundRect(
                    NoctColors.Green.copy(alpha = 0.84f),
                    style = stroke,
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(8.dp.toPx()),
                )
                drawLine(
                    NoctColors.Green.copy(alpha = 0.84f),
                    Offset(size.width * 0.28f, size.height * 0.52f),
                    Offset(
                        size.width * 0.45f,
                        size.height * 0.68f,
                    ),
                    strokeWidth = 2.2.dp.toPx(),
                    cap = StrokeCap.Round,
                )
                drawLine(
                    NoctColors.Green.copy(alpha = 0.84f),
                    Offset(size.width * 0.45f, size.height * 0.68f),
                    Offset(
                        size.width * 0.74f,
                        size.height * 0.34f,
                    ),
                    strokeWidth = 2.2.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }
            Text(
                "Local privacy",
                color = NoctColors.TextPrimary,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.bodyLarge,
            )
            Canvas(modifier = Modifier.size(width = 1.dp, height = 18.dp)) {
                drawLine(
                    NoctColors.GlassBorder.copy(alpha = 0.72f),
                    Offset(size.width * 0.5f, 0f),
                    Offset(size.width * 0.5f, size.height),
                    strokeWidth = size.width,
                )
            }
            Text(
                "Local network only. No accounts. No analytics. Your library stays on this device.",
                color = NoctColors.TextSecondary,
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, lineHeight = 20.sp),
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Canvas(modifier = Modifier.size(16.dp).semantics { contentDescription = "Care" }) {
                val stroke = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round)
                drawLine(
                    NoctColors.Magenta.copy(alpha = 0.82f),
                    Offset(size.width * 0.50f, size.height * 0.82f),
                    Offset(
                        size.width * 0.18f,
                        size.height * 0.48f,
                    ),
                    strokeWidth = stroke.width,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    NoctColors.Magenta.copy(alpha = 0.82f),
                    Offset(size.width * 0.50f, size.height * 0.82f),
                    Offset(
                        size.width * 0.82f,
                        size.height * 0.48f,
                    ),
                    strokeWidth = stroke.width,
                    cap = StrokeCap.Round,
                )
                drawCircle(
                    NoctColors.Magenta.copy(alpha = 0.82f),
                    radius = size.minDimension * 0.16f,
                    center = Offset(
                        size.width * 0.28f,
                        size.height * 0.36f,
                    ),
                    style = stroke,
                )
                drawCircle(
                    NoctColors.Magenta.copy(alpha = 0.82f),
                    radius = size.minDimension * 0.16f,
                    center = Offset(
                        size.width * 0.72f,
                        size.height * 0.36f,
                    ),
                    style = stroke,
                )
            }
        }
    }
}

@Composable
fun NoctReceiverHeroCard(
    title: String,
    subtitle: String,
    chips: List<String>,
    orbColor: Color,
    reducedMotion: Boolean,
    onOpenDevices: () -> Unit,
    compact: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(28.dp)
    Box(
        modifier =
        modifier
            .heightIn(min = if (compact) 154.dp else 182.dp)
            .drawWithCache {
                val leftGlow =
                    Brush.radialGradient(
                        colors = listOf(NoctColors.Cyan.copy(alpha = 0.22f), Color.Transparent),
                        center = Offset(size.width * 0.12f, size.height * 0.48f),
                        radius = size.maxDimension * 0.44f,
                    )
                val rightGlow =
                    Brush.radialGradient(
                        colors = listOf(NoctColors.Magenta.copy(alpha = 0.16f), Color.Transparent),
                        center = Offset(size.width * 0.88f, size.height * 0.36f),
                        radius = size.maxDimension * 0.42f,
                    )
                val corner = CornerRadius(28.dp.toPx(), 28.dp.toPx())
                onDrawBehind {
                    drawRoundRect(brush = leftGlow, cornerRadius = corner)
                    drawRoundRect(brush = rightGlow, cornerRadius = corner)
                }
            }
            .clip(shape)
            .background(Color(0xA8080D17))
            .noctGradientBorder(shape)
            .clickable(role = Role.Button, onClick = onOpenDevices)
            .semantics {
                role = Role.Button
                contentDescription = "$title. $subtitle. Open TVs."
            }
            .padding(
                horizontal = if (compact) 24.dp else noctSpace(NoctSpacing.xl),
                vertical = if (compact) 20.dp else noctSpace(NoctSpacing.lg),
            ),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(if (compact) 16.dp else noctSpace(NoctSpacing.lg))) {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                if (maxWidth < 440.dp) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(if (compact) 12.dp else noctSpace(NoctSpacing.md)),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        NoctOrb(modifier = Modifier.size(if (compact) 78.dp else 92.dp), color = orbColor, reducedMotion = reducedMotion)
                        Text(title, color = NoctColors.TextPrimary, style = MaterialTheme.typography.headlineMedium)
                        Text(subtitle, color = NoctColors.TextSecondary, style = MaterialTheme.typography.bodyLarge)
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(if (compact) 20.dp else noctSpace(NoctSpacing.lg)),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        NoctOrb(modifier = Modifier.size(if (compact) 108.dp else 132.dp), color = orbColor, reducedMotion = reducedMotion)
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(if (compact) 6.dp else noctSpace(NoctSpacing.sm)),
                        ) {
                            Text(
                                title,
                                color = NoctColors.TextPrimary,
                                style = if (compact) {
                                    MaterialTheme.typography.headlineMedium.copy(
                                        fontSize = 34.sp,
                                        lineHeight = 38.sp,
                                    )
                                } else {
                                    MaterialTheme.typography.headlineMedium
                                },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                subtitle,
                                color = NoctColors.TextSecondary,
                                style = if (compact) {
                                    MaterialTheme.typography.bodyLarge.copy(
                                        fontSize = 16.sp,
                                        lineHeight = 22.sp,
                                    )
                                } else {
                                    MaterialTheme.typography.bodyLarge
                                },
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Canvas(modifier = Modifier.size(if (compact) 24.dp else 28.dp).semantics { contentDescription = "Open TVs" }) {
                            drawLine(
                                orbColor.copy(alpha = 0.9f),
                                Offset(size.width * 0.34f, size.height * 0.18f),
                                Offset(
                                    size.width * 0.68f,
                                    size.height * 0.50f,
                                ),
                                strokeWidth = 3.dp.toPx(),
                                cap = StrokeCap.Round,
                            )
                            drawLine(
                                orbColor.copy(alpha = 0.9f),
                                Offset(size.width * 0.68f, size.height * 0.50f),
                                Offset(
                                    size.width * 0.34f,
                                    size.height * 0.82f,
                                ),
                                strokeWidth = 3.dp.toPx(),
                                cap = StrokeCap.Round,
                            )
                        }
                    }
                }
            }
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                if (maxWidth < 440.dp) {
                    Column(verticalArrangement = Arrangement.spacedBy(noctSpace(NoctSpacing.sm))) {
                        chips.forEach { chip ->
                            NoctStatusPill(chip, orbColor)
                        }
                    }
                } else {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(if (compact) 12.dp else noctSpace(NoctSpacing.md)),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        chips.forEachIndexed { index, chip ->
                            InlineHeroChip(label = chip, accent = orbColor, compact = compact)
                            if (index < chips.lastIndex) {
                                Canvas(modifier = Modifier.size(width = 1.dp, height = 18.dp)) {
                                    drawLine(
                                        NoctColors.GlassBorder.copy(alpha = 0.48f),
                                        Offset(size.width * 0.5f, 0f),
                                        Offset(size.width * 0.5f, size.height),
                                        strokeWidth = size.width,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InlineHeroChip(label: String, accent: Color, compact: Boolean = false) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Canvas(modifier = Modifier.size(if (compact) 16.dp else 18.dp).semantics { contentDescription = label }) {
            val stroke = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
            when {
                "signal" in label.lowercase() -> {
                    val x = size.width * 0.14f
                    val widths = size.width * 0.12f
                    listOf(0.72f, 0.56f, 0.38f, 0.18f).forEachIndexed { index, top ->
                        drawLine(
                            NoctColors.Cyan.copy(alpha = 0.92f),
                            Offset(x + index * size.width * 0.18f, size.height * top),
                            Offset(x + index * size.width * 0.18f, size.height * 0.86f),
                            strokeWidth = widths,
                            cap = StrokeCap.Round,
                        )
                    }
                }

                "looking" in label.lowercase() -> {
                    drawCircle(accent.copy(alpha = 0.24f), radius = size.minDimension * 0.46f, center = size.center)
                    drawCircle(accent.copy(alpha = 0.94f), radius = size.minDimension * 0.20f, center = size.center)
                }

                "local" in label.lowercase() -> {
                    drawRoundRect(
                        accent.copy(alpha = 0.92f),
                        topLeft = Offset(size.width * 0.16f, size.height * 0.18f),
                        size = androidx.compose.ui.geometry.Size(size.width * 0.68f, size.height * 0.62f),
                        cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()),
                        style = stroke,
                    )
                    drawLine(
                        accent.copy(alpha = 0.92f),
                        Offset(size.width * 0.34f, size.height * 0.52f),
                        Offset(
                            size.width * 0.46f,
                            size.height * 0.66f,
                        ),
                        strokeWidth = stroke.width,
                        cap = StrokeCap.Round,
                    )
                    drawLine(
                        accent.copy(alpha = 0.92f),
                        Offset(size.width * 0.46f, size.height * 0.66f),
                        Offset(
                            size.width * 0.68f,
                            size.height * 0.38f,
                        ),
                        strokeWidth = stroke.width,
                        cap = StrokeCap.Round,
                    )
                }

                else -> {
                    drawLine(
                        accent.copy(alpha = 0.92f),
                        Offset(size.width * 0.50f, size.height * 0.12f),
                        Offset(
                            size.width * 0.50f,
                            size.height * 0.88f,
                        ),
                        strokeWidth = stroke.width,
                        cap = StrokeCap.Round,
                    )
                    drawLine(
                        accent.copy(alpha = 0.92f),
                        Offset(size.width * 0.22f, size.height * 0.34f),
                        Offset(
                            size.width * 0.78f,
                            size.height * 0.34f,
                        ),
                        strokeWidth = stroke.width,
                        cap = StrokeCap.Round,
                    )
                    drawLine(
                        accent.copy(alpha = 0.92f),
                        Offset(size.width * 0.22f, size.height * 0.66f),
                        Offset(
                            size.width * 0.78f,
                            size.height * 0.66f,
                        ),
                        strokeWidth = stroke.width,
                        cap = StrokeCap.Round,
                    )
                }
            }
        }
        Text(
            label,
            color = NoctColors.TextSecondary,
            style = if (compact) {
                MaterialTheme.typography.bodyLarge.copy(
                    fontSize = 15.sp,
                    lineHeight = 20.sp,
                )
            } else {
                MaterialTheme.typography.bodyLarge
            },
            maxLines = 1,
        )
    }
}

@Composable
private fun NoctAccentButtonChrome(accent: Color, shape: RoundedCornerShape, glowIntensity: NoctAccentGlowIntensity?, fillBrush: Brush, borderBrush: Brush, modifier: Modifier = Modifier) {
    Box(
        modifier =
        modifier
            .then(
                if (glowIntensity != null) {
                    Modifier.noctAccentButtonGlow(accent = accent, shape = shape, intensity = glowIntensity)
                } else {
                    Modifier
                },
            )
            .clip(shape)
            .background(fillBrush, shape)
            .border(width = 1.5.dp, brush = borderBrush, shape = shape),
    )
}

private enum class NoctAccentGlowIntensity {
    Standard,
    Strong,
}

private fun noctPrimaryButtonFillBrush(accent: Color): Brush = Brush.linearGradient(
    listOf(
        accent,
        lerp(accent, Color.White, 0.28f),
        lerp(accent, NoctColors.Violet, 0.18f),
        accent.copy(alpha = 0.94f),
    ),
)

private fun noctPrimaryButtonBorderBrush(accent: Color): Brush = Brush.linearGradient(
    listOf(
        Color.White.copy(alpha = 0.62f),
        accent.copy(alpha = 0.98f),
        lerp(accent, NoctColors.Magenta, 0.35f).copy(alpha = 0.92f),
    ),
)

private fun Modifier.noctAccentButtonGlow(accent: Color, shape: RoundedCornerShape, intensity: NoctAccentGlowIntensity): Modifier = drawWithCache {
    val extra =
        when (intensity) {
            NoctAccentGlowIntensity.Standard -> 5.dp.toPx()
            NoctAccentGlowIntensity.Strong -> 7.dp.toPx()
        }
    val peakAlpha =
        when (intensity) {
            NoctAccentGlowIntensity.Standard -> 0.68f
            NoctAccentGlowIntensity.Strong -> 0.84f
        }
    val glowSize = Size(size.width + extra * 2f, size.height + extra * 2f)
    val pillRadius = glowSize.height / 2f
    val center = Offset(size.width * 0.5f, size.height * 0.5f)
    val glow =
        Brush.radialGradient(
            colors =
            listOf(
                accent.copy(alpha = peakAlpha),
                accent.copy(alpha = peakAlpha * 0.48f),
                NoctColors.Magenta.copy(alpha = peakAlpha * 0.22f),
                Color.Transparent,
            ),
            center = center,
            radius = kotlin.math.max(glowSize.width * 0.42f, glowSize.height * 0.88f),
        )
    onDrawBehind {
        translate(-extra, -extra) {
            drawRoundRect(
                brush = glow,
                size = glowSize,
                cornerRadius = CornerRadius(pillRadius, pillRadius),
            )
        }
    }
}

@Composable
fun Modifier.noctGradientBorder(shape: Shape = RoundedCornerShape(22.dp)): Modifier {
    val accent = LocalNoctAccent.current
    return border(
        width = 1.25.dp,
        brush =
        Brush.linearGradient(
            listOf(
                accent.copy(alpha = 0.78f),
                lerp(accent, NoctColors.Magenta, 0.4f).copy(alpha = 0.58f),
            ),
        ),
        shape = shape,
    )
}

@Composable
fun NoctMetricRow(label: String, value: String, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier = modifier) {
        if (maxWidth < 420.dp) {
            Column(verticalArrangement = Arrangement.spacedBy(NoctSpacing.xs)) {
                Text(text = label, color = NoctColors.TextSecondary)
                Text(text = value, color = NoctColors.TextPrimary, fontWeight = FontWeight.SemiBold)
            }
        } else {
            Row {
                Text(text = label, color = NoctColors.TextSecondary, modifier = Modifier.weight(1f))
                Text(text = value, color = NoctColors.TextPrimary, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
fun NoctFocusCard(modifier: Modifier = Modifier, contentPadding: PaddingValues = PaddingValues(NoctSpacing.lg), content: @Composable RowScope.() -> Unit) {
    var focused by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    val accent = LocalNoctAccent.current
    Card(
        modifier = modifier.onFocusChanged { focused = it.isFocused },
        colors = CardDefaults.cardColors(containerColor = if (focused) Color(0xCC101827) else NoctColors.Glass),
        border = BorderStroke(if (focused) 2.dp else 1.dp, if (focused) accent else NoctColors.GlassBorder),
        shape = RoundedCornerShape(8.dp),
    ) {
        Row(modifier = Modifier.padding(contentPadding), content = content)
    }
}
