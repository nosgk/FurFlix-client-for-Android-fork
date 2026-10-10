# FurFlix Android — 结构化问题清单

> 生成阶段:Harness 工程化改造 · 阶段 1(全量扫描)
> 生成日期:2026-10-10
> 扫描范围:`app/src/main`(116 Kotlin 文件,~32k 行)+ CI 工作流 + 文档
> 状态图例:`open` 待处理 / `fixed` 已修 / `wontfix` 不修 / `defer` 延后

## 字段规约

```
### <ID>  <标题>
- type: bug | refactor | security | perf | test | docs | i18n
- severity: P0 阻塞 | P1 高 | P2 中 | P3 低
- area: <模块/层>
- evidence: <文件:行号 + 事实>
- decision: 修复 / 重构 / 推后
- behavior-change: yes | no
- subtask: <REFACTOR-xxx / FIX-xxx>
- status: open
```

---

## 一、已知问题(用户报告,已实证定位)

### K-001  CF 验证通过后仍循环弹窗,退账号重登才缓解
- type: bug
- severity: **P0**
- area: network/auth (`data/remote`)
- evidence:
  - `FurAffinityScraper.kt:94-134` `getCookiesString()`:**有会话时**以 `sessionCookies`
    为权威,仅从 CookieManager 补齐 CF 白名单(`cf_clearance`/`__cf_bm`/`__cfruid`/`__cf_chl_seqn`)。
  - 但 `sessionCookies` 由 `setCookiesFromWebView()`(`:141-164`)在**登录时**填充,是快照。
    验证 WebView 通过后新签发的 `cf_clearance` 只落到 **CookieManager**,
    不进 `sessionCookies`。
  - 合并顺序 `merged.putAll(cmCf)` 后 `sessionCookies.forEach { merged[name]=value }`(`:117-118`)
    —— 若 `sessionCookies` 里残留**旧的/已失效的** `cf_clearance`,会**覆盖**刚签发的新值。
  - `CloudflareGate.resolve()`(`CloudflareGate.kt:65-71`)只推进代次、不清旧 cookie;
    重试立刻用被覆盖回旧值的 cookie,再次 403/挑战 → 循环弹窗。
  - 退账号重登 == 清空 `sessionCookies`(`clear()` `:142`),故能"临时解决",与用户描述完全吻合。
- decision: 修复
- behavior-change: no
- subtask: FIX-001
- status: open

> **记忆纠正**:既有记忆 `furflix-cf-verification-batch.md` 称"全链路统一运行时真实 WebView UA 后
> CF 循环已修复(92cdde0)"。代码实证显示:UA 统一**确实必要**(见 `UserAgent.kt:9-26`),
> 但**不是充分条件**——cookie 合并的覆盖顺序(`:117-118`)才是验证后仍循环的残留根因。
> 该记忆应修订为:"UA 统一已落地;cookie 覆盖问题待修"。

### K-002  下载方式为"原图"时 HTTP 403
- type: bug
- severity: **P1**
- area: data/download
- evidence:
  - `DownloadManager.kt:187-190` 原图模式取 `sub.fileUrl.ifEmpty { sub.fullImageUrl }`。
  - `fileUrl` 来自 `FurAffinityScraper.kt:1096-1099`:下载锚点缺失时回落
    `absoluteFaUrl(dl.attr("href"))` —— 可能是**详情页相对路径**,也可能指向
    `d.furaffinity.net` 原件。
  - 请求仅带 `User-Agent` 与固定 `Referer`(`DownloadManager.kt:240-242`),
    **未携带会话 cookie**(`a`/`b`/`cf_clearance`)。`d.furaffinity.net` 对无 cookie
    直链请求返回 403 是站点既有行为。
- decision: 修复
- behavior-change: no
- subtask: FIX-002
- status: open

### K-003  评论区排版崩溃(日期竖排、头像裁切、实体未解码)
- type: bug
- severity: **P1**
- area: ui/components
- evidence:
  - 日期竖排:`CommentsSection.kt:438` `Text(text = dateStr, fontSize = 11.sp)`
    在 `Row` 内**没有** `maxLines = 1`,也没有 `weight`/`fill = false` 约束;
    同行 `:401-413` 作者名用了 `Modifier.weight(1f, fill = false)`,
    `:416-422` title 也带 `maxLines = 1`;日期被挤压时换行成竖列(与截图一致)。
  - 头像裁切:`:356` `.padding(top = 8.dp)` 与外部容器叠加,
    且 `:345-351` `drawWithContent` 先画连接线再画内容,顶部无足够 inset。
  - 实体未解码:`TranslatableText`(`TranslationWidgets.kt:141-196`)把
    `s.translated` **直接** `Text()` 输出(`:168`),未做 `HtmlCompat.fromHtml` 解码;
    翻译结果中的 `<3` 被上游转成 `&lt; 3` 后原样显示。
    对比:`HtmlTextView.kt:85` 走 `HtmlCompat.fromHtml`,描述/简介无此问题。
- decision: 修复
- behavior-change: no
- subtask: FIX-003
- status: open

### K-004  用户页"画廊文件夹"未默认折叠
- type: bug
- severity: P2
- area: ui/screens
- evidence:
  - `UserProfileScreen.kt:754-832` `folders` 区块直接遍历 `grouped` 渲染,
    **无 expanded 状态**;同文件 `:1481` 的简介折叠用了
    `var expanded by remember { mutableStateOf(false) }`,两处模式不一致。
  - 文件夹多时首屏被撑长,与"默认折叠"预期不符。
- decision: 修复
- behavior-change: **yes**(UI 默认态变化)
- subtask: FIX-004
- status: open

### K-005  详情页右栏顺序:"作者画廊"下应为"简介/详细信息/评论"
- type: bug
- severity: P2
- area: ui/screens
- evidence:
  - `PostDetailScreen.kt:353` `AuthorGallerySection(...)`,
    `:446-464` 紧接 Tabs `[Story] / Description / Meta / Comments`,
    其中 `:385` `val tabIndexComments = tabs.lastIndex`(评论恒末位)。
  - 用户要求:作者画廊 **下方** 是"简介 + 详细信息 + 评论"一栏,且带折叠按钮。
- decision: 修复
- behavior-change: **yes**(布局顺序调整)
- subtask: FIX-005
- status: open

### K-006  搜索与筛选面板需按 FA 风格重构(移动端极客风)
- type: refactor
- severity: P2
- area: ui/screens
- evidence:
  - `SearchComponents.kt` 1202 行,屏级 UI 与筛选状态、URL 镜像耦合在同一文件。
  - 现有面板为通用 Material 控件堆叠,未对齐 `furaffinity.net` 的搜索面板信息架构。
- decision: 重构
- behavior-change: yes(视觉;URL 契约保持不变)
- subtask: REFACTOR-010 / FIX-006
- status: open

### K-007  翻译文本 HTML 实体泄漏(`<3` → `&lt; 3`)
- type: bug
- severity: P1
- area: translate/ui
- evidence: 同 K-003 第三条:`TranslationWidgets.kt:168` 未解码实体。
  独立编号以便单独验收与回归。
- decision: 修复
- behavior-change: no
- subtask: FIX-003
- status: open

---

## 二、架构与复杂度(扫描发现)

### A-001  屏级 Composable 严重膨胀
- type: refactor
- severity: **P1**
- area: ui/screens
- evidence:
  | 文件 | 行数 |
  | --- | --- |
  | `PostDetailScreen.kt` | 2054 |
  | `UserProfileScreen.kt` | 1916 |
  | `HomeScreen.kt` | 1431 |
  | `ProfileScreen.kt` | 1240 |
  | `SearchComponents.kt` | 1202 |
  | `AppearanceSettingsScreen.kt` | 1008 |
- decision: 重构(目标单文件 < 800 行)
- behavior-change: no
- subtask: REFACTOR-006 ~ REFACTOR-010
- status: open

### A-002  `FurAffinityScraper` 单文件 1704 行,承担 6 类职责
- type: refactor
- severity: **P1**
- area: data/remote
- evidence: `FurAffinityScraper.kt` 1704 行,内含:会话与 cookie 管理(`:94-178`)、
  通用 fetch + CF 重试(`:185-245`)、搜索(`:869+`)、用户页/画廊/文件夹(`:534-570`)、
  详情页解析(`:1085-1099`)、常量与缓存 map(`:50-56`)。
- decision: 按职责拆子文件,共享 `FaHtml` 解析工具
- behavior-change: no
- subtask: REFACTOR-004
- status: open

### A-003  全局单例式 object 承担状态,难以测试与生命周期管理
- type: refactor
- severity: P2
- area: data / viewmodel
- evidence:
  - `DownloadManager`(`DownloadManager.kt:61`)是 `object` + `appContext` 静态持有。
  - `FurAffinityScraper` 是 `object`,`sessionCookies`/`loggedIn` 为可变静态字段(`:31-33`)。
  - `SelectionStore`(注释自述"与 DownloadManager 同模式的单例")。
  - 6 个 ViewModel **零测试**,与静态单例耦合是直接原因之一。
- decision: 评估引入 DI 或至少注入接口(先做接缝,不引入框架)
- behavior-change: no
- subtask: REFACTOR-011
- status: open

### A-004  跨层反向依赖:UI 直接引用 data 层实现
- type: refactor
- severity: P2
- area: ui / data
- evidence:
  - `CloudflareGateDialog.kt:129,155` 直接调用
    `com.furflix.app.data.remote.UserAgent.update` / `CloudflareGate.resolve`。
  - `DownloadManager.kt:240` 直接引用 `data.remote.UserAgent.current()`。
  - 缺少 `domain` 层:无 `usecase`/`repository interface` 抽象。
- decision: 抽接口层(阶段 4 后半)
- behavior-change: no
- subtask: REFACTOR-011
- status: open

---

## 三、重复代码(候选公共件)

| ID | 重复项 | 出现位置 | 建议公共件 | 严重度 |
| --- | --- | --- | --- | --- |
| D-001 | 空/错误态 UI | 各 Screen 内联 | `ui/components/StateViews.kt` | P2 |
| D-002 | 下拉刷新容器 | HomeScreen / ProfileScreen / UserProfileScreen | `PullToRefreshBox` 统一 | P2 |
| D-003 | 头像 + 圆形裁剪 + 占位 | CommentsSection / PostDetail / UserProfile | `UserAvatar.kt` | P2 |
| D-004 | 分区标题(文字 + 分隔线) | 多屏 | `SectionHeader.kt` | P3 |
| D-005 | 折叠/展开(更多/更少) | UserProfileScreen:1481 与 K-004 需求 | `ExpandableSection.kt` | P2 |
| D-006 | 图片加载 `ImageRequest.Builder` 样板 | CommentsSection:368 / PostDetail 多处 | `rememberImageRequest()` | P3 |
| D-007 | Toast 提示 | DownloadManager:toast 与各屏 | `UiMessenger` | P3 |
| D-008 | 日期格式化 `DateFormat` | CommentsSection:431 等 | `formatFaDate()` | P3 |
| D-009 | 线程切换 `withContext(Dispatchers.IO)` | Scraper 内多处 | 统一到 repository 边界 | P2 |
| D-010 | 魔法数字(dp/sp/颜色) | 全局散落 | `theme/Dimen.kt` / `Color.kt` | P2 |
| D-011 | `AnimatedVisibility` 包裹模式 | 多屏 | 统一动效修饰符 | P3 |
| D-012 | 复选/多选状态管理 | SelectionStore + 各屏 | 统一 selection 容器 | P3 |

- subtask: REFACTOR-001 / REFACTOR-002
- status: open

---

## 四、测试缺口

### T-001  UI 层与 ViewModel 零测试
- type: test
- severity: **P1**
- area: ui / viewmodel
- evidence:
  | 目录 | 生产文件 | 测试文件 | 覆盖等级 |
  | --- | --- | --- | --- |
  | `ui/components` | 30 | 0 | **C(无)** |
  | `ui/screens` | 16 | 0 | **C** |
  | `ui/navigation` | 1 | 0 | C |
  | `ui/theme` | 7 | 0 | C |
  | `viewmodel` | 6 | 0 | **C** |
  | `data/repository` | 1 | 0 | C |
  | `data/download` | 2 | 0 | C |
  | `data/selection` | 1 | 0 | C |
  | `translate/ui` | 3 | 0 | C |
  | `data/local` | 11 | 2 | B |
  | `data/model` | 12 | 5 | B |
  | `data/remote` | 10 | 6 | **A** |
  | `translate/data` | 8 | 0 | C |
  | `utils` | 2 | 1 | B |
  - 全仓仅 20 个测试文件,**无 `androidTest`**,无 Compose UI 测试。
- decision: 阶段 5 补齐
- subtask: 阶段 5
- status: open

### T-002  无覆盖率度量与门禁
- type: test
- severity: P2
- area: ci
- evidence: `build.yml` 仅跑 `testDebugUnitTest`,无 JaCoCo、无阈值、无报告归档。
- decision: 阶段 3 加报告,阶段 5 加阈值
- status: open

---

## 五、安全 / 性能 / 健壮性

| ID | 类型 | 严重度 | 证据 | 建议 | 状态 |
| --- | --- | --- | --- | --- | --- |
| S-001 | security | P1 | `Log.d` 出现 36 处(`FurAffinityScraper.kt:160,185,208,209,239,271,282,869` 等),发布于 Release APK | 引入 Timber,Release 种 NoOp Tree | open |
| S-002 | security | P2 | `Log.d("FurScraper","Fetching: $url")` `:185` 与 `"Short HTML: ${html.take(500)}"` `:209` 会打印完整 URL 与页面片段,含会话上下文 | 降为 debug 且脱敏 | open |
| S-003 | robust | P2 | 空 catch 吞异常:23 处模式,典型 `HomeScreen.kt:303,1305`、`ProfileScreen.kt:1030-1031` | 至少 `Log.w` 或统一 ErrorHandler | open |
| S-004 | robust | P2 | `!!` 强制解包 9 处 | 改为 `?:` 安全回落 | open |
| S-005 | security | P2 | WebView 启用 JS 且 `setAcceptThirdPartyCookies(true)`(`CloudflareGateDialog.kt:123,131`),`domStorageEnabled` 开 | 审计必要性;验证 WebView 用完清理 storage | open |
| S-006 | perf | P2 | cookie 持久化在 DataStore(`FurRepository.kt:33,121-137`),会话态每次请求走序列化读 | 评估内存缓存层 | open |
| S-007 | robust | P3 | `AWAIT_TIMEOUT_MS = 180_000`(`CloudflareGate.kt:40`):等待 3 分钟无反馈 | 加倒计时/进度提示 | open |
| S-008 | perf | P3 | 多处 `remember` 依赖大对象(如 `remember(folders)` `UserProfileScreen.kt:764`) | 复核 key 稳定性 | open |

---

## 六、i18n

| ID | 类型 | 严重度 | 证据 | 建议 | 状态 |
| --- | --- | --- | --- | --- | --- |
| I-001 | i18n | P2 | 中文硬编码字符串 52 处(多为 KDoc 注释,但 `UserProfileScreen.kt:1488,1492` 附近有用户可见处理逻辑) | 逐条甄别:注释保留,UI 文案入 `strings.xml` | open |
| I-002 | i18n | P3 | `README_RU.md` / `README_UA.md` 与 EN/ZH 可能不同步 | 阶段 2 对齐并标 deprecated | open |

---

## 七、CI / 门禁

| ID | 类型 | 严重度 | 证据 | 建议 | 状态 |
| --- | --- | --- | --- | --- | --- |
| C-001 | ci | **P1** | 仅 `build.yml` 一个工作流;`lintDebug` 失败时 `grep | head -6` 只报 6 条,其余错误被吞 | 归档完整 lint 报告为 artifact | open |
| C-002 | ci | P2 | 无静态分析(ktlint/detekt) | 阶段 3 加 `quality.yml`,warn-only 起步 | open |
| C-003 | ci | P2 | 无 PR 模板 / CODEOWNERS / commit 规范校验 | 阶段 3 补齐 | open |
| C-004 | ci | P3 | `ubuntu-24.04` 钉死,2026-10-19 `ubuntu-latest` 迁移需复核 | 阶段 3 复核 | open |
| C-005 | ci | P3 | 无自动变更日志 | 阶段 3 评估 Release Please | open |

---

## 八、行为变化登记(阶段 4 冻结前确认)

| 编号 | 变更 | 关联 | breaking |
| --- | --- | --- | --- |
| F-1 | 用户页"画廊文件夹"默认折叠 | K-004 / FIX-004 | **yes** |
| F-2 | 详情页右栏:画廊 → 简介/详情/评论 折叠组 | K-005 / FIX-005 | **yes** |
| F-3 | 搜索面板重构(视觉) | K-006 / FIX-006 | yes(视觉) |
| F-4 | 评论区日期不换行、头像不裁切、实体解码 | K-003 / K-007 / FIX-003 | no(bug 修复) |
| F-5 | 原图下载带会话 cookie | K-002 / FIX-002 | no(bug 修复) |
| F-6 | CF cookie 合并顺序修正 | K-001 / FIX-001 | no(bug 修复) |

---

## 九、统计

| 分类 | 数量 |
| --- | --- |
| 已知问题(K-*) | 7 |
| 架构/复杂度(A-*) | 4 |
| 重复代码(D-*) | 12 |
| 测试(T-*) | 2 |
| 安全/性能/健壮(S-*) | 8 |
| i18n(I-*) | 2 |
| CI(C-*) | 5 |
| **合计** | **40** |

严重度分布:P0=1、P1=6、P2=20、P3=13。
