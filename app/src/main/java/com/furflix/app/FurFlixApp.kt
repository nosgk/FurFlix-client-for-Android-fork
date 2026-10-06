package com.furflix.app

import android.app.Application
import coil3.SingletonImageLoader
import com.furflix.app.data.remote.FurAffinityScraper
import com.furflix.app.ui.components.ImageLoaderFactory

class FurFlixApp : Application() {
    @OptIn(coil3.annotation.DelicateCoilApi::class)
    override fun onCreate() {
        super.onCreate()
        instance = this
        // UA 全链路单一来源：先按设备真实 WebView 内核版本解析，再建抓取与图片
        // 客户端——版本一致性是 Turnstile 静默签发与 cf_clearance 绑定的前提
        com.furflix.app.data.remote.UserAgent.resolve(this)
        FurAffinityScraper.init(this)
        // Route ALL Coil loads (even bare AsyncImage calls) through our loader,
        // which carries FA headers/cookies and the GIF decoder for animated avatars.
        SingletonImageLoader.setUnsafe(ImageLoaderFactory.getInstance(this))
    }

    companion object {
        lateinit var instance: FurFlixApp
            private set
    }
}
