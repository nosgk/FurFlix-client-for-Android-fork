# AGENTS.md — FurFlix Android 客户端协作规范

面向在本仓库工作的所有协作者与 agent。

## 项目概览

- Kotlin 2.x + Jetpack Compose(Material 3),单模块 `:app`,包名 `com.furflix.app`
- minSdk 26 / targetSdk 36 / compileSdk 37,AGP 9.4.1,Compose BOM 2026.09
- 上游:FurrychX/FurFlix-client-for-Android(低频跟进;rebase 时保持最小冲突面)
- 本 fork 的主线差异化工作:技术栈升级与**简体中文本地化**(规划细则文档按开发
  文档安全规范不入库,仅保留在仓库外的本地备份中)

## 构建与验证

- **禁止本地构建**。所有验证一律走 GitHub Actions(push 后观察 Build workflow:
  `lintDebug` + `assembleRelease`,debug APK 可手动 dispatch)。
- 提交前最低自检:XML 改动用解析器验良构;Kotlin 改动靠 CI 把关。

## i18n 规范(中文本地化)

1. **新增 UI 文案**:先在 `values/strings.xml` 就近注释组加英文键;同步在
   `values-zh-rCN/strings.xml` 镜像位置(相同注释组、相同顺序)补中文;有歧义的键
   在 zh 文件里加 ctx 注释。
2. **调用点**:Composable 内用 `stringResource(R.string.x)`;非组合上下文
   (ViewModel/Toast)用 `context.getString(...)`。**硬编码的用户可见文案是 bug**,
   发现即清退(上游遗留的俄语硬编码同理)。
3. **缺键回落英文是设计而非错误**:zh 允许暂时落后,rebase 后跑
   `./gradlew :app:lintDebug`,按 `MissingTranslation`/`ExtraTranslation` 报告对账补齐。
4. **占位符**:与默认文件保持一致的 `%s` 写法;站点抓取的内容(FA 标签、注册日期等)
   本身是英文,不翻译。
5. **不变量**:不要往 `MainActivity` 的 `android:configChanges` 里加 `locale`——
   那会静默打断语言切换(界面残留旧语言)。
6. 语言选择器里的语言名(English/Русский/Українська/中文)是视觉锚点,恒显母语名,
   不参与翻译。

## 提交规范

- Conventional Commits(`feat:`/`fix:`/`build:`/`docs:`),与仓库现有风格一致。
- 本地化改动与触碰上游源文件的改动(如硬编码清退)分开成独立 commit,便于 rebase
  整块重放。
