package com.furflix.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.furflix.app.ui.theme.DarkOnSurfaceVariant
import com.furflix.app.ui.theme.Dimens

@Composable
fun EmptyState(
    message: String,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Default.Inbox,
    iconSize: androidx.compose.ui.unit.Dp = 56.dp
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            // 用最小高度而非固定高度：文字缩放档拉满或横屏时，固定 200dp 会把
            // 图标+多行文案裁掉；heightIn 保证下限观感不变、上限随内容长。
            .heightIn(min = 200.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(iconSize),
                tint = DarkOnSurfaceVariant.copy(alpha = 0.6f)
            )
            Spacer(modifier = Modifier.height(Dimens.lg))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = DarkOnSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp)
            )
        }
    }
}
