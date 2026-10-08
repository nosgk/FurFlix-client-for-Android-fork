package com.furflix.app.data.download

import okhttp3.MediaType
import okhttp3.ResponseBody
import okio.Buffer
import okio.BufferedSource
import okio.ForwardingSource
import okio.buffer

/**
 * OkHttp 响应体进度包装：把每次 read 换算成累计字节回调。
 * 下载任务进度（DownloadManager）与图片加载进度（ImageLoadProgress）共用。
 */
class ProgressResponseBody(
    private val delegate: ResponseBody,
    private val onBytesRead: (totalRead: Long, contentLength: Long) -> Unit
) : ResponseBody() {

    private var bufferedSource: BufferedSource? = null

    override fun contentType(): MediaType? = delegate.contentType()

    override fun contentLength(): Long = delegate.contentLength()

    override fun source(): BufferedSource {
        var bs = bufferedSource
        if (bs == null) {
            // 先捕获 contentLength：对象体内 delegate 会解析到 ForwardingSource 的 Source 属性
            val length = delegate.contentLength()
            bs = object : ForwardingSource(delegate.source()) {
                var totalRead = 0L

                override fun read(sink: Buffer, byteCount: Long): Long {
                    val read = super.read(sink, byteCount)
                    if (read > 0) {
                        totalRead += read
                        onBytesRead(totalRead, length)
                    }
                    return read
                }
            }.buffer()
            bufferedSource = bs
        }
        return bs
    }
}

/** 测速窗口长度（原 600ms：快速加载常在首个窗口未满时就完成，网速从未有机会显示） */
private const val SPEED_SAMPLE_INTERVAL_MS = 300L

/**
 * 300ms 滑动窗口测速；窗口未满时返回 null（上层沿用上次显示值，
 * 不覆盖已亮出的速度）。
 */
internal class Speedometer {
    private var lastBytes = 0L
    private var windowBytes = 0L
    private var windowStart = 0L

    fun sample(totalRead: Long, now: Long): Long? {
        windowBytes += (totalRead - lastBytes).coerceAtLeast(0)
        lastBytes = totalRead
        if (windowStart == 0L) windowStart = now
        val elapsed = now - windowStart
        return if (elapsed >= SPEED_SAMPLE_INTERVAL_MS) {
            val bps = windowBytes * 1000 / elapsed.coerceAtLeast(1)
            windowBytes = 0
            windowStart = now
            bps
        } else {
            null
        }
    }
}
