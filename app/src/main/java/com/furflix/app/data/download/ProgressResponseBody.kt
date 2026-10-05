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
