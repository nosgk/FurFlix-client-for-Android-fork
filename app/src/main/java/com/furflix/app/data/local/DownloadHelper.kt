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
    val height: Int = 0
) {
    /** 宽高未知（旧数据或 pre-Q 量测失败）时按 1:1 处理，不劣于旧版显示。 */
    val ratio: Float
        get() = if (width > 0 && height > 0) width.toFloat() / height else 1f
}

object DownloadHelper {

    suspend fun getDownloadedImages(context: Context): List<DownloadedItem> = withContext(Dispatchers.IO) {
        val items = mutableListOf<DownloadedItem>()
        try {
            val relativeLocation = Environment.DIRECTORY_PICTURES + File.separator + "FurFlix"
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val projection = arrayOf(
                    MediaStore.Images.Media._ID,
                    MediaStore.Images.Media.DISPLAY_NAME,
                    MediaStore.Images.Media.WIDTH,
                    MediaStore.Images.Media.HEIGHT
                )
                val selection = "${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?"
                val selectionArgs = arrayOf("%$relativeLocation%")
                val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

                context.contentResolver.query(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    projection,
                    selection,
                    selectionArgs,
                    sortOrder
                )?.use { cursor ->
                    val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                    val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                    val widthColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.WIDTH)
                    val heightColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.HEIGHT)
                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(idColumn)
                        val name = cursor.getString(nameColumn) ?: ""
                        val contentUri = Uri.withAppendedPath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id.toString())
                        items.add(
                            DownloadedItem(
                                contentUri,
                                name,
                                width = cursor.getInt(widthColumn),
                                height = cursor.getInt(heightColumn)
                            )
                        )
                    }
                }
            } else {
                val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "FurFlix")
                if (dir.exists()) {
                    dir.listFiles()?.sortedByDescending { it.lastModified() }?.forEach { file ->
                        // pre-Q 没有 MediaStore 宽高可查，解码时只量尺寸不加载像素
                        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        BitmapFactory.decodeFile(file.absolutePath, bounds)
                        items.add(
                            DownloadedItem(
                                Uri.fromFile(file),
                                file.name,
                                width = bounds.outWidth,
                                height = bounds.outHeight
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
                uri, arrayOf(MediaStore.Images.Media.DISPLAY_NAME), null, null, null
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
