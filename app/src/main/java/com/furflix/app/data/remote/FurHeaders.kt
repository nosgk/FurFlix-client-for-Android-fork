package com.furflix.app.data.remote

import okhttp3.Request

/**
 * 抓取请求的 Fetch Metadata / Client Hints 拟真头集：真实 Chrome 导航请求
 * 除 UA 外还带 sec-ch-ua 三件套与 sec-fetch-* 四件套，缺失这些头是 OkHttp
 * 流量与浏览器流量的最大静态差异之一（CF 的 bot score 直接采信）。
 * 头值与 UserAgent.desktop()（桌面 Chrome/131 常量）自洽。
 */
object FurHeaders {

    /** HTML 文档导航请求（抓取链路） */
    fun applyDocumentHints(builder: Request.Builder) {
        builder.header("sec-ch-ua", UserAgent.secChUa())
            .header("sec-ch-ua-mobile", "?0")
            .header("sec-ch-ua-platform", "\"Windows\"")
            .header("sec-fetch-dest", "document")
            .header("sec-fetch-mode", "navigate")
            .header("sec-fetch-site", "same-origin")
            .header("sec-fetch-user", "?1")
            .header("upgrade-insecure-requests", "1")
    }

    /** 表单提交请求（登录 POST 等）：同导航集，但无 user 提示 */
    fun applyFormHints(builder: Request.Builder) {
        builder.header("sec-ch-ua", UserAgent.secChUa())
            .header("sec-ch-ua-mobile", "?0")
            .header("sec-ch-ua-platform", "\"Windows\"")
            .header("sec-fetch-dest", "document")
            .header("sec-fetch-mode", "navigate")
            .header("sec-fetch-site", "same-origin")
            .header("origin", FurAffinityScraper.FA_BASE)
    }

    /** 图片请求（Coil 图片端，跨站到 CDN） */
    fun applyImageHints(builder: Request.Builder) {
        builder.header("sec-ch-ua", UserAgent.secChUa())
            .header("sec-ch-ua-mobile", "?0")
            .header("sec-ch-ua-platform", "\"Windows\"")
            .header("sec-fetch-dest", "image")
            .header("sec-fetch-mode", "no-cors")
            .header("sec-fetch-site", "cross-site")
            .header(
                "accept",
                "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8"
            )
    }
}
