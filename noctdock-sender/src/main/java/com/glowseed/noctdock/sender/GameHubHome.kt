package com.glowseed.noctdock.sender

import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Shader.TileMode
import android.graphics.SweepGradient
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.glowseed.noctdock.core.ConsoleModeState
import com.glowseed.noctdock.core.DiscoveredReceiver
import com.glowseed.noctdock.core.LocalLibraryApp
import com.glowseed.noctdock.core.LocalNoctAccent
import com.glowseed.noctdock.core.NoctColors
import com.glowseed.noctdock.core.NoctDockAzaharContract
import com.glowseed.noctdock.core.NoctDockHeroOrb
import com.glowseed.noctdock.core.NoctGlassCard
import com.glowseed.noctdock.core.NoctOrb
import com.glowseed.noctdock.core.NoctPrimaryButton
import com.glowseed.noctdock.core.NoctSecondaryButton
import com.glowseed.noctdock.core.NoctSelectableCard
import com.glowseed.noctdock.core.NoctStatusPill
import com.glowseed.noctdock.core.PairingState
import com.glowseed.noctdock.core.ReceiverDisplayWording
import com.glowseed.noctdock.core.ScreenCloakMode
import com.glowseed.noctdock.core.StreamProfile
import com.glowseed.noctdock.core.StreamProfiles
import com.glowseed.noctdock.core.StreamSessionState
import com.glowseed.noctdock.core.noctGradientBorder
import com.glowseed.noctdock.core.noctSpace

/** Fluid sizing for one fixed Home layout — scales in/out, never swaps to a different layout. */
internal data class GameHubViewport(val width: Dp, val height: Dp) {
    private val minSide: Dp get() = minOf(width, height)

    val edgePaddingH: Dp get() = (width * 0.065f).coerceIn(24.dp, 40.dp)
    val edgePaddingV: Dp get() = (height * 0.03f).coerceIn(10.dp, 20.dp)
    val sectionGap: Dp get() = (minSide * 0.038f).coerceIn(12.dp, 20.dp)

    val portalCardMaxWidth: Dp get() = minOf(520.dp, width * 0.92f).coerceAtLeast(260.dp)
    val portalCardWidthFraction: Float get() = 0.92f

    val settingsCogSize: Dp get() = (minSide * 0.095f).coerceIn(40.dp, 46.dp)

    companion object {
        /** Same portal layout; spacing and orb shrink to fit [stageHeight] without scrolling. */
        fun portalFit(stageHeight: Dp): Float = minOf(1f, stageHeight.value / 460f).coerceIn(0.50f, 1f)

        /** Picks column count to fit all items; caps height only so rows use full width. */
        fun launcherGridLayout(stageWidth: Dp, stageHeight: Dp, itemCount: Int): GameHubLauncherGridLayout {
            val contentInsetH = (stageWidth * 0.05f).coerceIn(22.dp, 36.dp)
            val gap = 10.dp
            val availW = (stageWidth - contentInsetH * 2).coerceAtLeast(180.dp)
            val availH = stageHeight.coerceAtLeast(120.dp)
            val count = itemCount.coerceAtLeast(1)
            val maxTileHeight = (availH * 0.38f).coerceIn(96.dp, 142.dp)
            var best =
                GameHubLauncherGridLayout(
                    columns = 1,
                    rows = count,
                    tileWidth = availW,
                    tileHeight = minOf((availH - gap * (count - 1)) / count, maxTileHeight),
                    gap = gap,
                    contentInsetH = contentInsetH,
                )
            var bestScore = 0f
            for (columns in 1..count) {
                val rows = (count + columns - 1) / columns
                val rawW = (availW - gap * (columns - 1)) / columns
                val rawH = (availH - gap * (rows - 1)) / rows
                val tileWidth = rawW
                val tileHeight = minOf(rawH, maxTileHeight)
                if (tileWidth < 72.dp || tileHeight < 88.dp) continue
                val aspect = tileWidth / tileHeight
                if (aspect > 2.1f) continue
                val aspectBoost = if (aspect in 0.72f..1.55f) 1.12f else 1f
                val score = minOf(tileWidth.value, tileHeight.value) * aspectBoost
                if (score > bestScore) {
                    bestScore = score
                    best =
                        GameHubLauncherGridLayout(
                            columns = columns,
                            rows = rows,
                            tileWidth = tileWidth,
                            tileHeight = tileHeight,
                            gap = gap,
                            contentInsetH = contentInsetH,
                        )
                }
            }
            return best
        }

        /**
         * Same tile sizing as a full launcher page, but [GameHubLauncherGridLayout.rows] grows with
         * [itemCount] so the caller can put the grid in a vertical scroll when there are many apps.
         */
        fun libraryGridLayout(stageWidth: Dp, stageHeight: Dp, itemCount: Int): GameHubLauncherGridLayout {
            val count = itemCount.coerceAtLeast(1)
            val sizingCount = minOf(count, 12)
            val base = launcherGridLayout(stageWidth, stageHeight, sizingCount)
            val rows = (count + base.columns - 1) / base.columns
            return base.copy(rows = rows)
        }
    }
}

internal data class GameHubLauncherGridLayout(val columns: Int, val rows: Int, val tileWidth: Dp, val tileHeight: Dp, val gap: Dp, val contentInsetH: Dp)

internal enum class GameHubHomeMode {
    Portal,
    Launcher,
}

internal enum class GameHubShelfKind {
    Azahar,
    Favourites,
    RecentlyPlayed,
    Emulators,
    AndroidGames,
    StreamingApps,
}

internal enum class GameHubTileActionKind {
    ConnectFirst,
    Pair,
    LaunchOnScreen,
    Launch3dsMode,
    PickAzaharMode,
    EnterConsoleMode,
    StopConsoleMode,
}

internal data class GameHubLauncherItem(
    val id: String,
    val label: String,
    val shelf: GameHubShelfKind,
    val action: GameHubTileActionKind,
    val isAzahar: Boolean = false,
    val isFavourite: Boolean = false,
    val profileOverrideId: String? = null,
)

internal data class GameHubShelf(val kind: GameHubShelfKind, val title: String, val items: List<GameHubLauncherItem>)

internal data class GameHubPortalPresentation(
    val statusPill: String,
    val title: String = "Connect to a screen",
    val subtitle: String = "Open NoctDock Receiver on your TV, tablet, or phone.",
    val stateText: String,
    val primaryLabel: String,
    val primaryEnabled: Boolean,
    val primaryAction: GameHubTileActionKind,
)

internal object GameHubHomeMapper {
    fun isAzaharPackage(packageName: String): Boolean = packageName in NoctDockAzaharContract.PACKAGE_CANDIDATES

    fun isScreenTrusted(uiState: SenderUiState, receiver: DiscoveredReceiver): Boolean = uiState.trustedReceiver?.identity?.id == receiver.identity.id &&
        uiState.pairingState == PairingState.Trusted

    fun resolveMode(uiState: SenderUiState): GameHubHomeMode {
        val receiver = uiState.defaultReceiver ?: return GameHubHomeMode.Portal
        return if (isScreenTrusted(uiState, receiver)) GameHubHomeMode.Launcher else GameHubHomeMode.Portal
    }

    fun portalPresentation(uiState: SenderUiState): GameHubPortalPresentation {
        val receiver = uiState.defaultReceiver
        return when {
            uiState.consoleModeState == ConsoleModeState.Streaming ->
                GameHubPortalPresentation(
                    statusPill = "Docked",
                    stateText = "Console Mode is active on your screen.",
                    primaryLabel = "Stop Console Mode",
                    primaryEnabled = true,
                    primaryAction = GameHubTileActionKind.StopConsoleMode,
                )

            receiver == null ->
                GameHubPortalPresentation(
                    statusPill = "Looking for screen",
                    stateText = "Looking for a screen…",
                    primaryLabel = "Looking…",
                    primaryEnabled = false,
                    primaryAction = GameHubTileActionKind.ConnectFirst,
                )

            !isScreenTrusted(uiState, receiver) ->
                GameHubPortalPresentation(
                    statusPill = screenReadyPill(receiver),
                    stateText =
                    when (uiState.pairingState) {
                        PairingState.AwaitingCode -> "Enter the code shown on your screen"
                        PairingState.Failed -> "Pairing failed — tap Pair to try again"
                        else -> "Screen found — pair to continue"
                    },
                    primaryLabel = "Pair",
                    primaryEnabled = uiState.pairingState != PairingState.AwaitingCode,
                    primaryAction = GameHubTileActionKind.Pair,
                )

            else ->
                GameHubPortalPresentation(
                    statusPill = screenReadyPill(receiver),
                    stateText = "Screen ready",
                    primaryLabel = "Pair",
                    primaryEnabled = false,
                    primaryAction = GameHubTileActionKind.Pair,
                )
        }
    }

    fun screenReadyPill(receiver: DiscoveredReceiver): String = "${receiver.displayName} ${ReceiverDisplayWording.readyLabel(receiver.formFactor)}"

    /** All library apps in one list (manual + emulators from Settings → Library). Favourites first. */
    fun launcherItems(uiState: SenderUiState, receiverReady: Boolean): List<GameHubLauncherItem> {
        val items = mutableListOf<GameHubLauncherItem>()
        if (uiState.azaharStatus.installed) {
            val azaharOverride =
                uiState.libraryApps
                    .firstOrNull { isAzaharPackage(it.model.packageName) }
                    ?.model
                    ?.profileOverrideId
            items +=
                GameHubLauncherItem(
                    id = "azahar",
                    label = "NoctDock Azahar",
                    shelf = GameHubShelfKind.Azahar,
                    action = if (receiverReady) GameHubTileActionKind.PickAzaharMode else GameHubTileActionKind.ConnectFirst,
                    isAzahar = true,
                    profileOverrideId = azaharOverride,
                )
        }
        val sorted =
            uiState.libraryApps
                .filter { !isAzaharPackage(it.model.packageName) }
                .sortedWith(
                    compareByDescending<LibraryAppItem> { it.model.isFavourite }
                        .thenBy { it.model.label.lowercase() },
                )
        sorted.forEach { app ->
            items +=
                GameHubLauncherItem(
                    id = app.model.packageName,
                    label = displayLabel(app.model),
                    shelf = GameHubShelfKind.Favourites,
                    isFavourite = app.model.isFavourite,
                    profileOverrideId = app.model.profileOverrideId,
                    action = if (receiverReady) GameHubTileActionKind.LaunchOnScreen else GameHubTileActionKind.ConnectFirst,
                )
        }
        return items
    }

    fun tileActionLabel(action: GameHubTileActionKind): String = when (action) {
        GameHubTileActionKind.ConnectFirst -> "Connect first"
        GameHubTileActionKind.Pair -> "Pair"
        GameHubTileActionKind.LaunchOnScreen -> "Launch on Screen"
        GameHubTileActionKind.Launch3dsMode -> "Launch in 3DS Mode"
        GameHubTileActionKind.PickAzaharMode -> "Launch Azahar"
        GameHubTileActionKind.EnterConsoleMode -> "Enter Console Mode"
        GameHubTileActionKind.StopConsoleMode -> "Stop Console Mode"
    }

    fun displayLabel(model: LocalLibraryApp): String = model.label.trim()
}

internal enum class GameHubAppCategory {
    Emulator,
    Streaming,
    AndroidGame,
}

internal object GameHubAppClassifier {
    fun category(app: LocalLibraryApp): GameHubAppCategory {
        val text = app.searchableText
        return when {
            isEmulatorText(text) -> GameHubAppCategory.Emulator
            isStreamingText(text) -> GameHubAppCategory.Streaming
            else -> GameHubAppCategory.AndroidGame
        }
    }

    private fun isEmulatorText(text: String): Boolean = listOf(
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
        "m64",
        "melonds",
        "lime3ds",
    ).any { it in text }

    private fun isStreamingText(text: String): Boolean = listOf(
        "netflix",
        "youtube",
        "twitch",
        "prime video",
        "disney",
        "plex",
        "steam link",
        "moonlight",
        "geforce",
        "xbox",
        "cloud gaming",
        "stadia",
        "appletv",
        "hulu",
        "crunchyroll",
    ).any { it in text }
}

@Composable
internal fun GameHubHomeScreen(
    uiState: SenderUiState,
    viewModel: SenderViewModel,
    requestConsoleMode: () -> Unit,
    requestProjection: () -> Unit,
    openLibraryPanelOnStart: Boolean = false,
    openScreensPanelOnStart: Boolean = false,
    openConsoleModesPanelOnStart: Boolean = false,
    openSettingsPanelOnStart: Boolean = false,
    focusSystemStatusOnStart: Boolean = false,
    onOpenDiagnostics: () -> Unit,
) {
    val accent = LocalNoctAccent.current
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val reducedMotion = uiState.appearanceSettings.reducedMotion
    val settingsOverlayAllowed = remember { ScreenCloakPermissionHelper.canDrawOverlays(context) }
    val settingsSystemWriteAllowed = remember { ScreenCloakPermissionHelper.canWriteSystemSettings(context) }
    val settingsRows =
        remember(uiState, settingsOverlayAllowed, settingsSystemWriteAllowed) {
            buildGameHubSettingsRows(
                uiState = uiState,
                viewModel = viewModel,
                overlayAllowed = settingsOverlayAllowed,
                systemWriteAllowed = settingsSystemWriteAllowed,
                onOpenDiagnostics = onOpenDiagnostics,
                onScreenCloakTest = { viewModel.refreshScreenCloakTest() },
            )
        }
    val settingsFocusCount = gameHubSettingsFocusItemCount(settingsRows)
    val onScreenCloakModeSelected: (ScreenCloakMode) -> Unit by rememberUpdatedState {
        { mode: ScreenCloakMode ->
            viewModel.updateScreenCloakMode(mode)
            if (
                mode != ScreenCloakMode.OFF &&
                !uiState.appearanceSettings.screenCloakOverlayDisabledDueToTvPictureIssue &&
                !ScreenCloakPermissionHelper.canDrawOverlays(context)
            ) {
                context.startActivity(ScreenCloakPermissionHelper.overlayPermissionIntent(context))
            }
        }
    }
    val mode = remember(uiState.defaultReceiver?.identity?.id, uiState.trustedReceiver?.identity?.id, uiState.pairingState) {
        GameHubHomeMapper.resolveMode(uiState)
    }
    val portal = remember(uiState.defaultReceiver?.identity?.id, uiState.pairingState, uiState.consoleModeState) {
        GameHubHomeMapper.portalPresentation(uiState)
    }
    val trustedReceiverId = uiState.trustedReceiver?.identity?.id
    val receiverReady =
        uiState.defaultReceiver != null &&
            trustedReceiverId != null &&
            trustedReceiverId == uiState.defaultReceiver.identity.id &&
            uiState.pairingState == PairingState.Trusted
    val launcherItems = remember(uiState.libraryApps, receiverReady, uiState.azaharStatus.installed) {
        GameHubHomeMapper.launcherItems(uiState, receiverReady)
    }
    var libraryFilter by remember { mutableStateOf(GameHubLibraryFilter.All) }
    val libraryGridEntries =
        remember(uiState.libraryApps, uiState.libraryAddCandidates, uiState.libraryQuery, libraryFilter) {
            gameHubLibraryGridEntries(uiState, libraryFilter)
        }
    val homeFocus = remember { FocusRequester() }
    val libraryFocus = remember { FocusRequester() }
    val screensFocus = remember { FocusRequester() }
    val consoleModesFocus = remember { FocusRequester() }
    val settingsFocus = remember { FocusRequester() }
    val portalFocus = remember { FocusRequester() }
    val homeInputFocus = remember { FocusRequester() }
    var focusZone by remember { mutableStateOf(GameHubFocusZone.TopBar) }
    var topBarIndex by remember { mutableIntStateOf(0) }
    var libraryFilterIndex by remember { mutableIntStateOf(0) }
    var launcherGridInputActive by remember { mutableStateOf(false) }
    var focusedIndex by remember { mutableIntStateOf(0) }
    var homeBarFocused by remember { mutableStateOf(false) }
    var libraryBarFocused by remember { mutableStateOf(false) }
    var screensBarFocused by remember { mutableStateOf(false) }
    var consoleModesBarFocused by remember { mutableStateOf(false) }
    var settingsBarFocused by remember { mutableStateOf(false) }
    var showAzaharModePicker by remember { mutableStateOf(false) }
    var tileMenu by remember { mutableStateOf<GameHubTileMenu?>(null) }
    var homePanel by remember { mutableStateOf(GameHubHomePanel.Launcher) }

    fun focusTopBarButton(index: Int) {
        focusZone = GameHubFocusZone.TopBar
        topBarIndex = index.coerceIn(0, GAME_HUB_TOP_BAR_LAST_INDEX)
        launcherGridInputActive = false
        when (topBarIndex) {
            GAME_HUB_TOP_BAR_HOME -> homeFocus.requestFocus()
            GAME_HUB_TOP_BAR_LIBRARY -> libraryFocus.requestFocus()
            GAME_HUB_TOP_BAR_SCREENS -> screensFocus.requestFocus()
            GAME_HUB_TOP_BAR_CONSOLE_MODES -> consoleModesFocus.requestFocus()
            else -> settingsFocus.requestFocus()
        }
    }

    fun isAtHomeAnchor(): Boolean = homePanel == GameHubHomePanel.Launcher &&
        focusZone == GameHubFocusZone.TopBar &&
        topBarIndex == GAME_HUB_TOP_BAR_HOME &&
        !launcherGridInputActive

    /** Back / B first press: top bar Home tab with launcher panel, without entering the game grid. */
    fun navigateBackToHomeAnchor() {
        homePanel = GameHubHomePanel.Launcher
        launcherGridInputActive = false
        focusTopBarButton(GAME_HUB_TOP_BAR_HOME)
    }

    fun dismissOverlayMenus() {
        when (val menu = tileMenu) {
            is GameHubTileMenu.ProfilePicker -> tileMenu = GameHubTileMenu.Overflow(menu.item)

            else -> {
                showAzaharModePicker = false
                tileMenu = null
            }
        }
    }

    fun applyGoHome() {
        homePanel = GameHubHomePanel.Launcher
        when (mode) {
            GameHubHomeMode.Portal -> {
                launcherGridInputActive = false
                focusTopBarButton(GAME_HUB_TOP_BAR_HOME)
            }

            GameHubHomeMode.Launcher -> {
                if (launcherItems.isNotEmpty()) {
                    focusZone = GameHubFocusZone.Grid
                    launcherGridInputActive = true
                    libraryBarFocused = false
                    screensBarFocused = false
                    consoleModesBarFocused = false
                    settingsBarFocused = false
                    homeBarFocused = false
                    focusManager.clearFocus()
                    homeInputFocus.requestFocus()
                } else {
                    focusTopBarButton(GAME_HUB_TOP_BAR_HOME)
                }
            }
        }
    }

    fun openLibraryPanel() {
        homePanel = GameHubHomePanel.Library
        launcherGridInputActive = false
        focusedIndex = 0
        libraryFilterIndex = gameHubTopBarIndexForFilter(libraryFilter)
        focusTopBarButton(GAME_HUB_TOP_BAR_LIBRARY)
    }

    fun openScreensPanel() {
        homePanel = GameHubHomePanel.Screens
        launcherGridInputActive = false
        focusedIndex = 0
        viewModel.startDiscovery()
        focusTopBarButton(GAME_HUB_TOP_BAR_SCREENS)
    }

    fun openConsoleModesPanel() {
        homePanel = GameHubHomePanel.ConsoleModes
        launcherGridInputActive = false
        focusTopBarButton(GAME_HUB_TOP_BAR_CONSOLE_MODES)
    }

    fun openSettingsPanel() {
        homePanel = GameHubHomePanel.Settings
        launcherGridInputActive = false
        focusedIndex = 0
        focusTopBarButton(GAME_HUB_TOP_BAR_SETTINGS)
    }

    fun selectTopBarTab(index: Int) {
        when (index) {
            GAME_HUB_TOP_BAR_HOME -> applyGoHome()

            GAME_HUB_TOP_BAR_LIBRARY ->
                if (homePanel == GameHubHomePanel.Library) applyGoHome() else openLibraryPanel()

            GAME_HUB_TOP_BAR_SCREENS ->
                if (homePanel == GameHubHomePanel.Screens) applyGoHome() else openScreensPanel()

            GAME_HUB_TOP_BAR_CONSOLE_MODES ->
                if (homePanel == GameHubHomePanel.ConsoleModes) applyGoHome() else openConsoleModesPanel()

            else ->
                if (homePanel == GameHubHomePanel.Settings) applyGoHome() else openSettingsPanel()
        }
    }

    fun activateLibraryFilters() {
        focusZone = GameHubFocusZone.LibraryFilters
        launcherGridInputActive = false
        focusManager.clearFocus()
        homeInputFocus.requestFocus()
    }

    fun activateScreensList() {
        focusZone = GameHubFocusZone.ScreensList
        launcherGridInputActive = false
        focusManager.clearFocus()
        homeInputFocus.requestFocus()
    }

    fun activateConsoleModesList() {
        focusZone = GameHubFocusZone.ConsoleModesList
        launcherGridInputActive = false
        focusManager.clearFocus()
        homeInputFocus.requestFocus()
    }

    fun activateSettingsPanel() {
        focusZone = GameHubFocusZone.SettingsPanel
        launcherGridInputActive = false
        focusManager.clearFocus()
        homeInputFocus.requestFocus()
    }

    fun activateLauncherGrid() {
        focusZone = GameHubFocusZone.Grid
        launcherGridInputActive = true
        libraryBarFocused = false
        screensBarFocused = false
        consoleModesBarFocused = false
        settingsBarFocused = false
        homeBarFocused = false
        focusManager.clearFocus()
        homeInputFocus.requestFocus()
    }

    val availableProfiles =
        remember(uiState.defaultReceiver, uiState.deviceProfile, uiState.connectionTestResult) {
            ConsoleModeProfiles.available(uiState)
        }
    fun onPrimaryPortal() {
        when (portal.primaryAction) {
            GameHubTileActionKind.Pair, GameHubTileActionKind.ConnectFirst ->
                uiState.defaultReceiver?.let(viewModel::connect)

            GameHubTileActionKind.StopConsoleMode -> requestConsoleMode()

            else -> Unit
        }
    }
    val onTileActivate by rememberUpdatedState { item: GameHubLauncherItem ->
        when (item.action) {
            GameHubTileActionKind.ConnectFirst, GameHubTileActionKind.Pair -> {
                uiState.defaultReceiver?.let(viewModel::connect)
            }

            GameHubTileActionKind.Launch3dsMode -> viewModel.launchAzahar3dsMode()

            GameHubTileActionKind.PickAzaharMode -> showAzaharModePicker = true

            GameHubTileActionKind.LaunchOnScreen -> {
                val app = uiState.libraryApps.firstOrNull { it.model.packageName == item.id }?.model
                if (app != null) {
                    viewModel.launchInConsoleMode(app, permissionGranted = uiState.streamState == StreamSessionState.Active)
                    if (uiState.streamState != StreamSessionState.Active) {
                        requestConsoleMode()
                    }
                }
            }

            GameHubTileActionKind.StopConsoleMode -> requestConsoleMode()

            GameHubTileActionKind.EnterConsoleMode -> Unit
        }
        Unit
    }

    LaunchedEffect(uiState.defaultReceiver?.identity?.id, mode, uiState.pairingState) {
        if (mode != GameHubHomeMode.Portal) return@LaunchedEffect
        val receiver = uiState.defaultReceiver ?: return@LaunchedEffect
        if (GameHubHomeMapper.isScreenTrusted(uiState, receiver)) return@LaunchedEffect
        if (
            uiState.pairingState == PairingState.AwaitingCode ||
            uiState.pairingState == PairingState.Failed ||
            uiState.pairingState == PairingState.Trusted
        ) {
            return@LaunchedEffect
        }
        viewModel.connect(receiver)
    }

    LaunchedEffect(libraryFilter) {
        focusedIndex = 0
        libraryFilterIndex = gameHubTopBarIndexForFilter(libraryFilter)
    }

    LaunchedEffect(openLibraryPanelOnStart) {
        if (openLibraryPanelOnStart) {
            openLibraryPanel()
        }
    }

    LaunchedEffect(openScreensPanelOnStart) {
        if (openScreensPanelOnStart) {
            openScreensPanel()
        }
    }

    LaunchedEffect(openConsoleModesPanelOnStart) {
        if (openConsoleModesPanelOnStart) {
            openConsoleModesPanel()
        }
    }

    LaunchedEffect(openSettingsPanelOnStart, focusSystemStatusOnStart) {
        if (openSettingsPanelOnStart) {
            openSettingsPanel()
            if (focusSystemStatusOnStart) {
                gameHubSettingsFocusIndexForLabel(settingsRows, "System Status")?.let { index ->
                    focusedIndex = index
                    activateSettingsPanel()
                }
            }
        }
    }

    LaunchedEffect(mode) {
        if (mode == GameHubHomeMode.Portal && homePanel != GameHubHomePanel.Launcher) {
            applyGoHome()
        }
    }

    LaunchedEffect(homePanel, availableProfiles.size) {
        focusedIndex =
            when (homePanel) {
                GameHubHomePanel.ConsoleModes ->
                    gameHubConsoleModesPreferenceStartIndex(availableProfiles.size)

                else -> 0
            }
    }

    LaunchedEffect(mode, launcherItems.size) {
        if (homePanel != GameHubHomePanel.Launcher) return@LaunchedEffect
        focusedIndex = 0
        when (mode) {
            GameHubHomeMode.Portal -> {
                launcherGridInputActive = false
                focusTopBarButton(GAME_HUB_TOP_BAR_HOME)
            }

            GameHubHomeMode.Launcher ->
                if (launcherItems.isEmpty()) {
                    launcherGridInputActive = false
                    focusTopBarButton(GAME_HUB_TOP_BAR_HOME)
                } else {
                    activateLauncherGrid()
                }
        }
    }

    BackHandler(enabled = showAzaharModePicker || tileMenu != null || !isAtHomeAnchor()) {
        if (showAzaharModePicker || tileMenu != null) {
            dismissOverlayMenus()
        } else {
            navigateBackToHomeAnchor()
        }
    }

    GameHubGradientPhaseProvider(reducedMotion = reducedMotion) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing),
        ) {
            val viewport = remember(maxWidth, maxHeight) { GameHubViewport(maxWidth, maxHeight) }
            val gridLayout =
                remember(maxWidth, maxHeight, launcherItems.size, libraryGridEntries.size, availableProfiles.size, homePanel) {
                    val count =
                        when (homePanel) {
                            GameHubHomePanel.Library -> libraryGridEntries.size.coerceAtLeast(1)
                            GameHubHomePanel.Screens -> gameHubScreensItemCount(uiState.receivers.size)
                            GameHubHomePanel.ConsoleModes -> gameHubConsoleModesItemCount(availableProfiles.size)
                            GameHubHomePanel.Settings -> 1
                            GameHubHomePanel.Launcher -> launcherItems.size.coerceAtLeast(1)
                        }
                    GameHubViewport.launcherGridLayout(maxWidth, maxHeight, count)
                }
            Column(
                modifier =
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = viewport.edgePaddingH, vertical = viewport.edgePaddingV)
                    .focusRequester(homeInputFocus)
                    .focusable(focusZone != GameHubFocusZone.TopBar)
                    .onPreviewKeyEvent { event ->
                        if (event.gameHubIsBackDown()) {
                            if (showAzaharModePicker || tileMenu != null) {
                                dismissOverlayMenus()
                                return@onPreviewKeyEvent true
                            }
                            if (isAtHomeAnchor()) {
                                return@onPreviewKeyEvent false
                            }
                            navigateBackToHomeAnchor()
                            return@onPreviewKeyEvent true
                        } else if (event.type != KeyEventType.KeyDown) {
                            return@onPreviewKeyEvent false
                        } else if (showAzaharModePicker || tileMenu != null) {
                            return@onPreviewKeyEvent false
                        } else {
                            val launcherGridActive =
                                mode == GameHubHomeMode.Launcher &&
                                    homePanel == GameHubHomePanel.Launcher &&
                                    launcherItems.isNotEmpty()
                            val libraryGridActive = homePanel == GameHubHomePanel.Library
                            val screensListActive =
                                homePanel == GameHubHomePanel.Screens &&
                                    focusZone == GameHubFocusZone.ScreensList
                            val consoleModesListActive =
                                homePanel == GameHubHomePanel.ConsoleModes &&
                                    focusZone == GameHubFocusZone.ConsoleModesList
                            val settingsPanelActive =
                                homePanel == GameHubHomePanel.Settings &&
                                    focusZone == GameHubFocusZone.SettingsPanel
                            val screensCount = gameHubScreensItemCount(uiState.receivers.size)
                            val consoleModesCount = gameHubConsoleModesItemCount(availableProfiles.size)
                            val gridCount =
                                when {
                                    libraryGridActive -> libraryGridEntries.size
                                    screensListActive -> screensCount
                                    consoleModesListActive -> consoleModesCount
                                    settingsPanelActive -> settingsFocusCount
                                    else -> launcherItems.size
                                }
                            val gridHasTiles = gridCount > 0
                            when {
                                event.gameHubIsAcceptDown() -> {
                                    when (focusZone) {
                                        GameHubFocusZone.LibraryFilters -> {
                                            libraryFilter = GameHubLibraryFilter.entries[libraryFilterIndex]
                                            focusedIndex = 0
                                            if (libraryGridEntries.isNotEmpty()) activateLauncherGrid()
                                            true
                                        }

                                        GameHubFocusZone.ScreensList -> {
                                            if (uiState.receivers.isEmpty()) {
                                                viewModel.startDiscovery()
                                            } else {
                                                uiState.receivers.getOrNull(focusedIndex)?.let(viewModel::connect)
                                            }
                                            true
                                        }

                                        GameHubFocusZone.ConsoleModesList -> {
                                            gameHubConsoleModesFocusKind(focusedIndex, availableProfiles.size)?.let { kind ->
                                                gameHubConsoleModesPerformAccept(
                                                    kind = kind,
                                                    uiState = uiState,
                                                    viewModel = viewModel,
                                                    availableProfiles = availableProfiles,
                                                )
                                            }
                                            true
                                        }

                                        GameHubFocusZone.Grid -> {
                                            if (launcherGridActive) {
                                                launcherItems.getOrNull(focusedIndex)?.let(onTileActivate)
                                            } else if (libraryGridActive) {
                                                when (val entry = libraryGridEntries.getOrNull(focusedIndex)) {
                                                    is GameHubLibraryGridEntry.App ->
                                                        viewModel.launchOnly(entry.item.model)

                                                    is GameHubLibraryGridEntry.AddCandidate ->
                                                        viewModel.addLibraryApp(entry.item.model)

                                                    null -> Unit
                                                }
                                            }
                                            true
                                        }

                                        GameHubFocusZone.Portal -> {
                                            if (portal.primaryEnabled) onPrimaryPortal()
                                            true
                                        }

                                        GameHubFocusZone.SettingsPanel -> {
                                            gameHubSettingsFocusItemAt(settingsRows, focusedIndex)?.let { item ->
                                                gameHubSettingsPerformAccept(
                                                    item = item,
                                                    context = context,
                                                    overlayAllowed = settingsOverlayAllowed,
                                                    onScreenCloakModeSelected = onScreenCloakModeSelected,
                                                )
                                            }
                                            true
                                        }

                                        GameHubFocusZone.TopBar -> false
                                    }
                                }

                                event.key == Key.DirectionDown -> {
                                    when (focusZone) {
                                        GameHubFocusZone.TopBar ->
                                            when {
                                                homePanel == GameHubHomePanel.Launcher -> {
                                                    when (mode) {
                                                        GameHubHomeMode.Portal -> {
                                                            focusZone = GameHubFocusZone.Portal
                                                            portalFocus.requestFocus()
                                                            true
                                                        }

                                                        GameHubHomeMode.Launcher ->
                                                            if (gridHasTiles) {
                                                                focusedIndex = 0
                                                                activateLauncherGrid()
                                                                true
                                                            } else {
                                                                false
                                                            }
                                                    }
                                                }

                                                homePanel == GameHubHomePanel.Library -> {
                                                    activateLibraryFilters()
                                                    true
                                                }

                                                homePanel == GameHubHomePanel.Screens -> {
                                                    focusedIndex = 0
                                                    activateScreensList()
                                                    true
                                                }

                                                homePanel == GameHubHomePanel.ConsoleModes -> {
                                                    focusedIndex =
                                                        gameHubConsoleModesPreferenceStartIndex(availableProfiles.size)
                                                    activateConsoleModesList()
                                                    true
                                                }

                                                homePanel == GameHubHomePanel.Settings -> {
                                                    activateSettingsPanel()
                                                    true
                                                }

                                                else -> false
                                            }

                                        GameHubFocusZone.LibraryFilters ->
                                            if (libraryGridEntries.isNotEmpty()) {
                                                focusedIndex = 0
                                                activateLauncherGrid()
                                                true
                                            } else {
                                                false
                                            }

                                        GameHubFocusZone.ScreensList ->
                                            if (gridHasTiles) {
                                                focusedIndex = gameHubScreensMoveDown(focusedIndex, gridCount)
                                                true
                                            } else {
                                                false
                                            }

                                        GameHubFocusZone.ConsoleModesList ->
                                            if (gridHasTiles) {
                                                focusedIndex = gameHubConsoleModesMoveDown(focusedIndex, gridCount)
                                                true
                                            } else {
                                                false
                                            }

                                        GameHubFocusZone.SettingsPanel ->
                                            if (gridHasTiles) {
                                                focusedIndex = gameHubSettingsMoveDown(focusedIndex, gridCount)
                                                true
                                            } else {
                                                false
                                            }

                                        GameHubFocusZone.Grid ->
                                            if (gridHasTiles) {
                                                focusedIndex =
                                                    when {
                                                        screensListActive ->
                                                            gameHubScreensMoveDown(focusedIndex, gridCount)

                                                        consoleModesListActive ->
                                                            gameHubConsoleModesMoveDown(focusedIndex, gridCount)

                                                        settingsPanelActive ->
                                                            gameHubSettingsMoveDown(focusedIndex, gridCount)

                                                        else ->
                                                            gameHubGridMoveDown(focusedIndex, gridLayout.columns, gridCount)
                                                    }
                                                true
                                            } else {
                                                false
                                            }

                                        GameHubFocusZone.Portal -> false
                                    }
                                }

                                event.key == Key.DirectionUp -> {
                                    when (focusZone) {
                                        GameHubFocusZone.Grid,
                                        GameHubFocusZone.ScreensList,
                                        GameHubFocusZone.ConsoleModesList,
                                        GameHubFocusZone.SettingsPanel,
                                        ->
                                            if (screensListActive) {
                                                if (focusedIndex == 0) {
                                                    focusTopBarButton(GAME_HUB_TOP_BAR_SCREENS)
                                                    true
                                                } else {
                                                    focusedIndex = gameHubScreensMoveUp(focusedIndex, gridCount)
                                                    true
                                                }
                                            } else if (consoleModesListActive) {
                                                val consoleModesTopIndex =
                                                    gameHubConsoleModesPreferenceStartIndex(availableProfiles.size)
                                                if (focusedIndex <= consoleModesTopIndex) {
                                                    focusTopBarButton(GAME_HUB_TOP_BAR_CONSOLE_MODES)
                                                    true
                                                } else {
                                                    focusedIndex = gameHubConsoleModesMoveUp(focusedIndex, gridCount)
                                                    true
                                                }
                                            } else if (settingsPanelActive) {
                                                if (focusedIndex == 0) {
                                                    focusTopBarButton(GAME_HUB_TOP_BAR_SETTINGS)
                                                    true
                                                } else {
                                                    focusedIndex = gameHubSettingsMoveUp(focusedIndex, gridCount)
                                                    true
                                                }
                                            } else if (focusedIndex / gridLayout.columns == 0) {
                                                when (homePanel) {
                                                    GameHubHomePanel.Library -> activateLibraryFilters()
                                                    else -> focusTopBarButton(gameHubTopBarIndexForPanel(homePanel))
                                                }
                                                true
                                            } else if (gridHasTiles) {
                                                focusedIndex =
                                                    gameHubGridMoveUp(focusedIndex, gridLayout.columns, gridCount)
                                                true
                                            } else {
                                                false
                                            }

                                        GameHubFocusZone.LibraryFilters -> {
                                            focusTopBarButton(GAME_HUB_TOP_BAR_LIBRARY)
                                            true
                                        }

                                        GameHubFocusZone.Portal -> {
                                            focusTopBarButton(GAME_HUB_TOP_BAR_HOME)
                                            true
                                        }

                                        GameHubFocusZone.TopBar -> false
                                    }
                                }

                                event.key == Key.DirectionRight -> {
                                    when (focusZone) {
                                        GameHubFocusZone.TopBar -> {
                                            focusTopBarButton((topBarIndex + 1).coerceAtMost(GAME_HUB_TOP_BAR_LAST_INDEX))
                                            true
                                        }

                                        GameHubFocusZone.ConsoleModesList -> {
                                            gameHubConsoleModesFocusKind(focusedIndex, availableProfiles.size)?.let { kind ->
                                                gameHubConsoleModesPerformHorizontal(
                                                    kind = kind,
                                                    forward = true,
                                                    uiState = uiState,
                                                    viewModel = viewModel,
                                                )
                                            }
                                            true
                                        }

                                        GameHubFocusZone.ScreensList -> false

                                        GameHubFocusZone.SettingsPanel -> {
                                            gameHubSettingsFocusItemAt(settingsRows, focusedIndex)?.let { item ->
                                                gameHubSettingsPerformHorizontal(
                                                    item = item,
                                                    forward = true,
                                                    context = context,
                                                    onScreenCloakModeSelected = onScreenCloakModeSelected,
                                                )
                                            }
                                            true
                                        }

                                        GameHubFocusZone.LibraryFilters -> {
                                            val next =
                                                (libraryFilterIndex + 1)
                                                    .coerceAtMost(GameHubLibraryFilter.entries.lastIndex)
                                            libraryFilterIndex = next
                                            libraryFilter = GameHubLibraryFilter.entries[next]
                                            focusedIndex = 0
                                            true
                                        }

                                        GameHubFocusZone.Grid ->
                                            if (gridHasTiles && !screensListActive && !consoleModesListActive && !settingsPanelActive) {
                                                focusedIndex =
                                                    gameHubGridMoveRight(focusedIndex, gridLayout.columns, gridCount)
                                                true
                                            } else {
                                                false
                                            }

                                        else -> false
                                    }
                                }

                                event.key == Key.DirectionLeft -> {
                                    when (focusZone) {
                                        GameHubFocusZone.TopBar -> {
                                            focusTopBarButton((topBarIndex - 1).coerceAtLeast(0))
                                            true
                                        }

                                        GameHubFocusZone.ConsoleModesList -> {
                                            gameHubConsoleModesFocusKind(focusedIndex, availableProfiles.size)?.let { kind ->
                                                gameHubConsoleModesPerformHorizontal(
                                                    kind = kind,
                                                    forward = false,
                                                    uiState = uiState,
                                                    viewModel = viewModel,
                                                )
                                            }
                                            true
                                        }

                                        GameHubFocusZone.ScreensList -> false

                                        GameHubFocusZone.SettingsPanel -> {
                                            gameHubSettingsFocusItemAt(settingsRows, focusedIndex)?.let { item ->
                                                gameHubSettingsPerformHorizontal(
                                                    item = item,
                                                    forward = false,
                                                    context = context,
                                                    onScreenCloakModeSelected = onScreenCloakModeSelected,
                                                )
                                            }
                                            true
                                        }

                                        GameHubFocusZone.LibraryFilters -> {
                                            val next = (libraryFilterIndex - 1).coerceAtLeast(0)
                                            libraryFilterIndex = next
                                            libraryFilter = GameHubLibraryFilter.entries[next]
                                            focusedIndex = 0
                                            true
                                        }

                                        GameHubFocusZone.Grid ->
                                            if (gridHasTiles && !screensListActive && !consoleModesListActive && !settingsPanelActive) {
                                                focusedIndex =
                                                    gameHubGridMoveLeft(focusedIndex, gridLayout.columns, gridCount)
                                                true
                                            } else {
                                                false
                                            }

                                        else -> false
                                    }
                                }

                                else -> false
                            }
                        }
                    },
                verticalArrangement = Arrangement.spacedBy(viewport.sectionGap),
            ) {
                GameHubTopBar(
                    uiState = uiState,
                    mode = mode,
                    portalStatus = portal.statusPill,
                    homeFocus = homeFocus,
                    libraryFocus = libraryFocus,
                    screensFocus = screensFocus,
                    consoleModesFocus = consoleModesFocus,
                    settingsFocus = settingsFocus,
                    iconButtonSize = viewport.settingsCogSize,
                    reducedMotion = reducedMotion,
                    topBarIndex = topBarIndex,
                    focusZone = focusZone,
                    homePanel = homePanel,
                    onHomeFocusChanged = {
                        homeBarFocused = it
                        if (it) {
                            focusZone = GameHubFocusZone.TopBar
                            topBarIndex = GAME_HUB_TOP_BAR_HOME
                        }
                    },
                    onLibraryFocusChanged = {
                        libraryBarFocused = it
                        if (it) {
                            focusZone = GameHubFocusZone.TopBar
                            topBarIndex = GAME_HUB_TOP_BAR_LIBRARY
                        }
                    },
                    onScreensFocusChanged = {
                        screensBarFocused = it
                        if (it) {
                            focusZone = GameHubFocusZone.TopBar
                            topBarIndex = GAME_HUB_TOP_BAR_SCREENS
                        }
                    },
                    onConsoleModesFocusChanged = {
                        consoleModesBarFocused = it
                        if (it) {
                            focusZone = GameHubFocusZone.TopBar
                            topBarIndex = GAME_HUB_TOP_BAR_CONSOLE_MODES
                        }
                    },
                    onSettingsFocusChanged = {
                        settingsBarFocused = it
                        if (it) {
                            focusZone = GameHubFocusZone.TopBar
                            topBarIndex = GAME_HUB_TOP_BAR_SETTINGS
                        }
                    },
                    onGoHome = { selectTopBarTab(GAME_HUB_TOP_BAR_HOME) },
                    onOpenLibrary = { selectTopBarTab(GAME_HUB_TOP_BAR_LIBRARY) },
                    onOpenScreens = { selectTopBarTab(GAME_HUB_TOP_BAR_SCREENS) },
                    onOpenConsoleModes = { selectTopBarTab(GAME_HUB_TOP_BAR_CONSOLE_MODES) },
                    onOpenSettings = { selectTopBarTab(GAME_HUB_TOP_BAR_SETTINGS) },
                )
                Box(
                    modifier =
                    Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                ) {
                    AnimatedContent(
                        targetState = homePanel,
                        transitionSpec = {
                            fadeIn(animationSpec = tween(900, easing = FastOutSlowInEasing)) togetherWith
                                fadeOut(animationSpec = tween(650, easing = FastOutSlowInEasing))
                        },
                        label = "gamehub-panel",
                        modifier = Modifier.fillMaxSize(),
                    ) { activePanel ->
                        when (activePanel) {
                            GameHubHomePanel.Library ->
                                GameHubLibraryStage(
                                    uiState = uiState,
                                    accent = accent,
                                    reducedMotion = reducedMotion,
                                    filter = libraryFilter,
                                    filterFocusIndex = libraryFilterIndex,
                                    filtersFocused = focusZone == GameHubFocusZone.LibraryFilters,
                                    gridInputActive = focusZone == GameHubFocusZone.Grid,
                                    focusedIndex = focusedIndex,
                                    onFilterChange = { libraryFilter = it },
                                    onFocusedIndexChange = { focusedIndex = it },
                                    onRefresh = { viewModel.refreshLibrary(clearIconCache = true) },
                                    onQueryChange = viewModel::updateLibraryQuery,
                                    onToggleFavourite = viewModel::toggleFavourite,
                                    onAddApp = viewModel::addLibraryApp,
                                    onLaunchApp = viewModel::launchOnly,
                                )

                            GameHubHomePanel.Screens ->
                                GameHubScreensStage(
                                    uiState = uiState,
                                    viewModel = viewModel,
                                    focusedIndex = focusedIndex,
                                    listInputActive = focusZone == GameHubFocusZone.ScreensList,
                                    reducedMotion = reducedMotion,
                                    onSearchAgain = viewModel::startDiscovery,
                                )

                            GameHubHomePanel.ConsoleModes ->
                                GameHubConsoleModesStage(
                                    uiState = uiState,
                                    viewModel = viewModel,
                                    availableProfiles = availableProfiles,
                                    focusedIndex = focusedIndex,
                                    listInputActive = focusZone == GameHubFocusZone.ConsoleModesList,
                                    reducedMotion = reducedMotion,
                                )

                            GameHubHomePanel.Settings ->
                                GameHubSettingsStage(
                                    uiState = uiState,
                                    viewModel = viewModel,
                                    focusedIndex = focusedIndex,
                                    listInputActive = focusZone == GameHubFocusZone.SettingsPanel,
                                    reducedMotion = reducedMotion,
                                    onOpenDiagnostics = onOpenDiagnostics,
                                )

                            GameHubHomePanel.Launcher ->
                                when (mode) {
                                    GameHubHomeMode.Portal ->
                                        GameHubPortalStage(
                                            portal = portal,
                                            viewport = viewport,
                                            accent = accent,
                                            reducedMotion = reducedMotion,
                                            portalFocus = portalFocus,
                                            onPrimary = {
                                                if (portal.primaryEnabled) onPrimaryPortal()
                                            },
                                        )

                                    GameHubHomeMode.Launcher ->
                                        GameHubLauncherStage(
                                            items = launcherItems,
                                            libraryApps = uiState.libraryApps,
                                            accent = accent,
                                            reducedMotion = reducedMotion,
                                            focusedIndex = focusedIndex,
                                            gridInputActive = focusZone == GameHubFocusZone.Grid,
                                            onTileActivate = onTileActivate,
                                            onTileLongPress = { tileMenu = GameHubTileMenu.Overflow(it) },
                                        )
                                }
                        }
                    }
                }
                GameHubPrivacyFooter()
            }
            when (val menu = tileMenu) {
                is GameHubTileMenu.Overflow -> {
                    val packageName = gameHubPackageName(menu.item, uiState)
                    val libraryApp = packageName?.let(viewModel::libraryAppForPackage)
                    GameHubTileOverflowMenu(
                        item = menu.item,
                        showFavourite = libraryApp != null && !menu.item.isAzahar,
                        isFavourite = libraryApp?.isFavourite == true,
                        accent = accent,
                        reducedMotion = reducedMotion,
                        onDismiss = { tileMenu = null },
                        onFavourite = {
                            libraryApp?.let(viewModel::toggleFavourite)
                            tileMenu = null
                        },
                        onConsoleMode = { tileMenu = GameHubTileMenu.ProfilePicker(menu.item) },
                    )
                }

                is GameHubTileMenu.ProfilePicker -> {
                    val packageName = gameHubPackageName(menu.item, uiState)
                    GameHubAppProfilePickerSheet(
                        item = menu.item,
                        globalProfile = uiState.performanceSettings.selectedProfile,
                        selectedOverrideId = menu.item.profileOverrideId,
                        availableProfiles = availableProfiles,
                        accent = accent,
                        reducedMotion = reducedMotion,
                        onDismiss = { tileMenu = null },
                        onBack = { tileMenu = GameHubTileMenu.Overflow(menu.item) },
                        onSelectProfile = { profileId ->
                            packageName?.let { viewModel.setAppProfileOverride(it, profileId) }
                            tileMenu = null
                        },
                    )
                }

                null -> Unit
            }
            if (showAzaharModePicker) {
                GameHubAzaharModeDialog(
                    accent = accent,
                    reducedMotion = reducedMotion,
                    onDismiss = { showAzaharModePicker = false },
                    onNormal = {
                        showAzaharModePicker = false
                        if (viewModel.launchAzahar()) {
                            requestConsoleMode()
                        }
                    },
                    onThreeDs = {
                        showAzaharModePicker = false
                        viewModel.launchAzahar3dsMode()
                    },
                )
            }
        }
    }
}

private sealed interface GameHubTileMenu {
    data class Overflow(val item: GameHubLauncherItem) : GameHubTileMenu

    data class ProfilePicker(val item: GameHubLauncherItem) : GameHubTileMenu
}

private fun gameHubPackageName(item: GameHubLauncherItem, uiState: SenderUiState): String? = if (item.isAzahar) {
    uiState.libraryApps.firstOrNull { GameHubHomeMapper.isAzaharPackage(it.model.packageName) }?.model?.packageName
} else {
    item.id
}

@Composable
private fun GameHubGlassOverlay(
    accent: Color,
    reducedMotion: Boolean,
    onDismiss: () -> Unit,
    panelAlignment: Alignment,
    panelWidthFraction: Float,
    panelMaxWidth: Dp,
    panelModifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(24.dp)
    val blockClicks = remember { MutableInteractionSource() }
    Box(
        modifier =
        Modifier
            .fillMaxSize()
            .background(Color(0xA0081018))
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }, onClick = onDismiss),
    ) {
        Box(
            modifier =
            Modifier
                .align(panelAlignment)
                .fillMaxWidth(panelWidthFraction)
                .widthIn(max = panelMaxWidth)
                .then(panelModifier)
                .padding(4.dp)
                .gameHubFocusRing(
                    shape,
                    accent,
                    focused = true,
                    strokeDp = 3.5f,
                    reducedMotion = reducedMotion,
                    cornerRadius = 24.dp,
                    modalGlow = true,
                )
                .clickable(indication = null, interactionSource = blockClicks, onClick = {}),
        ) {
            NoctGlassCard(
                modifier = Modifier.fillMaxWidth().clip(shape),
                content = content,
            )
        }
    }
}

@Composable
private fun GameHubTileOverflowMenu(
    item: GameHubLauncherItem,
    showFavourite: Boolean,
    isFavourite: Boolean,
    accent: Color,
    reducedMotion: Boolean,
    onDismiss: () -> Unit,
    onFavourite: () -> Unit,
    onConsoleMode: () -> Unit,
) {
    GameHubGlassOverlay(
        accent = accent,
        reducedMotion = reducedMotion,
        onDismiss = onDismiss,
        panelAlignment = Alignment.BottomCenter,
        panelWidthFraction = 0.72f,
        panelMaxWidth = 280.dp,
        panelModifier = Modifier.padding(bottom = 28.dp),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (showFavourite) {
                GameHubOverflowMenuRow(
                    label = if (isFavourite) "Remove favourite" else "Add favourite",
                    trailing = if (isFavourite) "★" else "☆",
                    trailingColor = NoctColors.Magenta,
                    onClick = onFavourite,
                )
            }
            GameHubOverflowMenuRow(
                label = "Console mode",
                trailing = "›",
                onClick = onConsoleMode,
            )
        }
    }
}

@Composable
private fun GameHubOverflowMenuRow(label: String, trailing: String, onClick: () -> Unit, trailingColor: Color = NoctColors.TextSecondary) {
    Row(
        modifier =
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = NoctColors.TextPrimary, fontWeight = FontWeight.Medium, style = MaterialTheme.typography.bodyLarge)
        Text(trailing, color = trailingColor, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun GameHubAppProfilePickerSheet(
    item: GameHubLauncherItem,
    globalProfile: StreamProfile,
    selectedOverrideId: String?,
    availableProfiles: List<StreamProfile>,
    accent: Color,
    reducedMotion: Boolean,
    onDismiss: () -> Unit,
    onBack: () -> Unit,
    onSelectProfile: (String?) -> Unit,
) {
    GameHubGlassOverlay(
        accent = accent,
        reducedMotion = reducedMotion,
        onDismiss = onDismiss,
        panelAlignment = Alignment.Center,
        panelWidthFraction = 0.94f,
        panelMaxWidth = 1120.dp,
        panelModifier = Modifier.fillMaxHeight(0.88f),
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val stageWidth = maxWidth
            val profileCardHeight = if (maxHeight < 520.dp) 176.dp else 200.dp
            Column(
                modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    NoctSecondaryButton(text = "Back", onClick = onBack, minHeight = 40.dp)
                    NoctSecondaryButton(text = "Done", onClick = onDismiss, minHeight = 40.dp)
                }
                Text(
                    item.label,
                    color = NoctColors.TextPrimary,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "Console mode for this app only. Global setting stays ${globalProfile.title}.",
                    color = NoctColors.TextSecondary,
                    style = MaterialTheme.typography.bodySmall,
                )
                NoctSelectableCard(
                    selected = selectedOverrideId == null,
                    onClick = { onSelectProfile(null) },
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ConsoleModeOrbBadge(
                            profile = globalProfile,
                            accent = consoleModeAccent(globalProfile),
                            modifier = Modifier.size(44.dp),
                            iconSize = 20.dp,
                        )
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("Default", color = NoctColors.TextPrimary, fontWeight = FontWeight.SemiBold)
                            Text(
                                "Uses global · ${globalProfile.title}",
                                color = NoctColors.TextSecondary,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
                if (stageWidth >= 700.dp && availableProfiles.size >= 2) {
                    availableProfiles.chunked(2).forEach { rowProfiles ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            rowProfiles.forEach { profile ->
                                ConsoleModeProfileCard(
                                    profile = profile,
                                    selected = selectedOverrideId == profile.id,
                                    onSelect = { onSelectProfile(profile.id) },
                                    modifier = Modifier.weight(1f),
                                    cardHeight = profileCardHeight,
                                )
                            }
                            if (rowProfiles.size == 1) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                } else {
                    availableProfiles.forEach { profile ->
                        ConsoleModeProfileCard(
                            profile = profile,
                            selected = selectedOverrideId == profile.id,
                            onSelect = { onSelectProfile(profile.id) },
                            modifier = Modifier.fillMaxWidth(),
                            cardHeight = profileCardHeight,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GameHubAzaharModeDialog(accent: Color, reducedMotion: Boolean, onDismiss: () -> Unit, onNormal: () -> Unit, onThreeDs: () -> Unit) {
    val shape = RoundedCornerShape(24.dp)
    val blockClicks = remember { MutableInteractionSource() }
    Box(
        modifier =
        Modifier
            .fillMaxSize()
            .background(Color(0xA0081018))
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }, onClick = onDismiss),
    ) {
        Box(
            modifier =
            Modifier
                .align(Alignment.Center)
                .fillMaxWidth(0.9f)
                .widthIn(max = 400.dp)
                .padding(4.dp)
                .gameHubFocusRing(
                    shape,
                    accent,
                    focused = true,
                    strokeDp = 3.5f,
                    reducedMotion = reducedMotion,
                    cornerRadius = 24.dp,
                    modalGlow = true,
                )
                .clickable(indication = null, interactionSource = blockClicks, onClick = {}),
        ) {
            NoctGlassCard(
                modifier = Modifier.fillMaxWidth().clip(shape),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 22.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(
                        "NoctDock Azahar",
                        color = NoctColors.TextPrimary,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "Choose how you want to launch.",
                        color = NoctColors.TextSecondary,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    NoctPrimaryButton(
                        text = "Launch in 3DS Mode",
                        onClick = onThreeDs,
                        modifier = Modifier.fillMaxWidth(),
                        minHeight = 48.dp,
                    )
                    NoctSecondaryButton(
                        text = "Launch",
                        onClick = onNormal,
                        modifier = Modifier.fillMaxWidth(),
                        minHeight = 48.dp,
                    )
                }
            }
        }
    }
}

@Composable
private fun GameHubPrivacyFooter(modifier: Modifier = Modifier) {
    val accent = LocalNoctAccent.current
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Surface(
            color = Color(0x55101824),
            shape = RoundedCornerShape(50),
            border = androidx.compose.foundation.BorderStroke(1.dp, NoctColors.GlassBorder.copy(alpha = 0.65f)),
        ) {
            Text(
                "Local network only  ·  No accounts  ·  No cloud",
                color = NoctColors.TextSecondary.copy(alpha = 0.9f),
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun GameHubTopBar(
    uiState: SenderUiState,
    mode: GameHubHomeMode,
    portalStatus: String,
    homeFocus: FocusRequester,
    libraryFocus: FocusRequester,
    screensFocus: FocusRequester,
    consoleModesFocus: FocusRequester,
    settingsFocus: FocusRequester,
    iconButtonSize: Dp,
    reducedMotion: Boolean,
    topBarIndex: Int,
    focusZone: GameHubFocusZone,
    homePanel: GameHubHomePanel,
    onHomeFocusChanged: (Boolean) -> Unit,
    onLibraryFocusChanged: (Boolean) -> Unit,
    onScreensFocusChanged: (Boolean) -> Unit,
    onConsoleModesFocusChanged: (Boolean) -> Unit,
    onSettingsFocusChanged: (Boolean) -> Unit,
    onGoHome: () -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenScreens: () -> Unit,
    onOpenConsoleModes: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val topBarFocused = focusZone == GameHubFocusZone.TopBar
    val accent = LocalNoctAccent.current
    val pillText =
        when (mode) {
            GameHubHomeMode.Launcher ->
                uiState.defaultReceiver?.let(GameHubHomeMapper::screenReadyPill)
                    ?: portalStatus

            GameHubHomeMode.Portal -> portalStatus
        }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GameHubReceiverPill(text = pillText, accent = accent)
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GameHubHomeButton(
                onClick = onGoHome,
                buttonSize = iconButtonSize,
                reducedMotion = reducedMotion,
                selected =
                gameHubTopBarTabSelected(
                    GAME_HUB_TOP_BAR_HOME,
                    topBarFocused,
                    topBarIndex,
                    homePanel,
                ),
                modifier =
                Modifier
                    .focusRequester(homeFocus)
                    .focusable(topBarFocused),
                onFocusChanged = onHomeFocusChanged,
            )
            GameHubLibraryButton(
                onClick = onOpenLibrary,
                buttonSize = iconButtonSize,
                reducedMotion = reducedMotion,
                selected =
                gameHubTopBarTabSelected(
                    GAME_HUB_TOP_BAR_LIBRARY,
                    topBarFocused,
                    topBarIndex,
                    homePanel,
                ),
                modifier =
                Modifier
                    .focusRequester(libraryFocus)
                    .focusable(topBarFocused),
                onFocusChanged = onLibraryFocusChanged,
            )
            GameHubScreensButton(
                onClick = onOpenScreens,
                buttonSize = iconButtonSize,
                reducedMotion = reducedMotion,
                selected =
                gameHubTopBarTabSelected(
                    GAME_HUB_TOP_BAR_SCREENS,
                    topBarFocused,
                    topBarIndex,
                    homePanel,
                ),
                modifier =
                Modifier
                    .focusRequester(screensFocus)
                    .focusable(topBarFocused),
                onFocusChanged = onScreensFocusChanged,
            )
            GameHubConsoleModesButton(
                onClick = onOpenConsoleModes,
                buttonSize = iconButtonSize,
                reducedMotion = reducedMotion,
                selected =
                gameHubTopBarTabSelected(
                    GAME_HUB_TOP_BAR_CONSOLE_MODES,
                    topBarFocused,
                    topBarIndex,
                    homePanel,
                ),
                modifier =
                Modifier
                    .focusRequester(consoleModesFocus)
                    .focusable(topBarFocused),
                onFocusChanged = onConsoleModesFocusChanged,
            )
            GameHubSettingsCog(
                onClick = onOpenSettings,
                cogSize = iconButtonSize,
                reducedMotion = reducedMotion,
                selected =
                gameHubTopBarTabSelected(
                    GAME_HUB_TOP_BAR_SETTINGS,
                    topBarFocused,
                    topBarIndex,
                    homePanel,
                ),
                modifier =
                Modifier
                    .focusRequester(settingsFocus)
                    .focusable(topBarFocused),
                onFocusChanged = onSettingsFocusChanged,
            )
        }
    }
}

@Composable
private fun GameHubReceiverPill(text: String, accent: Color, modifier: Modifier = Modifier) {
    val gradientPhase = LocalGameHubGradientPhase.current
    val shape = RoundedCornerShape(50)
    val fillColors =
        remember(accent) {
            gameHubClosedColorLoop(
                gameHubSmoothSweepColors(accent).map { it.copy(alpha = (it.alpha * 0.38f).coerceIn(0.16f, 0.48f)) },
            )
        }
    Surface(
        modifier = modifier,
        color = Color.Transparent,
        contentColor = NoctColors.TextPrimary,
        border = androidx.compose.foundation.BorderStroke(1.5.dp, accent.copy(alpha = 0.42f)),
        shape = shape,
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier =
            Modifier
                .drawBehind {
                    val pillCorner = CornerRadius(size.height / 2f, size.height / 2f)
                    drawRoundRect(color = Color(0x77101828), cornerRadius = pillCorner)
                    drawRoundRect(
                        brush = gameHubFlowingLinearBrush(fillColors, size, gradientPhase),
                        cornerRadius = pillCorner,
                    )
                }
                .padding(horizontal = 14.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier =
                Modifier
                    .size(12.dp)
                    .drawBehind {
                        drawCircle(
                            brush =
                            Brush.radialGradient(
                                colors = listOf(accent.copy(alpha = 0.9f), Color.Transparent),
                                center = center,
                                radius = size.minDimension * 1.4f,
                            ),
                        )
                        drawCircle(color = accent, radius = size.minDimension * 0.38f, center = center)
                    },
            )
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun gameHubTopBarIconColor(ringActive: Boolean): Color = NoctColors.TextPrimary.copy(alpha = if (ringActive) 1f else 0.9f)

private fun gameHubTopBarIconStroke(): Float = 2.9f

private fun gameHubTopBarRingStroke(selected: Boolean): Float = if (selected) 4f else 3f

@Composable
private fun GameHubHomeButton(
    onClick: () -> Unit,
    buttonSize: Dp = 46.dp,
    reducedMotion: Boolean = false,
    selected: Boolean = false,
    modifier: Modifier = Modifier,
    onFocusChanged: (Boolean) -> Unit = {},
) {
    val accent = LocalNoctAccent.current
    val ringActive = selected
    val scale by animateFloatAsState(
        if (ringActive) 1.06f else 1f,
        animationSpec = tween(280, easing = FastOutSlowInEasing),
        label = "home-scale",
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
                strokeDp = gameHubTopBarRingStroke(selected),
                idleBorderDp = 2.dp,
                reducedMotion = reducedMotion,
            )
            .gameHubActivateOnAccept(onClick)
            .clickable(role = Role.Button, onClick = onClick)
            .onFocusChanged { onFocusChanged(it.isFocused) }
            .semantics {
                role = Role.Button
                contentDescription = "Home"
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(iconSize)) {
            val color = gameHubTopBarIconColor(ringActive)
            val strokePx = gameHubTopBarIconStroke().dp.toPx()
            val cap = androidx.compose.ui.graphics.StrokeCap.Round
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
private fun GameHubLibraryButton(
    onClick: () -> Unit,
    buttonSize: Dp = 46.dp,
    reducedMotion: Boolean = false,
    selected: Boolean = false,
    modifier: Modifier = Modifier,
    onFocusChanged: (Boolean) -> Unit = {},
) {
    val accent = LocalNoctAccent.current
    val ringActive = selected
    val scale by animateFloatAsState(
        if (ringActive) 1.06f else 1f,
        animationSpec = tween(280, easing = FastOutSlowInEasing),
        label = "library-scale",
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
                strokeDp = gameHubTopBarRingStroke(selected),
                idleBorderDp = 2.dp,
                reducedMotion = reducedMotion,
            )
            .gameHubActivateOnAccept(onClick)
            .clickable(role = Role.Button, onClick = onClick)
            .onFocusChanged { onFocusChanged(it.isFocused) }
            .semantics {
                role = Role.Button
                contentDescription = "Library"
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(iconSize)) {
            val stroke = Stroke(width = gameHubTopBarIconStroke().dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
            val inset = size.minDimension * 0.14f
            val gap = size.minDimension * 0.09f
            val cell = (size.minDimension - inset * 2f - gap) / 2f
            val grid = cell * 2f + gap
            val originX = (size.width - grid) / 2f
            val originY = (size.height - grid) / 2f
            val color = gameHubTopBarIconColor(ringActive)
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
private fun GameHubScreensButton(
    onClick: () -> Unit,
    buttonSize: Dp = 46.dp,
    reducedMotion: Boolean = false,
    selected: Boolean = false,
    modifier: Modifier = Modifier,
    onFocusChanged: (Boolean) -> Unit = {},
) {
    val accent = LocalNoctAccent.current
    val ringActive = selected
    val scale by animateFloatAsState(
        if (ringActive) 1.06f else 1f,
        animationSpec = tween(280, easing = FastOutSlowInEasing),
        label = "screens-scale",
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
                strokeDp = gameHubTopBarRingStroke(selected),
                idleBorderDp = 2.dp,
                reducedMotion = reducedMotion,
            )
            .gameHubActivateOnAccept(onClick)
            .clickable(role = Role.Button, onClick = onClick)
            .onFocusChanged { onFocusChanged(it.isFocused) }
            .semantics {
                role = Role.Button
                contentDescription = "Screens"
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(iconSize)) {
            val strokePx = gameHubTopBarIconStroke().dp.toPx()
            val stroke = Stroke(width = strokePx, cap = androidx.compose.ui.graphics.StrokeCap.Round)
            val color = gameHubTopBarIconColor(ringActive)
            val cap = androidx.compose.ui.graphics.StrokeCap.Round
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
private fun GameHubConsoleModesButton(
    onClick: () -> Unit,
    buttonSize: Dp = 46.dp,
    reducedMotion: Boolean = false,
    selected: Boolean = false,
    modifier: Modifier = Modifier,
    onFocusChanged: (Boolean) -> Unit = {},
) {
    val accent = LocalNoctAccent.current
    val ringActive = selected
    val scale by animateFloatAsState(
        if (ringActive) 1.06f else 1f,
        animationSpec = tween(280, easing = FastOutSlowInEasing),
        label = "modes-scale",
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
                strokeDp = gameHubTopBarRingStroke(selected),
                idleBorderDp = 2.dp,
                reducedMotion = reducedMotion,
            )
            .gameHubActivateOnAccept(onClick)
            .clickable(role = Role.Button, onClick = onClick)
            .onFocusChanged { onFocusChanged(it.isFocused) }
            .semantics {
                role = Role.Button
                contentDescription = "Console Modes"
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(iconSize)) {
            val stroke = Stroke(width = gameHubTopBarIconStroke().dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
            val color = gameHubTopBarIconColor(ringActive)
            val outerR = size.minDimension * 0.38f
            val innerR = size.minDimension * 0.22f
            drawCircle(color = color, radius = outerR, center = center, style = stroke)
            drawCircle(color = color.copy(alpha = 0.88f), radius = innerR, center = center, style = stroke)
            drawCircle(color = color, radius = size.minDimension * 0.07f, center = center)
        }
    }
}

@Composable
private fun GameHubSettingsCog(
    onClick: () -> Unit,
    cogSize: Dp = 46.dp,
    reducedMotion: Boolean = false,
    selected: Boolean = false,
    modifier: Modifier = Modifier,
    onFocusChanged: (Boolean) -> Unit = {},
) {
    val accent = LocalNoctAccent.current
    val ringActive = selected
    val scale by animateFloatAsState(
        if (ringActive) 1.06f else 1f,
        animationSpec = tween(280, easing = FastOutSlowInEasing),
        label = "cog-scale",
    )
    val iconSize = cogSize * 0.54f
    Box(
        modifier =
        modifier
            .scale(scale)
            .size(cogSize)
            .clip(CircleShape)
            .gameHubFocusRing(
                CircleShape,
                accent,
                focused = ringActive,
                strokeDp = gameHubTopBarRingStroke(selected),
                idleBorderDp = 2.dp,
                reducedMotion = reducedMotion,
            )
            .gameHubActivateOnAccept(onClick)
            .clickable(role = Role.Button, onClick = onClick)
            .onFocusChanged { onFocusChanged(it.isFocused) }
            .semantics {
                role = Role.Button
                contentDescription = "Settings"
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(iconSize)) {
            val iconColor = gameHubTopBarIconColor(ringActive)
            val stroke = Stroke(width = gameHubTopBarIconStroke().dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
            val c = center
            val r = size.minDimension * 0.34f
            drawCircle(color = iconColor.copy(alpha = if (ringActive) 0.22f else 0.12f), radius = r * 1.35f, center = c)
            drawCircle(color = iconColor, radius = r, center = c, style = stroke)
            val toothStroke = gameHubTopBarIconStroke().dp.toPx()
            val cap = androidx.compose.ui.graphics.StrokeCap.Round
            for (i in 0 until 8) {
                val angle = (i * 45f) * (Math.PI / 180f).toFloat()
                val inner = Offset(c.x + kotlin.math.cos(angle) * r * 0.55f, c.y + kotlin.math.sin(angle) * r * 0.55f)
                val outer = Offset(c.x + kotlin.math.cos(angle) * r * 0.92f, c.y + kotlin.math.sin(angle) * r * 0.92f)
                drawLine(color = iconColor, start = inner, end = outer, strokeWidth = toothStroke, cap = cap)
            }
        }
    }
}

@Composable
private fun GameHubPortalStage(portal: GameHubPortalPresentation, viewport: GameHubViewport, accent: Color, reducedMotion: Boolean, portalFocus: FocusRequester, onPrimary: () -> Unit) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val stageHeight = this.maxHeight
        val fit = GameHubViewport.portalFit(stageHeight)
        val buttonMinHeight = (64.dp * fit).coerceAtLeast(52.dp)
        val portalShape = RoundedCornerShape(22.dp)
        val portalGlowAlpha by animateFloatAsState(0.88f, animationSpec = tween(400), label = "portal-glow")
        val cardPadV = (24.dp * fit).coerceAtLeast(14.dp)
        val blockGap = (14.dp * fit).coerceAtLeast(8.dp)
        val textGap = (7.dp * fit).coerceAtLeast(4.dp)
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = if (fit < 0.98f) Alignment.TopCenter else Alignment.Center,
        ) {
            Box(
                modifier =
                Modifier
                    .padding(top = if (fit < 0.98f) 8.dp else 0.dp)
                    .widthIn(max = viewport.portalCardMaxWidth)
                    .fillMaxWidth(viewport.portalCardWidthFraction)
                    .padding(4.dp)
                    .gameHubFocusRing(
                        portalShape,
                        accent,
                        focused = true,
                        strokeDp = 3.5f,
                        reducedMotion = reducedMotion,
                        cornerRadius = 22.dp,
                        modalGlow = true,
                    ),
            ) {
                NoctGlassCard(
                    modifier = Modifier.fillMaxWidth().clip(portalShape),
                    contentPadding = PaddingValues(horizontal = 28.dp * fit, vertical = cardPadV),
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(blockGap),
                        modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp)
                            .drawBehind {
                                drawRoundRect(
                                    brush =
                                    Brush.radialGradient(
                                        colors =
                                        listOf(
                                            accent.copy(alpha = portalGlowAlpha * 0.58f),
                                            NoctColors.Magenta.copy(alpha = portalGlowAlpha * 0.36f),
                                            NoctColors.Cyan.copy(alpha = portalGlowAlpha * 0.14f),
                                            Color(0x66101820),
                                        ),
                                        center = Offset(size.width * 0.5f, size.height * 0.38f),
                                        radius = size.maxDimension * 0.95f,
                                    ),
                                    cornerRadius = CornerRadius(20.dp.toPx()),
                                )
                            },
                    ) {
                        GameHubDockOrb(accent = accent, reducedMotion = reducedMotion, large = true, sizeScale = fit)
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(textGap),
                        ) {
                            Text(
                                portal.title,
                                color = NoctColors.TextPrimary,
                                style = MaterialTheme.typography.headlineMedium,
                                textAlign = TextAlign.Center,
                            )
                            Text(
                                portal.subtitle,
                                color = NoctColors.TextSecondary,
                                style = MaterialTheme.typography.bodyLarge,
                                textAlign = TextAlign.Center,
                            )
                            Text(
                                portal.stateText,
                                color = accent.copy(alpha = 0.92f),
                                fontWeight = FontWeight.Medium,
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center,
                            )
                        }
                        NoctPrimaryButton(
                            text = portal.primaryLabel,
                            onClick = { if (portal.primaryEnabled) onPrimary() },
                            modifier =
                            Modifier
                                .fillMaxWidth()
                                .alpha(if (portal.primaryEnabled) 1f else 0.42f)
                                .focusRequester(portalFocus)
                                .focusable(enabled = portal.primaryEnabled),
                            minHeight = buttonMinHeight,
                        )
                    }
                }
            }
        }
    }
}

internal fun gameHubGridMoveDown(index: Int, columns: Int, count: Int): Int {
    val row = index / columns
    val col = index % columns
    val nextRow = row + 1
    val maxRow = (count - 1) / columns
    if (nextRow > maxRow) return index
    val target = nextRow * columns + col
    return if (target < count) target else index
}

internal fun gameHubGridMoveUp(index: Int, columns: Int, count: Int): Int {
    val row = index / columns
    val col = index % columns
    if (row == 0) return index
    return (row - 1) * columns + col
}

internal fun gameHubGridMoveRight(index: Int, columns: Int, count: Int): Int {
    val col = index % columns
    if (col >= columns - 1 || index + 1 >= count) return index
    return index + 1
}

internal fun gameHubGridMoveLeft(index: Int, columns: Int, count: Int): Int {
    val col = index % columns
    if (col <= 0) return index
    return index - 1
}

@Composable
private fun GameHubLauncherStage(
    items: List<GameHubLauncherItem>,
    libraryApps: List<LibraryAppItem>,
    accent: Color,
    reducedMotion: Boolean,
    focusedIndex: Int,
    gridInputActive: Boolean,
    onTileActivate: (GameHubLauncherItem) -> Unit,
    onTileLongPress: (GameHubLauncherItem) -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val layout = GameHubViewport.launcherGridLayout(maxWidth, maxHeight, items.size.coerceAtLeast(1))
        val gridFocused = gridInputActive && focusedIndex in items.indices
        Column(
            modifier =
            Modifier
                .fillMaxSize()
                .padding(horizontal = layout.contentInsetH),
            verticalArrangement = Arrangement.Top,
        ) {
            if (items.isEmpty()) {
                NoctGlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Nothing in your library yet", color = NoctColors.TextPrimary, fontWeight = FontWeight.SemiBold)
                        Text(
                            "Add games and emulators in Settings → Library.",
                            color = NoctColors.TextSecondary,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(layout.gap),
                    horizontalAlignment = Alignment.Start,
                ) {
                    for (row in 0 until layout.rows) {
                        Row(
                            modifier = Modifier.fillMaxWidth().height(layout.tileHeight),
                            horizontalArrangement = Arrangement.spacedBy(layout.gap),
                            verticalAlignment = Alignment.Top,
                        ) {
                            for (col in 0 until layout.columns) {
                                val index = row * layout.columns + col
                                if (index < items.size) {
                                    val item = items[index]
                                    val libraryEntry =
                                        if (item.isAzahar) {
                                            libraryApps.firstOrNull { GameHubHomeMapper.isAzaharPackage(it.model.packageName) }
                                        } else {
                                            libraryApps.firstOrNull { it.model.packageName == item.id }
                                        }
                                    GameHubLauncherTile(
                                        item = item,
                                        iconPackageName = libraryEntry?.model?.packageName,
                                        icon = libraryEntry?.icon,
                                        tileWidth = layout.tileWidth,
                                        tileHeight = layout.tileHeight,
                                        accent = accent,
                                        reducedMotion = reducedMotion,
                                        focused = gridInputActive && index == focusedIndex,
                                        rowHasFocus = gridFocused,
                                        onClick = { onTileActivate(item) },
                                        onLongPress = { onTileLongPress(item) },
                                    )
                                } else {
                                    Spacer(modifier = Modifier.size(layout.tileWidth, layout.tileHeight))
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
private fun GameHubLauncherIcon(packageName: String, icon: android.graphics.drawable.Drawable, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val bitmap = SenderAppIconCache.bitmapFor(context, packageName, icon)
    Image(
        bitmap = bitmap,
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = modifier,
    )
}

@Composable
internal fun GameHubLauncherTile(
    item: GameHubLauncherItem,
    iconPackageName: String?,
    icon: android.graphics.drawable.Drawable?,
    tileWidth: Dp,
    tileHeight: Dp,
    accent: Color,
    reducedMotion: Boolean,
    focused: Boolean,
    rowHasFocus: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val tileInteraction = remember { MutableInteractionSource() }
    // Focus shown via ring/glow only — no size pop (avoids overlapping neighbours).
    val scale = 1f
    val dimAlpha =
        when {
            focused -> 1f
            rowHasFocus -> 0.52f
            else -> 0.82f
        }
    val glowAlpha by animateFloatAsState(if (focused) 0.72f else 0.14f, animationSpec = tween(400), label = "tile-glow")
    val shape = RoundedCornerShape(20.dp)
    val iconSize = minOf(tileWidth, tileHeight) * 0.46f
    val pad = 8.dp
    Box(
        modifier =
        modifier
            .width(tileWidth)
            .height(tileHeight)
            .scale(scale)
            .clip(shape)
            .gameHubFocusRing(
                shape,
                accent,
                focused = focused,
                strokeDp = if (focused) 3.5f else 1f,
                reducedMotion = reducedMotion,
            )
            .combinedClickable(
                interactionSource = tileInteraction,
                indication = null,
                role = Role.Button,
                onClick = onClick,
                onLongClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongPress()
                },
            ),
    ) {
        Box(
            modifier =
            Modifier
                .matchParentSize()
                .padding(2.dp)
                .clip(shape)
                .alpha(dimAlpha),
        ) {
            androidx.compose.foundation.Canvas(modifier = Modifier.matchParentSize()) {
                drawRoundRect(
                    brush =
                    Brush.radialGradient(
                        colors =
                        listOf(
                            accent.copy(alpha = glowAlpha * 0.42f),
                            NoctColors.Magenta.copy(alpha = glowAlpha * 0.24f),
                            Color(0x88101820),
                        ),
                        center = Offset(size.width * 0.5f, size.height * 0.38f),
                        radius = size.maxDimension * 0.95f,
                    ),
                    cornerRadius = CornerRadius(18.dp.toPx()),
                )
            }
            Box(
                modifier = Modifier.fillMaxSize().padding(pad),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(iconSize)) {
                        if (icon != null && iconPackageName != null) {
                            GameHubLauncherIcon(
                                packageName = iconPackageName,
                                icon = icon,
                                modifier = Modifier.size(iconSize * 0.92f),
                            )
                        } else {
                            NoctOrb(modifier = Modifier.fillMaxSize(), color = accent, reducedMotion = reducedMotion)
                        }
                    }
                    Text(
                        item.label,
                        color = NoctColors.TextPrimary,
                        fontWeight = if (focused) FontWeight.SemiBold else FontWeight.Medium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelMedium,
                        lineHeight = MaterialTheme.typography.labelMedium.lineHeight * 0.92f,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            item.profileOverrideId?.let { overrideId ->
                val profileTitle = StreamProfiles.all.firstOrNull { it.id == overrideId }?.title
                if (profileTitle != null) {
                    Text(
                        profileTitle,
                        color = accent.copy(alpha = 0.88f),
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier =
                        Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 4.dp),
                    )
                }
            }
        }
        if (item.isFavourite) {
            GameHubFavouriteStar(
                accent = accent,
                modifier =
                Modifier
                    .zIndex(1f)
                    .align(Alignment.TopEnd)
                    .padding(top = 7.dp, end = 7.dp)
                    .graphicsLayer { alpha = 1f },
            )
        }
    }
}

internal val LocalGameHubGradientPhase = compositionLocalOf { 0f }

/** One infinite transition for hub gradients; only composables that read [LocalGameHubGradientPhase] recompose each frame. */
@Composable
internal fun GameHubGradientPhaseProvider(reducedMotion: Boolean, content: @Composable () -> Unit) {
    val phase = rememberGameHubGradientPhase(reducedMotion)
    CompositionLocalProvider(LocalGameHubGradientPhase provides phase, content = content)
}

@Composable
internal fun rememberGameHubGradientPhase(reducedMotion: Boolean): Float {
    if (reducedMotion) return 0f
    val transition = rememberInfiniteTransition(label = "gamehub-gradient-phase")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(animation = tween(4000, easing = LinearEasing)),
        label = "gamehub-gradient-phase-value",
    )
    return phase - phase.toInt()
}

@Composable
internal fun GameHubFavouriteStar(accent: Color, modifier: Modifier = Modifier) {
    val gradientPhase = LocalGameHubGradientPhase.current
    val starColors =
        remember(accent) {
            listOf(
                accent,
                NoctColors.Magenta,
                NoctColors.Violet,
                NoctColors.Cyan,
                accent,
            )
        }
    val starPath = remember { Path() }
    Canvas(
        modifier =
        modifier
            .size(22.dp)
            .graphicsLayer { alpha = 1f },
    ) {
        val outerRadius = size.minDimension * 0.44f
        starPath.reset()
        appendGameHubStarPath(starPath, center, outerRadius)
        drawPath(starPath, brush = gameHubRotatingSweepBrush(starColors, center, gradientPhase))
    }
}

private fun appendGameHubStarPath(path: Path, center: Offset, outerRadius: Float) {
    val innerRadius = outerRadius * 0.45f
    val step = (Math.PI / 5.0).toFloat()
    var angle = (-Math.PI / 2.0).toFloat()
    for (i in 0 until 10) {
        val radius = if (i % 2 == 0) outerRadius else innerRadius
        val x = center.x + radius * kotlin.math.cos(angle)
        val y = center.y + radius * kotlin.math.sin(angle)
        if (i == 0) {
            path.moveTo(x, y)
        } else {
            path.lineTo(x, y)
        }
        angle += step
    }
    path.close()
}

@Composable
internal fun Modifier.gameHubFocusRing(
    shape: Shape,
    accent: Color,
    focused: Boolean,
    strokeDp: Float,
    reducedMotion: Boolean = false,
    cornerRadius: Dp = 20.dp,
    idleBorderDp: Dp = 1.dp,
    modalGlow: Boolean = false,
): Modifier {
    if (!focused) {
        return border(idleBorderDp, NoctColors.GlassBorder.copy(alpha = if (shape === CircleShape) 0.75f else 0.55f), shape)
    }
    val ringColors =
        remember(accent, modalGlow) {
            if (modalGlow) {
                gameHubSmoothSweepColors(accent)
            } else {
                listOf(
                    accent.copy(alpha = 0.95f),
                    NoctColors.Magenta.copy(alpha = 0.92f),
                    NoctColors.Violet.copy(alpha = 0.88f),
                    accent.copy(alpha = 0.78f),
                    accent.copy(alpha = 0.95f),
                )
            }
        }
    val gradientPhase =
        when {
            reducedMotion || !focused -> 0f
            else -> LocalGameHubGradientPhase.current
        }
    return drawBehind {
        val stroke = strokeDp.dp.toPx()
        val brightRingColors =
            if (modalGlow) {
                ringColors.map { it.copy(alpha = (it.alpha * 1.2f).coerceIn(0.9f, 1f)) }
            } else {
                ringColors
            }
        val brush = gameHubRotatingSweepBrush(brightRingColors, center, gradientPhase)
        if (modalGlow) {
            val haloBrush =
                gameHubRotatingSweepBrush(
                    brightRingColors.map { it.copy(alpha = (it.alpha * 0.72f).coerceIn(0.5f, 0.9f)) },
                    center,
                    gradientPhase,
                )
            drawGameHubGradientRing(
                shape = shape,
                brush = haloBrush,
                strokeWidth = stroke * 1.35f,
                cornerRadius = cornerRadius,
                outside = true,
            )
        }
        drawGameHubGradientRing(
            shape = shape,
            brush = brush,
            strokeWidth = stroke,
            cornerRadius = cornerRadius,
        )
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawGameHubGradientRing(shape: Shape, brush: Brush, strokeWidth: Float, cornerRadius: Dp, outside: Boolean = false) {
    val inset = if (outside) -strokeWidth / 2f else strokeWidth / 2f
    if (shape === CircleShape) {
        drawCircle(
            brush = brush,
            radius = size.minDimension / 2f - inset,
            center = center,
            style = Stroke(width = strokeWidth),
        )
    } else {
        val corner = cornerRadius.toPx()
        drawRoundRect(
            brush = brush,
            topLeft = Offset(inset, inset),
            size = Size(size.width - inset * 2f, size.height - inset * 2f),
            cornerRadius = CornerRadius(corner, corner),
            style = Stroke(width = strokeWidth),
        )
    }
}

/** Scrolling pill fill — [TileMode.REPEAT] + closed colors so phase 0 and 1 match. */
private fun gameHubFlowingLinearBrush(colors: List<Color>, size: Size, phase: Float): ShaderBrush {
    val loop = gameHubClosedColorLoop(colors)
    val n = loop.size
    if (n == 0) return ShaderBrush(LinearGradient(0f, 0f, 1f, 0f, intArrayOf(0), null, TileMode.CLAMP))
    val intColors = IntArray(n) { loop[it].toArgb() }
    val positions = FloatArray(n) { i -> i.toFloat() / (n - 1).coerceAtLeast(1) }
    val tileWidth = size.width.coerceAtLeast(1f)
    val midY = size.height * 0.5f
    val scroll = (phase - phase.toInt()) * tileWidth
    val shader =
        LinearGradient(0f, midY, tileWidth, midY, intColors, positions, TileMode.REPEAT).apply {
            Matrix().also { matrix ->
                matrix.setTranslate(-scroll, 0f)
                setLocalMatrix(matrix)
            }
        }
    return ShaderBrush(shader)
}

private fun gameHubClosedColorLoop(colors: List<Color>): List<Color> {
    if (colors.isEmpty() || colors.size == 1) return colors
    return if (colors.first() == colors.last()) colors else colors + colors.first()
}

/** Many small lerps around the wheel — no harsh bands when used for modal glow. */
private fun gameHubSmoothSweepColors(accent: Color, stepsPerSegment: Int = 6): List<Color> {
    val hues = listOf(accent, NoctColors.Cyan, NoctColors.Magenta, NoctColors.Violet)
    val out = ArrayList<Color>(hues.size * stepsPerSegment)
    for (i in hues.indices) {
        val from = hues[i]
        val to = hues[(i + 1) % hues.size]
        for (s in 0 until stepsPerSegment) {
            out.add(lerp(from, to, s / stepsPerSegment.toFloat()))
        }
    }
    if (out.isNotEmpty() && out.first() != out.last()) {
        out[out.lastIndex] = out.first()
    }
    return out
}

/** Rotates sweep-gradient angle only — outline stays fixed; loop is seamless at 0°/360°. */
private fun gameHubRotatingSweepBrush(colors: List<Color>, center: Offset, phase: Float): ShaderBrush {
    val (intColors, positions) = gameHubSeamlessSweepStops(colors)
    val shader =
        SweepGradient(center.x, center.y, intColors, positions).apply {
            Matrix().also { matrix ->
                matrix.setRotate(phase * 360f, center.x, center.y)
                setLocalMatrix(matrix)
            }
        }
    return ShaderBrush(shader)
}

private fun gameHubSeamlessSweepStops(colors: List<Color>): Pair<IntArray, FloatArray> {
    if (colors.isEmpty()) return IntArray(0) to FloatArray(0)
    val loop =
        if (colors.size == 1 || colors.first() == colors.last()) {
            colors
        } else {
            colors + colors.first()
        }
    val n = loop.size
    return IntArray(n) { loop[it].toArgb() } to FloatArray(n) { i -> i.toFloat() / (n - 1).coerceAtLeast(1) }
}

@Composable
private fun GameHubDockOrb(accent: Color, reducedMotion: Boolean, large: Boolean = false, sizeScale: Float = 1f) {
    NoctDockHeroOrb(
        accent = accent,
        reducedMotion = reducedMotion,
        large = large,
        sizeScale = sizeScale,
    )
}
