package com.glowseed.noctdock.sender

import android.graphics.drawable.ColorDrawable
import androidx.compose.ui.unit.dp
import com.glowseed.noctdock.core.ConsoleModeState
import com.glowseed.noctdock.core.DiscoveredReceiver
import com.glowseed.noctdock.core.LocalLibraryApp
import com.glowseed.noctdock.core.PairingState
import com.glowseed.noctdock.core.ProtocolVersion
import com.glowseed.noctdock.core.ReceiverFormFactor
import com.glowseed.noctdock.core.ReceiverIdentity
import com.glowseed.noctdock.core.TrustedReceiver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GameHubHomeMapperTest {
    private val receiver =
        DiscoveredReceiver(
            identity = ReceiverIdentity("tv-1", "key"),
            deviceName = "Living Room",
            serviceName = "Living Room TV",
            hostAddress = "192.168.1.20",
            port = 45454,
            protocolVersion = ProtocolVersion.Current,
            receiverAppVersion = "0.1.0",
            supportedCodecs = emptyList(),
            supportedMaxResolution = "1920x1080",
            formFactor = ReceiverFormFactor.TV,
            pairingRequired = true,
        )

    @Test
    fun portalShowsPairNotConsoleModeWhenScreenFoundButNotTrusted() {
        val ui =
            baseState().copy(
                receivers = listOf(receiver),
                selectedReceiver = receiver,
                trustedReceiver =
                TrustedReceiver(receiver.identity, receiver.displayName, receiver.hostAddress, receiver.port, 1L),
                pairingState = PairingState.NotRequired,
            )
        assertEquals(GameHubHomeMode.Portal, GameHubHomeMapper.resolveMode(ui))
        val portal = GameHubHomeMapper.portalPresentation(ui)
        assertEquals("Pair", portal.primaryLabel)
        assertEquals(GameHubTileActionKind.Pair, portal.primaryAction)
        assertFalse(GameHubHomeMapper.isScreenTrusted(ui, receiver))
    }

    @Test
    fun noReceiverShowsPortalMode() {
        val ui = SenderUiState()
        assertEquals(GameHubHomeMode.Portal, GameHubHomeMapper.resolveMode(ui))
        assertEquals("Looking for a screen…", GameHubHomeMapper.portalPresentation(ui).stateText)
    }

    @Test
    fun trustedReceiverShowsLauncher() {
        val ui =
            baseState().copy(
                receivers = listOf(receiver),
                selectedReceiver = receiver,
                trustedReceiver =
                TrustedReceiver(
                    identity = receiver.identity,
                    displayName = receiver.displayName,
                    lastHostAddress = receiver.hostAddress,
                    port = receiver.port,
                    trustedAtMillis = 1L,
                ),
                pairingState = PairingState.Trusted,
            )
        assertEquals(GameHubHomeMode.Launcher, GameHubHomeMapper.resolveMode(ui))
    }

    @Test
    fun disconnectReturnsToPortal() {
        val trusted =
            baseState().copy(
                receivers = listOf(receiver),
                selectedReceiver = receiver,
                trustedReceiver =
                TrustedReceiver(receiver.identity, receiver.displayName, receiver.hostAddress, receiver.port, 1L),
                pairingState = PairingState.Trusted,
            )
        assertEquals(GameHubHomeMode.Launcher, GameHubHomeMapper.resolveMode(trusted))
        val disconnected = trusted.copy(trustedReceiver = null, pairingState = PairingState.Required)
        assertEquals(GameHubHomeMode.Portal, GameHubHomeMapper.resolveMode(disconnected))
    }

    @Test
    fun launcherItemsListsAllLibraryAppsWithFavouritesFirst() {
        val favourite =
            LibraryAppItem(LocalLibraryApp("com.game", "Stardew Valley", isFavourite = true), icon = ColorDrawable())
        val other = LibraryAppItem(LocalLibraryApp("com.other", "Zelda"), icon = ColorDrawable())
        val ui =
            baseState().copy(
                azaharStatus = AzaharIntegrationStatus(installed = true),
                libraryApps = listOf(other, favourite),
            )
        val items = GameHubHomeMapper.launcherItems(ui, receiverReady = true)
        assertEquals("azahar", items.first().id)
        assertEquals("Stardew Valley", items[1].label)
        assertTrue(items[1].isFavourite)
        assertEquals(3, items.size)
    }

    @Test
    fun connectedHomeGridLayoutUsesFourByThree() {
        val layout = GameHubViewport.connectedHomeGridLayout(stageWidth = 720.dp, stageHeight = 360.dp)
        assertEquals(4, layout.columns)
        assertEquals(3, layout.rows)
        assertTrue(layout.tileWidth >= 72.dp)
        assertTrue(layout.tileHeight >= 88.dp)
    }

    @Test
    fun launcherGridNavigationMovesAcrossPages() {
        assertEquals(12, gameHubLauncherGridMoveRight(11, count = 20))
        assertEquals(11, gameHubLauncherGridMoveLeft(12))
        assertEquals(0, gameHubLauncherGridLocalRow(3))
        assertEquals(2, gameHubLauncherGridLocalRow(10))
    }

    @Test
    fun swipePagePreservesGridPositionWithinBounds() {
        assertEquals(17, gameHubFocusIndexForPage(1, currentIndex = 5, columns = 4, pageSize = 12, count = 20))
        assertEquals(19, gameHubFocusIndexForPage(1, currentIndex = 11, columns = 4, pageSize = 12, count = 20))
        assertEquals(5, gameHubFocusIndexForPage(0, currentIndex = 5, columns = 4, pageSize = 12, count = 20))
    }

    @Test
    fun launcherGridNavigationMovesByRowWithinPage() {
        assertEquals(4, gameHubLauncherGridMoveDown(0, count = 8))
        assertEquals(0, gameHubLauncherGridMoveUp(4))
    }

    @Test
    fun azaharTileOpensModePickerWhenReady() {
        val ui =
            baseState().copy(
                receivers = listOf(receiver),
                selectedReceiver = receiver,
                trustedReceiver =
                TrustedReceiver(
                    receiver.identity,
                    receiver.displayName,
                    receiver.hostAddress,
                    receiver.port,
                    1L,
                ),
                pairingState = PairingState.Trusted,
                azaharStatus = AzaharIntegrationStatus(installed = true),
            )
        val azahar = GameHubHomeMapper.launcherItems(ui, receiverReady = true).first()
        assertEquals(GameHubTileActionKind.PickAzaharMode, azahar.action)
        assertTrue(azahar.isAzahar)
    }

    @Test
    fun launcherItemsExcludeAzaharPackageFromLibraryDuplicates() {
        val ui =
            baseState().copy(
                azaharStatus = AzaharIntegrationStatus(installed = true),
                libraryApps =
                listOf(
                    LibraryAppItem(
                        LocalLibraryApp("com.glowseed.noctdock.azahar", "NoctDock Azahar"),
                        icon = ColorDrawable(),
                    ),
                    LibraryAppItem(LocalLibraryApp("com.game", "Stardew"), icon = ColorDrawable()),
                ),
            )
        val items = GameHubHomeMapper.launcherItems(ui, receiverReady = true)
        assertEquals(2, items.size)
        assertEquals("azahar", items.first().id)
        assertEquals("Stardew", items[1].label)
    }

    @Test
    fun displayLabelsNeverExposePackageNames() {
        val label =
            GameHubHomeMapper.displayLabel(
                LocalLibraryApp(packageName = "com.example.secret", label = "Stardew Valley"),
            )
        assertEquals("Stardew Valley", label)
        assertFalse(label.contains("com.example"))
    }

    @Test
    fun settingsActionIsReachableThroughMapperLabels() {
        assertEquals("Launch on Screen", GameHubHomeMapper.tileActionLabel(GameHubTileActionKind.LaunchOnScreen))
    }

    @Test
    fun libraryGridNavigationPagesHorizontally() {
        assertEquals(8, gameHubLibraryGridMoveRight(7, count = 16))
        assertEquals(7, gameHubLibraryGridMoveLeft(8))
        assertEquals(4, gameHubLibraryGridMoveDown(0, count = 10))
        assertEquals(0, gameHubLibraryGridMoveUp(4))
    }

    @Test
    fun connectedLibraryGridLayoutUsesFourByTwo() {
        val layout = GameHubViewport.connectedLibraryGridLayout(stageWidth = 720.dp, stageHeight = 320.dp)
        assertEquals(4, layout.columns)
        assertEquals(2, layout.rows)
    }

    @Test
    fun emulatorClassifierRecognizesRetroarch() {
        val app = LocalLibraryApp("com.retroarch", "RetroArch")
        assertEquals(GameHubAppCategory.Emulator, GameHubAppClassifier.category(app))
    }

    private fun baseState(): SenderUiState = SenderUiState(consoleModeState = ConsoleModeState.Idle)
}
