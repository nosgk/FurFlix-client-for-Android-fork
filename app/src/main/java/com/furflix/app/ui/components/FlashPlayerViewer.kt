package com.furflix.app.ui.components

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.furflix.app.R

/**
 * Flash 全屏播放器：FA 自己用 Ruffle 模拟 SWF（ruffle.js 与 SWF 同在
 * d.furaffinity.net CDN），WebView 以该域为 base 加载本地壳页 → 脚本、wasm、
 * SWF 全部同源，免 CORS/免免授权设置。仅用于 Flash 作品的详情全屏层。
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun FlashPlayerViewer(
    swfUrl: String,
    pageUrl: String,
    onClose: () -> Unit
) {
    BackHandler { onClose() }
    var pageReady by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        val context = LocalContext.current
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                WebView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    // Ruffle 起播不依赖用户手势，避免首帧点击后才出声画
                    settings.mediaPlaybackRequiresUserGesture = false
                    setBackgroundColor(android.graphics.Color.BLACK)
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            pageReady = true
                        }
                    }
                    loadHtml(this, swfUrl)
                }
            },
            onRelease = { it.stopLoading(); it.destroy() }
        )

        if (!pageReady) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center).size(40.dp),
                color = MaterialTheme.colorScheme.primary
            )
        }

        // 顶部操作条：关闭 + 在浏览器打开兜底（Ruffle 兼容性不保证）
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 44.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.5f))
                    .clickable {
                        try {
                            context.startActivity(
                                android.content.Intent(
                                    android.content.Intent.ACTION_VIEW,
                                    android.net.Uri.parse(pageUrl)
                                )
                            )
                        } catch (_: Exception) {
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.OpenInBrowser,
                    contentDescription = stringResource(R.string.cd_open_in_browser),
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(Modifier.size(10.dp))
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.5f))
                    .clickable { onClose() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = stringResource(R.string.cd_close),
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // 兼容性提示角标（Ruffle 对老 SWF 兼容有限，指路浏览器兜底）
        Text(
            text = stringResource(R.string.flash_ruffle_hint),
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.45f),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(14.dp)
        )
    }
}

/** 注入 SWF 地址的本地壳页；d.furaffinity.net 为 base，同域加载 ruffle.js 与 wasm。 */
private fun loadHtml(webView: WebView, swfUrl: String) {
    val safe = swfUrl
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("<", "\\u003C")
        .replace("\n", "")
    val html = """<!DOCTYPE html><html><head>
<meta name="viewport" content="width=device-width, initial-scale=1">
<style>html,body{margin:0;padding:0;height:100%;background:#000;overflow:hidden}
#stage{width:100%;height:100%}ruffle-player{width:100%;height:100%;}</style>
<script src="https://d.furaffinity.net/media/ruffle-0.2.0/ruffle.js"></script>
</head><body><div id="stage"></div><script>
window.RufflePlayer = window.RufflePlayer || {};
window.addEventListener("load", function() {
  try {
    var ruffle = window.RufflePlayer.newest();
    var player = ruffle.createPlayer();
    player.style.width = "100%";
    player.style.height = "100%";
    document.getElementById("stage").appendChild(player);
    player.load("$safe");
  } catch (e) {
    document.title = "RUFFLE_ERROR";
  }
});
</script></body></html>"""
    webView.loadDataWithBaseURL("https://d.furaffinity.net/", html, "text/html", "UTF-8", null)
}
