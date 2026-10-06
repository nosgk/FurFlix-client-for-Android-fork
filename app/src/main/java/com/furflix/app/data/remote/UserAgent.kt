package com.furflix.app.data.remote

/**
 * 全链路 UA 单一来源。
 *
 * 两条链路、两种 UA（2026-10-07 登录回归实测定案）：
 *  - 抓取/图片（OkHttp）：桌面 Chrome UA——FA 桌面主题解析规则按它校准；
 *  - 登录/验证（WebView）：**不设 UA，用系统默认 Android WebView UA**——
 *    WebView 的平台/TLS 指纹是 Android 的，UA 声称 Windows 桌面会造成
 *    平台不一致，CF/Turnstile 按伪装流量拒绝（3f8e899 统一桌面 UA 后
 *    登录即开始失败、7ee8af9 原生 UA 可登录，为对照实测结论）。
 * cf_clearance 不做跨链路强绑定依赖：FA 的 CF 配置实测不校验
 * clearance-UA 绑定（抓取链路无 clearance 也长期正常）。
 */
object UserAgent {

    /** 桌面 Chrome UA（OkHttp 抓取与图片链路；版本与 FurHeaders 自洽） */
    fun desktop(): String =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

    /** 桌面链路的低熵 Client Hint（与 [desktop] 版本自洽） */
    fun secChUa(): String = "\"Chromium\";v=\"131\", \"Not_A Brand\";v=\"24\""
}
