package com.eink.reader.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import com.eink.reader.ui.theme.EInkBlack
import com.eink.reader.ui.theme.EInkBorder
import com.eink.reader.ui.theme.EInkWhite
import com.eink.reader.util.EInkHelper
import kotlinx.coroutines.launch

/**
 * Nút nhảy trang nổi (Page Up / Page Down Floating Buttons) chuyên dụng cho E-Ink.
 * Thay vì phải miết tay trượt liên tục trên màn hình gây bóng mờ,
 * người dùng chỉ cần chạm 1 lần để nhảy dứt khoát 1 trang màn hình.
 */
@Composable
fun EInkScrollButtons(
    modifier: Modifier = Modifier,
    onPageUp: () -> Unit,
    onPageDown: () -> Unit,
    onRefresh: (() -> Unit)? = null
) {
    val interactionSource = remember { MutableInteractionSource() }

    Column(
        modifier = modifier
            .border(1.dp, EInkBorder, RoundedCornerShape(4.dp))
            .background(EInkWhite, RoundedCornerShape(4.dp))
            .padding(2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Nút Cuộn Lên (Page Up)
        Box(
            modifier = Modifier
                .size(36.dp)
                .border(1.dp, EInkBorder, RoundedCornerShape(2.dp))
                .background(EInkWhite)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onPageUp
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.KeyboardArrowUp,
                contentDescription = "Cuộn lên 1 trang",
                tint = EInkBlack,
                modifier = Modifier.size(22.dp)
            )
        }

        // Nút Cuộn Xuống (Page Down)
        Box(
            modifier = Modifier
                .size(36.dp)
                .border(1.dp, EInkBorder, RoundedCornerShape(2.dp))
                .background(EInkWhite)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onPageDown
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = "Cuộn xuống 1 trang",
                tint = EInkBlack,
                modifier = Modifier.size(22.dp)
            )
        }

        // Nút Khử bóng ma (Refresh Screen)
        if (onRefresh != null) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .border(1.dp, EInkBorder, RoundedCornerShape(2.dp))
                    .background(EInkWhite)
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = onRefresh
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Khử bóng ma màn hình",
                    tint = EInkBlack,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

/**
 * Phiên bản tích hợp sẵn cho ScrollState (Column cuộn dọc)
 */
@Composable
fun EInkScrollButtonsForScrollState(
    scrollState: ScrollState,
    modifier: Modifier = Modifier,
    viewportHeightPx: Float = 1200f,
    onRefresh: (() -> Unit)? = null
) {
    val coroutineScope = rememberCoroutineScope()
    val jumpPx = (viewportHeightPx * 0.85f).toInt()

    EInkScrollButtons(
        modifier = modifier,
        onPageUp = {
            coroutineScope.launch {
                val target = (scrollState.value - jumpPx).coerceAtLeast(0)
                scrollState.scrollTo(target)
            }
        },
        onPageDown = {
            coroutineScope.launch {
                val target = (scrollState.value + jumpPx).coerceAtMost(scrollState.maxValue)
                scrollState.scrollTo(target)
            }
        },
        onRefresh = onRefresh
    )
}

/**
 * Phiên bản tích hợp sẵn cho LazyListState (LazyColumn danh sách)
 */
@Composable
fun EInkScrollButtonsForLazyList(
    listState: LazyListState,
    modifier: Modifier = Modifier,
    onRefresh: (() -> Unit)? = null
) {
    val coroutineScope = rememberCoroutineScope()

    EInkScrollButtons(
        modifier = modifier,
        onPageUp = {
            coroutineScope.launch {
                val visibleCount = listState.layoutInfo.visibleItemsInfo.size.coerceAtLeast(1)
                val target = (listState.firstVisibleItemIndex - (visibleCount - 1).coerceAtLeast(1)).coerceAtLeast(0)
                listState.scrollToItem(target)
            }
        },
        onPageDown = {
            coroutineScope.launch {
                val visibleCount = listState.layoutInfo.visibleItemsInfo.size.coerceAtLeast(1)
                val totalCount = listState.layoutInfo.totalItemsCount
                val target = (listState.firstVisibleItemIndex + (visibleCount - 1).coerceAtLeast(1))
                    .coerceAtMost((totalCount - 1).coerceAtLeast(0))
                listState.scrollToItem(target)
            }
        },
        onRefresh = onRefresh
    )
}

/**
 * Phiên bản tích hợp sẵn cho LazyGridState (LazyVerticalGrid lưới truyện)
 */
@Composable
fun EInkScrollButtonsForLazyGrid(
    gridState: LazyGridState,
    modifier: Modifier = Modifier,
    onRefresh: (() -> Unit)? = null
) {
    val coroutineScope = rememberCoroutineScope()

    EInkScrollButtons(
        modifier = modifier,
        onPageUp = {
            coroutineScope.launch {
                val visibleCount = gridState.layoutInfo.visibleItemsInfo.size.coerceAtLeast(1)
                val target = (gridState.firstVisibleItemIndex - (visibleCount - 2).coerceAtLeast(1)).coerceAtLeast(0)
                gridState.scrollToItem(target)
            }
        },
        onPageDown = {
            coroutineScope.launch {
                val visibleCount = gridState.layoutInfo.visibleItemsInfo.size.coerceAtLeast(1)
                val totalCount = gridState.layoutInfo.totalItemsCount
                val target = (gridState.firstVisibleItemIndex + (visibleCount - 2).coerceAtLeast(1))
                    .coerceAtMost((totalCount - 1).coerceAtLeast(0))
                gridState.scrollToItem(target)
            }
        },
        onRefresh = onRefresh
    )
}
