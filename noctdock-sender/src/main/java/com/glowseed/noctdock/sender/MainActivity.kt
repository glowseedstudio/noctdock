package com.glowseed.noctdock.sender

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.media.projection.MediaProjectionManager
import android.os.BatteryManager
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import android.widget.ImageView
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.glowseed.noctdock.core.AccentTheme
import com.glowseed.noctdock.core.AppearanceDefaults
import com.glowseed.noctdock.core.BackgroundMotionMode
import com.glowseed.noctdock.core.BackgroundSurface
import com.glowseed.noctdock.core.ConnectionRecommendation
import com.glowseed.noctdock.core.ConsoleModeState
import com.glowseed.noctdock.core.DeviceCapabilityDetector
import com.glowseed.noctdock.core.DiscoveredReceiver
import com.glowseed.noctdock.core.LocalLibraryApp
import com.glowseed.noctdock.core.LocalNoctAccent
import com.glowseed.noctdock.core.NebulaTheme
import com.glowseed.noctdock.core.NoctAppearance
import com.glowseed.noctdock.core.NoctBackground
import com.glowseed.noctdock.core.NoctBatteryPill
import com.glowseed.noctdock.core.NoctCard
import com.glowseed.noctdock.core.NoctColors
import com.glowseed.noctdock.core.NoctError
import com.glowseed.noctdock.core.NoctGlassCard
import com.glowseed.noctdock.core.NoctLog
import com.glowseed.noctdock.core.NoctMetricRow
import com.glowseed.noctdock.core.NoctNavCard
import com.glowseed.noctdock.core.NoctOrb
import com.glowseed.noctdock.core.NoctPrimaryButton
import com.glowseed.noctdock.core.NoctPrimaryConsoleButton
import com.glowseed.noctdock.core.NoctPrivacyBar
import com.glowseed.noctdock.core.NoctReceiverHeroCard
import com.glowseed.noctdock.core.NoctSecondaryButton
import com.glowseed.noctdock.core.NoctSectionHeader
import com.glowseed.noctdock.core.NoctSelectableCard
import com.glowseed.noctdock.core.NoctSpacing
import com.glowseed.noctdock.core.NoctStatusPill
import com.glowseed.noctdock.core.NoctTheme
import com.glowseed.noctdock.core.NoctWordmark
import com.glowseed.noctdock.core.PairingState
import com.glowseed.noctdock.core.ReceiverDisplayWording
import com.glowseed.noctdock.core.ReceiverFormFactor
import com.glowseed.noctdock.core.ScreenCloakMode
import com.glowseed.noctdock.core.Smooth60HzMode
import com.glowseed.noctdock.core.SoundMode
import com.glowseed.noctdock.core.StreamHealth
import com.glowseed.noctdock.core.StreamHealthCalculator
import com.glowseed.noctdock.core.StreamProfile
import com.glowseed.noctdock.core.StreamProfiles
import com.glowseed.noctdock.core.UiDensity
import com.glowseed.noctdock.core.noctGradientBorder
import com.glowseed.noctdock.core.noctSpace
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        NoctLog.configure(debugLogs = BuildConfig.NOCT_DEBUG_LOGS, infoLogs = !BuildConfig.NOCT_PERF_BUILD)
        setContent { NoctTheme { SenderApp() } }
    }
}

private object SenderRoutes {
    const val ROUTE_HOME = "home"
    const val ROUTE_DEVICES = "devices"
    const val ROUTE_LIBRARY = "library"
    const val ROUTE_PERFORMANCE = "performance"
    const val ROUTE_DIAGNOSTICS = "diagnostics"
    const val ROUTE_DOCK = "dock"
    const val ROUTE_SETTINGS = "settings"
}

@Composable
private fun SenderApp(navController: NavHostController = rememberNavController()) {
    val viewModel: SenderViewModel = viewModel()
    val uiState = viewModel.uiState.collectAsStateWithLifecycle().value
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val haptics = LocalHapticFeedback.current
    val projectionManager = remember { context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager }
    var startAfterAudioPermission by remember { mutableStateOf(false) }
    var pendingSmooth60HzAsk by remember { mutableStateOf(false) }
    val activity = context as? ComponentActivity
    val projectionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val resultData = result.data
            if (result.resultCode == Activity.RESULT_OK && resultData != null) {
                viewModel.startConsoleMode(result.resultCode, resultData)
                if (
                    !pendingSmooth60HzAsk &&
                    uiState.performanceSettings.smooth60HzMode == Smooth60HzMode.Always &&
                    activity != null
                ) {
                    viewModel.applySmooth60HzIfNeeded(activity)
                }
                viewModel.launchPendingAfterConsoleStart()
                navController.navigate(SenderRoutes.ROUTE_DOCK)
            } else {
                viewModel.setError(NoctError.MediaProjectionDenied)
            }
        }
    val audioPermissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
            if (startAfterAudioPermission) {
                startAfterAudioPermission = false
                projectionLauncher.launch(projectionManager.createScreenCaptureIntent())
            }
        }
    val requestConsoleMode = {
        val receiver = uiState.defaultReceiver
        when {
            receiver == null -> viewModel.startConsoleMode(permissionGranted = false)

            uiState.trustedReceiver?.identity?.id != receiver.identity.id -> viewModel.connect(receiver)

            uiState.consoleModeState == ConsoleModeState.Streaming -> {
                (context as? ComponentActivity)?.let(viewModel::clearSmooth60Hz)
                viewModel.stopConsoleMode()
            }

            else -> {
                if (uiState.appearanceSettings.hapticsEnabled) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                val needsAudioPermission =
                    uiState.performanceSettings.soundMode == SoundMode.TV || uiState.performanceSettings.soundMode == SoundMode.BOTH
                if (
                    needsAudioPermission &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED
                ) {
                    startAfterAudioPermission = true
                    audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                } else {
                    pendingSmooth60HzAsk = uiState.performanceSettings.smooth60HzMode == Smooth60HzMode.AskOnStart
                    projectionLauncher.launch(projectionManager.createScreenCaptureIntent())
                }
            }
        }
    }
    DisposableEffect(lifecycleOwner, viewModel) {
        val observer =
            LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_START -> viewModel.setAppForeground(true)
                    Lifecycle.Event.ON_STOP -> viewModel.setAppForeground(false)
                    else -> Unit
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.setAppForeground(false)
        }
    }

    NoctAppearance(
        accentTheme = uiState.appearanceSettings.accentTheme,
        uiDensity = uiState.appearanceSettings.uiDensity,
    ) {
        GameHubControllerLayoutHost(layout = uiState.appearanceSettings.controllerLayout) {
            NoctBackground(
                dynamicNebula = uiState.consoleModeState != ConsoleModeState.Streaming && !uiState.appearanceSettings.reducedMotion,
                ambientMotionEnabled = !uiState.appearanceSettings.reducedMotion,
                theme = uiState.appearanceSettings.backgroundTheme,
                motionMode = uiState.appearanceSettings.backgroundMotionMode,
                reducedMotion = uiState.appearanceSettings.reducedMotion,
                batterySaver = uiState.performanceSettings.batterySaverMode,
                surface = BackgroundSurface.Handheld,
            ) {
            NavHost(navController = navController, startDestination = SenderRoutes.ROUTE_HOME) {
                composable(SenderRoutes.ROUTE_HOME) { entry ->
                    val openLibraryPanel =
                        entry.savedStateHandle.get<Boolean>("openLibrary") == true
                    if (openLibraryPanel) {
                        entry.savedStateHandle.remove<Boolean>("openLibrary")
                    }
                    val openScreensPanel =
                        entry.savedStateHandle.get<Boolean>("openScreens") == true
                    if (openScreensPanel) {
                        entry.savedStateHandle.remove<Boolean>("openScreens")
                    }
                    val openConsoleModesPanel =
                        entry.savedStateHandle.get<Boolean>("openConsoleModes") == true
                    if (openConsoleModesPanel) {
                        entry.savedStateHandle.remove<Boolean>("openConsoleModes")
                    }
                    val openSettingsPanel =
                        entry.savedStateHandle.get<Boolean>("openSettings") == true
                    if (openSettingsPanel) {
                        entry.savedStateHandle.remove<Boolean>("openSettings")
                    }
                    val focusSystemStatus =
                        entry.savedStateHandle.get<Boolean>("focusSystemStatus") == true
                    if (focusSystemStatus) {
                        entry.savedStateHandle.remove<Boolean>("focusSystemStatus")
                    }
                    HomeScreen(
                        navController = navController,
                        uiState = uiState,
                        viewModel = viewModel,
                        requestConsoleMode = requestConsoleMode,
                        requestProjection = { projectionLauncher.launch(projectionManager.createScreenCaptureIntent()) },
                        openLibraryPanel = openLibraryPanel,
                        openScreensPanel = openScreensPanel,
                        openConsoleModesPanel = openConsoleModesPanel,
                        openSettingsPanel = openSettingsPanel,
                        focusSystemStatus = focusSystemStatus,
                    )
                }
                composable(SenderRoutes.ROUTE_DEVICES) {
                    LaunchedEffect(Unit) {
                        navController.getBackStackEntry(SenderRoutes.ROUTE_HOME).savedStateHandle["openScreens"] = true
                        navController.navigate(SenderRoutes.ROUTE_HOME) {
                            launchSingleTop = true
                        }
                    }
                }
                composable(SenderRoutes.ROUTE_LIBRARY) {
                    LaunchedEffect(Unit) {
                        navController.getBackStackEntry(SenderRoutes.ROUTE_HOME).savedStateHandle["openLibrary"] = true
                        navController.navigate(SenderRoutes.ROUTE_HOME) {
                            launchSingleTop = true
                        }
                    }
                }
                composable(SenderRoutes.ROUTE_PERFORMANCE) {
                    LaunchedEffect(Unit) {
                        navController.getBackStackEntry(SenderRoutes.ROUTE_HOME).savedStateHandle["openConsoleModes"] = true
                        navController.navigate(SenderRoutes.ROUTE_HOME) {
                            launchSingleTop = true
                        }
                    }
                }
                composable(SenderRoutes.ROUTE_DIAGNOSTICS) { DiagnosticsScreen(navController, uiState, viewModel) }
                composable(SenderRoutes.ROUTE_DOCK) { DockModeScreen(navController, uiState, viewModel) }
                composable(SenderRoutes.ROUTE_SETTINGS) {
                    LaunchedEffect(Unit) {
                        navController.getBackStackEntry(SenderRoutes.ROUTE_HOME).savedStateHandle["openSettings"] = true
                        navController.navigate(SenderRoutes.ROUTE_HOME) {
                            launchSingleTop = true
                        }
                    }
                }
            }
            PairingDialog(uiState, viewModel)
            ErrorDialog(uiState, viewModel, navController)
            if (pendingSmooth60HzAsk && uiState.consoleModeState == ConsoleModeState.Streaming && activity != null) {
                AlertDialog(
                    onDismissRequest = { pendingSmooth60HzAsk = false },
                    title = { Text("Smooth 60 Hz") },
                    text = {
                        Text(
                            "Some handhelds stream more smoothly at 60 Hz. NoctDock can request this where Android allows it.",
                        )
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                pendingSmooth60HzAsk = false
                                viewModel.applySmooth60HzIfNeeded(activity)
                            },
                        ) {
                            Text("Request 60 Hz")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { pendingSmooth60HzAsk = false }) { Text("Not now") }
                    },
                )
            }
            if (!uiState.appearanceSettings.controllerLayoutConfigured) {
                GameHubControllerLayoutPicker(
                    onConfirm = { layout ->
                        if (uiState.appearanceSettings.hapticsEnabled) {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                        viewModel.confirmControllerLayout(layout)
                    },
                )
            }
        }
    }
    }
}

@Composable
private fun ScreenFrame(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier =
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = noctSpace(NoctSpacing.lg), vertical = noctSpace(NoctSpacing.xl)),
        verticalArrangement = Arrangement.spacedBy(noctSpace(NoctSpacing.lg)),
        content = content,
    )
}

@Composable
private fun HomeScreen(
    navController: NavHostController,
    uiState: SenderUiState,
    viewModel: SenderViewModel,
    requestConsoleMode: () -> Unit,
    requestProjection: () -> Unit,
    openLibraryPanel: Boolean = false,
    openScreensPanel: Boolean = false,
    openConsoleModesPanel: Boolean = false,
    openSettingsPanel: Boolean = false,
    focusSystemStatus: Boolean = false,
) {
    GameHubHomeScreen(
        uiState = uiState,
        viewModel = viewModel,
        requestConsoleMode = requestConsoleMode,
        requestProjection = requestProjection,
        openLibraryPanelOnStart = openLibraryPanel,
        openScreensPanelOnStart = openScreensPanel,
        openConsoleModesPanelOnStart = openConsoleModesPanel,
        openSettingsPanelOnStart = openSettingsPanel,
        focusSystemStatusOnStart = focusSystemStatus,
        onOpenDiagnostics = { navController.navigate(SenderRoutes.ROUTE_DIAGNOSTICS) },
    )
}

@Composable
private fun HomeFavouriteLaunchRow(uiState: SenderUiState, viewModel: SenderViewModel, requestProjection: () -> Unit, modifier: Modifier = Modifier) {
    val receiver = uiState.defaultReceiver ?: return
    val autoReady =
        uiState.appearanceSettings.autoReconnect &&
            uiState.trustedReceiver?.identity?.id == receiver.identity.id &&
            uiState.consoleModeState == ConsoleModeState.Ready
    if (!autoReady) return
    val launchApps = uiState.libraryApps
    if (launchApps.isEmpty()) return

    NoctGlassCard(
        modifier = modifier,
        contentPadding = PaddingValues(noctSpace(NoctSpacing.md)),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(noctSpace(NoctSpacing.md)),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(noctSpace(NoctSpacing.xs))) {
                Text("Quick launch", color = NoctColors.TextPrimary, fontWeight = FontWeight.SemiBold)
                Text("Choose an app for Console Mode", color = NoctColors.TextSecondary)
            }
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(noctSpace(NoctSpacing.sm)),
                verticalArrangement = Arrangement.spacedBy(noctSpace(NoctSpacing.sm)),
            ) {
                launchApps.forEach { item ->
                    HomeFavouriteButton(
                        item = item,
                        uiState = uiState,
                        viewModel = viewModel,
                        requestProjection = requestProjection,
                        modifier = Modifier.widthIn(min = 190.dp, max = 260.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeFavouriteButton(item: LibraryAppItem, uiState: SenderUiState, viewModel: SenderViewModel, requestProjection: () -> Unit, modifier: Modifier = Modifier) {
    NoctGlassCard(
        modifier = modifier.clickable {
            viewModel.launchInConsoleMode(
                item.model,
                permissionGranted =
                uiState.streamState == com.glowseed.noctdock.core.StreamSessionState.Active,
            )
            if (uiState.streamState != com.glowseed.noctdock.core.StreamSessionState.Active) requestProjection()
        },
        contentPadding = PaddingValues(horizontal = noctSpace(NoctSpacing.md), vertical = noctSpace(NoctSpacing.sm)),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(noctSpace(NoctSpacing.sm)), verticalAlignment = Alignment.CenterVertically) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(42.dp)) {
                NoctOrb(modifier = Modifier.fillMaxSize(), color = NoctColors.Magenta, reducedMotion = true)
                AndroidView(
                    factory = { context -> ImageView(context).apply { setImageDrawable(item.icon) } },
                    update = { it.setImageDrawable(item.icon) },
                    modifier = Modifier.size(26.dp),
                )
            }
            Text(
                item.model.label,
                color = NoctColors.TextPrimary,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

private data class HomePresentation(
    val topStatus: String,
    val title: String,
    val subtitle: String,
    val chips: List<String>,
    val primaryAction: String,
    val primaryEnabled: Boolean,
    val accent: androidx.compose.ui.graphics.Color,
)

private fun SenderUiState.toHomePresentation(accentColor: androidx.compose.ui.graphics.Color): HomePresentation {
    val receiver = defaultReceiver
    val trusted = receiver != null && trustedReceiver?.identity?.id == receiver.identity.id
    val modeChip = if (performanceSettings.batterySaverMode) "Battery Saver" else "${performanceSettings.selectedProfile.title} Mode"
    val soundChip = performanceSettings.soundMode.label.replace(" Mode", "")
    return when {
        lastError != null ->
            HomePresentation(
                topStatus = "Needs attention",
                title = "Screen Connection Interrupted",
                subtitle = "Return to Console Mode when the room is ready.",
                chips = listOf("Try again", "System Status"),
                primaryAction = "Try Again",
                primaryEnabled = true,
                accent = NoctColors.Magenta,
            )

        consoleModeState == ConsoleModeState.Streaming ->
            HomePresentation(
                topStatus = "Docked",
                title = receiver?.displayName ?: "NoctDock Receiver",
                subtitle = "Console Mode active",
                chips = listOf("Playing on screen", soundChip, "Stable connection"),
                primaryAction = "Stop Mode",
                primaryEnabled = true,
                accent = NoctColors.Green,
            )

        receiver == null ->
            HomePresentation(
                topStatus = "Looking for screen...",
                title = "Looking for a screen",
                subtitle = "Open NoctDock Receiver on your TV, phone, or tablet.",
                chips = listOf("Searching nearby", "Local network only"),
                primaryAction = "Looking for screen...",
                primaryEnabled = false,
                accent = NoctColors.Violet,
            )

        !trusted ->
            HomePresentation(
                topStatus = ReceiverDisplayWording.readyLabel(receiver.formFactor),
                title = receiver.displayName,
                subtitle = "Ready to pair",
                chips = listOf(modeChip, soundChip, "Strong signal"),
                primaryAction = "Pair with ${ReceiverDisplayWording.receiverNoun(receiver.formFactor)}",
                primaryEnabled = true,
                accent = accentColor,
            )

        else ->
            HomePresentation(
                topStatus = ReceiverDisplayWording.readyLabel(receiver.formFactor),
                title = receiver.displayName,
                subtitle = "Ready when you are",
                chips = listOf(modeChip, soundChip, "Strong signal"),
                primaryAction = "Enter Mode",
                primaryEnabled = true,
                accent = accentColor,
            )
    }
}

@Composable
private fun DeviceHeroPanel(receiver: DiscoveredReceiver?, uiState: SenderUiState, modifier: Modifier = Modifier) {
    val accent = deviceAccent(receiver, uiState)
    NoctReceiverHeroCard(
        title = receiver?.displayName ?: "Looking for NoctDock Receiver",
        subtitle = deviceHeroSubtitle(receiver, uiState),
        chips = deviceHeroChips(receiver, uiState),
        orbColor = accent,
        reducedMotion = uiState.appearanceSettings.reducedMotion,
        onOpenDevices = {},
        modifier = modifier.heightIn(min = 300.dp),
    )
}

private fun deviceScreenSubtitle(uiState: SenderUiState): String = when {
    uiState.receivers.isEmpty() -> "Looking for a room to dock to"
    uiState.trustedReceiver != null -> "Choose where Console Mode appears"
    else -> "Pair once, then dock instantly"
}

private fun deviceHeroSubtitle(receiver: DiscoveredReceiver?, uiState: SenderUiState): String = when {
    receiver == null -> "Open NoctDock Receiver on your TV, phone, or tablet."
    !receiver.isOnline -> "TV Connection Interrupted"
    uiState.trustedReceiver?.identity?.id == receiver.identity.id -> "Ready when you are"
    receiver.pairingRequired -> "Ready to pair"
    else -> "Ready nearby"
}

private fun deviceHeroChips(receiver: DiscoveredReceiver?, uiState: SenderUiState): List<String> = when {
    receiver == null -> listOf("Looking", "Local only")
    !receiver.isOnline -> listOf("Offline")
    uiState.trustedReceiver?.identity?.id == receiver.identity.id -> listOf("Recently used", "Ready")
    receiver.pairingRequired -> listOf("Pair once", "Local only")
    else -> listOf("Nearby", "Ready")
}

private fun deviceHeroState(receiver: DiscoveredReceiver?, uiState: SenderUiState): String = when {
    receiver == null -> "Searching"
    !receiver.isOnline -> "Interrupted"
    uiState.trustedReceiver?.identity?.id == receiver.identity.id -> ReceiverDisplayWording.readyLabel(receiver.formFactor)
    receiver.pairingRequired -> "Pairing"
    else -> ReceiverDisplayWording.readyLabel(receiver.formFactor)
}

private fun deviceAccent(receiver: DiscoveredReceiver?, uiState: SenderUiState): androidx.compose.ui.graphics.Color = when {
    receiver == null -> NoctColors.Violet
    !receiver.isOnline -> NoctColors.Magenta
    uiState.trustedReceiver?.identity?.id == receiver.identity.id -> NoctColors.Green
    else -> NoctColors.Cyan
}

@Composable
private fun TvOrbBadge(accent: androidx.compose.ui.graphics.Color, modifier: Modifier = Modifier, iconSize: androidx.compose.ui.unit.Dp = 24.dp) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        NoctOrb(modifier = Modifier.fillMaxSize(), color = accent, reducedMotion = true)
        Canvas(modifier = Modifier.size(iconSize)) {
            val iconColor = NoctColors.TextPrimary.copy(alpha = 0.96f)
            val strokeWidth = 2.2.dp.toPx()
            val stroke = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            drawRoundRect(
                color = iconColor,
                topLeft = Offset(size.width * 0.12f, size.height * 0.18f),
                size = androidx.compose.ui.geometry.Size(size.width * 0.76f, size.height * 0.50f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx(), 4.dp.toPx()),
                style = stroke,
            )
            drawLine(
                iconColor,
                Offset(size.width * 0.50f, size.height * 0.68f),
                Offset(size.width * 0.50f, size.height * 0.84f),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round,
            )
            drawLine(
                iconColor,
                Offset(size.width * 0.30f, size.height * 0.86f),
                Offset(size.width * 0.70f, size.height * 0.86f),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round,
            )
        }
    }
}

@Composable
private fun ReceiverCard(receiver: DiscoveredReceiver, uiState: SenderUiState, onSelect: () -> Unit, onConnect: () -> Unit) {
    val trusted = uiState.trustedReceiver?.identity?.id == receiver.identity.id
    val selected =
        uiState.selectedReceiver?.identity?.id == receiver.identity.id || uiState.defaultReceiver?.identity?.id == receiver.identity.id
    val accent = deviceAccent(receiver, uiState)
    NoctSelectableCard(selected = selected, onClick = onSelect, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(noctSpace(NoctSpacing.md)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TvOrbBadge(accent = accent, modifier = Modifier.size(48.dp), iconSize = 22.dp)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(noctSpace(NoctSpacing.xs))) {
                Text(
                    receiver.displayName,
                    color = NoctColors.TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    style = androidx.compose.material3.MaterialTheme.typography.titleLarge,
                )
                Text(deviceHeroSubtitle(receiver, uiState), color = NoctColors.TextSecondary)
                Row(horizontalArrangement = Arrangement.spacedBy(noctSpace(NoctSpacing.sm))) {
                    if (trusted) NoctStatusPill("Recently used", NoctColors.Green)
                    if (selected) NoctStatusPill("Selected", NoctColors.Cyan)
                    if (!receiver.isOnline) NoctStatusPill("Offline", NoctColors.Magenta)
                }
            }
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(noctSpace(NoctSpacing.sm)),
            ) {
                val noun = receiverNoun(receiver)
                NoctPrimaryButton(if (trusted) "Use this $noun" else "Pair with $noun", onConnect, minHeight = 46.dp)
                NoctSecondaryButton(if (selected) "Selected" else "Choose", onSelect, minHeight = 42.dp)
            }
        }
    }
}

private fun receiverNoun(receiver: DiscoveredReceiver): String = ReceiverDisplayWording.receiverNoun(receiver.formFactor)

@Composable
private fun LibraryScreen(navController: NavHostController, uiState: SenderUiState, viewModel: SenderViewModel, requestProjection: () -> Unit) {
    ScreenFrame {
        val emulators = uiState.libraryApps.filter { classifyApp(it.model) == "Emulators" }
        val manualApps = uiState.libraryApps.filter { classifyApp(it.model) == "Added" }
        val favourites = uiState.libraryApps.filter { it.model.isFavourite }
        Row(
            modifier = Modifier.fillMaxWidth().widthIn(max = 920.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TopTitle("Library", "Launch your games and apps")
            Row(horizontalArrangement = Arrangement.spacedBy(noctSpace(NoctSpacing.md))) {
                NoctSecondaryButton("Refresh", { viewModel.refreshLibrary(clearIconCache = true) })
                NoctSecondaryButton("Back", navController::popBackStack)
            }
        }
        NoctGlassCard(
            modifier = Modifier.fillMaxWidth().widthIn(max = 920.dp),
            contentPadding = PaddingValues(noctSpace(NoctSpacing.lg)),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(noctSpace(NoctSpacing.lg)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ConsoleModeOrbBadge(
                    profile = StreamProfiles.Balanced,
                    accent = NoctColors.Cyan,
                    modifier = Modifier.size(72.dp),
                    iconSize = 30.dp,
                )
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(noctSpace(NoctSpacing.sm))) {
                    Text(
                        "Dock library",
                        color = NoctColors.TextPrimary,
                        fontWeight = FontWeight.SemiBold,
                        style = androidx.compose.material3.MaterialTheme.typography.titleLarge,
                    )
                    Text(
                        "Emulators are added automatically. Add other apps only when you want them here.",
                        color = NoctColors.TextSecondary,
                    )
                }
                NoctStatusPill("${emulators.size} emulators", NoctColors.Violet)
                NoctStatusPill("${manualApps.size} added", NoctColors.Cyan)
            }
        }
        if (favourites.isNotEmpty()) LibraryShelf("Favourites", favourites, uiState, viewModel, requestProjection)
        AzaharLibraryCard(uiState, viewModel, requestConsoleMode = requestProjection)
        OutlinedTextField(
            value = uiState.libraryQuery,
            onValueChange = viewModel::updateLibraryQuery,
            label = { Text("Search installed apps") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().widthIn(max = 920.dp),
        )
        LibraryShelf("Emulators", emulators, uiState, viewModel, requestProjection)
        LibraryShelf("Added apps", manualApps, uiState, viewModel, requestProjection, removable = true)
        ManualAddShelf(uiState, viewModel)
    }
}

@Composable
private fun AzaharLibraryCard(uiState: SenderUiState, viewModel: SenderViewModel, requestConsoleMode: () -> Unit) {
    NoctGlassCard(
        modifier = Modifier.fillMaxWidth().widthIn(max = 920.dp),
        contentPadding = PaddingValues(noctSpace(NoctSpacing.lg)),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(noctSpace(NoctSpacing.md)),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(58.dp)) {
                NoctOrb(modifier = Modifier.fillMaxSize(), color = NoctColors.Magenta, reducedMotion = true)
                Text("3DS", color = NoctColors.TextPrimary, fontWeight = FontWeight.Bold)
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(noctSpace(NoctSpacing.xs))) {
                Text(
                    "NoctDock Azahar",
                    color = NoctColors.TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    style = androidx.compose.material3.MaterialTheme.typography.titleLarge,
                )
                Text(
                    if (uiState.azaharStatus.installed) "3DS Mode available" else "NoctDock Azahar is not installed.",
                    color = NoctColors.TextSecondary,
                )
                if (uiState.azaharStatus.installed) {
                    Row(horizontalArrangement = Arrangement.spacedBy(noctSpace(NoctSpacing.sm))) {
                        NoctStatusPill("Top Screen to Screen", NoctColors.Magenta)
                        NoctStatusPill("Touch stays on handheld", NoctColors.Cyan)
                    }
                }
            }
            if (uiState.azaharStatus.installed) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(noctSpace(NoctSpacing.sm)),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    NoctSecondaryButton(
                        "Launch",
                        onClick = {
                            if (viewModel.launchAzahar()) {
                                requestConsoleMode()
                            }
                        },
                        minHeight = 42.dp,
                    )
                    NoctPrimaryButton("Launch in 3DS Mode", viewModel::launchAzahar3dsMode, minHeight = 42.dp)
                }
            } else {
                NoctStatusPill("Missing", NoctColors.Magenta)
            }
        }
    }
}

@Composable
private fun LibraryShelf(title: String, apps: List<LibraryAppItem>, uiState: SenderUiState, viewModel: SenderViewModel, requestProjection: () -> Unit, removable: Boolean = false) {
    if (apps.isEmpty()) return
    Row(
        modifier = Modifier.fillMaxWidth().widthIn(max = 920.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NoctSectionHeader(title)
        NoctStatusPill("${apps.size}", if (title == "Emulators") NoctColors.Violet else NoctColors.Cyan)
    }
    Column(verticalArrangement = Arrangement.spacedBy(noctSpace(NoctSpacing.md))) {
        apps.forEach { item ->
            LibraryAppCard(item, uiState, viewModel, requestProjection, removable)
        }
    }
}

private fun classifyApp(app: LocalLibraryApp): String {
    val text = app.searchableText
    return when {
        isEmulatorAppText(text) -> "Emulators"
        else -> "Added"
    }
}

private fun isEmulatorAppText(text: String): Boolean = listOf(
    "retroarch",
    "azahar",
    "dolphin",
    "ppsspp",
    "aethersx2",
    "nether",
    "duckstation",
    "citra",
    "yuzu",
    "sudachi",
    "emulator",
).any { it in text }

@Composable
private fun LibraryAppCard(item: LibraryAppItem, uiState: SenderUiState, viewModel: SenderViewModel, requestProjection: () -> Unit, removable: Boolean = false) {
    NoctGlassCard(
        modifier = Modifier.fillMaxWidth().widthIn(max = 920.dp),
        contentPadding = PaddingValues(noctSpace(NoctSpacing.lg)),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(noctSpace(NoctSpacing.md)),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(58.dp)) {
                NoctOrb(
                    modifier = Modifier.fillMaxSize(),
                    color = if (classifyApp(item.model) ==
                        "Emulators"
                    ) {
                        NoctColors.Violet
                    } else {
                        NoctColors.Cyan
                    },
                    reducedMotion = true,
                )
                AndroidView(
                    factory = { context -> ImageView(context).apply { setImageDrawable(item.icon) } },
                    update = { it.setImageDrawable(item.icon) },
                    modifier = Modifier.size(34.dp),
                )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(noctSpace(NoctSpacing.xs))) {
                Text(
                    item.model.label,
                    color = NoctColors.TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    style = androidx.compose.material3.MaterialTheme.typography.titleLarge,
                )
                Text(
                    if (classifyApp(item.model) ==
                        "Emulators"
                    ) {
                        "Ready for play"
                    } else {
                        "Added to your library"
                    },
                    color = NoctColors.TextSecondary,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(noctSpace(NoctSpacing.sm)), verticalAlignment = Alignment.CenterVertically) {
                NoctStarToggle(
                    selected = item.model.isFavourite,
                    onClick = { viewModel.toggleFavourite(item.model) },
                )
                if (removable) NoctSecondaryButton("Remove", { viewModel.removeLibraryApp(item.model) }, minHeight = 42.dp)
                NoctSecondaryButton("Launch", { viewModel.launchOnly(item.model) }, minHeight = 42.dp)
                NoctPrimaryButton("Launch in Console Mode", {
                    viewModel.launchInConsoleMode(
                        item.model,
                        permissionGranted =
                        uiState.streamState == com.glowseed.noctdock.core.StreamSessionState.Active,
                    )
                    if (uiState.streamState != com.glowseed.noctdock.core.StreamSessionState.Active) requestProjection()
                }, minHeight = 42.dp)
            }
        }
    }
}

@Composable
private fun NoctStarToggle(selected: Boolean, onClick: () -> Unit) {
    val color = if (selected) NoctColors.Magenta else NoctColors.TextSecondary
    Box(
        modifier =
        Modifier
            .size(42.dp)
            .clickable(onClick = onClick)
            .noctGradientBorder(RoundedCornerShape(50))
            .padding(9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val path = androidx.compose.ui.graphics.Path()
            val center = Offset(size.width * 0.5f, size.height * 0.5f)
            val outer = size.minDimension * 0.48f
            val inner = size.minDimension * 0.21f
            repeat(10) { index ->
                val angle = -1.5708f + index * 0.62831855f
                val radius = if (index % 2 == 0) outer else inner
                val point = Offset(
                    x = center.x + kotlin.math.cos(angle) * radius,
                    y = center.y + kotlin.math.sin(angle) * radius,
                )
                if (index == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
            }
            path.close()
            if (selected) {
                drawPath(path, color)
            } else {
                drawPath(path, color, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
            }
        }
    }
}

@Composable
private fun ManualAddShelf(uiState: SenderUiState, viewModel: SenderViewModel) {
    NoctSectionHeader("Add apps", "Emulators appear automatically. Add anything else yourself.")
    if (uiState.libraryAddCandidates.isEmpty()) {
        NoctGlassCard(
            modifier = Modifier.fillMaxWidth().widthIn(max = 920.dp),
            contentPadding = PaddingValues(noctSpace(NoctSpacing.lg)),
        ) {
            Text("Search installed apps to add them here.", color = NoctColors.TextSecondary)
        }
        return
    }
    uiState.libraryAddCandidates.forEach { item ->
        NoctGlassCard(
            modifier = Modifier.fillMaxWidth().widthIn(max = 920.dp),
            contentPadding = PaddingValues(noctSpace(NoctSpacing.lg)),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(NoctSpacing.md),
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(52.dp)) {
                    NoctOrb(modifier = Modifier.fillMaxSize(), color = NoctColors.Cyan, reducedMotion = true)
                    AndroidView(
                        factory = { context -> ImageView(context).apply { setImageDrawable(item.icon) } },
                        update = { it.setImageDrawable(item.icon) },
                        modifier = Modifier.size(30.dp),
                    )
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(NoctSpacing.xs)) {
                    Text(item.model.label, color = NoctColors.TextPrimary, fontWeight = FontWeight.SemiBold)
                    Text("Add manually", color = NoctColors.TextSecondary)
                }
                NoctPrimaryButton("Add", { viewModel.addLibraryApp(item.model) }, minHeight = 44.dp)
            }
        }
    }
}

@Composable
private fun DockModeScreen(navController: NavHostController, uiState: SenderUiState, viewModel: SenderViewModel) {
    val context = LocalContext.current
    DisposableEffect(Unit) {
        val window = (context as? Activity)?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
    NoctBackground(
        dynamicNebula = true,
        ambientMotionEnabled = !uiState.appearanceSettings.reducedMotion,
        theme = uiState.appearanceSettings.backgroundTheme,
        motionMode = uiState.appearanceSettings.backgroundMotionMode,
        reducedMotion = uiState.appearanceSettings.reducedMotion,
        batterySaver = true,
        surface = BackgroundSurface.Dock,
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(noctSpace(NoctSpacing.xl)),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(noctSpace(NoctSpacing.lg))) {
                Row(horizontalArrangement = Arrangement.spacedBy(noctSpace(NoctSpacing.md))) {
                    NoctOrb(
                        modifier = Modifier.size(34.dp),
                        color = NoctColors.Green,
                        reducedMotion = uiState.appearanceSettings.reducedMotion,
                    )
                    NoctStatusPill("Console Mode", NoctColors.Green)
                }
                Text(
                    uiState.defaultReceiver?.displayName ?: "Looking for screen",
                    color = NoctColors.TextPrimary,
                    style = androidx.compose.material3.MaterialTheme.typography.headlineMedium,
                )
                Text(
                    "Connected to ${uiState.defaultReceiver?.displayName ?: "your screen"}",
                    color = NoctColors.TextSecondary,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(noctSpace(NoctSpacing.sm))) {
                    NoctStatusPill(uiState.performanceSettings.soundMode.label.replace(" Mode", ""), NoctColors.Cyan)
                    if (uiState.screenCloakStatus.active) {
                        NoctStatusPill("Screen Cloak active", NoctColors.Violet)
                    }
                }
                if (uiState.performanceSettings.showStreamOverlay) {
                    NoctCard(modifier = Modifier.widthIn(max = 420.dp)) {
                        NoctMetricRow("Picture", if (uiState.metrics.fps > 0) "${uiState.metrics.fps} fps" else "Ready")
                        NoctMetricRow("Sound", uiState.performanceSettings.soundMode.label)
                        NoctMetricRow("Battery", batteryPercent(context))
                    }
                }
            }
            ActionRow {
                NoctPrimaryButton(
                    "Stop Console Mode",
                    {
                        (context as? ComponentActivity)?.let(viewModel::clearSmooth60Hz)
                        viewModel.stopConsoleMode()
                        navController.navigate(SenderRoutes.ROUTE_HOME)
                    },
                )
                NoctSecondaryButton("Restore full controls", { navController.navigate(SenderRoutes.ROUTE_HOME) })
            }
        }
    }
}

private fun batteryPercent(context: Context): String {
    val manager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
    val percent = manager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
    return if (percent in 0..100) "$percent%" else "Unavailable"
}

@Composable
private fun DiagnosticsScreen(navController: NavHostController, uiState: SenderUiState, viewModel: SenderViewModel) {
    ScreenFrame {
        TopTitle("System Status", "Detailed Console Mode health")
        var latencyTestActive by remember { mutableStateOf(false) }
        var flashOn by remember { mutableStateOf(false) }
        if (latencyTestActive) {
            LaunchedEffect(latencyTestActive) {
                if (uiState.performanceSettings.soundMode == SoundMode.TV || uiState.performanceSettings.soundMode == SoundMode.BOTH) {
                    val tone = ToneGenerator(AudioManager.STREAM_MUSIC, 80)
                    tone.startTone(ToneGenerator.TONE_PROP_BEEP, 90)
                    delay(120)
                    tone.release()
                }
                while (latencyTestActive) {
                    flashOn = !flashOn
                    delay(250)
                }
            }
        }
        val snapshot = viewModel.diagnosticsSnapshot()
        val grade =
            StreamHealthCalculator.grade(
                StreamHealth(
                    packetLossPercent = snapshot.metrics.packetLossPercent,
                    jitterMs = snapshot.metrics.jitterMs,
                    queueDepth = snapshot.metrics.queueDepth,
                    droppedFramesPerMinute = snapshot.metrics.droppedFrames,
                    receiverDecodeErrors = 0,
                    thermalThrottling = false,
                    encoderOverloaded = false,
                ),
            )
        DiagnosticsGroup("Discovery") {
            NoctMetricRow("Broadcasting", if (snapshot.broadcasting) "yes" else "no")
            NoctMetricRow("Last broadcast restart", snapshot.lastBroadcastRestartLabel)
            NoctMetricRow("Discovery state", snapshot.discoveryStateLabel)
        }
        DiagnosticsGroup("Display") {
            NoctMetricRow("60 Hz requested", if (snapshot.refreshRateHelperStatus.requested60Hz) "yes" else "no")
            NoctMetricRow(
                "Active refresh rate",
                snapshot.refreshRateHelperStatus.activeRefreshRateHz?.let { "$it Hz" } ?: "Unknown",
            )
            NoctMetricRow("60 Hz helper", snapshot.refreshRateHelperStatus.resultLabel())
        }
        DiagnosticsGroup("Connection") {
            NoctMetricRow("Stream health", grade.name)
            NoctMetricRow("Current screen", snapshot.receiverName)
            NoctMetricRow("Connection state", snapshot.connectionState.name)
            NoctMetricRow("Stream status", snapshot.streamState.name)
            NoctMetricRow("Connection test", snapshot.connectionTestResult?.friendlyLabel ?: "Not run")
            NoctMetricRow("Tested Mbps", snapshot.connectionTestResult?.throughputMbps?.toString() ?: "Not run")
            NoctMetricRow("Test packet loss", snapshot.connectionTestResult?.packetLossPercent?.let { "$it%" } ?: "Not run")
            NoctMetricRow("Test jitter", snapshot.connectionTestResult?.jitterMs?.let { "$it ms" } ?: "Not run")
            NoctMetricRow("Test RTT", snapshot.connectionTestResult?.roundTripMs?.let { "$it ms" } ?: "Not run")
            NoctMetricRow("Receiver transport", snapshot.connectionTestResult?.receiverTransport?.name ?: "Unknown")
            NoctMetricRow(
                "Test age",
                snapshot.connectionTestResult?.let { if (it.isStale()) "Test again recommended" else "Fresh" } ?: "Not run",
            )
            NoctMetricRow("Dropped frames", "${snapshot.metrics.droppedFrames}")
            NoctMetricRow("Packet loss estimate", "${snapshot.metrics.packetLossPercent}%")
            NoctMetricRow("Queue depth", "${snapshot.metrics.queueDepth}")
        }
        DiagnosticsGroup("Picture") {
            NoctMetricRow("FPS", "${snapshot.metrics.fps}")
            NoctMetricRow("Bitrate", "${snapshot.metrics.bitrateMbps} Mbps")
            NoctMetricRow("Selected codec", snapshot.selectedCodec.friendlyName)
            NoctMetricRow("Requested output", snapshot.requestedResolution)
            NoctMetricRow("Actual encoder", snapshot.actualEncoderResolution)
            NoctMetricRow("Virtual display", snapshot.virtualDisplayResolution)
            NoctMetricRow("Configured bitrate", "${snapshot.configuredBitrateMbps} Mbps")
            NoctMetricRow("Receiver decoder MIME", snapshot.receiverDecoderMime)
            NoctMetricRow("Receiver surface", snapshot.receiverSurfaceResolution)
        }
        DiagnosticsGroup("Sound") {
            NoctMetricRow("Active sound mode", snapshot.soundMode.label)
            NoctMetricRow("Audio packets sent", "${snapshot.metrics.audioPacketsSent}")
            NoctMetricRow("Audio packets received", "${snapshot.metrics.audioPacketsReceived}")
            NoctMetricRow("Audio underruns", "${snapshot.metrics.audioUnderruns}")
            NoctMetricRow("Audio drops", "${snapshot.metrics.audioDrops}")
            NoctMetricRow("Audio buffer", "${snapshot.metrics.audioBufferMs} ms")
            NoctMetricRow("Estimated A/V offset", "${snapshot.metrics.avOffsetMs} ms")
        }
        DiagnosticsGroup("Device") {
            NoctMetricRow("Detected handheld", snapshot.deviceProfile)
            NoctMetricRow("Support tier", snapshot.handheldTier.name)
            NoctMetricRow("Support level", snapshot.deviceSupportLevel.name.replace('_', ' '))
            NoctMetricRow("Recommended mode", snapshot.recommendedProfile)
            NoctMetricRow("Encoder", snapshot.encoderName)
            NoctMetricRow("HEVC support", if (snapshot.encoderCapability.supportsHevc) "Yes" else "No")
            NoctMetricRow("1080p60 capability", if (snapshot.encoderCapability.supports1080p60) "Available" else "Not reported")
            NoctMetricRow("Decoder feedback", snapshot.decoderFeedback)
            NoctMetricRow("Foreground package", uiState.foregroundPackage ?: "None")
        }
        DiagnosticsGroup("Advanced") {
            NoctMetricRow(
                "Azahar installed",
                if (uiState.azaharLaunchDiagnostics.packageInstalled ||
                    uiState.azaharStatus.installed
                ) {
                    "Yes"
                } else {
                    "No"
                },
            )
            NoctMetricRow("Azahar receiver address", uiState.azaharLaunchDiagnostics.receiverAddressPassed)
            NoctMetricRow("Azahar selected codec", uiState.azaharLaunchDiagnostics.selectedCodec)
            NoctMetricRow("Azahar sound mode", uiState.azaharLaunchDiagnostics.soundMode)
            NoctMetricRow("Azahar launch sent", if (uiState.azaharLaunchDiagnostics.launchIntentSent) "Yes" else "No")
            NoctMetricRow("Background mode", snapshot.backgroundMode)
            NoctMetricRow("Reduced Motion", if (snapshot.reducedMotion) "On" else "Off")
            NoctMetricRow("Battery Saver", if (snapshot.batterySaverMode) "On" else "Off")
            NoctMetricRow("Screen Cloak mode", snapshot.screenCloakStatus.mode.label)
            NoctMetricRow("Screen Cloak method", snapshot.screenCloakStatus.method.name.replace('_', ' '))
            NoctMetricRow("Screen Cloak state", snapshot.screenCloakStatus.state.name.replace('_', ' '))
            NoctMetricRow("Screen Cloak permission", snapshot.screenCloakStatus.permissionLabel)
            NoctMetricRow("Screen Cloak active", if (snapshot.screenCloakStatus.active) "Yes" else "No")
            NoctMetricRow(
                "Screen Cloak restore",
                snapshot.screenCloakStatus.restoreSucceeded?.let { if (it) "Restored" else "Restore failed" } ?: "Not needed",
            )
            NoctMetricRow("TV picture block", if (snapshot.screenCloakStatus.disabledDueToTvPictureIssue) "Yes" else "No")
            NoctMetricRow("Latency quality", if (snapshot.metrics.latencyMs <= 60) "Low" else "Elevated")
            NoctMetricRow("Pacing delay", "${snapshot.metrics.pacingDelayMs} ms")
            NoctMetricRow("Paced packets", "${snapshot.metrics.pacedPackets}")
            NoctMetricRow("Last error", snapshot.lastError?.message ?: "None")
        }
        NoctGlassCard(modifier = Modifier.fillMaxWidth().widthIn(max = 820.dp)) {
            Text("Latency Test", color = NoctColors.TextPrimary, fontWeight = FontWeight.SemiBold)
            Text("Use a slow-motion camera to measure handheld-to-screen delay accurately.", color = NoctColors.TextSecondary)
            if (latencyTestActive) {
                NoctCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        if (flashOn) "FLASH" else "READY",
                        color = if (flashOn) NoctColors.Cyan else NoctColors.TextSecondary,
                        style = androidx.compose.material3.MaterialTheme.typography.headlineMedium,
                    )
                }
            }
            ActionRow {
                if (latencyTestActive) {
                    NoctSecondaryButton("Stop Latency Test", { latencyTestActive = false })
                } else {
                    NoctPrimaryButton("Latency Test", { latencyTestActive = true })
                }
            }
        }
        ActionRow {
            NoctPrimaryButton("Copy support report", viewModel::copySupportReportToClipboard)
            NoctSecondaryButton("Back", navController::popBackStack)
        }
        Text(
            "Includes System Status and recent in-app logs. Paste into GitHub issues when reporting bugs.",
            color = NoctColors.TextSecondary,
        )
        if (uiState.diagnosticsCopied) {
            Text("Support report copied to clipboard.", color = NoctColors.Green)
        }
    }
}

@Composable
private fun AdvancedManualConnection(uiState: SenderUiState, viewModel: SenderViewModel) {
    NoctCard(modifier = Modifier.fillMaxWidth().widthIn(max = 820.dp)) {
        NoctSecondaryButton("Advanced & Experimental", { viewModel.setManualExpanded(!uiState.manualExpanded) })
        if (uiState.manualExpanded) {
            Spacer(Modifier.height(NoctSpacing.md))
            OutlinedTextField(
                value = uiState.manualHost,
                onValueChange = viewModel::updateManualHost,
                label = { Text("Local host or IP") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = uiState.manualPort,
                onValueChange = viewModel::updateManualPort,
                label = { Text("Port") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            NoctPrimaryButton("Connect manually", viewModel::connectManual)
        }
    }
}

@Composable
private fun PairingDialog(uiState: SenderUiState, viewModel: SenderViewModel) {
    val target = uiState.pairingTarget
    if (uiState.pairingState != PairingState.AwaitingCode || target == null) return
    var code by remember(target.identity.id) { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = {},
        title = { Text("Pair with ${target.displayName}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(NoctSpacing.md)) {
                Text(uiState.pairingMessage.ifBlank { "Enter the 4-digit code shown on NoctDock Receiver." })
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it.filter(Char::isDigit).take(4) },
                    label = { Text("Pairing code") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    colors =
                    OutlinedTextFieldDefaults.colors(
                        focusedTextColor = NoctColors.TextPrimary,
                        unfocusedTextColor = NoctColors.TextPrimary,
                        cursorColor = NoctColors.Cyan,
                        focusedBorderColor = NoctColors.Cyan,
                        unfocusedBorderColor = NoctColors.GlassBorder,
                        focusedLabelColor = NoctColors.Cyan,
                        unfocusedLabelColor = NoctColors.TextSecondary,
                    ),
                )
            }
        },
        confirmButton = { TextButton(onClick = { viewModel.submitPairingCode(code) }, enabled = code.length == 4) { Text("Pair") } },
    )
}

@Composable
private fun ErrorDialog(uiState: SenderUiState, viewModel: SenderViewModel, navController: NavHostController) {
    val error = uiState.lastError ?: return
    AlertDialog(
        onDismissRequest = {},
        title = { Text("Console Mode needs attention") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(NoctSpacing.sm)) {
                Text(error.message)
                TextButton(onClick = viewModel::copySupportReportToClipboard) {
                    Text("Copy support report")
                }
                if (uiState.diagnosticsCopied) {
                    Text("Support report copied to clipboard.", color = NoctColors.Green)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                viewModel.clearError()
                viewModel.startConsoleMode(permissionGranted = false)
            }) { Text(error.retryLabel) }
        },
        dismissButton = {
            if (error.diagnosticsUseful) {
                TextButton(
                    onClick = {
                        viewModel.clearError()
                        navController.getBackStackEntry(SenderRoutes.ROUTE_HOME).savedStateHandle["openSettings"] = true
                        navController.getBackStackEntry(SenderRoutes.ROUTE_HOME).savedStateHandle["focusSystemStatus"] = true
                        navController.navigate(SenderRoutes.ROUTE_HOME) {
                            launchSingleTop = true
                        }
                    },
                ) {
                    Text("System Status")
                }
            }
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ActionRow(content: @Composable FlowRowScope.() -> Unit) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(noctSpace(NoctSpacing.md)),
        verticalArrangement = Arrangement.spacedBy(noctSpace(NoctSpacing.md)),
        content = content,
    )
}

@Composable
private fun TopTitle(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(noctSpace(NoctSpacing.xs))) {
        Text(title, color = NoctColors.TextPrimary, style = androidx.compose.material3.MaterialTheme.typography.headlineMedium)
        if (subtitle.isNotBlank()) {
            Text(subtitle, color = NoctColors.TextSecondary, style = androidx.compose.material3.MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun DiagnosticsGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    NoctGlassCard(modifier = Modifier.fillMaxWidth().widthIn(max = 820.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(noctSpace(NoctSpacing.sm))) {
            Text(
                title,
                color = NoctColors.TextPrimary,
                fontWeight = FontWeight.SemiBold,
                style = androidx.compose.material3.MaterialTheme.typography.titleLarge,
            )
            content()
        }
    }
}
