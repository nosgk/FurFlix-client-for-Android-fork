package com.furflix.app.player

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.util.Log
import com.furflix.app.data.remote.FaMediaClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import kotlin.math.abs

/**
 * 音频波形峰值提取：解码成 PCM 后按桶取最大振幅，供进度条位置画波形图。
 *
 * 为什么自己解而不是拿现成的：ExoPlayer 只把 PCM 喂给 AudioTrack，不暴露采样；
 * 系统也没有"取波形"的 API。所以走 MediaExtractor + MediaCodec 解一遍。
 *
 * 取数方式：先把音频字节落到临时文件（MediaExtractor 需要可随机访问的数据源），
 * 解完删掉。峰值结果按 URL 哈希缓存在 `cacheDir/waveforms`，同一作品只解一次。
 *
 * 全程**失败即返回 null**：波形是锦上添花，任何机型/编码不支持都不该影响播放。
 */
object WaveformExtractor {

    private const val TAG = "Waveform"

    /** 波形桶数：足够细腻又不至于让 Canvas 每帧画太多线。 */
    const val BUCKETS = 320

    private const val DIR_NAME = "waveforms"

    /** 单次解码的样本上限（约 15 分钟 48kHz 立体声），防御异常文件。 */
    private const val MAX_FRAMES = 48_000L * 60 * 15

    private fun cacheFile(context: Context, url: String): File {
        val dir = File(context.cacheDir, DIR_NAME).apply { if (!exists()) mkdirs() }
        val hash = MessageDigest.getInstance("SHA-1").digest(url.toByteArray())
            .joinToString("") { "%02x".format(it) }
        return File(dir, hash)
    }

    /** 读缓存峰值（未缓存返回 null）。 */
    fun cached(context: Context, url: String): FloatArray? {
        val file = cacheFile(context, url)
        if (!file.isFile || file.length() <= 0L) return null
        return runCatching {
            DataInputStream(file.inputStream().buffered()).use { input ->
                val n = input.readInt()
                if (n !in 1..4096) return null
                FloatArray(n) { input.readFloat() }
            }
        }.getOrNull()
    }

    /**
     * 取峰值：缓存优先；未缓存则下载 + 解码一次并落盘。
     * 返回的数组已归一化到 0..1（最大峰值映射为 1）。
     */
    suspend fun peaks(context: Context, url: String): FloatArray? = withContext(Dispatchers.IO) {
        cached(context, url)?.let { return@withContext it }
        val app = context.applicationContext
        val tmp = File(app.cacheDir, "waveform_${System.nanoTime()}.tmp")
        try {
            val bytes = runCatching {
                FaMediaClient.newCall(url).execute().use { resp ->
                    if (!resp.isSuccessful) return@use null
                    resp.body.bytes()
                }
            }.getOrNull() ?: return@withContext null
            tmp.writeBytes(bytes)
            val raw = decodePeaks(tmp.absolutePath) ?: return@withContext null
            val normalized = normalize(raw)
            runCatching {
                DataOutputStream(cacheFile(app, url).outputStream().buffered()).use { out ->
                    out.writeInt(normalized.size)
                    normalized.forEach { out.writeFloat(it) }
                }
            }
            normalized
        } catch (e: Exception) {
            Log.w(TAG, "waveform failed for $url: ${e.javaClass.simpleName}: ${e.message}")
            null
        } finally {
            runCatching { tmp.delete() }
        }
    }

    /** 归一到 0..1；全静音时给一条 0 线。 */
    private fun normalize(peaks: FloatArray): FloatArray {
        var max = 0f
        for (v in peaks) if (v > max) max = v
        if (max <= 0f) return peaks
        for (i in peaks.indices) peaks[i] = (peaks[i] / max).coerceIn(0f, 1f)
        return peaks
    }

    /**
     * MediaExtractor + MediaCodec 解码，边解边按桶取 max|sample|。
     *
     * 桶大小由"总帧数"（由容器时长 × 采样率估算）决定，所以不必把整段 PCM 留在内存里。
     */
    private fun decodePeaks(path: String): FloatArray? {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(path)
            var trackIndex = -1
            var format: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val f = extractor.getTrackFormat(i)
                val mime = f.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("audio/")) {
                    trackIndex = i
                    format = f
                    break
                }
            }
            if (trackIndex < 0 || format == null) return null
            extractor.selectTrack(trackIndex)

            val mime = format.getString(MediaFormat.KEY_MIME) ?: return null
            val sampleRate = format.getIntegerOrDefault(MediaFormat.KEY_SAMPLE_RATE, 44_100)
            val channelCount = format.getIntegerOrDefault(MediaFormat.KEY_CHANNEL_COUNT, 2)
            val durationUs = if (format.containsKey(MediaFormat.KEY_DURATION)) {
                format.getLong(MediaFormat.KEY_DURATION)
            } else {
                0L
            }
            val totalFrames = if (durationUs > 0L) {
                (durationUs * sampleRate / 1_000_000L).coerceIn(1L, MAX_FRAMES)
            } else {
                MAX_FRAMES
            }

            val peaks = FloatArray(BUCKETS)
            var framesSeen = 0L
            var framesPerBucket = (totalFrames / BUCKETS).coerceAtLeast(1L)

            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()

            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false
            while (!outputDone) {
                if (!inputDone) {
                    val inIndex = codec.dequeueInputBuffer(10_000)
                    if (inIndex >= 0) {
                        val buffer = codec.getInputBuffer(inIndex) ?: continue
                        val size = extractor.readSampleData(buffer, 0)
                        if (size < 0) {
                            codec.queueInputBuffer(
                                inIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM
                            )
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(inIndex, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                when (val outIndex = codec.dequeueOutputBuffer(info, 10_000)) {
                    MediaCodec.INFO_TRY_AGAIN_LATER -> Unit
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> Unit
                    else -> if (outIndex >= 0) {
                        val out = codec.getOutputBuffer(outIndex)
                        if (out != null && info.size > 0) {
                            framesSeen = accumulate(
                                out, info, channelCount, framesSeen, framesPerBucket, peaks
                            )
                        }
                        codec.releaseOutputBuffer(outIndex, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                    }
                }
            }
            return peaks
        } catch (e: Exception) {
            Log.w(TAG, "decode failed: ${e.javaClass.simpleName}: ${e.message}")
            return null
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            runCatching { extractor.release() }
        }
    }

    /** 把一块 PCM（16bit 交错）并进峰值桶；返回累计帧数。 */
    private fun accumulate(
        buffer: ByteBuffer,
        info: MediaCodec.BufferInfo,
        channelCount: Int,
        framesSeen: Long,
        framesPerBucket: Long,
        peaks: FloatArray
    ): Long {
        val shorts = buffer.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        val available = info.size / 2
        val frames = available / channelCount.coerceAtLeast(1)
        var seen = framesSeen
        for (f in 0 until frames) {
            var peak = 0
            for (c in 0 until channelCount) {
                val idx = f * channelCount + c
                if (idx >= available) break
                val v = abs(shorts.get(idx).toInt())
                if (v > peak) peak = v
            }
            val bucket = (seen / framesPerBucket).toInt()
            if (bucket in peaks.indices) {
                val value = peak / 32768f
                if (value > peaks[bucket]) peaks[bucket] = value
            }
            seen++
        }
        return seen
    }

    private fun MediaFormat.getIntegerOrDefault(key: String, fallback: Int): Int =
        if (containsKey(key)) runCatching { getInteger(key) }.getOrDefault(fallback) else fallback
}
