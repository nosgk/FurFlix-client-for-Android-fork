package com.furflix.app.data.remote

import android.content.Context
import android.os.Build
import android.webkit.WebView

/**
 * 全链路单一 UA 来源（抓取/图片加载/登录 WebView 共用）。
 *
 * 版本号取设备 WebView 内核的真实主版本（WebView.getCurrentWebViewPackage，
 * API 26+）：CF/Turnstile 会校验 UA 声称的 Chrome 版本与内核实际指纹的一致性——
 * 硬编码的版本号与设备内核脱节时 Turnstile 拒绝静默签发 token，登录卡在人机
 * 验证。OkHttp 与 WebView 使用同一 UA 也保证 cf_clearance 的 UA 绑定在两条
 * 链路间一致。
 */
object UserAgent {

    /** 设备无法提供内核版本时的兜底版本（与解析规则校准时的样本一致） */
    private const val FALLBACK_VERSION = "131"

    @Volatile
    private var version: String? = null

    @Volatile
    private var resolved = false

    /**
     * 启动时解析一次（Application/Activity onCreate）。重复调用幂等，
     * 异常 ROM 拿不到内核包时回落 [FALLBACK_VERSION]。
     */
    fun resolve(context: Context) {
        if (resolved) return
        resolved = true
        version = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WebView.getCurrentWebViewPackage()
                    ?.versionName
                    ?.substringBefore('.')
                    ?.takeIf { it.toIntOrNull() != null }
            } else {
                null
            } ?: FALLBACK_VERSION
        } catch (_: Exception) {
            FALLBACK_VERSION
        }
    }

    /** 桌面 Chrome UA（与 WebView 登录端、OkHttp 抓取端、Coil 图片端共用） */
    fun desktop(): String =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/${version ?: FALLBACK_VERSION}.0.0.0 Safari/537.36"

    /**
     * OkHttp 侧的 sec-ch-ua 低熵 Client Hint（与 [desktop] 的版本自洽；
     * UA 声称桌面 Chrome/WebView 内核版本，双值一致才不拉开指纹分差）。
     */
    fun secChUa(): String {
        val v = version ?: FALLBACK_VERSION
        return "\"Chromium\";v=\"$v\", \"Not_A Brand\";v=\"24\""
    }

    /** 当前解析出的内核主版本（供诊断/测试） */
    fun currentVersion(): String = version ?: FALLBACK_VERSION
}
