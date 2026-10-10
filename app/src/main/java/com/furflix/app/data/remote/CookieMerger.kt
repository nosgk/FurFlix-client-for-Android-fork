package com.furflix.app.data.remote

/**
 * Cookie 合并策略(纯函数,供单测)。
 *
 * ## 背景(K-001)
 * `cf_clearance` 由 Cloudflare 按"签发它的 UA"绑定校验。验证 WebView 通过
 * 挑战后,新签发的 clearance 只落入系统 CookieManager,**不会**进入登录时
 * 建立的 `sessionCookies` 快照。
 *
 * 旧实现先放 CookieManager 的 CF 白名单,再用 `sessionCookies` 全量覆盖
 * —— 快照里若残留旧 clearance,就会把刚签发的新值覆盖掉,导致验证通过后
 * 重试仍被拒、验证对话框循环弹出。退账号重登会清空快照,所以"重登能临时
 * 解决"正是该覆盖顺序的表征。
 *
 * ## 现行策略
 * - **CF 系 cookie 以运行时(CookieManager)为权威**:它持有最近一次签发的
 *   通行凭证;
 * - 其余 cookie 以 `sessionCookies` 为权威(登录态 a/b 等);
 * - `sessionCookies` 中的 CF 系值仅作**回落**:运行时缺失时才用,不覆盖新值;
 * - 会话 cookie 仍覆盖运行时里的同名非 CF 项,保持登录态权威。
 *
 * 顺序即:session(非 CF)→ session 的 CF 值(回落)→ 运行时 CF(权威覆盖)。
 */
object CookieMerger {

    /**
     * CF 系 cookie 白名单:验证/挑战链路签发的通行凭证。
     * 不含 FCCDCF/FCNEC 等 consent cookie——Cookie 头膨胀反而恶化指纹。
     */
    val CF_COOKIE_NAMES: Set<String> =
        setOf("cf_clearance", "__cf_bm", "__cfruid", "__cf_chl_seqn")

    /**
     * 合并会话快照与运行时 cookie。
     *
     * @param session 登录时建立的会话快照(a/b 等权威登录态,可能含过期 CF 值)
     * @param runtime 系统 CookieManager 的实时值(持有最近签发的 CF 凭证)
     * @param forceNsfw true 去掉 sfw,false 注入 sfw=1,null 不动
     * @return 用于 Cookie 请求头的字符串
     */
    fun merge(
        session: Map<String, String>,
        runtime: Map<String, String>,
        forceNsfw: Boolean? = null,
    ): String {
        val merged = LinkedHashMap<String, String>()

        // 1) 会话快照中的非 CF 项(登录态权威)
        session.forEach { (name, value) ->
            if (name !in CF_COOKIE_NAMES) merged[name] = value
        }

        // 2) 会话快照中的 CF 项仅作回落,后面会被运行时值覆盖
        session.forEach { (name, value) ->
            if (name in CF_COOKIE_NAMES) merged[name] = value
        }

        // 3) 运行时 CF 值为权威:覆盖任何同名项(含快照里的过期值)
        runtime.forEach { (name, value) ->
            if (name in CF_COOKIE_NAMES) merged[name] = value
        }

        // 4) 无会话(访客态)时补上运行时非 CF 项,维持原有全量回落行为
        if (session.isEmpty()) {
            runtime.forEach { (name, value) ->
                if (name !in CF_COOKIE_NAMES) merged[name] = value
            }
        }

        applySfw(merged, forceNsfw)
        return merged.entries.joinToString("; ") { "${it.key}=${it.value}" }
    }

    /**
     * 会话快照中需要清理的 CF 系键:运行时已签发新值时,快照里的旧值即脏值。
     * 返回应从 `session` 中移除的键名集合。
     */
    fun staleSessionKeys(session: Map<String, String>, runtime: Map<String, String>): Set<String> =
        session.keys.filterTo(LinkedHashSet()) { it in CF_COOKIE_NAMES && it in runtime }

    private fun applySfw(merged: LinkedHashMap<String, String>, forceNsfw: Boolean?) {
        when (forceNsfw) {
            true -> merged.remove("sfw")
            false -> merged["sfw"] = "1"
            null -> Unit
        }
    }
}
