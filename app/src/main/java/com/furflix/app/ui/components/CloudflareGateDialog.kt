package com.furflix.app.ui.components

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.furflix.app.R
import com.furflix.app.ui.theme.SubtleGray
import com.furflix.app.ui.theme.DarkBackground

/**
 * CF 验证处理模块的 UI 侧：抓取链路命中 Cloudflare 拦截时由
 * CloudflareGate.challengeUrl 驱动弹出，加载被拦页让用户以真实 WebView
 * 完成人机验证（cookie 落入系统 CookieManager）；页面离开挑战态后自动
 * resolve() 回执，挂起的抓取协程随即重试。
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun CloudflareGateDialog(
    url: String,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = DarkBackground
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Shield,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.cf_gate_title),
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            stringResource(R.string.cf_gate_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = SubtleGray
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = stringResource(R.string.cd_close),
                            tint = SubtleGray
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .navigationBarsPadding()
                ) {
                    CloudflareGateWebView(url = url)
                }
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun CloudflareGateWebView(url: String) {
    // 通过回执只发一次：挑战页的 meta-refresh 链会多次 onPageFinished
    var passed by remember { mutableIntStateOf(0) }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            WebView(ctx).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                // 验证 WebView 不设 UA：Android 原生 UA 与平台/TLS 指纹一致，
                // CF 按合法客户端放行（桌面 UA 伪装 Windows 平台会被拒绝）。
                // 同时把运行时 UA 回写单一来源——验证即将签发的 cf_clearance
                // 按 UA 绑定，抓取链路必须与本 WebView 同 UA 才能吃到通行证
                com.furflix.app.data.remote.UserAgent.update(settings.userAgentString)
                android.webkit.CookieManager.getInstance().setAcceptCookie(true)
                android.webkit.CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                        view: WebView?,
                        request: WebResourceRequest?
                    ): Boolean = false

                    override fun onPageFinished(view: WebView?, finishedUrl: String?) {
                        super.onPageFinished(view, finishedUrl)
                        if (passed > 0) return
                        // 挑战通过判据：页面标题/内容离开挑战态；延迟 800ms
                        // 躲开挑战骨架的 meta-refresh 中间帧
                        Handler(Looper.getMainLooper()).postDelayed({
                            view?.evaluateJavascript(
                                "(function(){var t=(document.title||'').toLowerCase();" +
                                    "var h=(document.body?document.body.innerHTML:'');" +
                                    "if(t.indexOf('just a moment')>=0||t.indexOf('checking')>=0||" +
                                    "h.indexOf('_cf_chl_opt')>=0||h.indexOf('cf-error-details')>=0)return'CHALLENGE';" +
                                    "return'OK';})();"
                            ) { result ->
                                val r = result?.removeSurrounding("\"")?.trim() ?: ""
                                if (r == "OK" && passed == 0) {
                                    passed = 1
                                    com.furflix.app.data.remote.CloudflareGate.resolve()
                                }
                            }
                        }, 800)
                    }
                }
                webChromeClient = WebChromeClient()
                loadUrl(url)
            }
        }
    )
}
