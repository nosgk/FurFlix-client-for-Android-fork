package com.furflix.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.furflix.app.R
import com.furflix.app.ui.theme.BorderDark
import com.furflix.app.ui.theme.CardBackground
import com.furflix.app.ui.theme.DarkBackground
import com.furflix.app.ui.theme.DarkSurfaceVariant
import com.furflix.app.ui.theme.SubtleGray

/** 主媒体区的一个操作入口（阅读文档 / 查看页面 / 从描述读故事）。 */
private data class HeroAction(
    val label: String,
    val icon: ImageVector,
    val primary: Boolean,
    val onClick: () -> Unit
)

/**
 * 文档/store 类作品（doc/docx/rtf/odt/txt/pdf）主媒体区：封面（若有）+
 * 文件元信息卡 + [onOpenViewer] 非空时的全屏查看器入口（PDF 进自研 PDF
 * 查看器，其余进文档查看器）。不自动载入原件——正文/翻译/查找由查看器承担；
 * 为空时仅下载提示，文件交给系统应用打开。
 *
 * [badgeLabel]/[badgeIcon] 为封面角标的内容数据（DOC/PDF 等格式名，同卡片
 * 徽标，不进 i18n 资源体系），按调用方文件类型传入。
 *
 * [onReadDescription] 非空时并排给出「从作品描述查看故事」——不少作者把正文
 * 直接写在作品描述里（store 类尤其常见），该入口把描述当正文送进文档查看器。
 * [onOpenPageViewer] 供 PDF 并排给出「查看页面」（渲染版查看器）：文本抽取失败
 * 的扫描件仍能翻页阅读。
 */
@Composable
fun DocumentHeroContent(
    fileName: String,
    fileSize: String,
    coverUrl: String?,
    modifier: Modifier = Modifier,
    badgeLabel: String = "DOC",
    badgeIcon: ImageVector = Icons.Default.Description,
    onOpenViewer: (() -> Unit)? = null,
    onReadDescription: (() -> Unit)? = null,
    onOpenPageViewer: (() -> Unit)? = null
) {
    val actions = buildList {
        if (onOpenViewer != null) {
            add(
                HeroAction(
                    label = stringResource(R.string.doc_open_viewer),
                    icon = Icons.AutoMirrored.Filled.MenuBook,
                    primary = true,
                    onClick = onOpenViewer
                )
            )
        }
        if (onOpenPageViewer != null) {
            add(
                HeroAction(
                    label = stringResource(R.string.doc_open_pages),
                    icon = Icons.Default.PictureAsPdf,
                    primary = false,
                    onClick = onOpenPageViewer
                )
            )
        }
        if (onReadDescription != null) {
            add(
                HeroAction(
                    label = stringResource(R.string.doc_read_description),
                    icon = Icons.Default.Article,
                    primary = false,
                    onClick = onReadDescription
                )
            )
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .fillMaxWidth()
            .background(DarkBackground)
            .padding(horizontal = 24.dp, vertical = 28.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth(0.55f)
                .aspectRatio(1f)
                .clip(RoundedCornerShape(24.dp))
                .background(DarkSurfaceVariant)
                .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f), RoundedCornerShape(24.dp))
        ) {
            if (!coverUrl.isNullOrEmpty()) {
                AsyncImage(
                    model = coverUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                // 覆盖一层文档徽标，避免被误认为图片作品
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color.Black.copy(alpha = 0.55f),
                    modifier = Modifier.align(Alignment.BottomCenter).padding(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            badgeIcon,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(text = badgeLabel, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            } else {
                Icon(
                    badgeIcon,
                    contentDescription = null,
                    tint = SubtleGray,
                    modifier = Modifier.size(64.dp)
                )
            }
        }
        if (fileName.isNotEmpty()) {
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = fileName,
                fontSize = 13.sp,
                color = Color.White,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )
        }
        if (fileSize.isNotEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = fileSize, fontSize = 12.sp, color = SubtleGray)
        }
        if (actions.isNotEmpty()) {
            Spacer(modifier = Modifier.height(14.dp))
            Row(
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                actions.forEach { action ->
                    Surface(
                        onClick = action.onClick,
                        shape = RoundedCornerShape(20.dp),
                        color = if (action.primary) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                        } else {
                            CardBackground
                        },
                        border = BorderStroke(
                            1.dp,
                            if (action.primary) {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                            } else {
                                BorderDark
                            }
                        )
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp)
                        ) {
                            Icon(
                                action.icon,
                                contentDescription = null,
                                tint = if (action.primary) MaterialTheme.colorScheme.primary else Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(7.dp))
                            Text(
                                text = action.label,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (action.primary) MaterialTheme.colorScheme.primary else Color.White
                            )
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = stringResource(R.string.doc_download_hint),
            fontSize = 12.sp,
            color = SubtleGray,
            textAlign = TextAlign.Center
        )
    }
}
