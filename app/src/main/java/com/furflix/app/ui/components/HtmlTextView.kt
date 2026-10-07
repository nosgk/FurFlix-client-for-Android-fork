package com.furflix.app.ui.components

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import android.net.Uri
import android.text.TextUtils
import android.widget.TextView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.graphics.toColorInt
import androidx.core.text.HtmlCompat
import com.furflix.app.data.remote.FaLink
import com.furflix.app.data.remote.parseFaLink
import com.furflix.app.ui.theme.LinkColor
import coil3.asDrawable
import kotlinx.coroutines.launch
import kotlin.math.min

/**
 * HTML 渲染（原文与译文共用；URLSpan 重写为站内导航）。
 * 链接统一经 [parseFaLink] 判定：FA 的作品/用户（含 gallery/favorites/scraps/
 * journals 等子页）链接走站内路由，其余（外部站、FA 搜索页、媒体直链）走系统
 * 浏览器。此前只认 "/user/"、"/view/" 两个子串且不校验 host——/gallery/ 等
 * 链接全被丢给浏览器，表现为"应用内打不开 FA 链接"。
 *
 * 内联图片（FA 简介/描述里的 iconusername 头像，`<a><img></a>`）经
 * Html.ImageGetter 渲染：仅放行 a.furaffinity.net 域，异步经 Coil 加载
 * （内存/磁盘缓存友好，动图头像保留动画），加载完成整段 spannable 重建。
 * 详情页描述与用户页简介共用此组件。
 */
@Composable
fun HtmlTextView(
    html: String,
    onAuthorClick: (String) -> Unit = {},
    onNavigateToPost: (String) -> Unit = {},
    maxLines: Int = Int.MAX_VALUE
) {
    val context = LocalContext.current
    val textColor = "#E0E0E0".toColorInt()
    val linkColor = LinkColor.hashCode()

    // 点击回调经 rememberUpdatedState 固定最新值（spannable 在 remember 中构建，
    // ClickableSpan 必须点击时读最新回调而不是构建期快照）
    val currentOnAuthor by rememberUpdatedState(onAuthorClick)
    val currentOnPost by rememberUpdatedState(onNavigateToPost)

    // 内联头像：url → Drawable（null = 加载中/失败，先跳过）；epoch 驱动重建
    val inlineImages = remember { HashMap<String, Drawable?>() }
    var imageEpoch by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(html) {
        if (!html.contains("<img")) return@LaunchedEffect
        val sources = runCatching {
            org.jsoup.Jsoup.parseBodyFragment(html).select("img[src]").map { it.attr("src") }
        }.getOrDefault(emptyList())
        for (src in sources) {
            val url = normalizeInlineImageUrl(src) ?: continue
            if (inlineImages.containsKey(url)) continue
            inlineImages[url] = null
            scope.launch {
                val drawable = loadInlineAvatarDrawable(context, url)
                inlineImages[url] = drawable
                imageEpoch++
            }
        }
    }

    val rendered = remember(html, imageEpoch) {
        val imageGetter = android.text.Html.ImageGetter { source ->
            normalizeInlineImageUrl(source)?.let { inlineImages[it] }
        }
        val builder = android.text.SpannableStringBuilder(
            HtmlCompat.fromHtml(html, HtmlCompat.FROM_HTML_MODE_COMPACT, imageGetter, null)
        )
        val spans = builder.getSpans(0, builder.length, android.text.style.URLSpan::class.java)
        for (span in spans) {
            val start = builder.getSpanStart(span)
            val end = builder.getSpanEnd(span)
            val flags = builder.getSpanFlags(span)
            val url = span.url
            builder.removeSpan(span)
            builder.setSpan(object : android.text.style.ClickableSpan() {
                override fun onClick(widget: android.view.View) {
                    when (val link = parseFaLink(url)) {
                        is FaLink.Submission -> if (link.id.isNotEmpty()) currentOnPost(link.id)
                        is FaLink.User -> if (link.username.isNotEmpty()) currentOnAuthor(link.username)
                        // 站内搜索深链需完整筛选状态与 tab 切换，描述内罕见，
                        // 交系统浏览器；非 FA 链接同样外开
                        else -> openExternal(context, url)
                    }
                }
            }, start, end, flags)
        }
        builder
    }

    AndroidView(
        factory = { ctx ->
            TextView(ctx).apply {
                setTextColor(textColor)
                setLinkTextColor(linkColor)
                textSize = 15f
                movementMethod = android.text.method.LinkMovementMethod.getInstance()
            }
        },
        update = { textView ->
            textView.maxLines = maxLines
            textView.ellipsize =
                if (maxLines == Int.MAX_VALUE) null else TextUtils.TruncateAt.END
            textView.text = rendered
        }
    )
}

/** 内联图片 URL 归一化：仅放行 FA 头像 CDN（a.furaffinity.net），协议相对补 https。 */
private fun normalizeInlineImageUrl(source: String): String? {
    val s = source.trim()
    val url = when {
        s.startsWith("//") -> "https:$s"
        s.startsWith("http://") -> "https://${s.removePrefix("http://")}"
        s.startsWith("https://") -> s
        else -> return null
    }
    return if (url.contains("//a.furaffinity.net/")) url else null
}

/**
 * 异步取头像 Drawable：走全局 Coil（命中内存/磁盘缓存零流量），按 ~28dp 等比
 * 定界（span 内嵌无法自适应 intrinsic），失败返回 null（静默跳过该图）。
 */
private suspend fun loadInlineAvatarDrawable(context: Context, url: String): Drawable? =
    runCatching {
        val request = coil3.request.ImageRequest.Builder(context)
            .data(url)
            .size(128)
            .allowHardware(false)
            .build()
        val image = (ImageLoaderFactory.getInstance(context).execute(request)
            as? coil3.request.SuccessResult)?.image
            ?: return@runCatching null
        val drawable = image.asDrawable(context.resources)
        val density = context.resources.displayMetrics.density
        val target = (28f * density).toInt().coerceAtLeast(24)
        val w = drawable.intrinsicWidth
        val h = drawable.intrinsicHeight
        if (w > 0 && h > 0) {
            val scale = min(target.toFloat() / w, target.toFloat() / h)
            drawable.setBounds(
                0, 0,
                (w * scale).toInt().coerceAtLeast(1),
                (h * scale).toInt().coerceAtLeast(1)
            )
        } else {
            drawable.setBounds(0, 0, target, target)
        }
        drawable
    }.getOrNull()

private fun openExternal(context: android.content.Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (_: ActivityNotFoundException) {
        // 无浏览器可接手：静默忽略，比崩溃好
    }
}
