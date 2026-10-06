package com.furflix.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.furflix.app.data.remote.DynamicThumbnail
import com.furflix.app.data.remote.FaLink
import com.furflix.app.data.remote.parseFaLink
import com.furflix.app.data.repository.FurRepository
import com.furflix.app.ui.components.LocalThumbnailQuality
import com.furflix.app.ui.screens.*
import com.furflix.app.viewmodel.MainViewModel
import kotlinx.coroutines.flow.MutableStateFlow

object Routes {
    const val HOME = "home"
    const val POST_DETAIL = "post/{postId}/{source}"
    const val SETTINGS = "settings"
    const val STORAGE_SETTINGS = "storage_settings"
    const val DOWNLOADS = "downloads"
    const val APPEARANCE_SETTINGS = "appearance_settings"
    const val FILTERS_SETTINGS = "filters_settings"
    const val TRANSLATION_SETTINGS = "translation_settings"
    const val LOGIN = "login"
    // 可选 folder* 参数：作品详情页 Folders 区块直达“用户页内文件夹浏览”
    const val USER_PROFILE =
        "user/{username}?folderId={folderId}&folderName={folderName}&folderGroup={folderGroup}&folderHref={folderHref}&folderCount={folderCount}"
    const val TAG_SEARCH = "tag_search/{tag}"
    const val JOURNAL = "journal/{journalId}"
    const val ABOUT_DEVELOPER = "about_developer"

    fun postDetail(postId: String, source: String = "main") = "post/$postId/$source"
    fun userProfile(username: String, folder: com.furflix.app.data.model.GalleryFolder? = null): String {
        val base = "user/$username"
        if (folder == null) return base
        val count = folder.count.toString()
        return base + listOf(
            "folderId=" + android.net.Uri.encode(folder.id),
            "folderName=" + android.net.Uri.encode(folder.name),
            "folderGroup=" + android.net.Uri.encode(folder.group),
            "folderHref=" + android.net.Uri.encode(folder.href),
            "folderCount=" + android.net.Uri.encode(count)
        ).joinToString("&", "?")
    }
    fun tagSearch(tag: String) = "tag_search/${tag.replace("#", "%23")}"
    fun journal(journalId: String) = "journal/$journalId"
}

@Composable
fun FurFlixNavGraph(
    /** 外部链接事件通道：MainActivity 的 VIEW intent 与剪贴板弹窗写入，此处消费 */
    deepLinkUri: MutableStateFlow<android.net.Uri?> = remember { MutableStateFlow<android.net.Uri?>(null) }
) {
    val navController = rememberNavController()
    val mainViewModel: MainViewModel = viewModel()
    val watchlistViewModel: com.furflix.app.viewmodel.WatchlistViewModel = viewModel()

    // 外部链接分发：作品/用户页直达（parseFaLink 与剪贴板、搜索框共用）
    val pendingDeepLink by deepLinkUri.collectAsState()
    LaunchedEffect(pendingDeepLink) {
        val uri = pendingDeepLink ?: return@LaunchedEffect
        when (val link = parseFaLink(uri.toString())) {
            is FaLink.Submission -> navController.navigate(Routes.postDetail(link.id, "main"))
            is FaLink.User -> navController.navigate(Routes.userProfile(link.username))
            null -> Unit
        }
        deepLinkUri.value = null
    }

    // 缩略图清晰度偏好全树注入一次，卡片与预取经 LocalThumbnailQuality 读取
    val appContext = LocalContext.current.applicationContext
    val qualityFlow = remember { FurRepository.getInstance(appContext).thumbnailQualityFlow }
    val thumbnailQuality by qualityFlow.collectAsState(initial = DynamicThumbnail.TIER_AUTO)

    CompositionLocalProvider(LocalThumbnailQuality provides thumbnailQuality) {
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            // 全局页面转场：轻位移 + 淡入淡出，进场慢出场快（FastOutSlowIn 曲线）
            enterTransition = {
                androidx.compose.animation.fadeIn(
                    androidx.compose.animation.core.tween(280, easing = androidx.compose.animation.core.FastOutSlowInEasing)
                ) + androidx.compose.animation.slideInHorizontally(
                    androidx.compose.animation.core.tween(280, easing = androidx.compose.animation.core.FastOutSlowInEasing)
                ) { it / 10 }
            },
            exitTransition = {
                androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(180)) +
                    androidx.compose.animation.slideOutHorizontally(androidx.compose.animation.core.tween(180)) { -it / 20 }
            },
            popEnterTransition = {
                androidx.compose.animation.fadeIn(
                    androidx.compose.animation.core.tween(280, easing = androidx.compose.animation.core.FastOutSlowInEasing)
                ) + androidx.compose.animation.slideInHorizontally(
                    androidx.compose.animation.core.tween(280, easing = androidx.compose.animation.core.FastOutSlowInEasing)
                ) { -it / 20 }
            },
            popExitTransition = {
                androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(180)) +
                    androidx.compose.animation.slideOutHorizontally(androidx.compose.animation.core.tween(180)) { it / 10 }
            }
        ) {
        composable(Routes.HOME) {
            HomeScreen(
                onNavigateToPost = { postId -> navController.navigate(Routes.postDetail(postId, "main")) },
                onNavigateToUser = { username -> navController.navigate(Routes.userProfile(username)) },
                onNavigateToSettings = { navController.navigate(Routes.SETTINGS) },
                onNavigateToDownloads = { navController.navigate(Routes.DOWNLOADS) },
                onNavigateToFavoritesPost = { postId -> navController.navigate(Routes.postDetail(postId, "favorites")) },
                viewModel = mainViewModel,
                watchlistViewModel = watchlistViewModel
            )
        }

        composable(
            route = Routes.POST_DETAIL,
            arguments = listOf(
                navArgument("postId") { type = NavType.StringType },
                navArgument("source") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val postId = backStackEntry.arguments?.getString("postId") ?: return@composable
            val source = backStackEntry.arguments?.getString("source") ?: "main"
            
            val submissions: List<com.furflix.app.data.model.Submission>
            val onLoadMore: () -> Unit
            
            if (source.startsWith("user_")) {
                val username = source.removePrefix("user_")
                val userProfileEntry = androidx.compose.runtime.remember(backStackEntry) { navController.getBackStackEntry(Routes.userProfile(username)) }
                val userViewModel: com.furflix.app.viewmodel.UserViewModel = viewModel(
                    viewModelStoreOwner = userProfileEntry,
                    factory = com.furflix.app.viewmodel.UserViewModel.Factory(
                        androidx.compose.ui.platform.LocalContext.current.applicationContext as android.app.Application,
                        username
                    )
                )
                submissions = userViewModel.submissions.collectAsState().value
                onLoadMore = { userViewModel.loadMore() }
            } else if (source == "favorites") {
                submissions = watchlistViewModel.favoritesList.collectAsState().value
                onLoadMore = { watchlistViewModel.loadMore() }
            } else if (source.startsWith("tag_")) {
                val tag = source.removePrefix("tag_")
                val searchRoute = Routes.tagSearch("@keywords $tag")
                val tagSearchEntry = androidx.compose.runtime.remember(backStackEntry) { navController.getBackStackEntry(searchRoute) }
                val tagViewModel: com.furflix.app.viewmodel.TagSearchViewModel = viewModel(
                    viewModelStoreOwner = tagSearchEntry,
                    factory = com.furflix.app.viewmodel.TagSearchViewModel.Factory(
                        androidx.compose.ui.platform.LocalContext.current.applicationContext as android.app.Application,
                        "@keywords $tag"
                    )
                )
                submissions = tagViewModel.submissions.collectAsState().value
                onLoadMore = { tagViewModel.loadMore() }
            } else {
                val selTab = mainViewModel.selectedTab.collectAsState().value
                submissions = when (selTab) {
                    2 -> mainViewModel.searchSubmissions.collectAsState().value
                    1 -> mainViewModel.latestSubmissions.collectAsState().value
                    else -> mainViewModel.browseSubmissions.collectAsState().value
                }
                onLoadMore = { mainViewModel.loadMore() }
            }

            PostDetailScreen(
                initialPostId = postId,
                submissions = submissions,
                onLoadMore = onLoadMore,
                onBack = { navController.popBackStack() },
                onAuthorClick = { username -> navController.navigate(Routes.userProfile(username)) },
                onNavigateToPost = { id -> navController.navigate(Routes.postDetail(id, source)) },
                onOpenFolder = { folder ->
                    // 文件夹链接自带所属用户：/gallery/{user}/folder/{id}/{slug}
                    val folderUsername = folder.href.substringAfter("/gallery/").substringBefore("/")
                        .ifEmpty { submissions.firstOrNull { it.id == postId }?.authorUsername.orEmpty() }
                    if (folderUsername.isNotEmpty()) {
                        navController.navigate(Routes.userProfile(folderUsername, folder))
                    }
                },
                onTagClick = { tag ->
                    navController.navigate(Routes.tagSearch("@keywords $tag"))
                },
                onTagAppend = { tag ->
                    // 追加搜索：以来源列表的标签集为基础并入新标签，重开标签搜索；
                    // 来源是主搜索且无 @keywords 组时，退化为单标签搜索
                    val currentQuery = if (source.startsWith("tag_")) {
                        "@keywords ${source.removePrefix("tag_")}"
                    } else {
                        mainViewModel.searchQuery.value
                    }
                    val tokens = Regex("@keywords\\s+(.+)$").find(currentQuery)
                        ?.groupValues?.get(1)?.split(" ")
                        ?.filter { it.isNotEmpty() }?.toMutableList()
                        ?: mutableListOf()
                    if (tokens.none { it.removePrefix("!") == tag }) tokens.add(tag)
                    navController.navigate(Routes.tagSearch("@keywords ${tokens.joinToString(" ")}"))
                },
                viewModel = mainViewModel
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onNavigateToLogin = { navController.navigate(Routes.LOGIN) },
                onNavigateToAppearance = { navController.navigate(Routes.APPEARANCE_SETTINGS) },
                onNavigateToFilters = { navController.navigate(Routes.FILTERS_SETTINGS) },
                onNavigateToTranslation = { navController.navigate(Routes.TRANSLATION_SETTINGS) },
                onNavigateToAbout = { navController.navigate(Routes.ABOUT_DEVELOPER) },
                onNavigateToStorage = { navController.navigate(Routes.STORAGE_SETTINGS) },
                viewModel = mainViewModel
            )
        }

        composable(Routes.STORAGE_SETTINGS) {
            StorageScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.TRANSLATION_SETTINGS) {
            com.furflix.app.translate.ui.TranslationSettingsScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.ABOUT_DEVELOPER) {
            AboutDeveloperScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.DOWNLOADS) {
            com.furflix.app.ui.screens.DownloadsScreen(
                onBack = { navController.popBackStack() },
                onNavigateToPost = { postId -> navController.navigate(Routes.postDetail(postId, "main")) },
                onNavigateToUser = { username -> navController.navigate(Routes.userProfile(username)) }
            )
        }

        composable(Routes.APPEARANCE_SETTINGS) {
            AppearanceSettingsScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.FILTERS_SETTINGS) {
            FiltersScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.LOGIN) {
            LoginScreen(
                onBack = { navController.popBackStack() },
                onLoginSuccess = {
                    navController.popBackStack(Routes.HOME, inclusive = false)
                },
                viewModel = mainViewModel
            )
        }

        composable(
            route = Routes.USER_PROFILE,
            arguments = listOf(
                navArgument("username") { type = NavType.StringType },
                navArgument("folderId") { type = NavType.StringType; defaultValue = "" },
                navArgument("folderName") { type = NavType.StringType; defaultValue = "" },
                navArgument("folderGroup") { type = NavType.StringType; defaultValue = "" },
                navArgument("folderHref") { type = NavType.StringType; defaultValue = "" },
                navArgument("folderCount") { type = NavType.StringType; defaultValue = "0" }
            )
        ) { backStackEntry ->
            val username = backStackEntry.arguments?.getString("username") ?: return@composable
            val userViewModel: com.furflix.app.viewmodel.UserViewModel = viewModel(
                viewModelStoreOwner = backStackEntry,
                factory = com.furflix.app.viewmodel.UserViewModel.Factory(
                    androidx.compose.ui.platform.LocalContext.current.applicationContext as android.app.Application,
                    username
                )
            )
            // 详情页 Folders 直达：带参进入时直接在该用户的文件夹内浏览
            val folderId = backStackEntry.arguments?.getString("folderId") ?: ""
            if (folderId.isNotEmpty()) {
                val initialFolder = remember(folderId, backStackEntry) {
                    com.furflix.app.data.model.GalleryFolder(
                        id = folderId,
                        name = backStackEntry.arguments?.getString("folderName").orEmpty(),
                        group = backStackEntry.arguments?.getString("folderGroup").orEmpty(),
                        href = backStackEntry.arguments?.getString("folderHref").orEmpty(),
                        count = backStackEntry.arguments?.getString("folderCount")?.toIntOrNull() ?: 0
                    )
                }
                androidx.compose.runtime.LaunchedEffect(initialFolder) {
                    userViewModel.openFolder(initialFolder)
                }
            }
            UserProfileScreen(
                username = username,
                viewModel = userViewModel,
                mainViewModel = mainViewModel,
                onBack = { navController.popBackStack() },
                onNavigateToPost = { postId -> navController.navigate(Routes.postDetail(postId, "user_$username")) },
                onNavigateToUser = { user -> navController.navigate(Routes.userProfile(user)) },
                onNavigateToJournal = { id -> navController.navigate(Routes.journal(id)) }
            )
        }

        composable(
            route = Routes.JOURNAL,
            arguments = listOf(navArgument("journalId") { type = NavType.StringType })
        ) { backStackEntry ->
            val journalId = backStackEntry.arguments?.getString("journalId") ?: return@composable
            val journalViewModel: com.furflix.app.viewmodel.JournalDetailViewModel = viewModel(
                viewModelStoreOwner = backStackEntry,
                factory = com.furflix.app.viewmodel.JournalDetailViewModel.Factory(
                    androidx.compose.ui.platform.LocalContext.current.applicationContext as android.app.Application,
                    journalId
                )
            )
            JournalDetailScreen(
                journalId = journalId,
                viewModel = journalViewModel,
                onBack = { navController.popBackStack() },
                onAuthorClick = { username -> navController.navigate(Routes.userProfile(username)) }
            )
        }

        composable(
            route = Routes.TAG_SEARCH,
            arguments = listOf(navArgument("tag") { type = NavType.StringType })
        ) { backStackEntry ->
            val tag = backStackEntry.arguments?.getString("tag") ?: return@composable
            val tagViewModel: com.furflix.app.viewmodel.TagSearchViewModel = viewModel(
                viewModelStoreOwner = backStackEntry,
                factory = com.furflix.app.viewmodel.TagSearchViewModel.Factory(
                    androidx.compose.ui.platform.LocalContext.current.applicationContext as android.app.Application,
                    tag
                )
            )
            TagSearchScreen(
                tag = tag.replace("@keywords ", "").replace("%23", "#"),
                viewModel = tagViewModel,
                mainViewModel = mainViewModel,
                onBack = { navController.popBackStack() },
                onNavigateToPost = { postId -> navController.navigate(Routes.postDetail(postId, "tag_${tag.replace("@keywords ", "")}")) },
                onNavigateToUser = { user -> navController.navigate(Routes.userProfile(user)) },
                cardStats = mainViewModel.cardStats.collectAsState().value
            )
        }
        }
    }
}
