package com.glowseed.noctdock.receiver

import android.app.Activity
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.glowseed.noctdock.core.BackgroundMotionMode
import com.glowseed.noctdock.core.BackgroundSurface
import com.glowseed.noctdock.core.NebulaTheme
import com.glowseed.noctdock.core.NoctBackground
import com.glowseed.noctdock.core.NoctColors
import com.glowseed.noctdock.core.NoctDockHeroOrb
import com.glowseed.noctdock.core.NoctFocusCard
import com.glowseed.noctdock.core.NoctGlassCard
import com.glowseed.noctdock.core.NoctLog
import com.glowseed.noctdock.core.NoctMetricRow
import com.glowseed.noctdock.core.NoctPrimaryButton
import com.glowseed.noctdock.core.NoctSecondaryButton
import com.glowseed.noctdock.core.NoctSelectableCard
import com.glowseed.noctdock.core.NoctSpacing
import com.glowseed.noctdock.core.NoctStatusPill
import com.glowseed.noctdock.core.NoctTheme
import com.glowseed.noctdock.core.NoctWordmark
import com.glowseed.noctdock.core.PairingState
import com.glowseed.noctdock.core.ReceiverAspectRatio
import com.glowseed.noctdock.core.ReceiverDisplayWording
import com.glowseed.noctdock.core.ReceiverFormFactor
import com.glowseed.noctdock.core.ReceiverScaleMode
import com.glowseed.noctdock.core.StreamMetrics
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        NoctLog.configure(debugLogs = BuildConfig.NOCT_DEBUG_LOGS, infoLogs = !BuildConfig.NOCT_PERF_BUILD)
        setContent { NoctTheme { TvApp() } }
    }
}

private object TvRoutes {
    const val ROUTE_HOME = "home"
    const val ROUTE_STREAM = "stream"
    const val ROUTE_SETTINGS = "settings"
    const val ROUTE_SYSTEM_STATUS = "system_status"
}

@Composable
private fun TvApp(navController: NavHostController = rememberNavController()) {
    val receiverViewModel: ReceiverViewModel = viewModel()
    val uiState = receiverViewModel.uiState.collectAsStateWithLifecycle().value
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var lastBackPressAt by remember { mutableStateOf(0L) }
    DisposableEffect(lifecycleOwner, receiverViewModel) {
        val observer =
            LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_START -> receiverViewModel.setAppForeground(true)
                    Lifecycle.Event.ON_STOP -> receiverViewModel.setAppForeground(false)
                    else -> Unit
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            receiverViewModel.setAppForeground(false)
        }
    }
    BackHandler {
        val now = System.currentTimeMillis()
        if (now - lastBackPressAt <= 2_000L) {
            (context as? Activity)?.finish()
        } else {
            lastBackPressAt = now
            Toast.makeText(context, "Press back again to exit NoctDock.", Toast.LENGTH_SHORT).show()
        }
    }
    LaunchedEffect(uiState.streamActive) {
        if (uiState.streamActive) {
            navController.navigate(TvRoutes.ROUTE_STREAM) { launchSingleTop = true }
        }
    }
    DisposableEffect(uiState.formFactor) {
        val activity = context as? Activity
        val previousOrientation = activity?.requestedOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        if (uiState.formFactor == ReceiverFormFactor.PHONE || uiState.formFactor == ReceiverFormFactor.TABLET) {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
        onDispose {
            activity?.requestedOrientation = previousOrientation
        }
    }
    NoctBackground(
        dynamicNebula = !uiState.streamActive,
        theme = NebulaTheme.VioletNebula,
        motionMode = if (uiState.streamActive) BackgroundMotionMode.DeepSpace else BackgroundMotionMode.AnimatedNebula,
        surface = BackgroundSurface.Tv,
    ) {
        NavHost(navController = navController, startDestination = TvRoutes.ROUTE_HOME) {
            composable(TvRoutes.ROUTE_HOME) { TvHomeScreen(navController, uiState, receiverViewModel) }
            composable(TvRoutes.ROUTE_STREAM) { TvStreamScreen(navController, uiState, receiverViewModel) }
            composable(TvRoutes.ROUTE_SETTINGS) { ReceiverSettingsScreen(navController, uiState, receiverViewModel) }
            composable(TvRoutes.ROUTE_SYSTEM_STATUS) { ReceiverSystemStatusScreen(navController, uiState, receiverViewModel) }
        }
    }
}

@Composable
private fun TvHomeScreen(navController: NavHostController, uiState: ReceiverUiState, viewModel: ReceiverViewModel) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val horizontalPadding =
            when {
                uiState.formFactor == ReceiverFormFactor.TV -> if (maxWidth < 620.dp) 24.dp else 72.dp
                maxWidth < 760.dp -> 20.dp
                else -> 28.dp
            }
        val verticalPadding =
            when {
                uiState.formFactor == ReceiverFormFactor.TV -> if (maxWidth < 620.dp) 28.dp else 48.dp
                else -> 20.dp
            }
        val phoneLandscape = uiState.formFactor != ReceiverFormFactor.TV && maxWidth >= 700.dp
        val appTitle = ReceiverDisplayWording.genericName(uiState.formFactor)
        val appSubtitle =
            if (uiState.formFactor == ReceiverFormFactor.TV) {
                "Wireless Dock Receiver"
            } else {
                "Waiting for handheld"
            }
        Box(
            modifier =
            Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = horizontalPadding, vertical = verticalPadding),
        ) {
            if (phoneLandscape) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(NoctSpacing.lg),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top,
                    ) {
                        CompactReceiverHeader(
                            title = appTitle,
                            subtitle = appSubtitle,
                            modifier = Modifier.weight(1f),
                        )
                        NoctStatusPill(tvStatus(uiState), tvAccent(uiState))
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(NoctSpacing.lg),
                        verticalAlignment = Alignment.Top,
                    ) {
                        NoctGlassCard(
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            contentPadding = PaddingValues(20.dp),
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(NoctSpacing.md),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                TvWaitingRing(
                                    color = tvAccent(uiState),
                                    active = !uiState.streamActive,
                                    modifier = Modifier.size(124.dp),
                                )
                                Text(
                                    tvHeadline(uiState),
                                    color = NoctColors.TextPrimary,
                                    style = androidx.compose.material3.MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = TextAlign.Center,
                                )
                                Text(
                                    tvSubtitle(uiState),
                                    color = NoctColors.TextSecondary,
                                    style = androidx.compose.material3.MaterialTheme.typography.bodyLarge,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = TextAlign.Center,
                                )
                                Spacer(modifier = Modifier.weight(1f))
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(NoctSpacing.xs),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    Text("Local privacy", color = NoctColors.TextPrimary, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        "Local network only. No accounts. No cloud.",
                                        color = NoctColors.TextSecondary,
                                        style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        textAlign = TextAlign.Center,
                                    )
                                }
                            }
                        }
                        Column(
                            modifier = Modifier.weight(1.08f).fillMaxHeight().verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(NoctSpacing.md),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            if (uiState.pairingState != PairingState.Trusted) {
                                PairingCodeCard(uiState = uiState, compact = true, modifier = Modifier.fillMaxWidth().focusable())
                            } else {
                                NoctGlassCard(modifier = Modifier.fillMaxWidth()) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text("Remembered handhelds", color = NoctColors.TextSecondary)
                                        Text("${uiState.trustedSenderCount}", color = NoctColors.TextPrimary, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                            NoctPrimaryButton("Open Console View", { navController.navigate(TvRoutes.ROUTE_STREAM) }, modifier = Modifier.fillMaxWidth().focusable(), minHeight = 58.dp)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(NoctSpacing.md),
                            ) {
                                if (uiState.pairingState != PairingState.Trusted) {
                                    NoctSecondaryButton("New code", viewModel::regeneratePairingCode, modifier = Modifier.weight(1f).focusable(), minHeight = 56.dp)
                                }
                                NoctSecondaryButton("Settings", { navController.navigate(TvRoutes.ROUTE_SETTINGS) }, modifier = Modifier.weight(1f).focusable(), minHeight = 56.dp)
                            }
                            NoctSecondaryButton("Clear paired devices", viewModel::clearPairedDevices, modifier = Modifier.fillMaxWidth().focusable(), minHeight = 56.dp)
                        }
                    }
                }
            } else {
                BoxWithConstraints(modifier = Modifier.align(Alignment.TopStart).fillMaxWidth()) {
                    if (maxWidth < 620.dp) {
                        Column(verticalArrangement = Arrangement.spacedBy(NoctSpacing.sm)) {
                            NoctWordmark(title = appTitle, subtitle = appSubtitle)
                            NoctStatusPill(tvStatus(uiState), tvAccent(uiState))
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            NoctWordmark(title = appTitle, subtitle = appSubtitle)
                            NoctStatusPill(tvStatus(uiState), tvAccent(uiState))
                        }
                    }
                }
                Column(
                    modifier = Modifier.align(Alignment.Center).widthIn(max = 780.dp).verticalScroll(rememberScrollState()).padding(bottom = 84.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(NoctSpacing.md),
                ) {
                    TvWaitingRing(
                        color = tvAccent(uiState),
                        active = !uiState.streamActive,
                        modifier = Modifier.size(168.dp),
                    )
                    Text(
                        tvHeadline(uiState),
                        color = NoctColors.TextPrimary,
                        style = androidx.compose.material3.MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        tvSubtitle(uiState),
                        color = NoctColors.TextSecondary,
                        style = androidx.compose.material3.MaterialTheme.typography.bodyLarge,
                    )
                    if (uiState.pairingState != PairingState.Trusted) {
                        PairingCodeCard(uiState = uiState, modifier = Modifier.focusable())
                    } else {
                        NoctGlassCard(modifier = Modifier.fillMaxWidth().widthIn(max = 520.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text("Remembered handhelds", color = NoctColors.TextSecondary)
                                Text("${uiState.trustedSenderCount}", color = NoctColors.TextPrimary, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                    BoxWithConstraints(Modifier.fillMaxWidth()) {
                        if (maxWidth < 620.dp) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(NoctSpacing.md),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                NoctPrimaryButton("Open Console View", { navController.navigate(TvRoutes.ROUTE_STREAM) }, modifier = Modifier.focusable(), minHeight = 62.dp)
                                if (uiState.pairingState != PairingState.Trusted) {
                                    NoctSecondaryButton("New code", viewModel::regeneratePairingCode, modifier = Modifier.focusable(), minHeight = 62.dp)
                                }
                                if (uiState.formFactor != ReceiverFormFactor.TV) {
                                    NoctSecondaryButton("Settings", { navController.navigate(TvRoutes.ROUTE_SETTINGS) }, modifier = Modifier.focusable(), minHeight = 62.dp)
                                }
                                NoctSecondaryButton("Clear paired devices", viewModel::clearPairedDevices, modifier = Modifier.focusable(), minHeight = 62.dp)
                            }
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(NoctSpacing.md, Alignment.CenterHorizontally),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                NoctPrimaryButton("Open Console View", { navController.navigate(TvRoutes.ROUTE_STREAM) }, modifier = Modifier.focusable(), minHeight = 62.dp)
                                if (uiState.pairingState != PairingState.Trusted) {
                                    NoctSecondaryButton("New code", viewModel::regeneratePairingCode, modifier = Modifier.focusable(), minHeight = 62.dp)
                                }
                                if (uiState.formFactor != ReceiverFormFactor.TV) {
                                    NoctSecondaryButton("Settings", { navController.navigate(TvRoutes.ROUTE_SETTINGS) }, modifier = Modifier.focusable(), minHeight = 62.dp)
                                }
                                NoctSecondaryButton("Clear paired devices", viewModel::clearPairedDevices, modifier = Modifier.focusable(), minHeight = 62.dp)
                            }
                        }
                    }
                    NoctGlassCard(modifier = Modifier.fillMaxWidth().widthIn(max = 640.dp)) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(NoctSpacing.xs),
                        ) {
                            Text("Local privacy", color = NoctColors.TextPrimary, fontWeight = FontWeight.SemiBold)
                            Text(
                                "Local network only. No accounts. No cloud.",
                                color = NoctColors.TextSecondary,
                                style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TvWaitingRing(color: Color, active: Boolean, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "tv-waiting-ring")
    val phase by
        transition.animateFloat(
            initialValue = 0f,
            targetValue = if (active) 1f else 0f,
            animationSpec = infiniteRepeatable(tween(2800, easing = LinearEasing), RepeatMode.Restart),
            label = "tv-ring-phase",
        )
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width * 0.5f, size.height * 0.5f)
            val waveStart = size.minDimension * 0.34f
            val waveEnd = size.minDimension * 0.50f
            fun drawPulseRing(offset: Float, index: Int) {
                val progress = (phase + offset) % 1f
                val fade = kotlin.math.sin(progress * Math.PI.toFloat()).coerceAtLeast(0f)
                val radius = waveStart + (waveEnd - waveStart) * progress
                val alpha = fade * fade * if (index == 0) 0.24f else 0.17f
                drawCircle(
                    color = if (index == 1) NoctColors.Magenta.copy(alpha = alpha * 0.82f) else color.copy(alpha = alpha),
                    radius = radius,
                    center = center,
                    style = Stroke(width = (1.2f + 1.4f * (1f - progress)).dp.toPx(), cap = StrokeCap.Round),
                )
            }
            if (active) {
                drawPulseRing(0f, 0)
                drawPulseRing(0.34f, 1)
                drawPulseRing(0.68f, 2)
            } else {
                drawCircle(color.copy(alpha = 0.12f), radius = waveEnd * 0.84f, center = center, style = Stroke(width = 1.4.dp.toPx()))
            }
            val pulse = if (active) 0.88f + 0.12f * kotlin.math.sin(phase * Math.PI.toFloat() * 2f) else 0.82f
            drawCircle(color.copy(alpha = 0.08f * pulse), radius = size.minDimension * 0.46f, center = center)
        }
        NoctDockHeroOrb(
            accent = color,
            large = true,
            sizeScale = 0.78f,
            modifier = Modifier.fillMaxSize(0.58f),
        )
    }
}

@Composable
private fun PairingCodeCard(uiState: ReceiverUiState, compact: Boolean = false, modifier: Modifier = Modifier) {
    NoctFocusCard(modifier = modifier.widthIn(max = if (compact) 460.dp else 520.dp)) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(if (compact) NoctSpacing.xs else NoctSpacing.sm),
        ) {
            Text(
                uiState.pairingCode,
                color = NoctColors.Cyan,
                style =
                if (compact) {
                    androidx.compose.material3.MaterialTheme.typography.displayMedium.copy(fontSize = 38.sp, lineHeight = 42.sp)
                } else {
                    androidx.compose.material3.MaterialTheme.typography.displayMedium
                },
                fontWeight = FontWeight.Bold,
            )
            Text(
                "Enter this code on your handheld",
                color = NoctColors.TextSecondary,
                style = if (compact) androidx.compose.material3.MaterialTheme.typography.bodyLarge else androidx.compose.material3.MaterialTheme.typography.bodyLarge,
                maxLines = if (compact) 2 else 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun CompactReceiverHeader(title: String, subtitle: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = title,
            color = NoctColors.TextPrimary,
            style = androidx.compose.material3.MaterialTheme.typography.displayMedium.copy(fontSize = 34.sp, lineHeight = 38.sp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = subtitle,
            color = NoctColors.TextSecondary,
            style = androidx.compose.material3.MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Normal),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun tvAccent(uiState: ReceiverUiState): Color = when {
    uiState.streamActive -> NoctColors.Green
    uiState.pairingState == PairingState.Failed -> NoctColors.Magenta
    uiState.pairingState == PairingState.Trusted -> NoctColors.Cyan
    else -> NoctColors.Violet
}

private fun tvHeadline(uiState: ReceiverUiState): String {
    val phase =
        ReceiverUiPhaseResolver.resolve(
            uiState.pairingState,
            uiState.streamActive,
            uiState.streamError != null,
        )
    return when (phase) {
        ReceiverUiPhase.ACTIVE -> uiState.sourceMetadata.displayTitle
        ReceiverUiPhase.PAIRING -> "Pair this screen"
        ReceiverUiPhase.INTERRUPTED -> "Connection interrupted"
        ReceiverUiPhase.WAITING -> "Waiting for handheld"
    }
}

private fun tvSubtitle(uiState: ReceiverUiState): String {
    val phase =
        ReceiverUiPhaseResolver.resolve(
            uiState.pairingState,
            uiState.streamActive,
            uiState.streamError != null,
        )
    return when (phase) {
        ReceiverUiPhase.ACTIVE -> uiState.sourceMetadata.displaySubtitle
        ReceiverUiPhase.PAIRING -> "Use the code below to trust this screen."
        ReceiverUiPhase.INTERRUPTED -> "Reconnect from your handheld to continue."
        ReceiverUiPhase.WAITING -> "Open NoctDock on your handheld to begin."
    }
}

private fun tvStatus(uiState: ReceiverUiState): String {
    val phase =
        ReceiverUiPhaseResolver.resolve(
            uiState.pairingState,
            uiState.streamActive,
            uiState.streamError != null,
        )
    return when (phase) {
        ReceiverUiPhase.ACTIVE -> uiState.sourceMetadata.displayTitle
        ReceiverUiPhase.PAIRING -> "Pairing"
        ReceiverUiPhase.INTERRUPTED -> "Interrupted"
        ReceiverUiPhase.WAITING -> "Waiting"
    }
}

@Composable
private fun TvStreamScreen(navController: NavHostController, uiState: ReceiverUiState, viewModel: ReceiverViewModel) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val settings = uiState.receiverSettings
    val compactPhoneOverlay = uiState.formFactor != ReceiverFormFactor.TV
    var controlsVisible by remember { mutableStateOf(!uiState.streamActive) }
    var hasSeenActiveStream by remember { mutableStateOf(uiState.streamActive) }
    LaunchedEffect(uiState.streamActive) {
        if (uiState.streamActive) {
            hasSeenActiveStream = true
        } else if (hasSeenActiveStream) {
            // Stay on Console View through brief packet gaps; leave only after sustained inactive.
            delay(2_500L)
            navController.popBackStack(TvRoutes.ROUTE_HOME, inclusive = false)
            hasSeenActiveStream = false
        }
    }
    LaunchedEffect(controlsVisible, uiState.streamActive) {
        if (controlsVisible && uiState.streamActive) {
            delay(3_500L)
            controlsVisible = false
        }
    }
    DisposableEffect(uiState.streamActive, settings.keepScreenAwake, settings.startFullscreen, settings.preferLandscapeWhilePlaying, uiState.formFactor) {
        val activity = context as? Activity
        val window = activity?.window
        val decor = window?.decorView
        val insetsController = window?.let { WindowInsetsControllerCompat(it, it.decorView) }
        val previousBarsBehavior = insetsController?.systemBarsBehavior
        val previousOrientation = activity?.requestedOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        val shouldKeepAwake = uiState.streamActive && (settings.keepScreenAwake || uiState.formFactor == ReceiverFormFactor.TV)
        val shouldUseImmersive = uiState.streamActive && settings.startFullscreen
        val shouldPreferLandscape = uiState.streamActive && settings.preferLandscapeWhilePlaying && uiState.formFactor != ReceiverFormFactor.TV
        if (shouldKeepAwake) window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (shouldUseImmersive && decor != null) {
            WindowCompat.setDecorFitsSystemWindows(window, false)
            insetsController?.hide(WindowInsetsCompat.Type.systemBars())
            insetsController?.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        if (shouldPreferLandscape) activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            if (shouldUseImmersive && decor != null) {
                insetsController?.show(WindowInsetsCompat.Type.systemBars())
                previousBarsBehavior?.let { insetsController.systemBarsBehavior = it }
                WindowCompat.setDecorFitsSystemWindows(window, true)
            }
            activity?.requestedOrientation = previousOrientation
        }
    }
    Box(
        modifier =
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { controlsVisible = !controlsVisible }
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyUp) {
                    controlsVisible = true
                    false
                } else {
                    false
                }
            },
    ) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize().clipToBounds(),
            contentAlignment = Alignment.Center,
        ) {
            if (uiState.streamActive) {
                val videoWidth = uiState.metrics.requestedWidth.takeIf { it > 0 } ?: uiState.metrics.actualEncoderWidth.takeIf { it > 0 } ?: 16
                val videoHeight = uiState.metrics.requestedHeight.takeIf { it > 0 } ?: uiState.metrics.actualEncoderHeight.takeIf { it > 0 } ?: 9
                val viewport =
                    ReceiverAspectRatio.contentSize(
                        containerWidth = constraints.maxWidth,
                        containerHeight = constraints.maxHeight,
                        videoWidth = videoWidth,
                        videoHeight = videoHeight,
                        mode = settings.scaleMode,
                    )
                val surfaceWidth = with(density) { viewport.width.toDp() }
                val surfaceHeight = with(density) { viewport.height.toDp() }
                TvPlaybackSurface(
                    controller = viewModel.receiverSessionController,
                    keepScreenAwake = settings.keepScreenAwake || uiState.formFactor == ReceiverFormFactor.TV,
                    modifier = Modifier.size(surfaceWidth, surfaceHeight),
                )
            }
        }
        if (!uiState.streamActive && !compactPhoneOverlay) {
            NoctGlassCard(
                modifier =
                Modifier
                    .align(Alignment.Center)
                    .widthIn(max = 620.dp),
            ) {
                Text(
                    uiState.status.ifBlank { "Waiting for handheld" },
                    color = NoctColors.TextPrimary,
                    style = androidx.compose.material3.MaterialTheme.typography.headlineMedium,
                )
                Text(
                    "NoctDock will return when your handheld is ready.",
                    color = NoctColors.TextSecondary,
                    style = androidx.compose.material3.MaterialTheme.typography.bodyLarge,
                )
            }
        }
        if (controlsVisible) {
            Column(
                modifier = Modifier.fillMaxSize().padding(if (compactPhoneOverlay) 18.dp else 24.dp),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = if (compactPhoneOverlay) Alignment.CenterHorizontally else Alignment.Start,
            ) {
                NoctGlassCard(modifier = Modifier.widthIn(max = if (compactPhoneOverlay) 340.dp else 560.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(NoctSpacing.sm)) {
                        Text(uiState.sourceMetadata.displayTitle, color = NoctColors.TextPrimary, fontWeight = FontWeight.SemiBold)
                        Text(
                            uiState.sourceMetadata.displaySubtitle,
                            color = NoctColors.TextSecondary,
                            style = androidx.compose.material3.MaterialTheme.typography.bodyLarge,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(NoctSpacing.sm)) {
                            NoctStatusPill(receiverConnectionQuality(uiState.metrics, uiState.streamActive), NoctColors.Cyan)
                            NoctStatusPill(receiverSoundState(uiState.metrics), if (uiState.metrics.audioPacketsReceived > 0) NoctColors.Green else NoctColors.Violet)
                        }
                    }
                }
                Row(
                    modifier = if (compactPhoneOverlay) Modifier.fillMaxWidth() else Modifier,
                    horizontalArrangement = Arrangement.spacedBy(NoctSpacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    NoctSecondaryButton(
                        "System Status",
                        { navController.navigate(TvRoutes.ROUTE_SYSTEM_STATUS) },
                        modifier = if (compactPhoneOverlay) Modifier.weight(1f).focusable() else Modifier.focusable(),
                        minHeight = if (compactPhoneOverlay) 56.dp else 64.dp,
                    )
                    if (uiState.formFactor != ReceiverFormFactor.TV) {
                        NoctSecondaryButton(
                            "Settings",
                            { navController.navigate(TvRoutes.ROUTE_SETTINGS) },
                            modifier = if (compactPhoneOverlay) Modifier.weight(1f).focusable() else Modifier.focusable(),
                            minHeight = if (compactPhoneOverlay) 56.dp else 64.dp,
                        )
                    }
                    NoctSecondaryButton(
                        "Back",
                        { navController.popBackStack() },
                        modifier = if (compactPhoneOverlay) Modifier.weight(1f).focusable() else Modifier.focusable(),
                        minHeight = if (compactPhoneOverlay) 56.dp else 64.dp,
                    )
                }
            }
        }
    }
}

@Composable
private fun ReceiverSettingsScreen(navController: NavHostController, uiState: ReceiverUiState, viewModel: ReceiverViewModel) {
    val settings = uiState.receiverSettings
    val defaultReceiverName = remember(uiState.defaultReceiverName) { uiState.defaultReceiverName }
    var receiverNameDraft by remember(settings.receiverName, defaultReceiverName) {
        mutableStateOf(settings.receiverName.ifBlank { defaultReceiverName })
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val horizontalPadding = if (maxWidth < 620.dp) 24.dp else 72.dp
        Column(
            modifier =
            Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = horizontalPadding, vertical = 36.dp),
            verticalArrangement = Arrangement.spacedBy(NoctSpacing.md),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().widthIn(max = 820.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text("Receiver Settings", color = NoctColors.TextPrimary, style = androidx.compose.material3.MaterialTheme.typography.headlineMedium)
                    Text("Screen behavior while Console Mode is active", color = NoctColors.TextSecondary)
                }
                NoctSecondaryButton("Back", navController::popBackStack, modifier = Modifier.focusable())
            }
            NoctGlassCard(modifier = Modifier.fillMaxWidth().widthIn(max = 820.dp)) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(NoctSpacing.md),
                ) {
                    Text("Receiver name", color = NoctColors.TextPrimary, fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(
                        value = receiverNameDraft,
                        onValueChange = { receiverNameDraft = it },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Shown on nearby handhelds") },
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(NoctSpacing.sm)) {
                        NoctSecondaryButton("Save", {
                            viewModel.updateReceiverName(receiverNameDraft)
                        }, modifier = Modifier.focusable(), minHeight = 44.dp)
                        NoctSecondaryButton("Use device name", {
                            receiverNameDraft = defaultReceiverName
                            viewModel.resetReceiverName()
                        }, modifier = Modifier.focusable(), minHeight = 44.dp)
                    }
                }
                ReceiverToggleRow("Start fullscreen", settings.startFullscreen, viewModel::updateStartFullscreen)
                ReceiverToggleRow("Keep screen awake", settings.keepScreenAwake, viewModel::updateKeepScreenAwake)
                ReceiverToggleRow("Prefer landscape while playing", settings.preferLandscapeWhilePlaying, viewModel::updatePreferLandscapeWhilePlaying)
                Column(verticalArrangement = Arrangement.spacedBy(NoctSpacing.sm)) {
                    Text("Video size", color = NoctColors.TextPrimary, fontWeight = FontWeight.SemiBold)
                    Text(if (settings.scaleMode == ReceiverScaleMode.FIT) "Fit Screen" else "Fill Screen", color = NoctColors.TextSecondary)
                    Row(horizontalArrangement = Arrangement.spacedBy(NoctSpacing.sm)) {
                        ReceiverModeChip("Fit Screen", settings.scaleMode == ReceiverScaleMode.FIT) { viewModel.updateScaleMode(ReceiverScaleMode.FIT) }
                        ReceiverModeChip("Fill Screen", settings.scaleMode == ReceiverScaleMode.FILL) { viewModel.updateScaleMode(ReceiverScaleMode.FILL) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReceiverToggleRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    NoctGlassCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = NoctSpacing.md, vertical = NoctSpacing.sm),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, color = NoctColors.TextPrimary, fontWeight = FontWeight.SemiBold)
            Switch(checked = checked, onCheckedChange = onCheckedChange, modifier = Modifier.focusable())
        }
    }
}

@Composable
private fun ReceiverSystemStatusScreen(navController: NavHostController, uiState: ReceiverUiState, viewModel: ReceiverViewModel) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val horizontalPadding = if (maxWidth < 620.dp) 24.dp else 72.dp
        Column(
            modifier =
            Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = horizontalPadding, vertical = 36.dp),
            verticalArrangement = Arrangement.spacedBy(NoctSpacing.md),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().widthIn(max = 860.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text("System Status", color = NoctColors.TextPrimary, style = androidx.compose.material3.MaterialTheme.typography.headlineMedium)
                    Text("Technical stream details for troubleshooting", color = NoctColors.TextSecondary)
                }
                NoctSecondaryButton("Back", navController::popBackStack, modifier = Modifier.focusable())
            }
            ReceiverDiagnosticsGroup("Discovery") {
                NoctMetricRow("Broadcasting", if (uiState.advertising) "yes" else "no")
                NoctMetricRow(
                    "Last broadcast restart",
                    if (uiState.lastBroadcastRestartAtMillis > 0L) {
                        java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
                            .format(java.util.Date(uiState.lastBroadcastRestartAtMillis))
                    } else {
                        "Never"
                    },
                )
                NoctMetricRow("Discovery state", uiState.discoveryLifecycleState.name.replace('_', ' '))
            }
            ReceiverDiagnosticsGroup("Connection") {
                NoctMetricRow("Incoming", "${uiState.metrics.receivedFps} fps")
                NoctMetricRow("Screen render", "${uiState.metrics.fps} fps")
                NoctMetricRow("Connection", receiverConnectionQuality(uiState.metrics, uiState.streamActive))
                NoctMetricRow("Handheld", uiState.senderName.ifBlank { "Waiting" })
                NoctMetricRow("Source app", uiState.sourceMetadata.sourceAppName.ifBlank { "Unknown" })
            }
            ReceiverDiagnosticsGroup("Sound") {
                NoctMetricRow("Audio", receiverSoundState(uiState.metrics))
                NoctMetricRow("Audio packets", "${uiState.metrics.audioPacketsReceived}")
                NoctMetricRow("Audio underruns", "${uiState.metrics.audioUnderruns}")
                NoctMetricRow("Audio drops", "${uiState.metrics.audioDrops}")
                NoctMetricRow("Audio buffer", "${uiState.metrics.audioBufferMs} ms")
                NoctMetricRow("A/V offset", "${uiState.metrics.avOffsetMs} ms")
            }
            ReceiverDiagnosticsGroup("Receiver") {
                NoctMetricRow("Reassembly drops", "${uiState.metrics.reassemblyDrops}")
                NoctMetricRow("Decoder", uiState.decoderName)
                NoctMetricRow("Requested", receiverResolutionLabel(uiState.metrics.requestedWidth, uiState.metrics.requestedHeight))
                NoctMetricRow("Receiver surface", receiverResolutionLabel(uiState.metrics.receiverSurfaceWidth, uiState.metrics.receiverSurfaceHeight))
                NoctMetricRow("Codec", uiState.metrics.receiverDecoderMime.ifBlank { uiState.metrics.codecMime.ifBlank { "Unknown" } })
            }
            NoctPrimaryButton(
                "Copy support report",
                viewModel::copySupportReportToClipboard,
                modifier = Modifier.widthIn(max = 860.dp),
            )
            Text(
                "Includes System Status and recent in-app logs. Paste into GitHub issues when reporting bugs.",
                color = NoctColors.TextSecondary,
            )
            if (uiState.supportReportCopied) {
                Text("Support report copied to clipboard.", color = NoctColors.Green)
            }
        }
    }
}

@Composable
private fun ReceiverModeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    NoctSelectableCard(
        selected = selected,
        onClick = onClick,
        modifier = Modifier.widthIn(min = 140.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = NoctSpacing.md, vertical = NoctSpacing.sm),
    ) {
        Text(label, color = NoctColors.TextPrimary, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun ReceiverDiagnosticsGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    NoctGlassCard(modifier = Modifier.fillMaxWidth().widthIn(max = 860.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(NoctSpacing.sm)) {
            Text(title, color = NoctColors.TextPrimary, fontWeight = FontWeight.SemiBold, style = androidx.compose.material3.MaterialTheme.typography.titleLarge)
            content()
        }
    }
}

private fun receiverConnectionQuality(metrics: StreamMetrics, streamActive: Boolean): String = when {
    !streamActive -> "Waiting"
    metrics.decoderErrors > 0 || metrics.packetLossPercent >= 6 || metrics.reassemblyDrops >= 12 -> "Unsteady"
    metrics.receivedFps >= 50 || metrics.fps >= 50 -> "Stable"
    else -> "Settling"
}

private fun receiverSoundState(metrics: StreamMetrics): String = if (metrics.audioPacketsReceived > 0) "Sound on" else "Sound off"

private fun receiverResolutionLabel(width: Int, height: Int): String = if (width > 0 && height > 0) "${width}x$height" else "Unknown"
