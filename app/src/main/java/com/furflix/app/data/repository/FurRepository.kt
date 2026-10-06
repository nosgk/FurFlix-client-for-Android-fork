package com.furflix.app.data.repository

import android.content.Context
import android.annotation.SuppressLint
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.furflix.app.data.model.Submission
import com.furflix.app.data.remote.DynamicThumbnail
import com.furflix.app.data.remote.FurAffinityScraper
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import com.furflix.app.data.local.TagsDatabase

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "furflix_prefs")

@SuppressLint("StaticFieldLeak")
class FurRepository(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }
    private val userNotesSerializer = MapSerializer(String.serializer(), String.serializer())

    companion object {
        private val COOKIES_KEY = stringPreferencesKey("fa_cookies")
        private val USERNAME_KEY = stringPreferencesKey("fa_username")

        /** 详情页布局取值（detailLayoutFlow） */
        const val DETAIL_LAYOUT_AUTO = "auto"
        const val DETAIL_LAYOUT_SINGLE = "single"
        const val DETAIL_LAYOUT_DUAL = "dual"

        /** 双栏比例默认值：左栏主图占比 65%（历史默认 60，应用户"腾出更多主图空间"调整） */
        const val DETAIL_SPLIT_DEFAULT = 65
        const val DETAIL_SPLIT_MIN = 50
        const val DETAIL_SPLIT_MAX = 75

        /** 关注卡片背景来源取值（watchlistBannerSourceFlow） */
        const val WATCHLIST_BANNER_SOURCE_BANNER = "banner"
        const val WATCHLIST_BANNER_SOURCE_LATEST = "latest_thumb"

        /** 关注卡片背景模糊半径上限（dp） */
        const val WATCHLIST_BANNER_BLUR_MAX = 20f

    private val RECENT_SEARCHES_KEY = stringPreferencesKey("recent_searches")
    private val IS_GRID_KEY = booleanPreferencesKey("is_grid")
    private val VIEW_MODE_KEY = stringPreferencesKey("view_mode")
    private val DOWNLOADS_VIEW_MODE_KEY = stringPreferencesKey("downloads_view_mode")
    private val GRID_COLUMNS_KEY = intPreferencesKey("grid_columns")
    private val THUMBNAIL_QUALITY_KEY = intPreferencesKey("thumbnail_quality")
    private val DETAIL_LAYOUT_KEY = stringPreferencesKey("detail_layout")
    private val DETAIL_SPLIT_KEY = intPreferencesKey("detail_split_percent")
    private val WATCHLIST_BANNER_SOURCE_KEY = stringPreferencesKey("watchlist_banner_source")
    private val WATCHLIST_BANNER_BLUR_KEY = floatPreferencesKey("watchlist_banner_blur_dp")
    private val USER_NOTES_KEY = stringPreferencesKey("user_notes")
    private val TEXT_SCALE_KEY = floatPreferencesKey("text_scale")
    private val HIDE_BARS_KEY = booleanPreferencesKey("hide_bars_on_scroll")
    private val CLIPBOARD_LINK_KEY = booleanPreferencesKey("clipboard_link_enabled")
        private val DEFAULT_TAB_KEY = androidx.datastore.preferences.core.intPreferencesKey("default_tab")
        private val THEME_ID_KEY = androidx.datastore.preferences.core.intPreferencesKey("theme_id")
        private val APP_ICON_KEY = stringPreferencesKey("app_icon")
        private val MUTED_WORDS_KEY = stringPreferencesKey("muted_words")
        private val BLOCKED_ARTISTS_KEY = stringPreferencesKey("blocked_artists")
        private val NSFW_ENABLED_KEY = booleanPreferencesKey("nsfw_enabled")
    private val DOWNLOAD_FOLDER_KEY = stringPreferencesKey("download_folder")
    private val LAST_SEARCH_FILTERS_KEY = stringPreferencesKey("last_search_filters")
    private val QUICK_PRESETS_KEY = stringPreferencesKey("quick_presets")

        @Volatile
        private var INSTANCE: FurRepository? = null

        fun getInstance(context: Context): FurRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: FurRepository(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    suspend fun saveCookiesFromWebView(cookies: String, username: String = "") {
        context.dataStore.edit { prefs ->
            prefs[COOKIES_KEY] = cookies
            if (username.isNotEmpty()) {
                prefs[USERNAME_KEY] = username
            }
        }
        if (username.isNotEmpty()) {
            FurAffinityScraper.setUsername(username)
        }
    }

    suspend fun loadSavedCookies(): Boolean {
        val prefs = context.dataStore.data.first()
        val cookies = prefs[COOKIES_KEY] ?: ""
        if (cookies.isNotEmpty()) {
            FurAffinityScraper.setCookiesFromWebView(cookies)
            val savedUsername = prefs[USERNAME_KEY] ?: ""
            // Guard against a previously-corrupted cached username (e.g. "Logged in",
            // which was a scraping bug that grabbed menu text instead of the real name).
            val isValidUsername = savedUsername.isNotEmpty() &&
                    !savedUsername.contains(" ") &&
                    savedUsername.length < 40
            if (isValidUsername) {
                FurAffinityScraper.setUsername(savedUsername)
            } else {
                if (savedUsername.isNotEmpty()) {
                    // Drop the bad cached value so it doesn't keep overriding a fresh,
                    // correctly-parsed username on future logins.
                    context.dataStore.edit { it.remove(USERNAME_KEY) }
                }
                // Re-derive the real username from the site right away instead of leaving
                // it blank until some other screen happens to call verifyLoginAndGetUsername().
                val freshUsername = FurAffinityScraper.verifyLoginAndGetUsername()
                if (!freshUsername.isNullOrEmpty()) {
                    context.dataStore.edit { it[USERNAME_KEY] = freshUsername }
                }
            }
            return true
        }
        return false
    }

    suspend fun logout() {
        context.dataStore.edit { prefs ->
            prefs.remove(COOKIES_KEY)
            prefs.remove(USERNAME_KEY)
        }
        FurAffinityScraper.clearSession()
    }

    fun isLoggedIn(): Boolean = FurAffinityScraper.isLoggedIn()



    suspend fun getUserProfile(username: String) = FurAffinityScraper.getUserProfile(username)

    suspend fun toggleWatch(watchUrl: String) = FurAffinityScraper.toggleWatch(watchUrl)

    suspend fun searchTags(prefix: String, limit: Int = 10): List<String> {
        if (prefix.isBlank()) return emptyList()
        val db = TagsDatabase.getInstance(context)
        return try {
            db.searchTags(prefix, limit)
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun toggleFavorite(favUrl: String) =
        FurAffinityScraper.toggleFavorite(favUrl)

    suspend fun getWatchlistBy(username: String, page: Int) = FurAffinityScraper.getWatchlistBy(username, page)

    suspend fun getWatchlistTo(username: String, page: Int) = FurAffinityScraper.getWatchlistTo(username, page)

    fun getUsername(): String = FurAffinityScraper.getUsername()

    private suspend fun filterSubmissions(submissions: List<Submission>): List<Submission> {
        val prefs = context.dataStore.data.first()
        val mutedWordsRaw = prefs[MUTED_WORDS_KEY] ?: ""
        val nsfwEnabled = prefs[NSFW_ENABLED_KEY] ?: true

        val mutedWords = if (mutedWordsRaw.isNotEmpty()) mutedWordsRaw.split("|||").map { it.lowercase() } else emptyList()

        return submissions.filter { sub ->
            val titleLower = sub.title.lowercase()
            val rating = sub.rating

            val isMutedWord = mutedWords.any { titleLower.contains(it) }
            val isNsfwBlocked = !nsfwEnabled && (rating == "Adult" || rating == "Mature")

            !isMutedWord && !isNsfwBlocked
        }
    }

    suspend fun getBrowse(page: Int = 1, filters: com.furflix.app.data.model.SearchFilters = com.furflix.app.data.model.SearchFilters()): List<Submission> {
        return filterSubmissions(FurAffinityScraper.getBrowsePage(page, filters))
    }

    suspend fun getLatest(page: Int = 1): List<Submission> {
        return filterSubmissions(FurAffinityScraper.getLatestPage(page))
    }



    suspend fun getSubmission(id: String, loadStoryText: Boolean = true): Submission? {
        return FurAffinityScraper.getSubmissionDetails(id, loadStoryText)
    }

    suspend fun getWatchlist(page: Int = 1): List<Submission> {
        return filterSubmissions(FurAffinityScraper.getWatchlistPage(page))
    }

    suspend fun search(query: String, page: Int, filters: com.furflix.app.data.model.SearchFilters = com.furflix.app.data.model.SearchFilters()): List<Submission> {
        return filterSubmissions(FurAffinityScraper.search(query, page, filters))
    }

    /** 清空指定用户页的分页缓存（下拉刷新时调用）。 */
    fun clearUserPagination(username: String) = FurAffinityScraper.clearUserPagination(username)

    suspend fun getUserGalleryFolder(
        username: String,
        folder: com.furflix.app.data.model.GalleryFolder,
        page: Int
    ): List<Submission> {
        val nsfwEnabled = nsfwEnabledFlow.first()
        return filterSubmissions(
            FurAffinityScraper.getUserGalleryFolder(username, folder, page, nsfwEnabled)
        )
    }

    suspend fun getUserGallery(username: String, page: Int): List<Submission> {
        val nsfwEnabled = nsfwEnabledFlow.first()
        return filterSubmissions(FurAffinityScraper.getUserGallery(username, page, nsfwEnabled))
    }

    suspend fun getUserFavorites(username: String, page: Int): List<Submission> {
        val nsfwEnabled = nsfwEnabledFlow.first()
        return filterSubmissions(FurAffinityScraper.getUserFavorites(username, page, nsfwEnabled))
    }

    /** 用户画廊文件夹（画廊页侧栏解析；缓存命中零请求）。 */
    suspend fun getUserGalleryFolders(username: String): List<com.furflix.app.data.model.GalleryFolder> =
        FurAffinityScraper.getUserGalleryFolders(username)

    /** 用户日志列表：与作品列表同一套 NSFW + 屏蔽词过滤（rating 判定）。 */
    suspend fun getUserJournals(username: String, page: Int): List<com.furflix.app.data.model.Journal> {
        val prefs = context.dataStore.data.first()
        val mutedWordsRaw = prefs[MUTED_WORDS_KEY] ?: ""
        val nsfwEnabled = prefs[NSFW_ENABLED_KEY] ?: true
        val mutedWords = if (mutedWordsRaw.isNotEmpty()) mutedWordsRaw.split("|||").map { it.lowercase() } else emptyList()
        return FurAffinityScraper.getUserJournals(username, page).filter { journal ->
            val titleLower = journal.title.lowercase()
            !mutedWords.any { titleLower.contains(it) } &&
                (nsfwEnabled || (journal.rating != "Adult" && journal.rating != "Mature"))
        }
    }

    suspend fun getJournal(journalId: String): com.furflix.app.data.model.Journal? =
        FurAffinityScraper.getJournalDetails(journalId)

    suspend fun saveRecentSearches(searches: List<String>) {
        context.dataStore.edit { prefs ->
            prefs[RECENT_SEARCHES_KEY] = searches.joinToString("|||")
        }
    }

    val recentSearchesFlow: kotlinx.coroutines.flow.Flow<List<String>> = context.dataStore.data.map { prefs ->
        val raw = prefs[RECENT_SEARCHES_KEY] ?: ""
        if (raw.isNotEmpty()) raw.split("|||").filter { it.isNotEmpty() } else emptyList()
    }

    suspend fun addRecentSearch(query: String) {
        val current = loadRecentSearches().toMutableList()
        val trimmedQuery = query.trim()
        if (trimmedQuery.isNotEmpty()) {
            current.remove(trimmedQuery)
            current.add(0, trimmedQuery)
            saveRecentSearches(current.take(20))
        }
    }

    // --- Appearance Preferences ---
    val defaultTabFlow: kotlinx.coroutines.flow.Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[DEFAULT_TAB_KEY] ?: 0
    }

    suspend fun saveDefaultTab(tab: Int) {
        context.dataStore.edit { prefs ->
            prefs[DEFAULT_TAB_KEY] = tab
        }
    }


    val themeFlow: kotlinx.coroutines.flow.Flow<com.furflix.app.ui.theme.ThemePreference> = context.dataStore.data.map { prefs ->
        com.furflix.app.ui.theme.ThemePreference.fromId(prefs[THEME_ID_KEY] ?: 0)
    }

    /**
     * Gallery view mode with legacy-key migration: until the new key is first
     * written, falls back to `is_grid` (true → GRID, false → LIST; MASONRY had
     * no legacy value). The old key is never deleted, so a downgrade keeps
     * working.
     */
    val viewModeFlow: kotlinx.coroutines.flow.Flow<com.furflix.app.data.model.GalleryViewMode> =
        context.dataStore.data.map { prefs ->
            val stored = prefs[VIEW_MODE_KEY]
            if (stored != null) {
                com.furflix.app.data.model.GalleryViewMode.fromId(stored)
            } else if (prefs[IS_GRID_KEY] == false) {
                com.furflix.app.data.model.GalleryViewMode.LIST
            } else {
                com.furflix.app.data.model.GalleryViewMode.GRID
            }
        }

    suspend fun saveViewMode(mode: com.furflix.app.data.model.GalleryViewMode) {
        context.dataStore.edit { prefs ->
            prefs[VIEW_MODE_KEY] = mode.name
        }
    }

    /**
     * 下载页视图模式（与画廊共用 GalleryViewMode 三态）。旧键迁移：本键未写过时
     * 回落 is_grid（true → GRID，false → LIST）；下载页自此不再写 is_grid，
     * 画廊的旧键迁移语义不受下载页切换影响。
     */
    val downloadsViewModeFlow: kotlinx.coroutines.flow.Flow<com.furflix.app.data.model.GalleryViewMode> =
        context.dataStore.data.map { prefs ->
            val stored = prefs[DOWNLOADS_VIEW_MODE_KEY]
            if (stored != null) {
                com.furflix.app.data.model.GalleryViewMode.fromId(stored)
            } else if (prefs[IS_GRID_KEY] == false) {
                com.furflix.app.data.model.GalleryViewMode.LIST
            } else {
                com.furflix.app.data.model.GalleryViewMode.GRID
            }
        }

    suspend fun saveDownloadsViewMode(mode: com.furflix.app.data.model.GalleryViewMode) {
        context.dataStore.edit { prefs ->
            prefs[DOWNLOADS_VIEW_MODE_KEY] = mode.name
        }
    }

    /**
     * 画廊网格列数：0 = 按屏宽自适应（GridCells.Adaptive），2..5 = 固定列数。
     * 默认 2，与历史上写死的 Fixed(2) 保持一致。
     */
    val gridColumnsFlow: kotlinx.coroutines.flow.Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[GRID_COLUMNS_KEY] ?: 2
    }

    suspend fun saveGridColumns(count: Int) {
        context.dataStore.edit { prefs ->
            prefs[GRID_COLUMNS_KEY] = count
        }
    }

    /** 文字缩放倍率：1.0 = 跟随系统（默认），其余档位乘在系统 fontScale 之上。 */
    val textScaleFlow: kotlinx.coroutines.flow.Flow<Float> = context.dataStore.data.map { prefs ->
        prefs[TEXT_SCALE_KEY] ?: 1f
    }

    suspend fun saveTextScale(scale: Float) {
        context.dataStore.edit { prefs ->
            prefs[TEXT_SCALE_KEY] = scale
        }
    }

    /** 滚动时自动隐藏顶/底栏（默认开）。 */
    val hideBarsOnScrollFlow: kotlinx.coroutines.flow.Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[HIDE_BARS_KEY] ?: true
    }

    suspend fun saveHideBarsOnScroll(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[HIDE_BARS_KEY] = enabled
        }
    }

    /** 剪贴板链接识别（回前台检测 FA 链接并询问跳转；默认开）。 */
    val clipboardLinkEnabledFlow: kotlinx.coroutines.flow.Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[CLIPBOARD_LINK_KEY] ?: true
    }

    suspend fun saveClipboardLinkEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[CLIPBOARD_LINK_KEY] = enabled
        }
    }

    /**
     * 缩略图档位（DynamicThumbnail.TIER_AUTO = 自动；其余 = 强制像素档位）。
     * 旧值迁移：早期 1=省流量/2=高清 两档映射到 300/600，语义等价。
     */
    val thumbnailQualityFlow: kotlinx.coroutines.flow.Flow<Int> = context.dataStore.data.map { prefs ->
        when (val stored = prefs[THUMBNAIL_QUALITY_KEY]) {
            null -> DynamicThumbnail.TIER_AUTO
            1 -> 300
            2 -> 600
            else -> stored
        }
    }

    suspend fun saveThumbnailQuality(quality: Int) {
        context.dataStore.edit { prefs ->
            prefs[THUMBNAIL_QUALITY_KEY] = quality
        }
    }

    /**
     * 详情页布局：auto = 屏宽 ≥600dp（平板/横屏）自动左图右文双栏；
     * single / dual 强制指定。
     */
    val detailLayoutFlow: kotlinx.coroutines.flow.Flow<String> = context.dataStore.data.map { prefs ->
        prefs[DETAIL_LAYOUT_KEY] ?: DETAIL_LAYOUT_AUTO
    }

    suspend fun saveDetailLayout(layout: String) {
        context.dataStore.edit { prefs ->
            prefs[DETAIL_LAYOUT_KEY] =
                if (layout == DETAIL_LAYOUT_SINGLE || layout == DETAIL_LAYOUT_DUAL) layout else DETAIL_LAYOUT_AUTO
        }
    }

    /**
     * 双栏比例：左栏主图宽度占比（%），设置里滑杆自由调节（吸附 5% 步进），
     * 右栏信息 = 100 − 该值。
     */
    val detailSplitFlow: kotlinx.coroutines.flow.Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[DETAIL_SPLIT_KEY] ?: DETAIL_SPLIT_DEFAULT
    }

    suspend fun saveDetailSplit(percent: Int) {
        context.dataStore.edit { prefs ->
            prefs[DETAIL_SPLIT_KEY] = percent.coerceIn(DETAIL_SPLIT_MIN, DETAIL_SPLIT_MAX)
        }
    }

    /**
     * 关注/粉丝用户卡片的背景图来源：banner = 资料页横幅（原行为）；
     * latest_thumb = 该用户最新作品的缩略图（分辨率仍走缩略图档位协商）。
     * 只影响列表卡片，用户主页顶部的横幅不受牵连。
     */
    val watchlistBannerSourceFlow: kotlinx.coroutines.flow.Flow<String> = context.dataStore.data.map { prefs ->
        prefs[WATCHLIST_BANNER_SOURCE_KEY] ?: WATCHLIST_BANNER_SOURCE_BANNER
    }

    suspend fun saveWatchlistBannerSource(source: String) {
        context.dataStore.edit { prefs ->
            prefs[WATCHLIST_BANNER_SOURCE_KEY] =
                if (source == WATCHLIST_BANNER_SOURCE_LATEST) source else WATCHLIST_BANNER_SOURCE_BANNER
        }
    }

    /** 关注卡片背景的高斯模糊半径（dp，0 = 清晰不模糊）。 */
    val watchlistBannerBlurFlow: kotlinx.coroutines.flow.Flow<Float> = context.dataStore.data.map { prefs ->
        prefs[WATCHLIST_BANNER_BLUR_KEY] ?: 0f
    }

    suspend fun saveWatchlistBannerBlur(dp: Float) {
        context.dataStore.edit { prefs ->
            prefs[WATCHLIST_BANNER_BLUR_KEY] = dp.coerceIn(0f, WATCHLIST_BANNER_BLUR_MAX)
        }
    }

    /**
     * 用户本地备注：username(小写) → 备注。纯本地数据，不与站点交互；
     * 存空串 = 清除。坏 JSON 视为无备注重来。
     */
    val userNotesFlow: kotlinx.coroutines.flow.Flow<Map<String, String>> = context.dataStore.data.map { prefs ->
        decodeUserNotes(prefs[USER_NOTES_KEY])
    }

    suspend fun saveUserNote(username: String, note: String) {
        context.dataStore.edit { prefs ->
            val current = decodeUserNotes(prefs[USER_NOTES_KEY]).toMutableMap()
            if (note.isBlank()) current.remove(username.lowercase()) else current[username.lowercase()] = note
            prefs[USER_NOTES_KEY] =
                if (current.isEmpty()) "" else json.encodeToString(userNotesSerializer, current)
        }
    }

    private fun decodeUserNotes(raw: String?): Map<String, String> {
        if (raw.isNullOrEmpty()) return emptyMap()
        return runCatching { json.decodeFromString(userNotesSerializer, raw) }.getOrDefault(emptyMap())
    }

    val appIconFlow: kotlinx.coroutines.flow.Flow<com.furflix.app.ui.theme.AppIcon> = context.dataStore.data.map { prefs ->
        com.furflix.app.ui.theme.AppIcon.fromId(prefs[APP_ICON_KEY] ?: "dark")
    }

    suspend fun saveAppIcon(icon: com.furflix.app.ui.theme.AppIcon) {
        context.dataStore.edit { prefs -> prefs[APP_ICON_KEY] = icon.id }
    }

    suspend fun saveTheme(themeId: Int) {
        context.dataStore.edit { prefs -> prefs[THEME_ID_KEY] = themeId }
    }

    suspend fun loadRecentSearches(): List<String> {
        val prefs = context.dataStore.data.first()
        val raw = prefs[RECENT_SEARCHES_KEY] ?: ""
        return if (raw.isNotEmpty()) raw.split("|||").filter { it.isNotEmpty() } else emptyList()
    }

    // --- Content Filters ---

    val mutedWordsFlow: kotlinx.coroutines.flow.Flow<List<String>> = context.dataStore.data.map { prefs ->
        val raw = prefs[MUTED_WORDS_KEY] ?: ""
        if (raw.isNotEmpty()) raw.split("|||") else emptyList()
    }

    suspend fun saveMutedWords(words: List<String>) {
        context.dataStore.edit { prefs -> prefs[MUTED_WORDS_KEY] = words.joinToString("|||") }
    }

    val nsfwEnabledFlow: kotlinx.coroutines.flow.Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[NSFW_ENABLED_KEY] ?: true
    }

    suspend fun saveNsfwEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[NSFW_ENABLED_KEY] = enabled }
    }

    val downloadFolderFlow: kotlinx.coroutines.flow.Flow<String> = context.dataStore.data.map { prefs ->
        prefs[DOWNLOAD_FOLDER_KEY] ?: ""
    }

    suspend fun saveDownloadFolder(uriString: String) {
        context.dataStore.edit { prefs -> prefs[DOWNLOAD_FOLDER_KEY] = uriString }
    }

    // --- Last search filters (JSON in a single key; unknown fields are
    // ignored so adding filter fields never bricks old stored values) ---

    private val filterJson = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }

    suspend fun saveSearchFilters(filters: com.furflix.app.data.model.SearchFilters) {
        context.dataStore.edit { prefs ->
            prefs[LAST_SEARCH_FILTERS_KEY] =
                filterJson.encodeToString(com.furflix.app.data.model.SearchFilters.serializer(), filters)
        }
    }

    /**
     * 快捷预设：保存的搜索查询（标签长按菜单保存，如 "@keywords fox"），
     * 搜索页可一键重放。与最近搜索同款 "|||" 分隔；调用方负责上限。
     */
    val quickPresetsFlow: kotlinx.coroutines.flow.Flow<List<String>> = context.dataStore.data.map { prefs ->
        val raw = prefs[QUICK_PRESETS_KEY] ?: ""
        if (raw.isNotEmpty()) raw.split("|||").filter { it.isNotEmpty() } else emptyList()
    }

    suspend fun saveQuickPresets(presets: List<String>) {
        context.dataStore.edit { prefs ->
            prefs[QUICK_PRESETS_KEY] = presets.joinToString("|||")
        }
    }

    suspend fun loadLastSearchFilters(): com.furflix.app.data.model.SearchFilters? {
        val prefs = context.dataStore.data.first()
        val raw = prefs[LAST_SEARCH_FILTERS_KEY] ?: return null
        return try {
            filterJson.decodeFromString(com.furflix.app.data.model.SearchFilters.serializer(), raw)
        } catch (_: Exception) {
            null
        }
    }
}