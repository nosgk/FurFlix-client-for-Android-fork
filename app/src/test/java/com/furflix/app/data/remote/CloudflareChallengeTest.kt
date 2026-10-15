package com.furflix.app.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * CF 拦截特征判定的回归样本（2026-10-07 浏览器实测校准）：
 * FA 登录页正常状态就带 Turnstile 组件与 challenges.cloudflare.com 脚本，
 * 这些绝不能当挑战特征——否则登录链路会被自己误判成挑战而拒绝放行。
 */
class CloudflareChallengeTest {

    private val loginPageNormalHtml = """
        <html><head><title>System Login -- Fur Affinity [dot] net</title>
        <script src="https://challenges.cloudflare.com/turnstile/v0/api.js?render=explicit"></script>
        </head><body>
        <div class="cf-turnstile" data-sitekey="0x4AAAAAABl0FuBaPYQlKnEi"></div>
        <input name="cf-turnstile-response" value="0.tfGwAAAA...">
        <input name="name"><input name="pass">
        </body></html>
    """.trimIndent()

    private val interstitialHtml = """
        <html><head><title>Just a moment...</title></head>
        <body><script>window._cf_chl_opt = {cvId: '3'};</script>
        <script src="/cdn-cgi/challenge-platform/h/b/orchestrate/chl_page/v1?ray=abc"></script>
        <div id="challenge-form">Verifying you are human</div></body></html>
    """.trimIndent()

    private val errorDetailsHtml = """
        <html><head><title>Error 1020 | Fur Affinity [dot] net</title></head>
        <body>
        <div class="cf-error-details">
            <h1>Error 1020 — Access denied (Ray ID: 8f3a1b2c9d4e5f60)</h1>
            <p>This website is using a security service to protect itself from online attacks.
            The action you just performed triggered the security solution. There are several
            actions that could trigger this block including submitting a certain word or phrase,
            a SQL command or malformed data.</p>
        </div>
        </body></html>
    """.trimIndent()

    @Test
    fun `interstitial challenge page is detected`() {
        assertTrue(isCloudflareChallenge(403, interstitialHtml))
        assertTrue(isCloudflareChallenge(503, interstitialHtml))
        // 挑战骨架经 meta-refresh 回放时可能以 200 呈现
        assertTrue(isCloudflareChallenge(200, interstitialHtml))
    }

    @Test
    fun `cf error page is a block, not a solvable challenge`() {
        // 1015 限流 / 1020 拒绝这类错误页人工验证通不过：绝不能当挑战弹验证框，
        // 否则用户被困在永远无法完成的验证界面（"安全检查误拦"）。
        assertFalse(isCloudflareChallenge(403, errorDetailsHtml))
        assertTrue(isCloudflareBlock(403, errorDetailsHtml))
        assertTrue(isCloudflareBlock(503, errorDetailsHtml))
        // 2xx 且带该词条多为正常页面正文偶然提及，不判为封禁
        assertEquals(CloudflareVerdict.NONE, classifyCloudflare(200, errorDetailsHtml))
    }

    @Test
    fun `normal login page with turnstile is NOT a challenge`() {
        assertFalse(isCloudflareChallenge(200, loginPageNormalHtml))
    }

    @Test
    fun `plain content page is not a challenge`() {
        assertFalse(
            isCloudflareChallenge(
                200,
                "<html><head><title>Browse</title></head><body><figure id=\"sid-1\">art</figure></body></html>"
            )
        )
    }

    @Test
    fun `user content that merely mentions the phrases is not a challenge`() {
        // 扫正文会把这类正常页面误判成挑战——用户侧表现就是"浏览中莫名弹出安全检查"，
        // 而且弹出的 WebView 加载的是正常页面，看起来"根本不是真正的 CF 检查"。
        val journal = "<html><head><title>My journal -- Fur Affinity [dot] net</title></head><body>" +
            "<div class=\"journal-body\"><p>I kept getting &quot;Just a moment...&quot; and " +
            "&quot;Checking your browser&quot; pages all day while browsing, and the site asked me " +
            "to prove I am human before letting me in. Very annoying.</p></div></body></html>"
        assertTrue(journal.length > 200)
        assertFalse(isCloudflareChallenge(200, journal))
    }

    @Test
    fun `cf challenge platform script alone is not a challenge marker`() {
        // CF 会在正常页面注入该脚本（Turnstile 等），它是脚本路径而非挑战页判据
        val normalWithCfScript = "<html><head><title>Browse -- Fur Affinity [dot] net</title>" +
            "<script src=\"/cdn-cgi/challenge-platform/h/b/orchestrate/chl_page/v1?ray=abc\"></script>" +
            "</head><body><figure id=\"sid-1\">art</figure></body></html>"
        assertFalse(isCloudflareChallenge(200, normalWithCfScript))
    }

    @Test
    fun `short or empty responses are never treated as challenges`() {
        assertFalse(isCloudflareChallenge(403, ""))
        assertFalse(isCloudflareChallenge(403, "<html>403</html>"))
        assertFalse(isCloudflareChallenge(503, "Just a moment"))
    }
}
