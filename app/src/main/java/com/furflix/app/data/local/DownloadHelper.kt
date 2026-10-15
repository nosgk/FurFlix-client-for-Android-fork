package com.furflix.app.data.local

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class DownloadedItem(
    val uri: Uri,
    val filename: String,
    val width: Int = 0,
    val height: Int = 0,
    val mimeType: String = ""
) {
    /** 宽高未知（旧数据或 pre-Q 量测失败）时按 1:1 处理，不劣于旧版显示。 */
    val ratio: Float
        get() = if (width > 0 && height > 0) width.toFloat() / height else 1f

    /** 音频/文档类下载：无位图可解，列表按类型图标呈现。 */
    val isAudio: Boolean get() = mimeType.startsWith("audio")
    val isDoc: Boolean get() = mimeType.startsWith("application/")
}

object DownloadHelper {

    /** 下载按扩展名分流到 Images/Audio/Downloads 三个集合（与 DownloadManager 写入侧对应）。 */
    private val COLLECTION_QUERIES = listOf(
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI to Environment.DIRECTORY_PICTURES,
        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI to Environment.DIRECTORY_MUSIC,
        (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Downloads.EXTERNAL_CONTENT_URI to Environment.DIRECTORY_DOWNLOADS
        } else null)
    ).filterNotNull()

    suspend fun getDownloadedImages(context: Context): List<DownloadedItem> = withContext(Dispatchers.IO) {
        val items = mutableListOf<DownloadedItem>()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val projection = arrayOf(
                    MediaStore.MediaColumns._ID,
                    MediaStore.MediaColumns.DISPLAY_NAME,
                    MediaStore.MediaColumns.MIME_TYPE,
                    MediaStore.MediaColumns.WIDTH,
                    MediaStore.MediaColumns.HEIGHT
                )
                for ((collection, rootDir) in COLLECTION_QUERIES) {
                    val relativeLocation = rootDir + File.separator + "FurFlix"
                    val selection = "${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?"
                    val selectionArgs = arrayOf("%$relativeLocation%")
                    val sortOrder = "${MediaStore.MediaColumns.DATE_ADDED} DESC"
                    context.contentResolver.query(
                        collection,
                        projection,
                        selection,
                        selectionArgs,
                        sortOrder
                    )?.use { cursor ->
                        val idColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                        val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                        val mimeColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)
                        val widthColumn = cursor.getColumnIndex(MediaStore.MediaColumns.WIDTH)
                        val heightColumn = cursor.getColumnIndex(MediaStore.MediaColumns.HEIGHT)
                        while (cursor.moveToNext()) {
                            val id = cursor.getLong(idColumn)
                            val name = cursor.getString(nameColumn) ?: ""
                            val contentUri = Uri.withAppendedPath(collection, id.toString())
                            items.add(
                                DownloadedItem(
                                    contentUri,
                                    name,
                                    width = if (widthColumn >= 0) cursor.getInt(widthColumn) else 0,
                                    height = if (heightColumn >= 0) cursor.getInt(heightColumn) else 0,
                                    mimeType = cursor.getString(mimeColumn) ?: ""
                                )
                            )
                        }
                    }
                }
            } else {
                // pre-Q：扫描各公共目录的 FurFlix 子目录；位图只对图片量尺寸
                for (rootDir in listOf(Environment.DIRECTORY_PICTURES, Environment.DIRECTORY_MUSIC, Environment.DIRECTORY_DOWNLOADS)) {
                    val dir = File(Environment.getExternalStoragePublicDirectory(rootDir), "FurFlix")
                    if (!dir.exists()) continue
                    dir.listFiles()?.sortedByDescending { it.lastModified() }?.forEach { file ->
                        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        BitmapFactory.decodeFile(file.absolutePath, bounds)
                        items.add(
                            DownloadedItem(
                                Uri.fromFile(file),
                                file.name,
                                width = bounds.outWidth,
                                height = bounds.outHeight,
                                mimeType = guessMimeFromName(file.name)
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("DownloadHelper", "Error getting downloaded images", e)
        }
        items
    }

    private fun guessMimeFromName(name: String): String {
        val ext = name.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "mp3" -> "audio/mpeg"
            "m4a" -> "audio/mp4"
            "ogg" -> "audio/ogg"
            "wav" -> "audio/wav"
            "flac" -> "audio/flac"
            "aac" -> "audio/aac"
            "mid", "midi" -> "audio/midi"
            "pdf" -> "application/pdf"
            "doc" -> "application/msword"
            "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            "rtf" -> "application/rtf"
            "swf" -> "application/x-shockwave-flash"
            else -> ""
        }
    }

    /**
     * 该本地副本是否仍然存在。content Uri 以能否查到行判定，file Uri 以文件是否存在判定。
     * 供 [DownloadedMediaRegistry] 做完整性自检：用户在文件管理器里删掉的文件必须
     * 立刻从"已下载"登记里消失。查询本身失败（多为权限/瞬时错误）时保守返回 true，
     * 宁可多留一条登记，也不要把有效副本误判成已删除。
     */
    fun exists(context: Context, uri: Uri): Boolean = try {
        if (uri.scheme == "content") {
            context.contentResolver.query(
                uri, arrayOf(MediaStore.MediaColumns._ID), null, null, null
            )?.use { it.moveToFirst() } ?: false
        } else {
            uri.path?.let { File(it).exists() } ?: false
        }
    } catch (e: Exception) {
        Log.w("DownloadHelper", "exists() query failed, keeping registration", e)
        true
    }

    fun deleteImage(context: Context, uri: Uri): Boolean {
        return try {
            var deleted = false
            if (uri.scheme == "content") {
                deleted = context.contentResolver.delete(uri, null, null) > 0
            } else {
                deleted = uri.path?.let { File(it).delete() } ?: false
            }
            if (deleted) {
                // 清理孤儿元数据：parseMeta 以文件名尾部的数字 id 为键
                val name = queryDisplayName(context, uri) ?: uri.lastPathSegment ?: ""
                val id = name.substringAfterLast("_", "").takeWhile { it.isDigit() }
                if (id.isNotEmpty()) {
                    context.getSharedPreferences("downloads_meta", Context.MODE_PRIVATE)
                        .edit().remove(id).apply()
                    // 同步撤销"已下载"登记：否则详情页仍会拿这个已删文件的 Uri 去加载
                    DownloadedMediaRegistry.unregister(id)
                }
            }
            deleted
        } catch (e: Exception) {
            Log.e("DownloadHelper", "Failed to delete image", e)
            false
        }
    }

    private fun queryDisplayName(context: Context, uri: Uri): String? = try {
        if (uri.scheme == "content") {
            context.contentResolver.query(
                uri, arrayOf(MediaStore.MediaColumns.DISPLAY_NAME), null, null, null
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        } else {
            null
        }
    } catch (_: Exception) {
        null
    }
}
