package com.furflix.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.furflix.app.ui.theme.DarkOnSurfaceVariant
import com.furflix.app.ui.theme.Dimens

@Composable
fun ErrorState(
    message: String,
    modifier: Modifier = Modifier,
    iconSize: androidx.compose.ui.unit.Dp = 64.dp,
    /** 可选重试：传了才显示按钮，避免各页面各写一套"图标+文案+重试"。 */
    retryLabel: String? = null,
    onRetry: (() -> Unit)? = null
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            // 同 EmptyState：最小高度而非固定高度，大字号/横屏下不裁切
            .heightIn(min = 200.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.ErrorOutline,
                contentDescription = null,
                modifier = Modifier.size(iconSize),
                tint = DarkOnSurfaceVariant
            )
            Spacer(modifier = Modifier.height(Dimens.lg))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = DarkOnSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp)
            )
            if (retryLabel != null && onRetry != null) {
                Spacer(modifier = Modifier.height(Dimens.sm))
                TextButton(onClick = onRetry) { Text(retryLabel) }
            }
        }
    }
}
