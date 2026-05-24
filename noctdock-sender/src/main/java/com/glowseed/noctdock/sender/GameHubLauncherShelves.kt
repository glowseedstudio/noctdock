package com.glowseed.noctdock.sender

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

internal const val GAME_HUB_LAUNCHER_GRID_COLUMNS = 4
internal const val GAME_HUB_LAUNCHER_GRID_ROWS = 3
internal const val GAME_HUB_LAUNCHER_PAGE_SIZE = GAME_HUB_LAUNCHER_GRID_COLUMNS * GAME_HUB_LAUNCHER_GRID_ROWS

internal const val GAME_HUB_LIBRARY_GRID_COLUMNS = 4
internal const val GAME_HUB_LIBRARY_GRID_ROWS = 2
internal const val GAME_HUB_LIBRARY_PAGE_SIZE = GAME_HUB_LIBRARY_GRID_COLUMNS * GAME_HUB_LIBRARY_GRID_ROWS

internal fun gameHubPagedGridPageCount(count: Int, pageSize: Int): Int =
    if (count <= 0) 0 else (count + pageSize - 1) / pageSize

internal fun gameHubPagedGridPageIndex(index: Int, pageSize: Int): Int =
    if (pageSize <= 0) 0 else index / pageSize

internal fun gameHubFocusIndexForPage(
    page: Int,
    currentIndex: Int,
    columns: Int,
    pageSize: Int,
    count: Int,
): Int {
    if (count <= 0) return 0
    val local = currentIndex % pageSize
    val localRow = local / columns
    val localCol = local % columns
    val target = page * pageSize + localRow * columns + localCol
    return target.coerceIn(0, count - 1)
}

internal fun gameHubPagedGridMoveRight(index: Int, count: Int): Int {
    if (count <= 0) return 0
    if (index + 1 < count) return index + 1
    return index
}

internal fun gameHubPagedGridMoveLeft(index: Int): Int {
    if (index > 0) return index - 1
    return index
}

internal fun gameHubPagedGridMoveDown(index: Int, count: Int, columns: Int, rows: Int): Int {
    if (count <= 0) return 0
    val pageSize = columns * rows
    val pageStart = (index / pageSize) * pageSize
    val local = index - pageStart
    val localRow = local / columns
    if (localRow >= rows - 1) return index
    val next = index + columns
    return if (next < count) next else index
}

internal fun gameHubPagedGridMoveUp(index: Int, columns: Int, rows: Int): Int {
    val pageSize = columns * rows
    val pageStart = (index / pageSize) * pageSize
    val local = index - pageStart
    val localRow = local / columns
    if (localRow <= 0) return index
    return index - columns
}

internal fun gameHubPagedGridLocalRow(index: Int, columns: Int, pageSize: Int): Int = (index % pageSize) / columns

internal fun gameHubLauncherGridMoveRight(index: Int, count: Int): Int = gameHubPagedGridMoveRight(index, count)

internal fun gameHubLauncherGridMoveLeft(index: Int): Int = gameHubPagedGridMoveLeft(index)

internal fun gameHubLauncherGridMoveDown(index: Int, count: Int): Int = gameHubPagedGridMoveDown(index, count, GAME_HUB_LAUNCHER_GRID_COLUMNS, GAME_HUB_LAUNCHER_GRID_ROWS)

internal fun gameHubLauncherGridMoveUp(index: Int): Int = gameHubPagedGridMoveUp(index, GAME_HUB_LAUNCHER_GRID_COLUMNS, GAME_HUB_LAUNCHER_GRID_ROWS)

internal fun gameHubLauncherGridLocalRow(index: Int): Int = gameHubPagedGridLocalRow(index, GAME_HUB_LAUNCHER_GRID_COLUMNS, GAME_HUB_LAUNCHER_PAGE_SIZE)

internal fun gameHubLibraryGridMoveRight(index: Int, count: Int): Int = gameHubPagedGridMoveRight(index, count)

internal fun gameHubLibraryGridMoveLeft(index: Int): Int = gameHubPagedGridMoveLeft(index)

internal fun gameHubLibraryGridMoveDown(index: Int, count: Int): Int = gameHubPagedGridMoveDown(index, count, GAME_HUB_LIBRARY_GRID_COLUMNS, GAME_HUB_LIBRARY_GRID_ROWS)

internal fun gameHubLibraryGridMoveUp(index: Int): Int = gameHubPagedGridMoveUp(index, GAME_HUB_LIBRARY_GRID_COLUMNS, GAME_HUB_LIBRARY_GRID_ROWS)

internal fun gameHubLibraryGridLocalRow(index: Int): Int = gameHubPagedGridLocalRow(index, GAME_HUB_LIBRARY_GRID_COLUMNS, GAME_HUB_LIBRARY_PAGE_SIZE)

internal data class GameHubPosterTileMetrics(val tileWidth: Dp, val tileHeight: Dp, val gap: Dp, val contentInsetH: Dp)

internal enum class GameHubLauncherTileVariant {
    Classic,
    Poster,
}

internal fun gameHubLauncherCoverMoveRight(index: Int, count: Int): Int = gameHubLauncherGridMoveRight(index, count)

internal fun gameHubLauncherCoverMoveLeft(index: Int): Int = gameHubLauncherGridMoveLeft(index)

internal fun gameHubPosterShelfMetrics(stageWidth: Dp, hero: Boolean = false): GameHubPosterTileMetrics {
    val contentInsetH = (stageWidth * 0.05f).coerceIn(22.dp, 36.dp)
    val tileHeight = if (hero) 116.dp else 98.dp
    val tileWidth = (tileHeight * 1.56f).coerceIn(136.dp, 188.dp)
    return GameHubPosterTileMetrics(
        tileWidth = tileWidth,
        tileHeight = tileHeight,
        gap = 12.dp,
        contentInsetH = contentInsetH,
    )
}
