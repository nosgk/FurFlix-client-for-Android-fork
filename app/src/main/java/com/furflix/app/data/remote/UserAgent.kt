package com.furflix.app.data.remote

import android.content.Context
import android.webkit.WebSettings

/**
 * 全链路 UA 单一来源（2026-10-08 CF 循环验证事故定案）。
 *
 * 全部出站链路——OkHttp 抓取 / Coil 图片 / 下载 / 登录与验证 WebView——统一使用
 * 设备**真实 WebView UA**（Application 创建时经 [init] 运行时获取）：
 *  - cf_clearance 被 CF 严格绑定到"签发它的 UA"。验证 WebView 只能用原生 UA
 *    通过人机验证（桌面 UA 平台不一致会被拒，3f8e899 登录回归实测），抓取链路
 *    曾配桌面 Chrome UA——clearance 拿到后重试仍按 UA 不匹配拒绝，验证对话框
 *    反复弹出、应用完全不可用（2026-10-08 实机报告）。绑定校验是 CF 的标准
 *    行为，"FA 不校验 clearance-UA 绑定"仅是无 clearance 场景的观测，从未真正
 *    跨链路验证过；两链路共用同一 UA 是绑定成立的唯一前提。
 *  - 客户端声明自洽：UA 与 Client Hints（FurHeaders）同源本文件，真实平台。
 *
 * 历史注：3f8e899~e6639a6 抓取链路曾用桌面 UA 以对齐桌面主题解析样本，并配
 * Fetch Metadata 拟真头压制挑战；该状态依赖 CF 的挑战策略静默，clearance 一旦
 * 介入即失效。本应用在 Android 移动 UA 下长期验证可用（初版~3f8e899），解析层
 * 以该形态为准校准。
 */
object UserAgent {

    /** init 前的回落值（JVM 单测/极端时序）：Android WebView 形态的移动 UA */
    private const val FALLBACK =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Version/4.0 Chrome/131.0.0.0 Mobile Safari/537.36"

    @Volatile
    private var runtime: String? = null

    /** Application 创建时调用：取设备 WebView 的真实 UA */
    fun init(context: Context) {
        runCatching { WebSettings.getDefaultUserAgent(context) }
            .getOrNull()
            ?.let { runtime = it }
    }

    /** 网络请求使用的 UA：设备真实 WebView UA */
    fun current(): String = runtime ?: FALLBACK

    /** 从当前 UA 解析 Chromium 主版本（Client Hints 与其保持自洽） */
    fun chromiumMajorVersion(): Int =
        Regex("""Chrome/(\d+)""").find(current())?.groupValues?.get(1)?.toIntOrNull() ?: 131

    /** 低熵 Client Hint 品牌三元组（与 [current] 版本/平台自洽，Android WebView 形态） */
    fun secChUa(): String {
        val v = chromiumMajorVersion()
        return "\"Not_A Brand\";v=\"24\", \"Chromium\";v=\"$v\", \"Android WebView\";v=\"$v\""
    }
}
