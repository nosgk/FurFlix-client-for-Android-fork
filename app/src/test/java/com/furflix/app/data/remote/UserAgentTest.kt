package com.furflix.app.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * UserAgent 纯 JVM 侧校验：未 init 时回落 UA 必须是 Android WebView 形态
 * （cf_clearance 的 UA 绑定要求全链路同 UA，桌面形态属于事故回归），且
 * Client Hints 与 UA 的版本/平台声明自洽。
 */
class UserAgentTest {

    @Test
    fun fallbackUaIsAndroidWebviewForm() {
        val ua = UserAgent.current()
        assertTrue("UA 应为 Android 平台: $ua", ua.contains("Android"))
        assertTrue("UA 应为移动形态: $ua", ua.contains("Mobile Safari"))
        assertTrue("UA 应含 Chrome 版本: $ua", ua.contains("Chrome/"))
        assertFalse("桌面 UA 是本次事故根因，不得回流: $ua", ua.contains("Windows NT"))
    }

    @Test
    fun clientHintsMatchUaVersionAndPlatform() {
        val version = Regex("""Chrome/(\d+)""").find(UserAgent.current())!!
            .groupValues[1]
        assertEquals(version, UserAgent.chromiumMajorVersion().toString())

        val hints = UserAgent.secChUa()
        assertTrue("品牌版本须与 UA 自洽: $hints", hints.contains("\"Chromium\";v=\"$version\""))
        assertTrue("WebView 品牌须与 UA 自洽: $hints", hints.contains("\"Android WebView\";v=\"$version\""))
    }
}
