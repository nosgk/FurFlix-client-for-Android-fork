package com.furflix.app.ui.components

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.TextView
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.graphics.toColorInt
import androidx.core.text.HtmlCompat
import com.furflix.app.data.remote.FaLink
import com.furflix.app.data.remote.parseFaLink
import com.furflix.app.ui.theme.LinkColor

/**
 * HTML 渲染（原文与译文共用；URLSpan 重写为站内导航）。
 * 链接统一经 [parseFaLink] 判定：FA 的作品/用户（含 gallery/favorites/scraps/
 * journals 等子页）链接走站内路由，其余（外部站、FA 搜索页、媒体直链）走系统
 * 浏览器。此前只认 "/user/"、"/view/" 两个子串且不校验 host——/gallery/ 等
 * 链接全被丢给浏览器，表现为"应用内打不开 FA 链接"。
 * 详情页描述与日志正文共用此组件。
 */
@Composable
fun HtmlTextView(
    html: String,
    onAuthorClick: (String) -> Unit = {},
    onNavigateToPost: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val textColor = "#E0E0E0".toColorInt()
    val linkColor = LinkColor.hashCode()
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
            val htmlSpannable = android.text.SpannableStringBuilder(
                HtmlCompat.fromHtml(html, HtmlCompat.FROM_HTML_MODE_COMPACT)
            )
            val spans = htmlSpannable.getSpans(0, htmlSpannable.length, android.text.style.URLSpan::class.java)
            for (span in spans) {
                val start = htmlSpannable.getSpanStart(span)
                val end = htmlSpannable.getSpanEnd(span)
                val flags = htmlSpannable.getSpanFlags(span)
                val url = span.url
                htmlSpannable.removeSpan(span)
                htmlSpannable.setSpan(object : android.text.style.ClickableSpan() {
                    override fun onClick(widget: android.view.View) {
                        when (val link = parseFaLink(url)) {
                            is FaLink.Submission -> if (link.id.isNotEmpty()) onNavigateToPost(link.id)
                            is FaLink.User -> if (link.username.isNotEmpty()) onAuthorClick(link.username)
                            // 站内搜索深链需完整筛选状态与 tab 切换，描述内罕见，
                            // 交系统浏览器；非 FA 链接同样外开
                            else -> openExternal(context, url)
                        }
                    }
                }, start, end, flags)
            }
            textView.text = htmlSpannable
        }
    )
}

private fun openExternal(context: android.content.Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (_: ActivityNotFoundException) {
        // 无浏览器可接手：静默忽略，比崩溃好
    }
}
