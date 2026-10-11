package com.furflix.app.data.local

import android.content.Context
import android.net.Uri
import kotlin.concurrent.thread
import java.util.concurrent.ConcurrentHashMap

/**
 * 已下载作品登记表：postId → 本地 MediaStore Uri。
 *
 * 为什么需要它：下载把文件写进 MediaStore，而 Coil 的磁盘缓存是另一个目录
 * （cacheDir/image_cache），两者互不相识——于是"已下载的作品在软件内查看"
 * 仍会整份重新下载一遍。下载方向早就会复用 Coil 缓存
 * （[com.furflix.app.data.download.DownloadManager] 的 readImageCacheBytes），
 * 这里补的是反向：查看方向复用本地文件。
 *
 * 为什么按 postId 而不是按 URL 索引：下载文件名是 `<安全标题>_<postId>.<ext>`，
 * postId 一定能从文件名还原，而 URL 只在新下载的元数据里才有——按 URL 索引会让
 * 用户**已有的下载全部失效**。两种下载方式下按 postId 复用都是安全的：
 * - copy（默认）：下载的就是 fullImageUrl，与详情页主图同源；
 * - original：下载的是 d.furaffinity.net 原件，比详情页默认展示的图**更清晰**，
 *   复用它是画质升级而非降级。
 * 因此不存在"拿展示文件顶替原图"的降质风险。
 *
 * 只登记图片类条目（按扩展名判断）：音频/文档类作品的详情页不走图片渲染路径，
 * 混进来只会让 Coil 拿到解不开的文件。
 */
object DownloadedMediaRegistry {

    private val IMAGE_EXTS = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "avif", "heic")

    private val byPostId = ConcurrentHashMap<String, Uri>()

    @Volatile
    private var warmedUp = false

    /** 登记单个已下载条目（下载完成时调用，uri 为空则忽略）。 */
    fun register(postId: String, uri: Uri?) {
        if (postId.isBlank() || uri == null) return
        byPostId[postId] = uri
    }

    /** 批量登记（下载页加载完列表后调用，用的是它已经查出来的那份数据）。 */
    fun registerAll(items: List<DownloadedItem>) {
        items.forEach { item ->
            if (!isImageFileName(item.filename)) return@forEach
            postIdFromFileName(item.filename)?.let { byPostId[it] = item.uri }
        }
    }

    /** 查本地副本；没有（或未下载过）返回 null，调用方回落网络 URL。 */
    fun lookup(postId: String?): Uri? =
        postId?.takeIf { it.isNotBlank() }?.let { byPostId[it] }

    /**
     * 文件名 → postId：下载命名恒为 `<安全标题>_<postId>.<ext>`，
     * 末段下划线之后的前导数字即 postId。取不到返回 null（老命名/手工改名）。
     */
    fun postIdFromFileName(fileName: String): String? =
        fileName.substringAfterLast('_', "")
            .takeWhile { it.isDigit() }
            .takeIf { it.isNotEmpty() }

    internal fun isImageFileName(fileName: String): Boolean =
        fileName.substringAfterLast('.', "").lowercase() in IMAGE_EXTS

    /**
     * 一次性预热：把 MediaStore 里**已有**的下载也登记进来（含本次改动之前下载的，
     * 它们没有 URL 元数据但文件名里有 postId）。仿 CacheJanitor 的做法起一个
     * 后台线程，不在启动路径上阻塞。
     */
    fun warmUpOnce(context: Context) {
        if (warmedUp) return
        synchronized(this) {
            if (warmedUp) return
            warmedUp = true
        }
        val appContext = context.applicationContext
        thread(name = "downloaded-media-registry") {
            runCatching {
                registerAll(
                    kotlinx.coroutines.runBlocking {
                        DownloadHelper.getDownloadedImages(appContext)
                    }
                )
            }
        }
    }

    /** 仅测试/登出场景使用。 */
    fun clear() {
        byPostId.clear()
        warmedUp = false
    }
}
