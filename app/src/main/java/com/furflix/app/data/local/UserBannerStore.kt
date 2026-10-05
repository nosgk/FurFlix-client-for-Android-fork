package com.furflix.app.data.local

import com.furflix.app.data.repository.FurRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

/**
 * 关注/粉丝用户卡片的背景图仓库，“我的-关注列表”与用户页关注/粉丝 tab 共用：
 * 同一用户在任何入口预热过，另一处直接命中。串行限速 + 按用户去重，键统一小写
 * （FA 用户名大小写不敏感，两处列表的原始大小写可能不同）。
 *
 * 背景来源跟随设置（资料页横幅 / 最新作品缩略图）：来源切换即作废整份缓存，
 * 重新可见时按新来源重建。只影响列表卡片——用户主页顶部的横幅不经此处。
 */
class UserBannerStore private constructor(private val repository: FurRepository) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private val attempted = ConcurrentHashMap.newKeySet<String>()

    @Volatile
    private var currentSource: String = FurRepository.WATCHLIST_BANNER_SOURCE_BANNER

    private val _banners = MutableStateFlow<Map<String, String>>(emptyMap())
    val banners: StateFlow<Map<String, String>> = _banners.asStateFlow()

    init {
        scope.launch {
            repository.watchlistBannerSourceFlow.collect { source ->
                if (source != currentSource) {
                    mutex.withLock {
                        currentSource = source
                        attempted.clear()
                        _banners.value = emptyMap()
                    }
                }
            }
        }
    }

    /** 可见用户卡片的背景预热：串行限速、去重；失败用户静默跳过。 */
    fun prefetch(usernames: List<String>) {
        val fresh = usernames.filter { it.isNotBlank() && attempted.add(it.lowercase()) }
        if (fresh.isEmpty()) return
        scope.launch {
            mutex.withLock {
                for (name in fresh) {
                    val url = resolveBannerUrl(name)
                    if (url.isNotEmpty()) {
                        _banners.update { it + (name.lowercase() to url) }
                    }
                    delay(200)
                }
            }
        }
    }

    private suspend fun resolveBannerUrl(name: String): String {
        return if (currentSource == FurRepository.WATCHLIST_BANNER_SOURCE_LATEST) {
            // 最新作品缩略图；画廊被过滤为空（无作品/屏蔽词）时回落资料页横幅
            val thumb = runCatching {
                repository.getUserGallery(name, 1).firstOrNull()?.thumbnailUrl ?: ""
            }.getOrDefault("")
            if (thumb.isNotEmpty()) thumb
            else runCatching { repository.getUserProfile(name).bannerUrl }.getOrDefault("")
        } else {
            runCatching { repository.getUserProfile(name).bannerUrl }.getOrDefault("")
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: UserBannerStore? = null

        fun getInstance(repository: FurRepository): UserBannerStore =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: UserBannerStore(repository).also { INSTANCE = it }
            }
    }
}
