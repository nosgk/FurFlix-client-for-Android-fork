package com.furflix.app.data

import com.furflix.app.data.remote.CloudflareChallengeException
import com.furflix.app.data.remote.GuestBlockedException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * [AppError] 分类规则(ADR-0006 接缝)。
 *
 * 分类决定 UI 能否给出可操作提示、是否自动重试、是否需要用户介入,
 * 因此规则本身要用测试锁住 —— 尤其"猜不出就 Unknown"这条:宁可不分类,
 * 也不能把错误归错类而给出误导性提示。
 */
class AppErrorTest {

    // ---------------------------------------------------------------- 已知类型

    @Test
    fun `cloudflare challenge maps to Cloudflare and needs user action`() {
        val e = AppError.from(CloudflareChallengeException("https://fa/view/1/"))
        assertTrue(e is AppError.Cloudflare)
        assertTrue("验证需用户完成,不可静默自动重试到底", e.needsUserAction)
        assertTrue("用户完成后应重试一次", e.retryable)
        assertEquals("https://fa/view/1/", (e as AppError.Cloudflare).pageUrl)
    }

    @Test
    fun `guest gate maps to GuestBlocked and is not retryable`() {
        val e = AppError.from(GuestBlockedException())
        assertTrue(e is AppError.GuestBlocked)
        assertTrue(e.needsUserAction)
        assertFalse("重试不会改变门页结果", e.retryable)
    }

    @Test
    fun `network failures map to Network and are retryable`() {
        listOf(
            SocketTimeoutException(),
            UnknownHostException(),
            IOException("stream closed")
        ).forEach { t ->
            val e = AppError.from(t)
            assertTrue("${t.javaClass.simpleName} 应归 Network", e is AppError.Network)
            assertTrue(e.retryable)
            assertFalse(e.needsUserAction)
        }
    }

    // ---------------------------------------------------------------- 消息归类

    @Test
    fun `http status in message maps to Http`() {
        val e = AppError.from(RuntimeException("HTTP 500 server error"))
        assertTrue(e is AppError.Http)
        assertEquals(500, (e as AppError.Http).code)
        assertTrue("5xx 可重试", e.retryable)
    }

    @Test
    fun `client 4xx maps to Unauthorized and is not retryable`() {
        val e403 = AppError.from(RuntimeException("HTTP 403 forbidden"))
        assertTrue("403 应归 Unauthorized", e403 is AppError.Unauthorized)
        val e401 = AppError.from(RuntimeException("HTTP 401"))
        assertTrue("401 应归 Unauthorized", e401 is AppError.Unauthorized)
        assertFalse("401/403 重试无意义,需重新登录", e401.retryable)
        assertTrue(e401.needsUserAction)
    }

    @Test
    fun `429 is retryable http`() {
        val e = AppError.from(RuntimeException("HTTP 429 too many requests"))
        assertTrue(e is AppError.Http)
        assertTrue("限流应退避重试", e.retryable)
    }

    // ---------------------------------------------------------------- 兜底

    @Test
    fun `unrecognized errors become Unknown without swallowing cause`() {
        val cause = RuntimeException("something odd")
        val e = AppError.from(cause)
        assertTrue(e is AppError.Unknown)
        assertEquals("原始异常必须保留以便排查", cause, e.cause)
        assertFalse("未知错误不自动重试", e.retryable)
    }

    @Test
    fun `AppError is a classification value, not a Throwable`() {
        // AppError 不继承 Throwable:它是"错误分类"而非异常本身,
        // 因此 from() 不接受 AppError 入参(否则编译器会判定该分支恒为假)。
        val e = AppError.Parse(RuntimeException("bad html"))
        assertFalse("AppError 不应是 Throwable", e is Throwable)
        assertTrue("cause 仍可保留原始异常", e.cause is RuntimeException)
    }

    // ---------------------------------------------------------------- 语义组合

    @Test
    fun `parse and storage errors are terminal`() {
        listOf(AppError.Parse(), AppError.Storage(), AppError.Quota()).forEach { e ->
            assertFalse("${e.javaClass.simpleName} 不应自动重试", e.retryable)
            assertFalse("${e.javaClass.simpleName} 无需用户介入", e.needsUserAction)
        }
    }
}
