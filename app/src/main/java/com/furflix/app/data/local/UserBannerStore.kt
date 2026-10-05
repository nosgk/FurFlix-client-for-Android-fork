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

/**
 * 关注/粉丝用户卡片的背景图仓库，“我的-关注列表”与用户页关注/粉丝 tab 共用：
 * 同一用户在任何入口预热过，另一处直接命中。串行限速 + 按用户去重，键统一小写
 * （FA 用户名大小写不敏感，两处列表的原始大小写可能不同）。
 */
class UserBannerStore private constructor(private val repository: FurRepository) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private val attempted = mutableSetOf<String>()

    private val _banners = MutableStateFlow<Map<String, String>>(emptyMap())
    val banners: StateFlow<Map<String, String>> = _banners.asStateFlow()

    /** 可见用户卡片的背景预热：VM 侧串行限速、去重；失败用户静默跳过。 */
    fun prefetch(usernames: List<String>) {
        val fresh = usernames.filter { it.isNotBlank() && attempted.add(it.lowercase()) }
        if (fresh.isEmpty()) return
        scope.launch {
            mutex.withLock {
                for (name in fresh) {
                    val url = runCatching { repository.getUserProfile(name).bannerUrl }.getOrDefault("")
                    if (url.isNotEmpty()) {
                        _banners.update { it + (name.lowercase() to url) }
                    }
                    delay(200)
                }
            }
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
