# AGENTS.md

> 这是本仓库的**地图**，不是手册。细节一律在 `docs/`，本文件只负责告诉你「该去哪看」。

## 这是什么

`sqltoy-navigator` 是一个 **IntelliJ IDEA 插件**（不是应用、也不是库）。它让 SqlToy ORM 项目的开发者在 Java 代码里的 `sqlId` 字符串与 XML 中的 `<sql id="...">` 定义之间双向跳转，为 XML 内的 SQL 提供高亮、括号匹配与参数识别，并在 `sqlId` 引用不到定义时给出置灰或报错提示。

- 构建：Gradle 9.5.1（**Wrapper 已提交，用 `./gradlew`**）+ IntelliJ Platform Gradle Plugin 2.16.0
- 平台 SDK：IntelliJ IDEA 2024.1.1，`sinceBuild = 241.15989.150`
- 字节码目标：Java 17
- **运行时零第三方依赖**：不引入 Lombok / Hutool / Guava 等，插件体积敏感
- 源码基线：**31 个类**，全部位于唯一包 `com.ax.sqltoy`，类名统一 `SqlToy` 前缀

## 命令速查

| 目的 | 命令 |
|------|------|
| 只编译（最快的语法/类型校验） | `./gradlew compileJava` |
| 跑测试 | `./gradlew test` |
| 起一个带本插件的沙箱 IDE（手动验证） | `./gradlew runIde` |
| 打包插件 | `./gradlew buildPlugin` → 产物在 `build/distributions/` |
| 无网络时 | 上述命令追加 `--offline` |

一律使用仓库自带的 `./gradlew`（Windows：`gradlew.bat`），不要依赖本机安装的 gradle。

## 仓库地图

| 路径 | 放什么 | 什么时候改 |
|------|--------|-----------|
| `AGENTS.md`（本文件） | 入口地图：命令、硬约束、任务导航 | 规则或命令有变化时 |
| `ARCHITECTURE.md` | 架构与代码结构：子系统划分、类间依赖、目录 | 新增子系统或大改结构时 |
| `PLANS.md` | 计划台账：进行中 / 已完成 | 有计划开始、完成或放弃时 |
| `docs/plans/active/` | **没结束的计划**（待开工 / 进行中） | 计划开始、进度变化时；**接手未完成的工作先看这里** |
| `docs/plans/completed/` | 已结束的计划（已完成 / 已放弃），归档 | 计划一结束就从 `active/` 移入 |
| `src/main/java/com/ax/sqltoy/` | 全部插件源码（唯一包） | 改功能时 |
| `src/main/resources/META-INF/plugin.xml` | 插件清单与**全部扩展点注册** | 新增/改名扩展点实现类时 |
| `src/main/resources/icons/`、`META-INF/pluginIcon.svg` | 图标资源 | 改视觉时 |
| `docs/reference/class-catalog.md` | **类职责的唯一事实来源** | 新增 / 删除 / 改名类时 |
| `docs/` | 开发者文档（计划、类目录、约定、领域语义、验证手册） | 改行为时同步 |
| `scripts/` | 可执行的工程脚本 | 改校验流程时 |
| `src/test/` | 结构校验测试 | 加规则时 |
| `release.sh` / `release.bat` | 发布流程脚本（行为见 `docs/branches.md`） | 不要直接改 |
| `README.md` | **面向插件使用者**，不是开发者文档 | 改用户可见功能时 |

## 硬约束（会被自动校验拦住）

> 机械化校验（`scripts/verify.sh` + `src/test/` 结构测试）在 harness 阶段 4 落地。**在那之前下列各项是人工审查项。**

完整原则与对应校验见 `docs/quality/golden-principles.md`（阶段 4）。以下是摘要：

1. 新增类必须放在 `com.ax.sqltoy` 包，类名以 `SqlToy` 开头。
2. 每个类型必须有类级 Javadoc；除 `record` 外必须含 `@author ax` 与 `@date yyyy-MM-dd`。
3. 扩展点实现类必须**同时**登记在 `plugin.xml` 与 `docs/reference/class-catalog.md`。
4. `plugin.xml` 里的 `language="SqlToySQL"` 必须与 `SqlToySqlLanguage` 构造函数传入的 id 一致。
5. `plugin.xml` 的 `depends` 必须覆盖源码实际用到的 IntelliJ 模块（Java / XML / lang / platform）。
6. 源码 UTF-8 且无 BOM（注释全中文，编码错了直接乱码）。
7. 禁止 `System.out` / `printStackTrace`（实测 0 处）；不留 `TODO` / `FIXME`（实测剩 **1 处**，见下）。
   - ⚠️ 校验必须**大小写不敏感**：现存那一处是小写 `todo`，写在 `SqlToyJavaSqlIdResolver.java:53`。按大小写敏感匹配会永远查不出这类标记。
   - 该处已不是普通待办 —— 它标在 `SQL_ID_FIRST_ARGUMENT_METHOD_NAMES` 上，而这个集合现在直接决定「未定义 sqlId」的报错范围。详见 `docs/conventions.md` 第 10 节与 `docs/sqltoy-semantics.md` 第 3 节。
8. 不引入运行时第三方依赖（`build.gradle.kts` 的依赖块只允许 `intellijPlatform { ... }`）。
9. **只改被点到的代码，最小修改**：没被提到的代码不能动（含顺手发现的坏味道、笔误、命名不一致）；同一需求有多种改法时选改动面最小的；需求没说的不替它补，不确定就先问。
   - ⚠️ 这一条**不受阶段 4 影响** —— 它没有机械化校验，**永远是人工审查项**。判的是「该不该动这段代码」，是意图判断，写不成断言。详见 `docs/conventions.md` 第 14 节。

## 任务导航

| 我想…… | 先读 | 通常改 |
|--------|------|--------|
| 弄清某个类是干什么的 | `docs/reference/class-catalog.md` | — |
| 改 Java ↔ XML 的 sqlId 跳转 | `ARCHITECTURE.md` 的第 1 条链路 | `SqlToySqlIdReferenceContributor` / `SqlToySqlIdReference` / `SqlToySqlIdXmlResolver` / `SqlToyJavaSqlIdResolver` |
| 改 `@include("sqlId")` 跳转 | `ARCHITECTURE.md` 的第 2 条链路 | `SqlToySqlInclude*` 系列（5 个类） |
| 改 gutter 图标 / 行标记 | `docs/reference/class-catalog.md` | `SqlToySqlIdLineMarkerProvider` / `SqlToyNavigationGutterIconRenderer` |
| 改 SQL 高亮、括号、词法 | `docs/intellij-platform.md` | `SqlToySqlLexer` / `SqlToySqlTokenTypes` / `SqlToySqlSyntaxHighlighter` / `SqlToySqlBraceMatcher` |
| 改置灰 / 提示逻辑 | `docs/sqltoy-semantics.md` | `SqlToySqlIdAnnotator`（Java 侧）/ `SqlToyXmlSqlAnnotator`（XML 侧） |
| 改「未定义 sqlId」报错范围 | `docs/reference/class-catalog.md` 的 G 节 | `SqlToyUnresolvedSqlIdInspection` / `SqlToyUnresolvedIncludeInspection`；开关在 `SqlToyJavaSqlIdResolver.SQL_ID_FIRST_ARGUMENT_METHOD_NAMES` |
| 新增一个扩展点 | `docs/intellij-platform.md` | `plugin.xml` + 实现类 + 类目录 |
| 新增数据库方言 | `docs/sqltoy-semantics.md` | `SqlToySqlIdXmlResolver` 的方言前缀表 |
| **加一条代码规范 / 记一个写法习惯** | `docs/conventions.md`（先判断用不用校验拦住） | 软约定 → `conventions.md` 对应主题节或第 13 节；硬约束 → `docs/quality/golden-principles.md`（阶段 4）+ 本节摘要一行 |
| 提交 / 发版 | `docs/branches.md` | — |

## 禁区

- **不要自动提交。** 改完只落盘到工作区，`git add` / `git commit` / `git push` 都等用户明确说了再做；要提交时范围也由用户指定（例如「只提交功能代码，不带 harness 文档」），按点名路径逐个 `git add`，不要 `git add -A`。规则全文见 `docs/conventions.md` 第 12 节。
- **最小修改：不动的代码不要动。** 只改任务明确点到的位置，不借机重构、改命名、清理「看起来是笔误」的地方；范围外发现的问题**报而不改**（在回复里列出，由用户决定要不要另开计划）。规则全文见 `docs/conventions.md` 第 14 节。
- 不要改动或删除 `build/`、`.gradle/`、`.idea/`、`.intellijPlatform/`、`gradle/wrapper/`。
- 不要改 `release.sh` / `release.bat` 的行为（要改先说明）。
- 不要重排 `plugin.xml` 的扩展点顺序，不要改已有扩展点的 id 与实现类名（改名必须同步过校验）。
- 不要为了让 `git status` 看起来干净而提交、`stash` 或回滚 —— 工作区停在「已改未提交」是正常状态（唯一例外：从 `docs/branches.md` §6.4 的误删事故中 `git checkout -- .` 找回文件）。

## 先读哪份

不确定从哪下手时，按这个顺序：

1. `ARCHITECTURE.md` —— 先弄清代码长什么样
2. `docs/reference/class-catalog.md` —— 再找到要改的那个类
3. `PLANS.md` → `docs/plans/active/` —— 看看有没有人正在改同一块；要接手未完成的工作，直接看那份计划末节的「当前进度与下次续接」

完整文档目录见 `docs/index.md`。
