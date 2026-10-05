package com.furflix.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Velocity

/**
 * HomeScreen 下拉刷新模式的可复用版：嵌套滚动捕获列表顶部的越界下拉，
 * Paw 指示器浮层居顶显示（与主页同一套视觉）。列表滚到顶后继续下拉触发
 * [onRefresh]；[isRefreshing] 归 false 时自动复位进度与触发标记。
 */
@Composable
fun PawPullRefreshBox(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    var pullProgress by remember { mutableFloatStateOf(0f) }
    val pullThreshold = 330f
    var hasTriggered by remember { mutableStateOf(false) }

    // 嵌套滚动连接只 remember 一次：经 rememberUpdatedState 读取最新值，
    // 避免闭包捕获到首帧的 isRefreshing/onRefresh
    val currentRefreshing by rememberUpdatedState(isRefreshing)
    val currentOnRefresh by rememberUpdatedState(onRefresh)

    LaunchedEffect(isRefreshing) {
        if (!isRefreshing) {
            pullProgress = 0f
            hasTriggered = false
        }
    }

    val connection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset = Offset.Zero

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                if (available.y > 0 && source == NestedScrollSource.UserInput && !currentRefreshing) {
                    pullProgress = (pullProgress + available.y).coerceIn(0f, pullThreshold * 1.5f)
                    if (pullProgress >= pullThreshold && !hasTriggered) {
                        hasTriggered = true
                        currentOnRefresh()
                    }
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (pullProgress > 0 && !currentRefreshing) {
                    pullProgress = 0f
                }
                return Velocity.Zero
            }
        }
    }

    Box(modifier.nestedScroll(connection)) {
        content()
        if (pullProgress > 0f || isRefreshing) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 12.dp)
                    .size(56.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(Color.Black.copy(alpha = 0.6f)),
                contentAlignment = Alignment.Center
            ) {
                PawPullIndicator(
                    isRefreshing = isRefreshing,
                    pullProgress = pullProgress / pullThreshold
                )
            }
        }
    }
}
