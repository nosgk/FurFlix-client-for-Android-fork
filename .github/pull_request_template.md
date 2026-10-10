## 变更类型

<!-- 选一项;对应 Conventional Commits 的 type -->
- [ ] `feat` 新功能
- [ ] `fix` 缺陷修复
- [ ] `refactor` 重构(行为不变)
- [ ] `docs` 文档
- [ ] `test` 测试
- [ ] `build` / `ci` 构建与门禁

## 说明

<!-- 做了什么、为什么。关联 issue 写 #编号 或 ISSUES.md 的 K-xxx / A-xxx / FIX-xxx -->

## 自检清单

- [ ] 已读 `AGENTS.md`(i18n、提交规范、不本机构建)
- [ ] PR 标题符合 Conventional Commits(`<type>[scope]: <描述>`)
- [ ] **未在本地构建**;验证以 GitHub Actions 结果为准
- [ ] 新增/修改的**用户可见文案**已进 `values/strings.xml` 与 `values-zh-rCN/strings.xml`
- [ ] 未往 `MainActivity` 的 `android:configChanges` 加 `locale`
- [ ] 逻辑变更已补单元测试(纯函数优先)
- [ ] 架构/决策变更已更新 `docs/ARCHITECTURE.md` 或新增 ADR
- [ ] 若修复了 `ISSUES.md` 中的条目:已把该条状态改为 `fixed` 并填 commit SHA

## 行为变化

<!-- 无则填「无」。有则列出:用户可感知的变更(含 UI 顺序、默认态、下载行为等),
     并在 ISSUES.md 对应条目标 behavior-change: yes -->

- 无

## 验证方式

<!-- 如何在装机后验证:进入哪个页面、做什么操作、期望看到什么 -->

## 截图 / 录屏

<!-- UI 变更请附;评论区、详情页、搜索面板等视觉改动尤其需要 -->
