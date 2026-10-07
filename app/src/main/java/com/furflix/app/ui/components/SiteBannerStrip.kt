package com.furflix.app.ui.components

import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.furflix.app.R

/**
 * 今日站点横幅条（FA site-banner，原始 1850×300）：供画廊顶栏等页头位置
 * 完整条带展示（不裁切），样式与启动页横屏条带一致；图片加载失败时不占位。
 */
@Composable
fun SiteBannerStrip(
    url: String,
    modifier: Modifier = Modifier
) {
    var loadFailed by remember(url) { mutableStateOf(false) }
    if (loadFailed) return
    AsyncImage(
        model = ImageRequest.Builder(LocalContext.current)
            .data(url)
            .crossfade(300)
            .build(),
        imageLoader = ImageLoaderFactory.getInstance(LocalContext.current),
        contentDescription = stringResource(R.string.cd_site_banner),
        modifier = modifier
            .padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 8.dp)
            .fillMaxWidth()
            .aspectRatio(1850f / 300f)
            .clip(RoundedCornerShape(14.dp)),
        contentScale = ContentScale.Fit,
        onError = { loadFailed = true }
    )
}
