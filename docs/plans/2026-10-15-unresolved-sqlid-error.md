# 计划：未定义 sqlId 升级为错误提示

- 状态：active（功能代码已提交，待 `runIde` 冒烟）
- 创建日期：2026-10-15
- 完成日期：
- 目标：Java 代码与 XML `@include` 引用了不存在的 sqlId 时，除下划线外**在编辑器右侧出现红色错误条纹**（等同代码报错的观感），并可在 `Settings → Inspections` 中关闭或调整级别。

## 提交记录

| 日期 | 分支 | 提交 | 内容 |
|------|------|------|------|
| 2026-10-15 | `feat-v20261015-unresolved-sqlid-error` | `d9009c7` | 本计划的全部功能代码，11 个文件 |

该分支基于 `develop`，且**只含功能代码**。本计划文档与 harness 文档都留在 `harness-bootstrap` 上，映射关系见 `PLANS.md` 的「计划 → 分支 / 提交」。

## 背景与现状

现状（全部为核实过的代码事实）：

- `SqlToySqlIdReference.java:32` 的引用是 **soft**（`super(element, range, true)`），平台不会产出 `Cannot resolve`，所以**当前根本没有红色波浪线**。
- `SqlToySqlIdAnnotator.java:55` 对解析不到的 sqlId 用 `newSilentAnnotation(HighlightSeverity.INFORMATION)` + 灰色前景。`silent` 意味着**不画波浪线、不进右侧 error stripe、不进 Problems 面板**。
- `SqlToyXmlSqlAnnotator.java:206` 对 `@include` 的处理同理：解析不到时不加图标，但仍然上色，同样没有错误级别。
- `build.gradle.kts:24-27` 的 changelog 显示 v1.1.0 的改动就是「Java 代码中引用的 sqlId 不存在，置灰」——本次是把它从置灰升级为错误。

技术前提（在本机 IDEA 2024.1.1 产物上反编译核实，非凭记忆）：

- 右侧错误条纹由**高亮级别**驱动，与文字属性无关；`level="ERROR"` 即可点亮。
- `ProblemDescriptor.setTextAttributes(TextAttributesKey)` → 实现在 `ProblemDescriptorBase`，被 `LocalInspectionsPass` 通过 `getEnforcedTextAttributes()` 读取并传入 `HighlightInfo.Builder.textAttributes(...)`。
- `InspectionProfileEntry` **没有** `getTextAttributesKey()`；`createProblemDescriptor` 的 12 个重载**都不接受** `TextAttributesKey`。
- `ProblemDescriptorUtil.getHighlightInfoType(ProblemHighlightType, HighlightSeverity, SeverityRegistrar)` 证实「高亮类型 + 级别 → 呈现」的映射链。

## 范围

### 要做

- Java 侧：**SqlToy DAO 方法首参的字面量**无法解析到 XML 定义时标为错误。高置信谓词 = `SqlToyJavaSqlIdResolver.isFirstSqlIdArgument`，方法名集合 = `find` / `findOne` / `findByMap` / `getSingleValue`。
- XML 侧：`@include("sqlId")` 无法解析到定义时标为错误。
- 两个检查都可通过 `Settings → Inspections` 关闭或改级别（默认 `ERROR`），结果进入 Problems 面板。

### 不做（边界）

- **不放宽报错范围到所有候选字符串。** `maybeSqlId` 的宽判定只要求匹配 `[A-Za-z0-9_.$:/\-]+`，`"UTF-8"`、`"2026-10-14"`、`"com.ax"` 全都命中；一旦对宽集合报错就是满屏红。宽集合**继续置灰**。
- **静态常量形式不报错。** 常量在这些位置无法确定用途：声明处无从判断是否用作 sqlId，使用处报错的位置又不在字符串上。保留现有跳转行为即可。
- **不改 `SqlToySqlIdReference` 的 soft 标记。** 改成非 soft 会让平台自己再报一个 `Cannot resolve`，与我们的消息打架，并且会改变 Ctrl+Click 与补全的既有行为。
- **不改 `SQL_ID_FIRST_ARGUMENT_METHOD_NAMES` 集合本身。** 它同时影响常量参数的跳转解析，扩名单属于另一个改动。
- 不做自定义配色（灰字 + 红波浪需随插件附配色方案 XML，本次不做，采用标准错误观感）。
- 不引入 Quick Fix。

## 步骤

| 阶段 | 内容 | 验收标准 |
|------|------|----------|
| 1 | 新增 `SqlToyUnresolvedSqlIdInspection`（JAVA）+ `SqlToyUnresolvedIncludeInspection`（XML） | `./gradlew compileJava --offline` 通过 |
| 2 | `plugin.xml` 注册 2 条 `localInspection`；补 2 份 `inspectionDescriptions/*.html` | `shortName` 与描述文件名一一对应 |
| 3 | 让出置灰：`SqlToySqlIdAnnotator` 高置信位置跳过、`SqlToyXmlSqlAnnotator` 未解析 include 跳过；`SqlToyJavaSqlIdResolver.isFirstSqlIdArgument` 提升为包内可见 | 同一区间不再叠两层高亮 |
| 4 | 同步 `docs/reference/class-catalog.md`（31 个类 / 12 条注册 / 新增 G 节）、`ARCHITECTURE.md`、`AGENTS.md`、`docs/index.md` | 类目录 ↔ 源码双向一致 |
| 5 | `runIde` 冒烟（见下） | 5 条全过 |

## 验证方式

1. **编译**：`./gradlew compileJava --offline` → `BUILD SUCCESSFUL`。
2. **结构一致性**：源码 31 个类全部登记在 `class-catalog.md`，无幻影条目；`plugin.xml` 注册条目数 = 12。
3. **`./gradlew runIde` 冒烟清单**（逐条人工确认）：
   1. `findByMap("does_not_exist", params)` → 字符串红字 + 红色波浪线 + **编辑器右侧红色条纹** + Problems 面板可见。
   2. 同一位置改成已定义的 sqlId → 红色消失，恢复绿色下划线，Ctrl+Click 可跳转。
   3. 普通字符串（如 `Charset.forName("UTF-8")`、`DateTimeFormatter.ofPattern("2026-10-14")`）→ **不报红**，保持灰色。
   4. XML 里 `@include("does_not_exist")` → 标红；改成已定义的 sqlId → 恢复原色 + 跳转图标。
   5. `Settings → Inspections → SqlToy Navigator` 里能搜到两条检查，改级别为 Warning 后红色变黄色、关掉后恢复置灰。
4. **反例**（必须确认没坏）：项目索引进行中（dumb mode）时不应出现满屏红 —— 两个检查都在 `DumbService.isDumb` 时短路。

## 风险与依赖

- **误报风险**是这次改造的主要风险，已用「高置信位置 + 宽集合保持置灰」把范围收紧。谓词依赖方法名集合，作者在 `SqlToyJavaSqlIdResolver.java:53` 标注过 `todo：后续扩展，或者拿掉` —— 集合不准会直接影响报错范围。
- **双击高亮**：annotator 的置灰与 inspection 的错误在同一区间会互相覆盖，行为不确定。已通过让 annotator 让位消除；这是阶段 3 存在的唯一理由。
- **dumb mode 误报**：解析器在索引未就绪时返回空列表，若检查不加守卫会把全部 sqlId 判为未定义。两个检查都加了短路。
- 依赖：需要 `docs/plans/2026-10-14-harness-bootstrap.md` 阶段 4 落地的结构测试来长期守住类目录一致性；当前一致性靠脚本人工核对。

## 决策记录

| 决策 | 取舍 |
|------|------|
| 用 `LocalInspectionTool` 而非改 annotator | 换来「Settings 可关、可调级别、进 Problems 面板」，代价是多 4 个文件 |
| Java 侧判定收窄到 DAO 首参 | 与「宽判定」相比，牺牲非常规调用的检出率，换来不把普通字符串标红 |
| XML 侧用 `findTargets`（精确）、Java 侧用 `findDialectTargets`（方言等价） | 与各自跳转路径保持一致。**报错范围必须等于可跳转范围**，否则会出现「报红但点得过去」 |
| 采用标准错误观感（红字 + 红波浪 + 红条纹） | 放弃「灰字 + 红波浪」，避免随插件再维护一套配色方案 XML，且非默认配色下不会退化 |
| 缺失的 include 不再由 annotator 上色 | 单一来源；代价是未解析的 include 从「绿色的错误 sqlId」变成「红色的错误 sqlId」 |

## 变更原因

（后续对计划本身的修改记在这里）
