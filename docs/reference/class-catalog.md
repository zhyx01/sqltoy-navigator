# 类目录（Class Catalog）

> **本文件是 `src/main/java/com/ax/sqltoy/` 结构的唯一事实来源。**
> 其它文档（含 `README.md`）一律引用本文件，不再各自维护类清单 —— 两处维护必然漂移。
>
> 一致性由 `src/test/java/com/ax/sqltoy/harness/ClassCatalogTest.java` 机械校验（**阶段 4 落地**）：源码里多一个未登记的类、或少一个已登记的类，校验都会失败。在那之前请人工核对。

## 统计

| 项 | 数量 |
|----|------|
| 源码类总数 | 31 |
| 包的数量 | 1（`com.ax.sqltoy`） |
| 被 `plugin.xml` 注册为扩展点的类 | 11 |
| `plugin.xml` 中的注册条目 | 12 |

## A. 基础设施：图标 / 语言 / 文件类型（4）

| 类 | 类型 | 职责 | 扩展点 |
|----|------|------|--------|
| `SqlToyIcons` | class | 集中管理插件图标加载，供行标记与文件类型复用 | — |
| `SqlToySqlLanguage` | class | 定义嵌入式 SqlToy SQL 语言；id 固定为 `SqlToySQL` | 被 `plugin.xml` 的 `language="SqlToySQL"` 属性引用 |
| `SqlToySqlFileType` | class | 嵌入式 SQL 语言的文件类型门面；合成扩展名 `sqltoy-sql` | — |
| `SqlToySqlFile` | class | 注入式 SQL 片段的 PSI 文件包装器 | — |

## B. sqlId 导航：Java ↔ XML（6）

| 类 | 类型 | 职责 | 扩展点 |
|----|------|------|--------|
| `SqlToyJavaSqlIdResolver` | class | 从 Java 字符串字面量中识别 SqlToy sqlId，并按 sqlId 反查 Java 使用处 | — |
| `SqlToySqlIdXmlResolver` | class | 扫描项目 XML 中的 `<sql id="...">` 定义，按 sqlId 分组缓存，并提供多方言等价候选 | — |
| `SqlToySqlIdReferenceContributor` | class | 把 Java 字符串字面量注册为可能的 sqlId 引用入口 | `psi.referenceContributor`（JAVA） |
| `SqlToySqlIdReference` | class | Java sqlId → XML 定义的解析跳转，并提供已有 sqlId 的补全候选 | — |
| `SqlToySqlIdAnnotator` | class | 为 Java sqlId 字符串添加可视下划线；解析不到定义的候选值置灰（高置信位置让位给 `SqlToyUnresolvedSqlIdInspection`） | `annotator`（JAVA） |
| `SqlToySqlIdLineMarkerProvider` | class | Java 字面量与 XML `id` 属性之间的双向 gutter 图标 | `codeInsight.lineMarkerProvider`（JAVA + XML） |

## C. include 引用：`@include("sqlId")`（5）

| 类 | 类型 | 职责 | 扩展点 |
|----|------|------|--------|
| `SqlToySqlInclude` | record | include 引用值对象：`sqlId` + 它在 XML 文本元素内的相对范围 | — |
| `SqlToySqlIncludeParser` | class | 从 SQL 文本节点中解析 `@include("sqlId")` | — |
| `SqlToySqlIncludeReferenceContributor` | class | 在 XML SQL 文本的 `@include("sqlId")` 上注册引用 | `psi.referenceContributor`（XML） |
| `SqlToySqlIncludeReference` | class | `@include("sqlId")` → XML 定义的解析跳转 | — |
| `SqlToySqlIncludeResolver` | class | 收集引用了某个 sqlId 的全部 include 位置（按文件缓存） | — |

## D. 导航支撑模型（4）

| 类 | 类型 | 职责 | 扩展点 |
|----|------|------|--------|
| `SqlToyNavigationTarget` | class | 导航候选目标：sqlId + PSI 元素 + 偏移量 | — |
| `SqlToyNavigationAction` | class | 执行导航；目标多于一个时先弹出候选窗 | — |
| `SqlToyNavigationPopupItem` | class | 导航候选弹窗的展示项 | — |
| `SqlToyNavigationGutterIconRenderer` | class | include 关系使用的 gutter 图标渲染 | — |

## E. 嵌入式 SQL 语言：词法 / 高亮 / 括号（6）

| 类 | 类型 | 职责 | 扩展点 |
|----|------|------|--------|
| `SqlToySqlTokenTypes` | class | 轻量 SQL 词法分析器的 token 类型注册表 | — |
| `SqlToySqlLexer` | class | 有状态轻量词法分析器；按上下文识别表名、表别名、列别名与命名参数 | — |
| `SqlToySqlParserDefinition` | class | 最小解析定义，只消费 token，不构建完整 SQL AST | `lang.parserDefinition` |
| `SqlToySqlSyntaxHighlighterFactory` | class | 按扩展点要求创建 `SqlToySqlSyntaxHighlighter` | `lang.syntaxHighlighterFactory` |
| `SqlToySqlSyntaxHighlighter` | class | SQL token 类型 → IntelliJ 编辑器颜色属性的映射 | — |
| `SqlToySqlBraceMatcher` | class | 小括号 / 中括号 / 大括号的配对信息 | `lang.braceMatcher` |

## F. XML SQL 注入与编辑增强（4）

| 类 | 类型 | 职责 | 扩展点 |
|----|------|------|--------|
| `SqlToyXmlSqlLanguageInjector` | class | 把 XML `<sql>` 标签正文注入为 IDEA SQL 语言；Database Tools 不可用时回退到插件内置轻量语言 | — |
| `SqlToyXmlSqlTextRanges` | class | 计算 XML 文本节点中的 SQL 正文范围，排除外层空白与可选 CDATA 包裹 | — |
| `SqlToyXmlSqlAnnotator` | class | 在 XML `<sql>` 正文应用 SQL token 高亮；把未被 Java 引用的 sqlId 置灰；未解析的 include 让位给 `SqlToyUnresolvedIncludeInspection` | `annotator`（XML） |
| `SqlToySqlWordOccurrenceHighlighter` | class | 当前 SqlToy SQL 块内选中单词的整词出现位置高亮（忽略大小写） | `editorFactoryListener` |

## G. 未解析引用检查（2）

把「引用了不存在的 sqlId」从置灰升级为错误：红色波浪线 + 右侧错误条纹，并进入 Problems 面板。

| 类 | 类型 | 职责 | 扩展点 |
|----|------|------|--------|
| `SqlToyUnresolvedSqlIdInspection` | class | Java 侧：SqlToy DAO 方法首参字面量无法解析到 XML 定义时标为错误 | `localInspection`（JAVA） |
| `SqlToyUnresolvedIncludeInspection` | class | XML 侧：`@include("sqlId")` 无法解析到定义时标为错误 | `localInspection`（XML） |

判定分工（**改报错范围前必读**）：

- **高置信位置**报错 —— 谓词是 `SqlToyJavaSqlIdResolver.isFirstSqlIdArgument`，方法名集合在 `SqlToyJavaSqlIdResolver.SQL_ID_FIRST_ARGUMENT_METHOD_NAMES`。**该集合是唯一开关**，改它会同时改变报错范围。
- **其余候选字符串**只置灰不报错 —— SqlToy sqlId 没有语法载体，判定放宽会把 `"UTF-8"`、`"2026-10-14"` 这类普通字符串全部标红。
- XML 侧报错用 `findTargets`（精确匹配），与 include 跳转完全一致；Java 侧用 `findDialectTargets`（方言等价），与 Java 跳转一致。**报错范围必须与可跳转范围相同。**

## 附录 1：`plugin.xml` 扩展点注册总表

上表「扩展点」列的来源。校验方式见 `../quality/golden-principles.md`。

| # | 扩展点 | 限定 | 实现类 | 所在文件 |
|---|--------|------|--------|----------|
| 1 | `lang.parserDefinition` | `language="SqlToySQL"` | `SqlToySqlParserDefinition` | `src/main/resources/META-INF/plugin.xml` |
| 2 | `lang.syntaxHighlighterFactory` | `language="SqlToySQL"` | `SqlToySqlSyntaxHighlighterFactory` | 同上 |
| 3 | `lang.braceMatcher` | `language="SqlToySQL"` | `SqlToySqlBraceMatcher` | 同上 |
| 4 | `psi.referenceContributor` | `language="JAVA"` | `SqlToySqlIdReferenceContributor` | 同上 |
| 5 | `psi.referenceContributor` | `language="XML"` | `SqlToySqlIncludeReferenceContributor` | 同上 |
| 6 | `annotator` | `language="JAVA"` | `SqlToySqlIdAnnotator` | 同上 |
| 7 | `annotator` | `language="XML"` | `SqlToyXmlSqlAnnotator` | 同上 |
| 8 | `editorFactoryListener` | — | `SqlToySqlWordOccurrenceHighlighter` | 同上 |
| 9 | `codeInsight.lineMarkerProvider` | `language="JAVA"` | `SqlToySqlIdLineMarkerProvider` | 同上 |
| 10 | `codeInsight.lineMarkerProvider` | `language="XML"` | `SqlToySqlIdLineMarkerProvider` | 同上 |
| 11 | `localInspection` | `language="JAVA"`, `level="ERROR"` | `SqlToyUnresolvedSqlIdInspection` | 同上 |
| 12 | `localInspection` | `language="XML"`, `level="ERROR"` | `SqlToyUnresolvedIncludeInspection` | 同上 |

模块依赖声明（`<depends>`）：`com.intellij.modules.platform`、`com.intellij.modules.lang`、`com.intellij.modules.xml`、`com.intellij.modules.java`。

## 附录 2：尚未并入 `develop` 的类

以下类存在于分支 `v20260831-optimization`（提交 `9d004ce`，2026-08-31），**不在 `develop` 基线内**，因此不计入上面的 31 个类：

| 类 | 类型 | 职责 | 扩展点 | 说明 |
|----|------|------|--------|------|
| `SqlToySqlIdHoverHintProvider` | class | 悬浮显示 sqlId 对应的 SQL 文本，并提供复制按钮 | `editorFactoryListener` | 该分支另含 `SqlToySqlIdAnnotator` 的改动与 `plugin.xml` 的 1 条新增注册 |

> 该分支合入 `develop` 后，本文件必须同步登记这两处变化，否则 `ClassCatalogTest` 会失败 —— 这正是这道校验存在的意义。
