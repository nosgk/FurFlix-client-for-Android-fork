package com.furflix.app

import android.app.Application
import coil3.SingletonImageLoader
import com.furflix.app.data.lifecycle.AppLifecycleTracker
import com.furflix.app.data.remote.FurAffinityScraper
import com.furflix.app.ui.components.ImageLoaderFactory

class FurFlixApp : Application() {
    @OptIn(coil3.annotation.DelicateCoilApi::class)
    override fun onCreate() {
        super.onCreate()
        instance = this
        // 前后台单一事实源：图片网络层闸门与后台预热协程都读它
        AppLifecycleTracker.install(this)
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
