package com.glowseed.noctdock.sender

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.foundation.border
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.activity.compose.BackHandler
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
    overflowRequestIndex: Int? = null,
    onOverflowRequestConsumed: () -> Unit = {},
    onFilterChange: (GameHubLibraryFilter) -> Unit,
    onFocusedIndexChange: (Int) -> Unit,
    onRefresh: () -> Unit,
    onQueryChange: (String) -> Unit,
    onToggleFavourite: (LocalLibraryApp) -> Unit,
    onAddApp: (LocalLibraryApp) -> Unit,
    onRemoveApp: (LocalLibraryApp) -> Unit,
    onLaunchApp: (LocalLibraryApp) -> Unit,
) {
    val entries =
        remember(uiState.libraryApps, uiState.libraryAddCandidates, uiState.libraryQuery, filter) {
            gameHubLibraryGridEntries(uiState, filter)
        }
    val gridFocused = gridInputActive && focusedIndex in entries.indices
    var searchExpanded by remember { mutableStateOf(uiState.libraryQuery.isNotBlank()) }
    var menuEntry by remember { mutableStateOf<GameHubLibraryGridEntry.App?>(null) }
    val searchFocus = remember { FocusRequester() }
    val filterScrollState = rememberScrollState()
    val filterBringIntoView = remember { BringIntoViewRequester() }

    LaunchedEffect(overflowRequestIndex, entries) {
        val index = overflowRequestIndex ?: return@LaunchedEffect
        when (val entry = entries.getOrNull(index)) {
            is GameHubLibraryGridEntry.App -> menuEntry = entry
            else -> Unit
        }
        onOverflowRequestConsumed()
    }

    BackHandler(enabled = menuEntry != null) {
        menuEntry = null
    }

    LaunchedEffect(searchExpanded) {
        if (searchExpanded) searchFocus.requestFocus()
    }

    LaunchedEffect(filterFocusIndex, filtersFocused) {
        if (filtersFocused) {
            filterScrollState.animateScrollTo((filterFocusIndex * 88).coerceAtLeast(0))
            filterBringIntoView.bringIntoView()
        }
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
                    .horizontalScroll(filterScrollState),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GameHubLibraryFilter.entries.forEachIndexed { index, option ->
                    val chipHighlighted = filtersFocused && filterFocusIndex == index
                    GameHubLibraryFilterChip(
                        label = option.label,
                        selected = filter == option,
                        highlighted = chipHighlighted,
                        accent = accent,
                        reducedMotion = reducedMotion,
                        modifier =
                        if (chipHighlighted) {
                            Modifier.bringIntoViewRequester(filterBringIntoView)
                        } else {
                            Modifier
                        },
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
                    onFocusedIndexChange = onFocusedIndexChange,
                    onEntryActivate = { entry ->
                        when (entry) {
                            is GameHubLibraryGridEntry.App -> onLaunchApp(entry.item.model)
                            is GameHubLibraryGridEntry.AddCandidate -> onAddApp(entry.item.model)
                        }
                    },
                    onEntryLongPress = { entry ->
                        when (entry) {
                            is GameHubLibraryGridEntry.App -> menuEntry = entry
                            is GameHubLibraryGridEntry.AddCandidate -> onAddApp(entry.item.model)
                        }
                    },
                )
            }
        }
    }
    menuEntry?.let { entry ->
        GameHubLibraryEntryMenu(
            entry = entry,
            accent = accent,
            reducedMotion = reducedMotion,
            onDismiss = { menuEntry = null },
            onFavourite = {
                onToggleFavourite(entry.item.model)
                menuEntry = null
            },
            onRemove = {
                val nextFocus =
                    if (focusedIndex >= entries.lastIndex) {
                        (entries.size - 2).coerceAtLeast(0)
                    } else {
                        focusedIndex
                    }
                onRemoveApp(entry.item.model)
                menuEntry = null
                onFocusedIndexChange(nextFocus)
            },
        )
    }
}

@Composable
private fun GameHubLibraryEntryMenu(
    entry: GameHubLibraryGridEntry.App,
    accent: Color,
    reducedMotion: Boolean,
    onDismiss: () -> Unit,
    onFavourite: () -> Unit,
    onRemove: () -> Unit,
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
            Text(
                entry.item.model.label,
                color = NoctColors.TextPrimary,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            GameHubOverflowMenuRow(
                label = if (entry.item.model.isFavourite) "Remove favourite" else "Add favourite",
                trailing = if (entry.item.model.isFavourite) "★" else "☆",
                trailingColor = NoctColors.Magenta,
                onClick = onFavourite,
            )
            if (entry.removable) {
                GameHubOverflowMenuRow(
                    label = "Remove from library",
                    trailing = "✕",
                    trailingColor = NoctColors.Magenta,
                    onClick = onRemove,
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
private fun GameHubLibraryFilterChip(label: String, selected: Boolean, highlighted: Boolean, accent: Color, reducedMotion: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    val ringActive = selected || highlighted
    val borderBrush =
        remember(accent, ringActive) {
            if (ringActive) {
                Brush.linearGradient(
                    listOf(
                        accent.copy(alpha = 0.82f),
                        NoctColors.Cyan.copy(alpha = 0.58f),
                        NoctColors.Magenta.copy(alpha = 0.52f),
                    ),
                )
            } else {
                Brush.linearGradient(
                    listOf(
                        NoctColors.GlassBorder.copy(alpha = 0.55f),
                        NoctColors.GlassBorder.copy(alpha = 0.28f),
                    ),
                )
            }
        }
    Box(
        modifier =
        modifier
            .clip(shape)
            .drawBehind {
                val pillCorner = CornerRadius(size.height / 2f, size.height / 2f)
                if (ringActive) {
                    drawRoundRect(
                        brush =
                        Brush.linearGradient(
                            colors =
                            listOf(
                                accent.copy(alpha = if (highlighted) 0.34f else 0.24f),
                                Color(0xCC141C28),
                                NoctColors.Violet.copy(alpha = 0.16f),
                            ),
                            start = Offset.Zero,
                            end = Offset(size.width, size.height),
                        ),
                        cornerRadius = pillCorner,
                    )
                    drawRoundRect(
                        brush =
                        Brush.radialGradient(
                            colors =
                            listOf(
                                Color.White.copy(alpha = 0.10f),
                                Color.Transparent,
                            ),
                            center = Offset(size.width * 0.5f, 0f),
                            radius = size.height * 1.6f,
                        ),
                        cornerRadius = pillCorner,
                    )
                } else {
                    drawRoundRect(color = Color(0x77101824), cornerRadius = pillCorner)
                }
            }
            .border(width = if (ringActive) 1.5.dp else 1.dp, brush = borderBrush, shape = shape)
            .gameHubFocusRing(
                shape = shape,
                accent = accent,
                focused = highlighted,
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
            .padding(horizontal = 16.dp, vertical = 9.dp),
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
    onFocusedIndexChange: (Int) -> Unit,
    onEntryActivate: (GameHubLibraryGridEntry) -> Unit,
    onEntryLongPress: (GameHubLibraryGridEntry) -> Unit,
) {
    val bringIntoView = remember { BringIntoViewRequester() }

    LaunchedEffect(focusedIndex, gridInputActive) {
        if (gridInputActive) {
            bringIntoView.bringIntoView()
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val pageCount = gameHubPagedGridPageCount(entries.size, GAME_HUB_LIBRARY_PAGE_SIZE)
        val layout =
            remember(maxWidth, maxHeight, pageCount) {
                GameHubViewport.connectedLibraryGridLayout(
                    stageWidth = maxWidth,
                    stageHeight = maxHeight,
                    pageDotsReserved = if (pageCount > 1) 10.dp else 0.dp,
                )
            }
        val pageIndex = gameHubPagedGridPageIndex(focusedIndex, GAME_HUB_LIBRARY_PAGE_SIZE)
        val focusedLocalRow = gameHubLibraryGridLocalRow(focusedIndex)

        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            GameHubHorizontalPagePager(
                pageCount = pageCount,
                currentPage = pageIndex,
                onPageChanged = { page ->
                    onFocusedIndexChange(
                        gameHubFocusIndexForPage(
                            page = page,
                            currentIndex = focusedIndex,
                            columns = GAME_HUB_LIBRARY_GRID_COLUMNS,
                            pageSize = GAME_HUB_LIBRARY_PAGE_SIZE,
                            count = entries.size,
                        ),
                    )
                },
                modifier = Modifier.weight(1f),
            ) { page ->
                val pageStart = page * GAME_HUB_LIBRARY_PAGE_SIZE
                val pageItems = entries.drop(pageStart).take(GAME_HUB_LIBRARY_PAGE_SIZE)
                Column(
                    modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = layout.contentInsetH),
                    verticalArrangement = Arrangement.spacedBy(layout.gap),
                ) {
                    for (row in 0 until GAME_HUB_LIBRARY_GRID_ROWS) {
                        Row(
                            modifier = Modifier.fillMaxWidth().height(layout.tileHeight),
                            horizontalArrangement = Arrangement.spacedBy(layout.gap),
                            verticalAlignment = Alignment.Top,
                        ) {
                            for (col in 0 until GAME_HUB_LIBRARY_GRID_COLUMNS) {
                                val localIndex = row * GAME_HUB_LIBRARY_GRID_COLUMNS + col
                                val globalIndex = pageStart + localIndex
                                if (globalIndex < entries.size && localIndex < pageItems.size) {
                                    val entry = pageItems[localIndex]
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
                                    val tileFocused = gridInputActive && focusedIndex == globalIndex
                                    val rowFocused = gridInputActive && focusedLocalRow == row
                                    GameHubLauncherTile(
                                        item = launcherItem,
                                        iconPackageName = packageName,
                                        icon = icon,
                                        tileWidth = layout.tileWidth,
                                        tileHeight = layout.tileHeight,
                                        accent = accent,
                                        reducedMotion = reducedMotion,
                                        focused = tileFocused,
                                        rowHasFocus = rowFocused,
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
                                    Spacer(modifier = Modifier.size(layout.tileWidth, layout.tileHeight))
                                }
                            }
                        }
                    }
                }
            }
            if (pageCount > 1) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    repeat(pageCount) { index ->
                        val active = index == pageIndex
                        Box(
                            modifier =
                            Modifier
                                .padding(horizontal = 3.dp)
                                .size(if (active) 8.dp else 6.dp)
                                .clip(CircleShape)
                                .background(
                                    if (active) accent.copy(alpha = 0.9f) else NoctColors.TextSecondary.copy(alpha = 0.35f),
                                ),
                        )
                    }
                }
            }
        }
    }
}
