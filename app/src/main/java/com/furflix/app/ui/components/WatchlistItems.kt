package com.furflix.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import coil3.compose.AsyncImage
import com.furflix.app.R
import com.furflix.app.data.model.WatchlistUser
import com.furflix.app.data.remote.DynamicThumbnail
import com.furflix.app.ui.theme.*

/** 长按给触觉确认，与画廊卡片长按多选一致。 */
private fun longPressWithHaptic(
    haptic: androidx.compose.ui.hapticfeedback.HapticFeedback,
    callback: (() -> Unit)?
): (() -> Unit)? = callback?.let {
    {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        it()
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WatchlistItem(
    user: WatchlistUser,
    note: String? = null,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null
) {
    val avatarUrl = if (user.avatarUrl.isNotEmpty())
        user.avatarUrl
    else
        "https://a.furaffinity.net/${user.username}.gif"
    val haptic = LocalHapticFeedback.current

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(DarkSurface)
            .combinedClickable(
                onClick = onClick,
                onLongClick = longPressWithHaptic(haptic, onLongClick)
            )
            .padding(start = 0.dp, end = 14.dp, top = 11.dp, bottom = 11.dp)
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(50.dp)
                .clip(RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp))
                .background(FA_Accent)
        )

        Spacer(Modifier.width(12.dp))

        AsyncImage(
            model = avatarUrl,
            contentDescription = stringResource(R.string.cd_avatar_of, user.displayName),
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(50.dp)
                .clip(CircleShape)
                .border(1.5.dp, FA_Accent.copy(alpha = 0.6f), CircleShape)
                .background(DarkSurfaceVariant)
        )

        Spacer(Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = user.displayName,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                color = Color.White
            )
            if (!note.isNullOrEmpty()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = note,
                    fontSize = 12.sp,
                    color = Cyan,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WatchlistGridItem(
    user: WatchlistUser,
    bannerUrl: String? = null,
    blurDp: Float = 0f,
    note: String? = null,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null
) {
    val avatarUrl = if (user.avatarUrl.isNotEmpty())
        user.avatarUrl
    else
        "https://a.furaffinity.net/${user.username}.gif"
    val haptic = LocalHapticFeedback.current

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(DarkSurface)
            .combinedClickable(
                onClick = onClick,
                onLongClick = longPressWithHaptic(haptic, onLongClick)
            )
    ) {
        // 用户自己设定的个人页横幅（或最新作品缩略图）作为卡片背景；压一层渐变保证名字可读。
        // 分辨率经 DynamicThumbnail 按卡片实际尺寸与全局缩略图档位协商——"最新作品"来源
        // 本身就是缩略图 URL，档位偏好在此处落地。
        if (!bannerUrl.isNullOrEmpty()) {
            BoxWithConstraints(modifier = Modifier.matchParentSize()) {
                val widthPx = with(LocalDensity.current) { maxWidth.toPx() }
                val heightPx = with(LocalDensity.current) { maxHeight.toPx() }
                val quality = LocalThumbnailQuality.current
                val displayUrl = remember(bannerUrl, widthPx, heightPx, quality) {
                    DynamicThumbnail(bannerUrl).bestThumbnailUrl(widthPx, heightPx, quality)
                }
                AsyncImage(
                    model = displayUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .matchParentSize()
                        .then(
                            if (blurDp > 0f) Modifier.blur(blurDp.dp, BlurredEdgeTreatment.Unbounded)
                            else Modifier
                        )
                )
            }
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Black.copy(alpha = 0.15f), Color.Black.copy(alpha = 0.55f))
                        )
                    )
            )
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            AsyncImage(
                model = avatarUrl,
                contentDescription = stringResource(R.string.cd_avatar_of, user.displayName),
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .border(1.5.dp, FA_Accent.copy(alpha = 0.6f), CircleShape)
                    .background(DarkSurfaceVariant)
            )

            Spacer(Modifier.height(12.dp))

            Text(
                text = user.displayName,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!note.isNullOrEmpty()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = note,
                    fontSize = 11.sp,
                    color = Cyan,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * 长按用户卡片弹出的本地备注编辑框：纯本地数据，存空 = 清除。
 */
@Composable
fun UserNoteDialog(
    user: WatchlistUser,
    currentNote: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf(currentNote) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkSurface,
        titleContentColor = Color.White,
        textContentColor = SubtleGray,
        title = { Text(stringResource(R.string.user_note_title, user.displayName)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text(stringResource(R.string.user_note_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(text) }) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            if (currentNote.isNotEmpty()) {
                TextButton(onClick = { onSave("") }) {
                    Text(stringResource(R.string.user_note_clear), color = MaterialTheme.colorScheme.error)
                }
            }
            Spacer(Modifier.width(4.dp))
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}
