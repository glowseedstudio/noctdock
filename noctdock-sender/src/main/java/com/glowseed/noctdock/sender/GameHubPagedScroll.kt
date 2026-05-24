package com.glowseed.noctdock.sender

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerScope
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import kotlin.math.abs
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun GameHubHorizontalPagePager(
    pageCount: Int,
    currentPage: Int,
    onPageChanged: (Int) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable PagerScope.(page: Int) -> Unit,
) {
    if (pageCount <= 0) return
    val safePageCount = pageCount.coerceAtLeast(1)
    val clampedPage = currentPage.coerceIn(0, safePageCount - 1)
    val pagerState =
        rememberPagerState(
            initialPage = clampedPage,
            pageCount = { safePageCount },
        )

    LaunchedEffect(clampedPage, safePageCount) {
        if (pagerState.currentPage != clampedPage) {
            pagerState.animateScrollToPage(clampedPage)
        }
    }

    LaunchedEffect(pagerState, clampedPage) {
        snapshotFlow { pagerState.settledPage to pagerState.isScrollInProgress }
            .distinctUntilChanged()
            .collect { (settledPage, scrolling) ->
                if (!scrolling && settledPage != clampedPage) {
                    onPageChanged(settledPage)
                }
            }
    }

    HorizontalPager(
        modifier = modifier,
        state = pagerState,
        userScrollEnabled = safePageCount > 1,
        beyondViewportPageCount = 0,
        pageContent = { page -> content(page) },
    )
}

internal fun gameHubLazyRowCenteredIndex(listState: LazyListState): Int? {
    val layoutInfo = listState.layoutInfo
    if (layoutInfo.visibleItemsInfo.isEmpty()) return null
    val viewportCenter = (layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset) / 2
    return layoutInfo.visibleItemsInfo.minByOrNull { item ->
        abs((item.offset + item.size / 2) - viewportCenter)
    }?.index
}

@Composable
internal fun GameHubCoverCarousel(
    itemCount: Int,
    focusedIndex: Int,
    contentPadding: PaddingValues,
    itemSpacing: Dp,
    onFocusedIndexChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    itemContent: @Composable (index: Int) -> Unit,
) {
    if (itemCount <= 0) return
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = focusedIndex.coerceIn(0, itemCount - 1),
    )
    val snapFling = rememberSnapFlingBehavior(lazyListState = listState)

    LaunchedEffect(focusedIndex, itemCount) {
        val target = focusedIndex.coerceIn(0, itemCount - 1)
        if (listState.firstVisibleItemIndex != target || listState.firstVisibleItemScrollOffset != 0) {
            listState.animateScrollToItem(target)
        }
    }

    LaunchedEffect(listState, focusedIndex, itemCount) {
        var wasScrolling = false
        snapshotFlow { listState.isScrollInProgress }
            .collect { scrolling ->
                if (wasScrolling && !scrolling) {
                    gameHubLazyRowCenteredIndex(listState)?.let { centered ->
                        val clamped = centered.coerceIn(0, itemCount - 1)
                        if (clamped != focusedIndex) {
                            onFocusedIndexChange(clamped)
                        }
                    }
                }
                wasScrolling = scrolling
            }
    }

    LazyRow(
        modifier = modifier,
        state = listState,
        flingBehavior = snapFling,
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(itemSpacing),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items(
            count = itemCount,
            key = { index -> index },
        ) { index ->
            itemContent(index)
        }
    }
}
