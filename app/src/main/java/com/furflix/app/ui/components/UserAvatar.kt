package com.furflix.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.furflix.app.ui.theme.DarkSurfaceVariant
import com.furflix.app.ui.theme.SubtleGray

/**
 * 圆形头像(D-003 公共件):远程图片 + 无图时的图标占位,两态共用同一圆形容器。
 *
 * 抽取前,这套"圆形裁剪 + 暗底 + 占位图标"的组合在评论区(3 处)、
 * PDF 查看器(2 处)、作品卡片、翻译组件里各自复制了一遍。
 *
 * @param url 头像地址;空串走占位分支
 * @param contentDescription 图片态的无障碍描述;占位态恒为 null(纯装饰)
 * @param size 外框尺寸;内部图形比外框小 2dp,与抽取前各处的观感一致
 * @param placeholder 占位图标(默认人像;评论区"更多回复"传 MoreHoriz)
 * @param onClick 非空时整枚头像可点(用于跳作者页)
 */
@Composable
fun UserAvatar(
    url: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    placeholder: ImageVector = Icons.Default.Person,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null
) {
    val clickable = if (onClick != null) {
        Modifier.clickable(enabled = enabled, onClick = onClick)
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .size(size)
            .then(clickable),
        contentAlignment = Alignment.Center
    ) {
        if (url.isNotEmpty()) {
            SubcomposeAsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(url)
                    .crossfade(true)
                    .build(),
                imageLoader = ImageLoaderFactory.getInstance(LocalContext.current),
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(size - 2.dp)
                    .clip(CircleShape)
                    .background(DarkSurfaceVariant)
            )
        } else {
            Box(
                modifier = Modifier
                    .size(size - 2.dp)
                    .clip(CircleShape)
                    .background(DarkSurfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    placeholder,
                    contentDescription = null,
                    tint = SubtleGray,
                    modifier = Modifier.size(size * 0.5f)
                )
            }
        }
    }
}

/** 仅占位图标的圆形头像(评论区"继续该楼"等无头像场景)。 */
@Composable
fun PlaceholderAvatar(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp
) {
    UserAvatar(
        url = "",
        contentDescription = null,
        modifier = modifier,
        size = size,
        placeholder = icon
    )
}

/** 头像点击态的背景色(供需要高亮选中头像的场景复用)。 */
val AvatarSurfaceColor @Composable get() = MaterialTheme.colorScheme.surfaceVariant
