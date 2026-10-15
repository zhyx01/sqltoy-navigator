# 文档索引

本目录是 `sqltoy-navigator` 的**开发者**文档。面向插件使用者的说明在仓库根目录的 `README.md`。

仓库根目录还有三份入口文档，本目录是它们的展开版：

- [`../AGENTS.md`](../AGENTS.md) —— 命令速查、硬约束、任务导航（给 AI agent 与新人看的地图）
- [`../ARCHITECTURE.md`](../ARCHITECTURE.md) —— 架构与代码结构：子系统划分、类间依赖、三条主链路
- [`../PLANS.md`](../PLANS.md) —— 计划台账：进行中 / 已完成

## 阅读路线

| 你的处境 | 建议顺序 |
|----------|----------|
| 第一次接触这个仓库 | `../ARCHITECTURE.md` → `reference/class-catalog.md` → `conventions.md` |
| 想接一个正在做的事 | `../PLANS.md` → `plans/active/` 里那份计划 → 直接看它末节的「当前进度与下次续接」 |
| 要改一个具体功能 | `reference/class-catalog.md` 定位类 → 对应领域文档 → `quality/verification.md` |
| 要加一个新扩展点 | `intellij-platform.md` → `reference/class-catalog.md`（登记）→ `quality/golden-principles.md`（约束） |
| 要发版 | `branches.md` |

## 文档清单

| 文档 | 内容 | 状态 |
|------|------|------|
| [`reference/class-catalog.md`](reference/class-catalog.md) | **全部 31 个类的职责与扩展点注册情况 —— 类清单的唯一事实来源** | ✅ |
| [`conventions.md`](conventions.md) | 代码约定：类型与可见性、Javadoc 模板、判空（**实测混用**）、注释风格、依赖、新增类的清单、**提交纪律（第 12 节）**、**个人代码习惯（第 13 节）**、**改动纪律：最小修改（第 14 节）** | ✅ |
| [`intellij-platform.md`](intellij-platform.md) | 平台知识：扩展点注册三规则、语言注入的环境依赖、`DumbService` 两种策略、`CachedValuesManager`、**annotator vs inspection 的能力边界**、可测试性 | ✅ |
| [`sqltoy-semantics.md`](sqltoy-semantics.md) | 业务语义：`maybeSqlId` 形态、**高置信位置谓词**、方言等价、`@include`、**四种视觉状态的完整规则表**、未覆盖场景 | ✅ |
| [`branches.md`](branches.md) | 分支模型与发布流程（从 `release.sh` / `release.bat` 反推）、版本号四处同步、发布前 checklist | ✅ |
| `quality/golden-principles.md` | 不可违背的原则，每条挂一个会失败的校验 | 🚧 阶段 4 |
| `quality/verification.md` | 分层验证手册 + `runIde` 手工冒烟清单 | 🚧 阶段 4 |
| [`plans/README.md`](plans/README.md) | 计划的写法、命名、生命周期与模板 | ✅ |
| `plans/active/` | **没结束的计划**（待开工 / 进行中）—— 中断后回到这里接着做 | — |
| [`plans/active/2026-10-14-Harness-工程改造-harness-bootstrap.md`](plans/active/2026-10-14-Harness-工程改造-harness-bootstrap.md) | 【进行中】把本仓库改造成 Harness 工程 | ✅ |
| [`plans/active/2026-10-15-未定义的-SQLID-错误-unresolved-sqlid-error.md`](plans/active/2026-10-15-未定义的-SQLID-错误-unresolved-sqlid-error.md) | 【进行中】未定义 sqlId 升级为错误提示 | ✅ |
| `plans/completed/` | **已结束的计划**（已完成 / 已放弃），归档 | — |
| [`plans/completed/2026-08-31-悬浮提示三处问题修复-hover-hint-fix.md`](plans/completed/2026-08-31-悬浮提示三处问题修复-hover-hint-fix.md) | 【已完成】悬浮提示三处问题修复（回溯录入，改动已丢失） | ✅ |

> 不再另建 `docs/architecture.md` —— 架构与结构内容统一由根目录 [`../ARCHITECTURE.md`](../ARCHITECTURE.md) 承载，避免两处维护。

## 维护规则

1. **一份事实只写一处。** 类清单只在 `reference/class-catalog.md` 维护，其它文档引用它，不复制它。
2. **改行为就同步文档。** 行为与文档不一致时，以代码为准并立刻修正文档 —— 文档漂移比没有文档更糟。
3. **规则必须可校验。** 想写进 `quality/golden-principles.md` 的规矩，必须先有对应的机械化校验（测试或脚本），否则不写。
4. **计划先行。** 需要多步执行的工作，先在根目录 `PLANS.md` 登记一行，再到 `plans/active/` 建文档；做完后移入 `plans/completed/` 归档，两边状态保持同步。
5. **提交时机由人决定。** 文档与代码改动一律只落盘，`git add` / `git commit` / `git push` 等用户明确指示再做。规则全文见 [`conventions.md`](conventions.md) 第 12 节 —— 本仓库所有涉及「提交」的表述都指回它。
