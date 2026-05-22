package com.glowseed.noctdock.sender

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.glowseed.noctdock.core.AppLibrarySorter
import com.glowseed.noctdock.core.LocalLibraryApp
import com.glowseed.noctdock.core.NoctColors
import com.glowseed.noctdock.core.NoctGlassCard
internal enum class GameHubHomePanel {
    Launcher,
    Library,
    Screens,
    ConsoleModes,
    Settings,
}

internal const val GAME_HUB_TOP_BAR_HOME = 0
internal const val GAME_HUB_TOP_BAR_LIBRARY = 1
internal const val GAME_HUB_TOP_BAR_SCREENS = 2
internal const val GAME_HUB_TOP_BAR_CONSOLE_MODES = 3
internal const val GAME_HUB_TOP_BAR_SETTINGS = 4
internal const val GAME_HUB_TOP_BAR_LAST_INDEX = GAME_HUB_TOP_BAR_SETTINGS

internal fun gameHubTopBarIndexForPanel(panel: GameHubHomePanel): Int = when (panel) {
    GameHubHomePanel.Launcher -> GAME_HUB_TOP_BAR_HOME
    GameHubHomePanel.Library -> GAME_HUB_TOP_BAR_LIBRARY
    GameHubHomePanel.Screens -> GAME_HUB_TOP_BAR_SCREENS
    GameHubHomePanel.ConsoleModes -> GAME_HUB_TOP_BAR_CONSOLE_MODES
    GameHubHomePanel.Settings -> GAME_HUB_TOP_BAR_SETTINGS
}

/** Focus ring on top-bar toggles only — never while a grid/list below has the active pointer. */
internal fun gameHubTopBarTabSelected(buttonIndex: Int, topBarFocused: Boolean, topBarIndex: Int, homePanel: GameHubHomePanel): Boolean = topBarFocused && topBarIndex == buttonIndex

internal enum class GameHubLibraryFilter(val label: String) {
    All("All"),
    Favourites("Favourites"),
    Emulators("Emulators"),
    Added("Added"),
    Discover("Add"),
}

internal sealed interface GameHubLibraryGridEntry {
    val key: String

    data class App(val item: LibraryAppItem, val removable: Boolean) : GameHubLibraryGridEntry {
        override val key: String = item.model.packageName
    }

    data class AddCandidate(val item: LibraryAppItem) : GameHubLibraryGridEntry {
        override val key: String = "add:${item.model.packageName}"
    }
}

internal fun gameHubClassifyLibraryApp(app: LocalLibraryApp): String = when {
    isGameHubEmulatorText(app.searchableText) -> "Emulators"
    else -> "Added"
}

internal fun isGameHubEmulatorText(text: String): Boolean = listOf(
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

internal fun gameHubLibraryGridEntries(uiState: SenderUiState, filter: GameHubLibraryFilter): List<GameHubLibraryGridEntry> {
    val query = uiState.libraryQuery
    val apps = uiState.libraryApps
    val favourites = apps.filter { it.model.isFavourite }
    val emulators = apps.filter { gameHubClassifyLibraryApp(it.model) == "Emulators" }
    val added = apps.filter { gameHubClassifyLibraryApp(it.model) == "Added" }
    val candidates =
        uiState.libraryAddCandidates.map { GameHubLibraryGridEntry.AddCandidate(it) }
    val filteredApps =
        when (filter) {
            GameHubLibraryFilter.All ->
                AppLibrarySorter.filter(apps.map { it.model }, query).mapNotNull { model ->
                    apps.firstOrNull { it.model.packageName == model.packageName }?.let {
                        GameHubLibraryGridEntry.App(it, removable = gameHubClassifyLibraryApp(it.model) == "Added")
                    }
                }

            GameHubLibraryFilter.Favourites ->
                AppLibrarySorter.filter(favourites.map { it.model }, query).mapNotNull { model ->
                    favourites.firstOrNull { it.model.packageName == model.packageName }?.let {
                        GameHubLibraryGridEntry.App(it, removable = gameHubClassifyLibraryApp(it.model) == "Added")
                    }
                }

            GameHubLibraryFilter.Emulators ->
                AppLibrarySorter.filter(emulators.map { it.model }, query).mapNotNull { model ->
                    emulators.firstOrNull { it.model.packageName == model.packageName }?.let {
                        GameHubLibraryGridEntry.App(it, removable = false)
                    }
                }

            GameHubLibraryFilter.Added ->
                AppLibrarySorter.filter(added.map { it.model }, query).mapNotNull { model ->
                    added.firstOrNull { it.model.packageName == model.packageName }?.let {
                        GameHubLibraryGridEntry.App(it, removable = true)
                    }
                }

            GameHubLibraryFilter.Discover ->
                AppLibrarySorter.filter(candidates.map { it.item.model }, query).mapNotNull { model ->
                    candidates.firstOrNull { it.item.model.packageName == model.packageName }
                }
        }
    return filteredApps
}

@Composable
internal fun GameHubLibraryStage(
    uiState: SenderUiState,
    accent: Color,
    reducedMotion: Boolean,
    filter: GameHubLibraryFilter,
    filterFocusIndex: Int,
    filtersFocused: Boolean,
    gridInputActive: Boolean,
    focusedIndex: Int,
    onFilterChange: (GameHubLibraryFilter) -> Unit,
    onFocusedIndexChange: (Int) -> Unit,
    onRefresh: () -> Unit,
    onQueryChange: (String) -> Unit,
    onToggleFavourite: (LocalLibraryApp) -> Unit,
    onAddApp: (LocalLibraryApp) -> Unit,
    onLaunchApp: (LocalLibraryApp) -> Unit,
) {
    val entries =
        remember(uiState.libraryApps, uiState.libraryAddCandidates, uiState.libraryQuery, filter) {
            gameHubLibraryGridEntries(uiState, filter)
        }
    val gridFocused = gridInputActive && focusedIndex in entries.indices
    var searchExpanded by remember { mutableStateOf(uiState.libraryQuery.isNotBlank()) }
    val searchFocus = remember { FocusRequester() }

    LaunchedEffect(searchExpanded) {
        if (searchExpanded) searchFocus.requestFocus()
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier =
                Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GameHubLibraryFilter.entries.forEachIndexed { index, option ->
                    GameHubLibraryFilterChip(
                        label = option.label,
                        selected = filter == option,
                        highlighted = filtersFocused && filterFocusIndex == index,
                        accent = accent,
                        reducedMotion = reducedMotion,
                        onClick = {
                            onFilterChange(option)
                            onFocusedIndexChange(0)
                        },
                    )
                }
            }
            GameHubLibraryToolIconButton(
                accent = accent,
                active = searchExpanded || uiState.libraryQuery.isNotBlank(),
                reducedMotion = reducedMotion,
                onClick = { searchExpanded = !searchExpanded },
                contentDescription = "Search apps",
            ) {
                GameHubLibrarySearchGlyph(active = searchExpanded || uiState.libraryQuery.isNotBlank())
            }
            GameHubLibraryToolIconButton(
                accent = accent,
                active = false,
                reducedMotion = reducedMotion,
                onClick = onRefresh,
                contentDescription = "Refresh library",
            ) {
                GameHubLibraryRefreshGlyph()
            }
        }
        AnimatedVisibility(
            visible = searchExpanded,
            enter = fadeIn() + expandVertically(expandFrom = Alignment.Top),
            exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Top),
        ) {
            OutlinedTextField(
                value = uiState.libraryQuery,
                onValueChange = onQueryChange,
                placeholder = { Text("Search apps") },
                singleLine = true,
                modifier =
                Modifier
                    .fillMaxWidth()
                    .focusRequester(searchFocus),
            )
        }
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (entries.isEmpty()) {
                NoctGlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            when (filter) {
                                GameHubLibraryFilter.Discover -> "No apps to add"
                                GameHubLibraryFilter.Favourites -> "No favourites yet"
                                else -> "Nothing here yet"
                            },
                            color = NoctColors.TextPrimary,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            when (filter) {
                                GameHubLibraryFilter.Discover ->
                                    "Search installed apps or add emulators from Settings → Library."

                                GameHubLibraryFilter.Added ->
                                    "Add games from the Add tab."

                                else ->
                                    "Emulators appear automatically. Star apps from the grid or launcher."
                            },
                            color = NoctColors.TextSecondary,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            } else {
                GameHubLibraryGrid(
                    entries = entries,
                    accent = accent,
                    reducedMotion = reducedMotion,
                    focusedIndex = focusedIndex,
                    gridInputActive = gridInputActive,
                    gridFocused = gridFocused,
                    onEntryActivate = { entry ->
                        when (entry) {
                            is GameHubLibraryGridEntry.App -> onLaunchApp(entry.item.model)
                            is GameHubLibraryGridEntry.AddCandidate -> onAddApp(entry.item.model)
                        }
                    },
                    onEntryLongPress = { entry ->
                        when (entry) {
                            is GameHubLibraryGridEntry.App -> onToggleFavourite(entry.item.model)
                            is GameHubLibraryGridEntry.AddCandidate -> onAddApp(entry.item.model)
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun GameHubLibraryToolIconButton(
    accent: Color,
    active: Boolean,
    reducedMotion: Boolean,
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    icon: @Composable () -> Unit,
) {
    Box(
        modifier =
        modifier
            .size(40.dp)
            .clip(CircleShape)
            .gameHubFocusRing(
                shape = CircleShape,
                accent = accent,
                focused = active,
                strokeDp = if (active) 3.5f else 2.5f,
                idleBorderDp = 2.dp,
                reducedMotion = reducedMotion,
            )
            .gameHubActivateOnAccept(onClick)
            .clickable(onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        icon()
    }
}

@Composable
private fun GameHubLibrarySearchGlyph(active: Boolean) {
    val color = NoctColors.TextPrimary.copy(alpha = if (active) 1f else 0.9f)
    Canvas(modifier = Modifier.size(18.dp)) {
        val stroke = Stroke(width = 2.4.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
        val lensR = size.minDimension * 0.28f
        val lensCenter = Offset(size.width * 0.42f, size.height * 0.42f)
        drawCircle(color = color, radius = lensR, center = lensCenter, style = stroke)
        val handleStart = Offset(lensCenter.x + lensR * 0.65f, lensCenter.y + lensR * 0.65f)
        val handleEnd = Offset(size.width * 0.82f, size.height * 0.82f)
        drawLine(color = color, start = handleStart, end = handleEnd, strokeWidth = 2.4.dp.toPx(), cap = stroke.cap)
    }
}

@Composable
private fun GameHubLibraryRefreshGlyph() {
    val color = NoctColors.TextPrimary.copy(alpha = 0.9f)
    Canvas(modifier = Modifier.size(18.dp)) {
        val stroke = Stroke(width = 2.3.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
        val r = size.minDimension * 0.34f
        drawArc(
            color = color,
            startAngle = 300f,
            sweepAngle = 260f,
            useCenter = false,
            topLeft = Offset(center.x - r, center.y - r),
            size = androidx.compose.ui.geometry.Size(r * 2f, r * 2f),
            style = stroke,
        )
        val tipAngle = Math.toRadians(300.0)
        val tipX = center.x + r * kotlin.math.cos(tipAngle).toFloat()
        val tipY = center.y + r * kotlin.math.sin(tipAngle).toFloat()
        drawLine(
            color = color,
            start = Offset(tipX, tipY),
            end = Offset(tipX - size.width * 0.12f, tipY - size.height * 0.06f),
            strokeWidth = 2.3.dp.toPx(),
            cap = stroke.cap,
        )
    }
}

@Composable
private fun GameHubLibraryFilterChip(label: String, selected: Boolean, highlighted: Boolean, accent: Color, reducedMotion: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    val ringActive = selected || highlighted
    Box(
        modifier =
        Modifier
            .clip(shape)
            .gameHubFocusRing(
                shape = shape,
                accent = accent,
                focused = ringActive,
                strokeDp =
                when {
                    highlighted -> 3.5f
                    selected -> 3f
                    else -> 1f
                },
                reducedMotion = reducedMotion,
                cornerRadius = 50.dp,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (ringActive) NoctColors.TextPrimary else NoctColors.TextSecondary,
            fontWeight = if (ringActive) FontWeight.SemiBold else FontWeight.Medium,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Composable
private fun GameHubLibraryGrid(
    entries: List<GameHubLibraryGridEntry>,
    accent: Color,
    reducedMotion: Boolean,
    focusedIndex: Int,
    gridInputActive: Boolean,
    gridFocused: Boolean,
    onEntryActivate: (GameHubLibraryGridEntry) -> Unit,
    onEntryLongPress: (GameHubLibraryGridEntry) -> Unit,
) {
    val scrollState = rememberScrollState()
    val bringIntoView = remember { BringIntoViewRequester() }
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val layout = GameHubViewport.libraryGridLayout(maxWidth, maxHeight, entries.size.coerceAtLeast(1))
        val density = LocalDensity.current
        LaunchedEffect(focusedIndex, gridInputActive, layout.columns, layout.tileHeight, layout.gap, entries.size) {
            if (!gridInputActive || focusedIndex !in entries.indices) return@LaunchedEffect
            val row = focusedIndex / layout.columns
            with(density) {
                val rowStridePx = (layout.tileHeight + layout.gap).roundToPx()
                val rowTopPx = rowStridePx * row
                val rowBottomPx = rowTopPx + layout.tileHeight.roundToPx()
                val viewportPx = maxHeight.roundToPx()
                val target =
                    when {
                        rowTopPx < scrollState.value -> rowTopPx
                        rowBottomPx > scrollState.value + viewportPx -> (rowBottomPx - viewportPx).coerceAtLeast(0)
                        else -> scrollState.value
                    }.coerceIn(0, scrollState.maxValue)
                scrollState.animateScrollTo(target)
            }
            bringIntoView.bringIntoView()
        }
        Column(
            modifier =
            Modifier
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .padding(horizontal = layout.contentInsetH),
            verticalArrangement = Arrangement.spacedBy(layout.gap),
        ) {
            for (row in 0 until layout.rows) {
                Row(
                    modifier = Modifier.fillMaxWidth().height(layout.tileHeight),
                    horizontalArrangement = Arrangement.spacedBy(layout.gap),
                    verticalAlignment = Alignment.Top,
                ) {
                    for (col in 0 until layout.columns) {
                        val index = row * layout.columns + col
                        if (index < entries.size) {
                            val entry = entries[index]
                            val item = entry as? GameHubLibraryGridEntry.App
                            val label =
                                when (entry) {
                                    is GameHubLibraryGridEntry.App -> entry.item.model.label
                                    is GameHubLibraryGridEntry.AddCandidate -> entry.item.model.label
                                }
                            val icon =
                                when (entry) {
                                    is GameHubLibraryGridEntry.App -> entry.item.icon
                                    is GameHubLibraryGridEntry.AddCandidate -> entry.item.icon
                                }
                            val packageName =
                                when (entry) {
                                    is GameHubLibraryGridEntry.App -> entry.item.model.packageName
                                    is GameHubLibraryGridEntry.AddCandidate -> entry.item.model.packageName
                                }
                            val launcherItem =
                                GameHubLauncherItem(
                                    id = packageName,
                                    label = label,
                                    shelf = GameHubShelfKind.AndroidGames,
                                    action = GameHubTileActionKind.LaunchOnScreen,
                                    isFavourite = item?.item?.model?.isFavourite == true,
                                )
                            val tileFocused = gridInputActive && index == focusedIndex
                            GameHubLauncherTile(
                                item = launcherItem,
                                iconPackageName = packageName,
                                icon = icon,
                                tileWidth = layout.tileWidth,
                                tileHeight = layout.tileHeight,
                                accent = accent,
                                reducedMotion = reducedMotion,
                                focused = tileFocused,
                                rowHasFocus = gridFocused,
                                modifier =
                                if (tileFocused) {
                                    Modifier.bringIntoViewRequester(bringIntoView)
                                } else {
                                    Modifier
                                },
                                onClick = { onEntryActivate(entry) },
                                onLongPress = { onEntryLongPress(entry) },
                            )
                        } else {
                            androidx.compose.foundation.layout.Spacer(
                                modifier = Modifier.size(layout.tileWidth, layout.tileHeight),
                            )
                        }
                    }
                }
            }
        }
    }
}
