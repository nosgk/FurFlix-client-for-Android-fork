package com.furflix.app

import android.app.Application
import coil3.SingletonImageLoader
import com.furflix.app.data.lifecycle.AppLifecycleTracker
import com.furflix.app.data.local.CacheJanitor
import com.furflix.app.data.remote.FurAffinityScraper
import com.furflix.app.data.repository.FurRepository
import com.furflix.app.ui.components.ImageLoaderFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class FurFlixApp : Application() {
    @OptIn(coil3.annotation.DelicateCoilApi::class)
    override fun onCreate() {
        super.onCreate()
        instance = this
        // 前后台单一事实源：图片网络层闸门与后台预热协程都读它
        AppLifecycleTracker.install(this)
        // 全链路 UA 单一来源先于任何网络组件就绪（cf_clearance 的 UA 绑定
        // 要求抓取/图片/下载与验证 WebView 同 UA，见 UserAgent KDoc）
        com.furflix.app.data.remote.UserAgent.init(this)
        FurAffinityScraper.init(this)
        // 磁盘缓存配额先于 loader 构建读定（DataStore 首读毫秒级），避免启动后再
        // 重建清缓存；设置页改配额走"清空重建"路径生效
        runCatching {
            ImageLoaderFactory.diskCacheLimitBytes = runBlocking {
                FurRepository.getInstance(this@FurFlixApp).imageCacheLimitFlow.first()
            }
        }
        // Route ALL Coil loads (even bare AsyncImage calls) through our loader,
        // which carries FA headers/cookies and the GIF decoder for animated avatars.
        SingletonImageLoader.setUnsafe(ImageLoaderFactory.getInstance(this))
        // 缓存碎片清理：旧版 PDF 临时残留 / 陈旧 .tmp / 文档缓存配额修剪
        CacheJanitor.cleanOnce(this)
        // 已下载作品登记表预热（后台线程）：让"软件内查看已下载作品"直接读本地
        // 文件而不重新下载；也覆盖本次改动之前下载的条目（靠文件名里的 postId）
        com.furflix.app.data.local.DownloadedMediaRegistry.warmUpOnce(this)
    }

    companion object {
        lateinit var instance: FurFlixApp
            private set
    }
}
