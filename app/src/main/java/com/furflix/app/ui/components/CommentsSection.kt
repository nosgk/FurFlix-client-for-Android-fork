package com.furflix.app.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.SubcomposeAsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.furflix.app.R
import com.furflix.app.data.model.Comment
import com.furflix.app.data.model.CommentNode
import com.furflix.app.data.model.buildTree
import com.furflix.app.data.model.recursiveCount
import com.furflix.app.data.model.recursivePath
import com.furflix.app.ui.theme.BorderDark
import com.furflix.app.ui.theme.CardBackground
import com.furflix.app.ui.theme.CommentBodyColor
import com.furflix.app.ui.theme.DarkSurfaceVariant
import com.furflix.app.ui.theme.MutedText
import com.furflix.app.ui.theme.SubtleGray
import com.furflix.app.translate.ui.TranslatableAutoEffect
import com.furflix.app.translate.ui.TranslatableText
import com.furflix.app.translate.ui.TranslationCategory
import com.furflix.app.translate.ui.TranslationIconButton
import com.furflix.app.translate.ui.rememberTranslationEntry
import com.furflix.app.translate.ui.rememberTranslationPrefs
import kotlinx.coroutines.delay
import java.text.DateFormat
import java.util.Date
import java.util.Locale

private val STEP = 16.dp
private val AVATAR_SIZE = 36.dp

/**
 * Minimal topology snapshot carried by every flattened row; drives indentation
 * and the stateless connector lines. [ancestorsContinue][i] says whether the
 * ancestor column i still has following siblings (i.e. its vertical passes
 * through this row).
 */
data class ThreadInfo(
    val depth: Int,
    val ancestorsContinue: List<Boolean>,
    val hasFollowingSibling: Boolean,
    val hasChildren: Boolean
)

/** One rendered line: a comment, or a "N replies" continue marker (comment = null). */
internal data class FlatCommentRow(
    val comment: Comment?,
    val info: ThreadInfo,
    val foldedReplyCount: Int = 0,
    val foldedParentCid: String? = null
)

/** In-place focus state: subtree rooted at [focusedCid], shown under [threadRootCid]'s context. */
private data class CommentFocus(val threadRootCid: String, val focusedCid: String)

/**
 * Threads FA's flat comments into an indented tree with connector lines.
 * Folding cutoff is derived from the container width (not per-row measuring,
 * which jitters) with a sp→dp conversion so larger system fonts inline fewer
 * levels. Branches deeper than the cutoff collapse into a "N replies" row
 * that focuses the subtree in place — the detail page is a HorizontalPager,
 * so there is no nav destination to push a separate screen onto.
 */
@Composable
fun CommentsSection(
    comments: List<Comment>,
    onAuthorClick: (String) -> Unit
) {
    val tree = remember(comments) { comments.buildTree() }
    val focusStack = remember { mutableStateListOf<CommentFocus>() }

    BackHandler(enabled = focusStack.isNotEmpty()) {
        focusStack.removeAt(focusStack.lastIndex)
    }

    BoxWithConstraints {
        val density = LocalDensity.current
        val minContentWidth = with(density) { 220.sp.toDp() }
        val cutoff = if (maxWidth <= 0.dp) {
            5
        } else {
            maxOf(1, ((maxWidth - 24.dp - minContentWidth) / STEP).toInt())
        }

        val focus = focusStack.lastOrNull()
        if (focus == null) {
            val rows = remember(tree, cutoff) { flattenRows(tree, cutoff) }
            Column {
                rows.forEach { row ->
                    val comment = row.comment
                    if (comment == null) {
                        ContinueThreadRow(
                            info = row.info,
                            count = row.foldedReplyCount,
                            onOpen = {
                                row.foldedParentCid?.let { parentCid ->
                                    val rootCid = tree.recursivePath(parentCid)?.first()?.comment?.id
                                    if (rootCid != null) focusStack.add(CommentFocus(rootCid, parentCid))
                                }
                            }
                        )
                    } else {
                        CommentThreadRow(
                            comment = comment,
                            info = row.info,
                            onAuthorClick = onAuthorClick
                        )
                    }
                }
            }
        } else {
            FocusedThread(
                tree = tree,
                focus = focus,
                cutoff = cutoff,
                onAuthorClick = onAuthorClick,
                onOpenFocus = { next -> focusStack.add(next) },
                onBack = { focusStack.removeAt(focusStack.lastIndex) }
            )
        }
    }
}

/**
 * Recursively flattens the tree into rows, replacing any branch that would
 * start deeper than [cutoff] with a single continue marker under its parent.
 */
private fun flattenRows(
    nodes: List<CommentNode>,
    cutoff: Int,
    depth: Int = 0,
    ancestorsContinue: List<Boolean> = emptyList(),
    out: MutableList<FlatCommentRow> = mutableListOf()
): List<FlatCommentRow> {
    nodes.forEachIndexed { index, node ->
        val hasFollowingSibling = index < nodes.lastIndex
        val hasChildren = node.answers.isNotEmpty()
        out += FlatCommentRow(
            comment = node.comment,
            info = ThreadInfo(depth, ancestorsContinue, hasFollowingSibling, hasChildren)
        )
        if (hasChildren && depth + 1 > cutoff) {
            out += FlatCommentRow(
                comment = null,
                info = ThreadInfo(depth + 1, ancestorsContinue + hasFollowingSibling, false, false),
                foldedReplyCount = node.answers.recursiveCount(),
                foldedParentCid = node.comment.id
            )
        } else {
            flattenRows(node.answers, cutoff, depth + 1, ancestorsContinue + hasFollowingSibling, out)
        }
    }
    return out
}

@Composable
private fun FocusedThread(
    tree: List<CommentNode>,
    focus: CommentFocus,
    cutoff: Int,
    onAuthorClick: (String) -> Unit,
    onOpenFocus: (CommentFocus) -> Unit,
    onBack: () -> Unit
) {
    val path = remember(tree, focus) { tree.recursivePath(focus.focusedCid) }
    if (path == null) {
        onBack()
        return
    }
    val threadRoot = path.first()
    val focused = path.last()
    val hiddenParents = path.size - 2

    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(CardBackground)
                .clickable(onClick = onBack)
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.comments_show_all),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.comments_show_all),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (path.size > 1) {
            CommentThreadRow(
                comment = threadRoot.comment,
                info = ThreadInfo(0, emptyList(), false, threadRoot.answers.isNotEmpty()),
                onAuthorClick = onAuthorClick
            )
            Text(
                text = stringResource(R.string.comments_parents_hidden, hiddenParents),
                style = MaterialTheme.typography.bodySmall,
                color = MutedText,
                modifier = Modifier.padding(start = STEP, top = 4.dp, bottom = 2.dp)
            )
        }

        val focusRows = remember(focused, cutoff) {
            buildList {
                add(
                    FlatCommentRow(
                        comment = focused.comment,
                        info = ThreadInfo(0, emptyList(), false, focused.answers.isNotEmpty())
                    )
                )
                if (focused.answers.isNotEmpty()) {
                    if (1 > cutoff) {
                        add(
                            FlatCommentRow(
                                comment = null,
                                info = ThreadInfo(1, listOf(false), false, false),
                                foldedReplyCount = focused.answers.recursiveCount(),
                                foldedParentCid = focused.comment.id
                            )
                        )
                    } else {
                        flattenRows(focused.answers, cutoff, depth = 1, ancestorsContinue = listOf(false), out = this)
                    }
                }
            }
        }
        focusRows.forEachIndexed { index, row ->
            val comment = row.comment
            if (comment == null) {
                ContinueThreadRow(
                    info = row.info,
                    count = row.foldedReplyCount,
                    onOpen = {
                        row.foldedParentCid?.let { onOpenFocus(CommentFocus(focus.threadRootCid, it)) }
                    }
                )
            } else {
                CommentThreadRow(
                    comment = comment,
                    info = row.info,
                    onAuthorClick = onAuthorClick,
                    highlight = index == 0
                )
            }
        }
    }
}

@Composable
private fun CommentThreadRow(
    comment: Comment,
    info: ThreadInfo,
    onAuthorClick: (String) -> Unit,
    highlight: Boolean = false
) {
    val prefs = rememberTranslationPrefs()
    val entry = rememberTranslationEntry(comment.id, comment.text, TranslationCategory.COMMENT)
    TranslatableAutoEffect(entry, prefs)

    var flashOn by remember(comment.id) { mutableStateOf(false) }
    val flashColor by animateColorAsState(
        targetValue = if (flashOn) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else Color.Transparent,
        animationSpec = tween(300),
        label = "comment_flash"
    )
    LaunchedEffect(highlight) {
        if (highlight) {
            repeat(5) {
                flashOn = !flashOn
                delay(300)
            }
            flashOn = false
        }
    }

    val lineColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.30f)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .drawWithContent {
                drawContent()
                drawThreadConnectors(info, lineColor)
            }
            .background(flashColor)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .padding(start = info.depth * STEP, end = 12.dp, top = 8.dp, bottom = 8.dp)
        ) {
            Box(
                modifier = Modifier.size(AVATAR_SIZE),
                contentAlignment = Alignment.Center
            ) {
                if (comment.authorAvatar.isNotEmpty()) {
                    SubcomposeAsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(comment.authorAvatar)
                            .crossfade(true)
                            .build(),
                        imageLoader = ImageLoaderFactory.getInstance(LocalContext.current),
                        contentDescription = comment.author,
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(DarkSurfaceVariant)
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(DarkSurfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Person,
                            contentDescription = null,
                            tint = SubtleGray,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = comment.author.ifEmpty {
                            comment.authorUsername.ifEmpty { stringResource(R.string.comments_hidden_author) }
                        },
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false).clickable {
                            if (comment.authorUsername.isNotEmpty()) onAuthorClick(comment.authorUsername)
                        }
                    )
                    if (comment.title.isNotEmpty()) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = comment.title,
                            fontSize = 11.sp,
                            color = SubtleGray,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    if (comment.text.isNotEmpty()) {
                        TranslationIconButton(entry = entry, enabled = prefs.enabled)
                    }
                    if (comment.timestamp > 0) {
                        val dateStr = remember(comment.timestamp) {
                            try {
                                val df = DateFormat.getDateInstance(DateFormat.MEDIUM, Locale.getDefault())
                                df.format(Date(comment.timestamp * 1000))
                            } catch (_: Exception) {
                                ""
                            }
                        }
                        if (dateStr.isNotEmpty()) {
                            Text(text = dateStr, fontSize = 11.sp, color = MutedText)
                        }
                    }
                }

                if (comment.text.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    TranslatableText(
                        entry = entry,
                        prefs = prefs,
                        style = TextStyle(fontSize = 13.sp, color = CommentBodyColor, lineHeight = 18.sp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ContinueThreadRow(
    info: ThreadInfo,
    count: Int,
    onOpen: () -> Unit
) {
    val lineColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.30f)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .drawWithContent {
                drawContent()
                drawThreadConnectors(info, lineColor)
            }
            .clickable(onClick = onOpen)
            .padding(start = info.depth * STEP, end = 12.dp, top = 6.dp, bottom = 6.dp)
    ) {
        Box(
            modifier = Modifier.size(AVATAR_SIZE),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(DarkSurfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.MoreHoriz,
                    contentDescription = null,
                    tint = SubtleGray,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(10.dp))

        Text(
            text = stringResource(R.string.comments_replies_count, count),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Icon(
            Icons.Default.ChevronRight,
            contentDescription = null,
            tint = MutedText,
            modifier = Modifier.size(16.dp)
        )
    }
}

/**
 * Stateless connector lines (same math as the iOS implementation): ancestor
 * pass-through verticals, this row's tee/elbow into its avatar, and the stem
 * that feeds its children. Drawn in the row foreground so indentation padding
 * never clips the lines.
 */
private fun DrawScope.drawThreadConnectors(info: ThreadInfo, color: Color) {
    val s = STEP.toPx()
    val strokeW = 1.5.dp.toPx()
    val rowPadding = 8.dp.toPx()
    val avatarRadius = AVATAR_SIZE.toPx() / 2f
    val cy = rowPadding + avatarRadius
    val radius = s / 2f

    val columns = maxOf(info.depth - 1, 0)
    for (col in 0 until columns) {
        if (col < info.ancestorsContinue.size && info.ancestorsContinue[col]) {
            val x = col * s + s / 2f
            drawLine(color, Offset(x, 0f), Offset(x, size.height), strokeW)
        }
    }

    if (info.depth >= 1) {
        val ownX = (info.depth - 1) * s + s / 2f
        val avatarX = info.depth * s
        val path = Path()
        path.moveTo(ownX, 0f)
        path.lineTo(ownX, cy - radius)
        if (info.hasFollowingSibling) {
            path.moveTo(ownX, cy + radius)
            path.lineTo(ownX, size.height)
        }
        path.quadraticTo(ownX, cy, ownX + radius, cy)
        path.lineTo(avatarX, cy)
        drawPath(path, color, style = Stroke(strokeW))
    }

    if (info.hasChildren) {
        val childX = info.depth * s + s / 2f
        drawLine(color, Offset(childX, cy + avatarRadius), Offset(childX, size.height), strokeW)
    }
}
