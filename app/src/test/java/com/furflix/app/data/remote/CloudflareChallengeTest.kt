package com.furflix.app.data.remote

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
        <html><head><title>Error 1020</title></head>
        <body><div class="cf-error-details">Access denied</div></body></html>
    """.trimIndent()

    @Test
    fun `interstitial challenge page is detected`() {
        assertTrue(isCloudflareChallenge(403, interstitialHtml))
        assertTrue(isCloudflareChallenge(503, interstitialHtml))
        // 挑战骨架经 meta-refresh 回放时可能以 200 呈现
        assertTrue(isCloudflareChallenge(200, interstitialHtml))
    }

    @Test
    fun `cf error page is detected regardless of status`() {
        assertTrue(isCloudflareChallenge(403, errorDetailsHtml))
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
    fun `short or empty responses are never treated as challenges`() {
        assertFalse(isCloudflareChallenge(403, ""))
        assertFalse(isCloudflareChallenge(403, "<html>403</html>"))
        assertFalse(isCloudflareChallenge(503, "Just a moment"))
    }
}
