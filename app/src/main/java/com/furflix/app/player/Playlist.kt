package com.furflix.app.player

/**
 * 播放列表来源：决定队列怎么构建、以及界面上显示成什么名字。
 */
sealed interface PlaylistSource {
    /** 单作品（详情页"就在当前列表里连着听"）。 */
    data object Single : PlaylistSource

    /** 某画师名下的全部音频作品。 */
    data class Artist(val username: String) : PlaylistSource

    /** 某画廊文件夹内的全部音频作品。 */
    data class Folder(
        val username: String,
        val folderId: String,
        val folderName: String
    ) : PlaylistSource

    /** 用户自定义（本地保存）列表。 */
    data class Custom(val playlistId: String) : PlaylistSource
}

/**
 * 队列里的一条。
 *
 * 刻意**只带 postId 与展示信息，音频直链按需解析**：画廊/文件夹的列表项里只有
 * `/view/` 链接、没有直链（figure 里给不出），若开播前把每条都抓一遍详情页，
 * 几十条就要等几十秒。详情页/自定义列表本来就有直链，直接带上即可。
 */
data class PlaylistItem(
    val postId: String,
    val title: String,
    val artist: String,
    val coverUrl: String?,
    val url: String? = null,
    /** 画师用户名（/user/ slug），用于"播放该画师全部音乐"。 */
    val artistUsername: String = ""
)

/** 一个播放列表。 */
data class Playlist(
    val name: String,
    val source: PlaylistSource,
    val items: List<PlaylistItem>
) {
    val size: Int get() = items.size
}
