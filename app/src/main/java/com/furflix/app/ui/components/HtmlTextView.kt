package com.furflix.app.ui.components

import android.content.Intent
import android.widget.TextView
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.graphics.toColorInt
import androidx.core.text.HtmlCompat
import com.furflix.app.ui.theme.LinkColor

/**
 * HTML 渲染（原文与译文共用；URLSpan 重写为站内导航）。
 * /user/ 链接 → onAuthorClick，/view/ 链接 → onNavigateToPost，其余走系统浏览器。
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
                        if (url.contains("/user/")) {
                            val username = url.substringAfter("/user/").substringBefore("/")
                            if (username.isNotEmpty()) onAuthorClick(username)
                        } else if (url.contains("/view/")) {
                            val id = url.substringAfter("/view/").substringBefore("/")
                            if (id.isNotEmpty()) onNavigateToPost(id)
                        } else {
                            val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url))
                            context.startActivity(intent)
                        }
                    }
                }, start, end, flags)
            }
            textView.text = htmlSpannable
        }
    )
}
