package com.furflix.app

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Density
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import android.os.Build
import coil3.SingletonImageLoader
import coil3.gif.GifDecoder
import coil3.gif.AnimatedImageDecoder
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.furflix.app.data.remote.parseFaLink
import com.furflix.app.ui.components.ImageLoaderFactory
import com.furflix.app.ui.navigation.FurFlixNavGraph
import com.furflix.app.ui.screens.SplashScreen
import com.furflix.app.ui.theme.FurFlixTheme
import com.furflix.app.ui.theme.AppIcon
import com.furflix.app.utils.IconManager
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : AppCompatActivity() {
    private var pendingAppIcon: AppIcon? = null

    /** 外部 VIEW intent 与剪贴板弹窗共用的链接事件通道（NavGraph 消费后置回 null） */
    private val deepLinkUri = MutableStateFlow<Uri?>(null)

    @OptIn(coil3.annotation.DelicateCoilApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        val imageLoader = ImageLoaderFactory.getInstance(this)
            .newBuilder()
            .components {
                if (Build.VERSION.SDK_INT >= 28) {
                    add(AnimatedImageDecoder.Factory())
                } else {
                    add(GifDecoder.Factory())
                }
            }
            .build()
        SingletonImageLoader.setUnsafe(imageLoader)

        enableEdgeToEdge()
        // 冷启动即带 VIEW intent（浏览器/App Links 唤起）
        handleViewIntent(intent)
        setContent {
            val repository = remember { com.furflix.app.data.repository.FurRepository.getInstance(this) }
            val themePref by repository.themeFlow.collectAsState(initial = com.furflix.app.ui.theme.ThemePreference.DEFAULT)
            val appIcon by repository.appIconFlow.collectAsState(initial = null)
            val textScale by repository.textScaleFlow.collectAsState(initial = 1f)

            if (appIcon != null) {
                pendingAppIcon = appIcon
            }

            // 文字缩放：用户倍率乘在系统 fontScale 之上（1.0 = 跟随系统）
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, density.fontScale * textScale)
            ) {
            FurFlixTheme(themePreference = themePref) {
                var showSplash by remember { mutableStateOf(true) }

                // ── 剪贴板链接识别：回前台检查一次；同一段内容每进程只询问一次，
                //    设置→通用 提供 开/关（clipboardLinkEnabledFlow）──
                val clipboardLinkEnabled by repository.clipboardLinkEnabledFlow.collectAsState(initial = true)
                var pendingClipboardLink by remember { mutableStateOf<String?>(null) }
                val lastCheckedClipboard = remember { mutableStateOf<String?>(null) }
                LifecycleResumeEffect(clipboardLinkEnabled) {
                    if (clipboardLinkEnabled) {
                        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                        val text = clipboard?.primaryClip?.getItemAt(0)?.text?.toString()
                        if (!text.isNullOrEmpty() && text != lastCheckedClipboard.value) {
                            lastCheckedClipboard.value = text
                            if (parseFaLink(text) != null) {
                                pendingClipboardLink = text
                            }
                        }
                    }
                    onPauseOrDispose { }
                }

                if (showSplash) {
                    SplashScreen(
                        onSplashFinished = { showSplash = false },
                        repository = repository
                    )
                } else {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        FurFlixNavGraph(deepLinkUri = deepLinkUri)

                        pendingClipboardLink?.let { link ->
                            AlertDialog(
                                onDismissRequest = { pendingClipboardLink = null },
                                title = { Text(stringResource(R.string.clipboard_link_dialog_title)) },
                                text = { Text(link) },
                                confirmButton = {
                                    TextButton(onClick = {
                                        pendingClipboardLink = null
                                        deepLinkUri.value = Uri.parse(
                                            if (link.startsWith("http")) link else "https://$link"
                                        )
                                    }) {
                                        Text(stringResource(R.string.link_open))
                                    }
                                },
                                dismissButton = {
                                    TextButton(onClick = { pendingClipboardLink = null }) {
                                        Text(stringResource(R.string.link_dismiss))
                                    }
                                }
                            )
                        }
                    }
                }
            }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // singleTask：应用在后台时点链接经此分发进现有导航栈
        handleViewIntent(intent)
    }

    private fun handleViewIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_VIEW) {
            intent.data?.let { deepLinkUri.value = it }
        }
    }

    override fun onStop() {
        super.onStop()
        pendingAppIcon?.let { icon ->
            IconManager.applyPendingIcon(this, icon)
        }
    }
}
