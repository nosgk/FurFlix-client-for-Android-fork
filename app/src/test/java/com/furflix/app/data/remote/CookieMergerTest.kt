package com.furflix.app.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Cookie 合并策略(纯函数)—— K-001 回归锁定。
 *
 * 核心不变量:**运行时(CookieManager)签发的 CF 系 cookie 必须胜出**,
 * 会话快照中的同名旧值只能作回落,绝不能覆盖新值。
 * 违反即回到"验证通过后仍循环弹窗"的缺陷。
 */
class CookieMergerTest {

    private val cf = mapOf("cf_clearance" to "NEW-from-webview")
    private val session = mapOf("a" to "auth-a", "b" to "auth-b")

    // ---------------------------------------------------------------- K-001

    @Test
    fun `newly issued cf_clearance wins over stale session snapshot`() {
        val stale = mapOf("cf_clearance" to "OLD-from-login")

        val out = CookieMerger.merge(
            session = session + stale,
            runtime = cf,
        )

        assertTrue(
            "运行时新签发的 clearance 必须胜出",
            out.contains("cf_clearance=NEW-from-webview")
        )
        assertFalse(
            "快照中的旧值不得出现",
            out.contains("OLD-from-login")
        )
    }

    @Test
    fun `stale cf value is used only as fallback when runtime has none`() {
        val stale = mapOf("cf_clearance" to "OLD-from-login")

        val out = CookieMerger.merge(
            session = session + stale,
            runtime = emptyMap(),
        )

        assertTrue(
            "运行时缺失时回落快照值",
            out.contains("cf_clearance=OLD-from-login")
        )
    }

    @Test
    fun `session auth cookies remain authoritative`() {
        val out = CookieMerger.merge(
            session = session,
            runtime = cf + mapOf("a" to "runtime-a"),
        )

        // a 是非 CF 项,会话快照权威,运行时的同名值不覆盖
        assertTrue(out.contains("a=auth-a"))
        assertFalse(out.contains("a=runtime-a"))
        assertTrue(out.contains("cf_clearance=NEW-from-webview"))
    }

    // ---------------------------------------------------------------- 访客态

    @Test
    fun `guest mode falls back to full runtime cookies`() {
        val out = CookieMerger.merge(
            session = emptyMap(),
            runtime = cf + mapOf("FCCDCF" to "consent"),
        )

        assertTrue(out.contains("cf_clearance=NEW-from-webview"))
        assertTrue(out.contains("FCCDCF=consent"))
    }

    @Test
    fun `guest mode ignores session when empty`() {
        val out = CookieMerger.merge(session = emptyMap(), runtime = cf)
        assertFalse(out.contains("a="))
    }

    // ---------------------------------------------------------------- sfw

    @Test
    fun `forceNsfw true removes sfw`() {
        val out = CookieMerger.merge(
            session = session + mapOf("sfw" to "1"),
            runtime = cf,
            forceNsfw = true,
        )
        assertFalse(out.contains("sfw="))
    }

    @Test
    fun `forceNsfw false injects sfw=1`() {
        val out = CookieMerger.merge(
            session = session,
            runtime = cf,
            forceNsfw = false,
        )
        assertTrue(out.contains("sfw=1"))
    }

    @Test
    fun `forceNsfw null leaves sfw untouched`() {
        val out = CookieMerger.merge(
            session = session + mapOf("sfw" to "1"),
            runtime = cf,
            forceNsfw = null,
        )
        assertTrue(out.contains("sfw=1"))
    }

    // ---------------------------------------------------------------- 脏键清理

    @Test
    fun `staleSessionKeys reports cf keys present in both`() {
        val stale = CookieMerger.staleSessionKeys(
            session = mapOf("cf_clearance" to "OLD", "a" to "auth"),
            runtime = mapOf("cf_clearance" to "NEW"),
        )
        assertEquals(setOf("cf_clearance"), stale)
    }

    @Test
    fun `staleSessionKeys is empty when runtime lacks cf cookies`() {
        val stale = CookieMerger.staleSessionKeys(
            session = mapOf("cf_clearance" to "OLD"),
            runtime = emptyMap(),
        )
        assertTrue(stale.isEmpty())
    }

    @Test
    fun `all cf cookie names are treated as cf family`() {
        CookieMerger.CF_COOKIE_NAMES.forEach { name ->
            val out = CookieMerger.merge(
                session = mapOf(name to "OLD"),
                runtime = mapOf(name to "NEW"),
            )
            assertTrue("$name 应被运行时覆盖", out.contains("$name=NEW"))
        }
    }

    // ---------------------------------------------------------------- 格式

    @Test
    fun `output is semicolon separated cookie header`() {
        val out = CookieMerger.merge(
            session = mapOf("a" to "1", "b" to "2"),
            runtime = cf,
        )
        val parts = out.split("; ").map { it.split("=")[0] }
        assertEquals(listOf("a", "b", "cf_clearance"), parts)
    }

    @Test
    fun `empty inputs produce empty header`() {
        assertEquals("", CookieMerger.merge(emptyMap(), emptyMap()))
    }
}
