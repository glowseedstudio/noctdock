package com.glowseed.noctdock.core

import kotlinx.serialization.Serializable

@Serializable
enum class ConsoleModeState {
    Idle,
    Searching,
    Pairing,
    Ready,
    Starting,
    Streaming,
    Stopping,
    Error,
}

@Serializable
enum class StreamSessionState {
    Idle,
    PermissionRequired,
    StartingService,
    CheckingEncoder,
    ConnectingReceiver,
    Active,
    Stopped,
    Failed,
}

@Serializable
enum class SoundMode(val label: String) {
    RETROID("Retroid Sound"),
    TV("TV Sound"),
    BOTH("Both"),
    QUIET("Quiet Mode"),
}

@Serializable
enum class NoctError(val message: String, val retryLabel: String = "Retry", val diagnosticsUseful: Boolean = true) {
    NoReceiverFound("No NoctDock screen was found nearby.", "Look again"),
    ReceiverOffline("The screen connection was interrupted.", "Return to Console Mode"),
    PairingFailed("Pairing failed. Check the 4-digit code shown on the receiver.", "Try pairing again"),
    MediaProjectionDenied("NoctDock needs permission to mirror your handheld in Console Mode.", "Try again"),
    EncoderUnavailable("This handheld cannot start Console Mode with the current video settings.", "Check again"),
    NetworkUnstable("The screen link is unstable. Move closer to Wi-Fi or choose a lighter Console Mode.", "Try again"),
    AudioCaptureUnavailable("This game keeps its sound private. Use Retroid Sound for this game.", "Open Sound settings"),
    ReceiverAudioUnavailable("TV sound could not start on this device. Try Retroid Sound.", "Open Sound settings"),
    AudioSyncUnstable("TV sound is having trouble keeping up. Try Retroid Sound or Performance Mode.", "Open Sound settings"),
    ReceiverDecoderError("NoctDock Receiver had trouble showing the picture.", "Return to Console Mode"),
    StreamStoppedUnexpectedly("Console Mode stopped unexpectedly.", "Start again"),
    AppLaunchFailed("The selected app could not be launched.", "Choose another app"),
}

@Serializable
enum class Smooth60HzMode {
    Off,
    AskOnStart,
    Always,
}

@Serializable
data class PerformanceSettings(
    val selectedProfileId: String = StreamProfiles.default.id,
    val smooth60HzMode: Smooth60HzMode = Smooth60HzMode.Off,
    val showStreamOverlay: Boolean = false,
    val preferLowLatencyCodec: Boolean = true,
    val keyframeIntervalSeconds: Int = 2,
    val maxQueueSize: Int = 2,
    val allowFrameDropping: Boolean = true,
    val manualBitrateMbps: Int? = null,
    val batterySaverMode: Boolean = false,
    val adaptiveBitrateEnabled: Boolean = true,
    val soundMode: SoundMode = SoundMode.RETROID,
    val lowerHandheldInTvSound: Boolean = true,
) {
    val selectedProfile: StreamProfile =
        StreamProfiles.all.firstOrNull { it.id == selectedProfileId } ?: StreamProfiles.default

    fun effectiveBitrateMbps(): Int {
        val requested = manualBitrateMbps ?: selectedProfile.bitrateMbps
        return if (batterySaverMode) requested.coerceAtMost(12) else requested
    }
}

@Serializable
data class LocalLibraryApp(
    val packageName: String,
    val label: String,
    val isFavourite: Boolean = false,
    val lastLaunchedAtMillis: Long = 0L,
    /** When set, Console Mode uses this profile for this app only (global default unchanged). */
    val profileOverrideId: String? = null,
) {
    val searchableText: String = "$label $packageName".lowercase()
}

object AppLibrarySorter {
    fun sort(apps: Collection<LocalLibraryApp>): List<LocalLibraryApp> = apps.sortedWith(
        compareByDescending<LocalLibraryApp> { it.isFavourite }
            .thenByDescending { it.lastLaunchedAtMillis }
            .thenBy { it.label.lowercase() }
            .thenBy { it.packageName },
    )

    fun filter(apps: Collection<LocalLibraryApp>, query: String): List<LocalLibraryApp> {
        val normalized = query.trim().lowercase()
        val filtered = if (normalized.isBlank()) apps else apps.filter { normalized in it.searchableText }
        return sort(filtered)
    }
}

@Serializable
data class StreamMetrics(
    val fps: Int = 0,
    val bitrateMbps: Int = 0,
    val latencyMs: Int = 0,
    val droppedFrames: Int = 0,
    val jitterMs: Int = 0,
    val packetLossPercent: Int = 0,
    val queueDepth: Int = 0,
    val receivedFps: Int = 0,
    val reassemblyDrops: Int = 0,
    val decoderErrors: Int = 0,
    val audioPacketsSent: Int = 0,
    val audioPacketsReceived: Int = 0,
    val audioUnderruns: Int = 0,
    val audioDrops: Int = 0,
    val audioBufferMs: Int = 0,
    val avOffsetMs: Int = 0,
    val pacingDelayMs: Int = 0,
    val pacedPackets: Int = 0,
    val requestedWidth: Int = 0,
    val requestedHeight: Int = 0,
    val actualEncoderWidth: Int = 0,
    val actualEncoderHeight: Int = 0,
    val virtualDisplayWidth: Int = 0,
    val virtualDisplayHeight: Int = 0,
    val codecMime: String = "",
    val configuredBitrateMbps: Int = 0,
    val receiverDecoderMime: String = "",
    val receiverSurfaceWidth: Int = 0,
    val receiverSurfaceHeight: Int = 0,
)

@Serializable
enum class NebulaTheme {
    CyanCore,
    MagentaDrift,
    VioletNebula,
    DeepSpace,
}

@Serializable
enum class BackgroundMotionMode {
    AnimatedNebula,
    MinimalDrift,
    DeepSpace,
}

@Serializable
enum class AccentTheme {
    Cyan,
    Magenta,
    Violet,
}

@Serializable
enum class UiDensity {
    Comfortable,
    Compact,
    Couch,
}

@Serializable
enum class ScreenCloakMode(val label: String, val description: String) {
    OFF("Off", "Keep the handheld screen unchanged."),
    DIM("Dim", "Gently lowers the handheld screen."),
    DARK("Dark", "Darkens the handheld screen for TV play."),
    MAXIMUM_DARK("Maximum Dark", "Lowest handheld brightness."),
}

@Serializable
enum class ScreenCloakMethod {
    NONE,
    TRANSPARENT_OVERLAY,
    SYSTEM_BRIGHTNESS_FALLBACK,
}

@Serializable
enum class ScreenCloakState {
    IDLE,
    PERMISSION_NEEDED,
    READY,
    ACTIVE,
    FAILED,
    RESTORING,
}

@Serializable
data class ScreenCloakStatus(
    val mode: ScreenCloakMode = ScreenCloakMode.OFF,
    val method: ScreenCloakMethod = ScreenCloakMethod.NONE,
    val state: ScreenCloakState = ScreenCloakState.IDLE,
    val active: Boolean = false,
    val overlayPermissionGranted: Boolean = false,
    val systemWritePermissionGranted: Boolean = false,
    val disabledDueToTvPictureIssue: Boolean = false,
    val restoreSucceeded: Boolean? = null,
) {
    val permissionLabel: String
        get() =
            when {
                mode == ScreenCloakMode.OFF -> "Not needed"
                method == ScreenCloakMethod.TRANSPARENT_OVERLAY && overlayPermissionGranted -> "Overlay allowed"
                method == ScreenCloakMethod.SYSTEM_BRIGHTNESS_FALLBACK && systemWritePermissionGranted -> "Brightness control allowed"
                overlayPermissionGranted -> "Overlay allowed"
                systemWritePermissionGranted -> "Brightness fallback allowed"
                else -> "Permission needed"
            }
}

data class ScreenCloakSession(
    val originalBrightness: Int? = null,
    val originalBrightnessMode: Int? = null,
    val capturedOriginal: Boolean = false,
    val appliedMethod: ScreenCloakMethod = ScreenCloakMethod.NONE,
    val restored: Boolean = false,
)

object ScreenCloakPolicy {
    fun overlayBrightness(mode: ScreenCloakMode): Float? = when (mode) {
        ScreenCloakMode.OFF -> null
        ScreenCloakMode.DIM -> 0.15f
        ScreenCloakMode.DARK -> 0.05f
        ScreenCloakMode.MAXIMUM_DARK -> 0.0f
    }

    fun fallbackBrightness(mode: ScreenCloakMode): Int? = when (mode) {
        ScreenCloakMode.OFF -> null
        ScreenCloakMode.DIM -> 38
        ScreenCloakMode.DARK -> 13
        ScreenCloakMode.MAXIMUM_DARK -> 0
    }

    fun preferredMethod(mode: ScreenCloakMode, overlayPermissionGranted: Boolean, systemWritePermissionGranted: Boolean, overlayDisabledDueToTvPictureIssue: Boolean): ScreenCloakMethod = when {
        mode == ScreenCloakMode.OFF -> ScreenCloakMethod.NONE
        !overlayDisabledDueToTvPictureIssue && overlayPermissionGranted -> ScreenCloakMethod.TRANSPARENT_OVERLAY
        systemWritePermissionGranted -> ScreenCloakMethod.SYSTEM_BRIGHTNESS_FALLBACK
        else -> ScreenCloakMethod.NONE
    }

    fun stateFor(
        mode: ScreenCloakMode,
        overlayPermissionGranted: Boolean,
        systemWritePermissionGranted: Boolean,
        overlayDisabledDueToTvPictureIssue: Boolean,
        active: Boolean = false,
        failed: Boolean = false,
    ): ScreenCloakState = when {
        mode == ScreenCloakMode.OFF -> ScreenCloakState.IDLE

        failed -> ScreenCloakState.FAILED

        active -> ScreenCloakState.ACTIVE

        preferredMethod(mode, overlayPermissionGranted, systemWritePermissionGranted, overlayDisabledDueToTvPictureIssue) !=
            ScreenCloakMethod.NONE ->
            ScreenCloakState.READY

        else -> ScreenCloakState.PERMISSION_NEEDED
    }
}

object ScreenCloakSessionTracker {
    fun captureOriginal(session: ScreenCloakSession, brightness: Int?, brightnessMode: Int?): ScreenCloakSession = if (session.capturedOriginal) {
        session
    } else {
        session.copy(
            originalBrightness = brightness,
            originalBrightnessMode = brightnessMode,
            capturedOriginal = true,
            restored = false,
        )
    }

    fun markApplied(session: ScreenCloakSession, method: ScreenCloakMethod): ScreenCloakSession = session.copy(appliedMethod = method, restored = false)

    fun markRestored(session: ScreenCloakSession): ScreenCloakSession = if (session.restored) {
        session
    } else {
        session.copy(appliedMethod = ScreenCloakMethod.NONE, restored = true)
    }
}

@Serializable
enum class GameHubLauncherLayout {
    Grid,
    Cover,
}

@Serializable
enum class GameHubControllerLayout(val label: String, val subtitle: String) {
    Xbox("Xbox layout", "A on bottom · B on the right"),
    Nintendo("Nintendo layout", "B on bottom · A on the right"),
}

@Serializable
data class AppearanceSettings(
    val reducedMotion: Boolean = false,
    val backgroundTheme: NebulaTheme = NebulaTheme.CyanCore,
    val backgroundMotionMode: BackgroundMotionMode = BackgroundMotionMode.AnimatedNebula,
    val accentTheme: AccentTheme = AccentTheme.Cyan,
    val uiDensity: UiDensity = UiDensity.Comfortable,
    val launcherLayout: GameHubLauncherLayout = GameHubLauncherLayout.Grid,
    val controllerLayout: GameHubControllerLayout = GameHubControllerLayout.Xbox,
    val controllerLayoutConfigured: Boolean = false,
    val hapticsEnabled: Boolean = true,
    val rememberLastReceiver: Boolean = true,
    val autoReconnect: Boolean = true,
    val screenCloakMode: ScreenCloakMode = ScreenCloakMode.OFF,
    val screenCloakOverlayDisabledDueToTvPictureIssue: Boolean = false,
)

object AppearanceDefaults {
    fun backgroundLabel(theme: NebulaTheme): String = when (theme) {
        NebulaTheme.CyanCore -> "Cyan Core"
        NebulaTheme.MagentaDrift -> "Magenta Drift"
        NebulaTheme.VioletNebula -> "Violet Nebula"
        NebulaTheme.DeepSpace -> "Deep Space"
    }

    fun backgroundMotionLabel(mode: BackgroundMotionMode): String = when (mode) {
        BackgroundMotionMode.AnimatedNebula -> "Animated Nebula"
        BackgroundMotionMode.MinimalDrift -> "Minimal Drift"
        BackgroundMotionMode.DeepSpace -> "Deep Space"
    }

    fun launcherLayoutLabel(layout: GameHubLauncherLayout): String = when (layout) {
        GameHubLauncherLayout.Grid -> "Grid"
        GameHubLauncherLayout.Cover -> "Cover"
    }

    fun controllerLayoutLabel(layout: GameHubControllerLayout): String = layout.label
}

@Serializable
enum class DockingTransitionState {
    Undocked,
    Preparing,
    Permission,
    Pairing,
    Docking,
    Docked,
    Recovering,
}

object DockingStateReducer {
    fun next(current: DockingTransitionState, receiverTrusted: Boolean, permissionGranted: Boolean, streamActive: Boolean): DockingTransitionState = when {
        streamActive -> DockingTransitionState.Docked
        !receiverTrusted -> DockingTransitionState.Pairing
        !permissionGranted -> DockingTransitionState.Permission
        current == DockingTransitionState.Permission -> DockingTransitionState.Docking
        else -> DockingTransitionState.Preparing
    }
}

/** Redacted in-app and clipboard diagnostics; omit installed app names unless explicitly requested. */
@Serializable
data class DiagnosticsSnapshot(
    val receiverName: String = "None",
    val connectionState: ConsoleModeState = ConsoleModeState.Idle,
    val streamState: StreamSessionState = StreamSessionState.Idle,
    val encoderName: String = "Not selected",
    val decoderFeedback: String = "No receiver feedback",
    val metrics: StreamMetrics = StreamMetrics(),
    val soundMode: SoundMode = SoundMode.RETROID,
    val deviceProfile: String = "Unknown Android handheld",
    val deviceTier: DevicePerformanceTier = DevicePerformanceTier.UNKNOWN,
    val handheldTier: HandheldPerformanceTier = HandheldPerformanceTier.UNKNOWN,
    val deviceSupportLevel: DeviceSupportLevel = DeviceSupportLevel.UNKNOWN_SAFE_DEFAULT,
    val recommendedProfile: String = StreamProfiles.Performance.title,
    val encoderCapability: EncoderCapabilitySummary = EncoderCapabilitySummary(),
    val backgroundMode: String = "Animated Nebula",
    val reducedMotion: Boolean = false,
    val batterySaverMode: Boolean = false,
    val selectedCodec: VideoCodec = VideoCodec.AVC,
    val requestedResolution: String = "Unknown",
    val actualEncoderResolution: String = "Unknown",
    val virtualDisplayResolution: String = "Unknown",
    val configuredBitrateMbps: Int = 0,
    val receiverDecoderMime: String = "Unknown",
    val receiverSurfaceResolution: String = "Unknown",
    val connectionTestResult: ConnectionTestResult? = null,
    val lastError: NoctError? = null,
    val screenCloakStatus: ScreenCloakStatus = ScreenCloakStatus(),
    val discoveryLifecycleState: DiscoveryLifecycleState = DiscoveryLifecycleState.WAITING,
    val broadcasting: Boolean = false,
    val lastBroadcastRestartLabel: String = "Never",
    val discoveryStateLabel: String = "Idle",
    val refreshRateHelperStatus: RefreshRateHelperStatus = RefreshRateHelperStatus(),
) {
    fun exportText(includeAppNames: Boolean = false): String = buildString {
        appendLine("NoctDock diagnostics")
        appendLine("Receiver: $receiverName")
        appendLine("Connection: $connectionState")
        appendLine("Stream: $streamState")
        appendLine("Encoder: $encoderName")
        appendLine("Decoder feedback: $decoderFeedback")
        appendLine("Sound mode: ${soundMode.label}")
        appendLine("Device profile: $deviceProfile")
        appendLine("Device tier: $deviceTier")
        appendLine("Handheld tier: $handheldTier")
        appendLine("Support level: $deviceSupportLevel")
        appendLine("Recommended profile: $recommendedProfile")
        appendLine(
            "Encoder capability: ${encoderCapability.encoderName}, hardware=${encoderCapability.hardwareAccelerated}, 1080p60=${encoderCapability.supports1080p60}",
        )
        appendLine("Background mode: $backgroundMode")
        appendLine("Reduced motion: $reducedMotion")
        appendLine("Battery Saver: $batterySaverMode")
        appendLine("Screen Cloak mode: ${screenCloakStatus.mode.label}")
        appendLine("Screen Cloak method: ${screenCloakStatus.method}")
        appendLine("Screen Cloak state: ${screenCloakStatus.state}")
        appendLine("Screen Cloak permission: ${screenCloakStatus.permissionLabel}")
        appendLine("Screen Cloak active: ${screenCloakStatus.active}")
        appendLine("Screen Cloak restore: ${screenCloakStatus.restoreSucceeded}")
        appendLine("Screen Cloak blocked for TV picture: ${screenCloakStatus.disabledDueToTvPictureIssue}")
        appendLine("Selected codec: ${selectedCodec.friendlyName}")
        appendLine("Requested output: $requestedResolution")
        appendLine("Actual encoder: $actualEncoderResolution")
        appendLine("Virtual display: $virtualDisplayResolution")
        appendLine("Configured bitrate: $configuredBitrateMbps Mbps")
        appendLine("Receiver decoder MIME: $receiverDecoderMime")
        appendLine("Receiver surface: $receiverSurfaceResolution")
        appendLine("Broadcasting: ${if (broadcasting) "yes" else "no"}")
        appendLine("Last broadcast restart: $lastBroadcastRestartLabel")
        appendLine("Discovery state: $discoveryStateLabel")
        appendLine("60 Hz requested: ${if (refreshRateHelperStatus.requested60Hz) "yes" else "no"}")
        appendLine("Active refresh rate: ${refreshRateHelperStatus.activeRefreshRateHz?.let { "$it Hz" } ?: "Unknown"}")
        appendLine("60 Hz helper: ${refreshRateHelperStatus.resultLabel()}")
        refreshRateHelperStatus.guidanceMessage?.let { appendLine("60 Hz guidance: $it") }
        appendLine("Connection test: ${connectionTestResult?.friendlyLabel ?: "Not run"}")
        appendLine("Connection stability: ${connectionTestResult?.stability ?: "Unknown"}")
        appendLine("Connection transport: ${connectionTestResult?.receiverTransport ?: "Unknown"}")
        appendLine("Connection stale: ${connectionTestResult?.isStale() ?: true}")
        appendLine("FPS: ${metrics.fps}")
        appendLine("Bitrate: ${metrics.bitrateMbps} Mbps")
        appendLine("Latency: ${metrics.latencyMs} ms")
        appendLine("Dropped frames: ${metrics.droppedFrames}")
        appendLine("Packet loss estimate: ${metrics.packetLossPercent}%")
        appendLine("Queue depth: ${metrics.queueDepth}")
        appendLine("Audio packets sent: ${metrics.audioPacketsSent}")
        appendLine("Audio packets received: ${metrics.audioPacketsReceived}")
        appendLine("Audio underruns: ${metrics.audioUnderruns}")
        appendLine("Audio drops: ${metrics.audioDrops}")
        appendLine("Audio buffer: ${metrics.audioBufferMs} ms")
        appendLine("A/V offset: ${metrics.avOffsetMs} ms")
        appendLine("Pacing delay: ${metrics.pacingDelayMs} ms")
        appendLine("Paced packets: ${metrics.pacedPackets}")
        appendLine("Last error: ${lastError?.message ?: "None"}")
        appendLine("Installed app names included: $includeAppNames")
    }
}

object ConsoleModeReducer {
    fun start(receiverTrusted: Boolean, permissionGranted: Boolean): ConsoleModeState = when {
        !receiverTrusted -> ConsoleModeState.Pairing
        !permissionGranted -> ConsoleModeState.Starting
        else -> ConsoleModeState.Streaming
    }

    fun stop(current: ConsoleModeState): ConsoleModeState = if (current == ConsoleModeState.Streaming) ConsoleModeState.Stopping else ConsoleModeState.Idle
}
