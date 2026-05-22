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
    fun gridLayoutFitsAllItemsOnOneScreen() {
        val layout = GameHubViewport.launcherGridLayout(stageWidth = 720.dp, stageHeight = 260.dp, itemCount = 7)
        assertTrue(layout.columns * layout.rows >= 7)
        val totalH = layout.tileHeight * layout.rows + layout.gap * (layout.rows - 1)
        assertTrue(totalH <= 260.dp)
        assertTrue(layout.contentInsetH >= 20.dp)
    }

    @Test
    fun gridLayoutFillsRowWidthForFourItems() {
        val layout = GameHubViewport.launcherGridLayout(stageWidth = 720.dp, stageHeight = 400.dp, itemCount = 4)
        assertTrue(layout.columns * layout.rows >= 4)
        assertTrue(layout.tileWidth >= 140.dp)
        assertTrue(layout.tileHeight <= 142.dp)
    }

    @Test
    fun gridNavigationMovesByRow() {
        assertEquals(5, gameHubGridMoveDown(1, columns = 4, count = 8))
        assertEquals(0, gameHubGridMoveUp(4, columns = 4, count = 8))
    }

    @Test
    fun gridNavigationMovesTwoByTwo() {
        assertEquals(2, gameHubGridMoveDown(0, columns = 2, count = 4))
        assertEquals(3, gameHubGridMoveDown(1, columns = 2, count = 4))
        assertEquals(1, gameHubGridMoveRight(0, columns = 2, count = 4))
        assertEquals(0, gameHubGridMoveLeft(1, columns = 2, count = 4))
        assertEquals(0, gameHubGridMoveUp(2, columns = 2, count = 4))
    }

    @Test
    fun gridNavigationDownStaysWhenNoCellBelow() {
        assertEquals(2, gameHubGridMoveDown(2, columns = 3, count = 4))
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
    fun emulatorShelfClassifiesRetroarch() {
        val app = LocalLibraryApp("com.retroarch", "RetroArch")
        assertEquals(GameHubAppCategory.Emulator, GameHubAppClassifier.category(app))
    }

    private fun baseState(): SenderUiState = SenderUiState(consoleModeState = ConsoleModeState.Idle)
}
