package com.furflix.app.player

import android.content.Context
import com.furflix.app.data.model.GalleryFolder
import com.furflix.app.data.model.Submission
import com.furflix.app.data.remote.faDerivedThumbnailUrl
import com.furflix.app.data.repository.FurRepository

/**
 * 从画师 / 画廊文件夹聚合音频作品生成播放列表。
 *
 * 画廊列表项**没有直链**（figure 里只有 `/view/` 链接，直链只在详情页的下载锚里），
 * 所以这里只收集 postId 与展示信息，直链等轮到播放时再解析（见 [AudioUrlResolver]）——
 * 否则"播放全部"要先把几十条详情页抓完才能出声。
 */
object PlaylistBuilder {

    /** 扫描页数上限：画师作品可能很多，没必要翻到底（队列本身也有上限）。 */
    private const val MAX_PAGES = 6

    /** FA 画廊每页约 48 条；不足此数即视为最后一页。 */
    private const val PAGE_FULL_THRESHOLD = 40

    /** 某画师名下的全部音频作品。 */
    suspend fun artistMusic(context: Context, username: String): Playlist {
        val repo = FurRepository.getInstance(context)
        val collected = LinkedHashMap<String, Submission>()
        var page = 1
        while (page <= MAX_PAGES) {
            val items = runCatching { repo.getUserGallery(username, page) }.getOrDefault(emptyList())
            if (items.isEmpty()) break
            items.filter { it.isAudioWork() }.forEach { collected[it.id] = it }
            if (items.size < PAGE_FULL_THRESHOLD) break
            page++
        }
        return Playlist(
            name = username,
            source = PlaylistSource.Artist(username),
            items = collected.values.map { it.toPlaylistItem() }
        )
    }

    /** 某个画廊文件夹内的全部音频作品。 */
    suspend fun folderMusic(
        context: Context,
        username: String,
        folder: GalleryFolder
    ): Playlist {
        val repo = FurRepository.getInstance(context)
        val collected = LinkedHashMap<String, Submission>()
        var page = 1
        while (page <= MAX_PAGES) {
            val items = runCatching { repo.getUserGalleryFolder(username, folder, page) }
                .getOrDefault(emptyList())
            if (items.isEmpty()) break
            items.filter { it.isAudioWork() }.forEach { collected[it.id] = it }
            if (items.size < PAGE_FULL_THRESHOLD) break
            page++
        }
        return Playlist(
            name = folder.name.ifEmpty { username },
            source = PlaylistSource.Folder(username, folder.id, folder.name),
            items = collected.values.map { it.toPlaylistItem() }
        )
    }

    /** 已解析直链的条目（详情页上下文）→ 队列条目。 */
    fun Submission.toPlaylistItemResolved(): PlaylistItem = PlaylistItem(
        postId = id,
        title = title.ifEmpty { fileName.ifEmpty { id } },
        artist = author,
        coverUrl = audioCoverUrl(this) ?: faDerivedThumbnailUrl(id, fileUrl.ifEmpty { fullImageUrl }),
        url = fileUrl.ifEmpty { fullImageUrl }.takeIf { it.isNotEmpty() },
        artistUsername = authorUsername
    )

    private fun Submission.toPlaylistItem(): PlaylistItem = PlaylistItem(
        postId = id,
        title = title.ifEmpty { fileName.ifEmpty { id } },
        artist = author,
        coverUrl = audioCoverUrl(this) ?: faDerivedThumbnailUrl(id, fileUrl.ifEmpty { fullImageUrl }),
        url = null,
        artistUsername = authorUsername
    )
}
