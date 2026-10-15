# 架构与代码结构

> 本文件描述**子系统的划分、类间依赖与仓库的物理结构**。
> 每个类的职责与扩展点注册情况见 [`docs/reference/class-catalog.md`](docs/reference/class-catalog.md) —— 那是唯一事实来源，本文件不重复维护类职责。
> 动手改代码前先看 [`AGENTS.md`](AGENTS.md)（命令速查与硬约束）、计划进度见 [`PLANS.md`](PLANS.md)。

## 目录结构

```text
sqltoy-navigator/
├── AGENTS.md                     # 入口地图：命令、硬约束、任务导航
├── PLANS.md                      # 计划台账：进行中 / 已完成
├── ARCHITECTURE.md               # 本文件：架构与代码结构
├── README.md                     # 面向插件使用者（不是开发者文档）
├── build.gradle.kts              # 构建配置
├── settings.gradle.kts           # 仓库与 Gradle 插件版本
├── gradlew / gradlew.bat         # Gradle Wrapper（已提交）
├── gradle/wrapper/               # Wrapper 配置与 jar
├── release.sh / release.bat      # 发布流程脚本（分支模型见 docs/branches.md）
├── docs/                         # 开发者文档
│   ├── index.md                  # 文档总索引
│   ├── conventions.md            # 代码约定 + 提交纪律（第 12 节）+ 最小修改（第 14 节）
│   ├── reference/
│   │   └── class-catalog.md      # 31 个类的职责与扩展点注册（唯一事实来源）
│   └── plans/
│       ├── README.md             # 计划的写法、命名与生命周期约定
│       ├── active/               # 没结束的计划（待开工 / 进行中）
│       │   ├── 2026-10-14-Harness-工程改造-harness-bootstrap.md
│       │   └── 2026-10-15-未定义的-SQLID-错误-unresolved-sqlid-error.md
│       └── completed/            # 已结束的计划（已完成 / 已放弃），归档
│           └── 2026-08-31-悬浮提示三处问题修复-hover-hint-fix.md
└── src/
    ├── main/
    │   ├── java/com/ax/sqltoy/   # 全部源码，唯一包，31 个类
    │   └── resources/
    │       ├── META-INF/plugin.xml        # 插件清单 + 全部扩展点注册
    │       ├── META-INF/pluginIcon.svg
    │       └── icons/                     # javaMarker / jump / sqlToyMarker
    └── test/                     # 结构校验测试（阶段 4 落地）
```

## 源码组织：一个包，七个子系统

31 个类全部在 `com.ax.sqltoy` 单包内。**不要新增包** —— 插件规模小，拆包只会增加跳转噪音。

| # | 子系统 | 类数 | 包含的类（省去 `SqlToy` 前缀） | 说明 |
|---|--------|------|------------------------------|------|
| A | 基础设施：图标 / 语言 / 文件类型 | 4 | `Icons`、`SqlLanguage`、`SqlFileType`、`SqlFile` | 底座层，几乎不依赖别人，被所有子系统复用 |
| B | sqlId 导航：Java ↔ XML | 6 | `JavaSqlIdResolver`、`SqlIdXmlResolver`、`SqlIdReferenceContributor`、`SqlIdReference`、`SqlIdAnnotator`、`SqlIdLineMarkerProvider` | 插件的核心功能 |
| C | include 引用 `@include("sqlId")` | 5 | `SqlInclude`、`SqlIncludeParser`、`SqlIncludeReferenceContributor`、`SqlIncludeReference`、`SqlIncludeResolver` | 与 B 同构，但作用在 XML 侧 |
| D | 导航支撑模型 | 4 | `NavigationTarget`、`NavigationAction`、`NavigationPopupItem`、`NavigationGutterIconRenderer` | B 与 C 共用的跳转执行与展示 |
| E | 嵌入式 SQL 语言：词法 / 高亮 / 括号 | 6 | `SqlTokenTypes`、`SqlLexer`、`SqlParserDefinition`、`SqlSyntaxHighlighter`、`SqlSyntaxHighlighterFactory`、`SqlBraceMatcher` | 闭环最完整、最独立的一组 |
| F | XML SQL 注入与编辑增强 | 4 | `XmlSqlLanguageInjector`、`XmlSqlTextRanges`、`XmlSqlAnnotator`、`SqlWordOccurrenceHighlighter` | 依赖 A/B/C/E，是依赖图最下游的一层 |
| G | 未解析引用检查（inspection） | 2 | `UnresolvedSqlIdInspection`、`UnresolvedIncludeInspection` | 把「引用了不存在的 sqlId」从置灰升级为错误；判定分工见 `docs/reference/class-catalog.md` 的 G 节 |

## 类间依赖（仓库扫描实测）

下表由扫描源码得出，不是凭印象写的。为便于阅读省去 `SqlToy` 前缀。

### 依赖最多的中枢（改这些要最小心）

| 类 | 出向依赖 | 说明 |
|----|---------|------|
| `XmlSqlAnnotator` | 11 个 | 唯一的汇总点：既要查 Java 引用、又要解析 include、还要做 SQL token 高亮。**它是全仓库耦合度最高的类** |
| `SqlIdLineMarkerProvider` | 4 个 | Java 与 XML 两侧的 gutter 标记都由它出 |
| `SqlIncludeResolver` | 4 个 | include 使用位置的收集与缓存 |
| `SqlParserDefinition` | 4 个 | 把语言、文件类型、词法、token 类型串起来 |
| `SqlIdReference` / `SqlIdXmlResolver` | 各 2~3 个 | 解析链的中间环节 _|_

### 无出向依赖的叶子

`Icons`、`SqlLanguage`、`SqlInclude`（record 值对象）。它们是纯底座 —— 改动波及面最广。

### 未解析检查的依赖位置

`UnresolvedSqlIdInspection` / `UnresolvedIncludeInspection` 是依赖图末端的**只读消费者**：各自只出向依赖解析器（`JavaSqlIdResolver` + `SqlIdXmlResolver` / `SqlIncludeParser` + `SqlIdXmlResolver`），没有任何类依赖它们，也没有引入新环。它们不缓存任何东西，解析结果的缓存一律复用解析器。

### 已知的循环依赖

`SqlIdXmlResolver` ↔ `XmlSqlTextRanges` 互相引用。历史遗留，不影响功能（两边都是工具性方法互调），但**新增功能不要再往这个环上挂边**。

## 三条主要链路

### 1. Java `sqlId` → XML 定义

1. `SqlIdReferenceContributor`（注册在 `psi.referenceContributor`，限定 `JAVA`）拦截 Java 字符串字面量。
2. 交给 `SqlIdReference` 承接解析：先用 `JavaSqlIdResolver` 判断该字面量是否像 sqlId 候选，再用 `SqlIdXmlResolver` 找 XML 定义。
3. `SqlIdXmlResolver` 先用**最长可索引片段**缩小 XML 文件范围（`PsiSearchHelper.processAllFilesWithWordInText`），再逐文件解析 `<sql id="...">`；结果按文件用 `CachedValuesManager` 缓存，缓存依赖 XML PSI 文件本身，所以改 XML 会自动重新收集。SQL 正文范围由 `XmlSqlTextRanges` 计算。
4. 命中多个目标时由 `NavigationAction` + `NavigationPopupItem` 弹候选窗，`NavigationTarget` 承载单个目标。
5. `SqlIdAnnotator`（Java 侧）与 `SqlIdLineMarkerProvider`（双向 gutter）复用同一条解析链做提示。

### 2. XML `@include("sqlId")` → XML 定义

1. `SqlIncludeReferenceContributor`（注册在 `psi.referenceContributor`，限定 `XML`）在 XML 文本上注册引用。
2. `SqlIncludeParser` 从 XML 文本节点里解析出 `SqlInclude` 值对象（`sqlId` + 在文本元素内的相对范围）。
3. `SqlIncludeReference` 借 `SqlIdXmlResolver` 定位目标定义 —— 也就是说 include 跳转与 sqlId 跳转**共用同一套 XML 定义索引**。
4. 反向查询（"哪些地方 include 了这个 sqlId"）由 `SqlIncludeResolver` 负责，供 XML 侧 gutter 使用；它把结果缓冲为 `NavigationTarget`。

### 3. XML SQL 的编辑增强链路

1. `XmlSqlLanguageInjector` 把 `<sql>` 标签正文注入为 IDEA SQL 语言；Database Tools 不可用时回退到插件内置的 `SqlLanguage`（id `SqlToySQL`）。
2. `SqlParserDefinition` + `SqlLexer` + `SqlTokenTypes` 做词法分析，`SqlSyntaxHighlighter` 把 token 映射到编辑器颜色。
3. `XmlSqlAnnotator` 汇总输出：SQL token 高亮 + 把**未被 Java 引用的 sqlId 置灰**（为此它同时依赖 `JavaSqlIdResolver`、`SqlIncludeResolver`、`NavigationGutterIconRenderer`）。
4. `SqlWordOccurrenceHighlighter` 负责块内选中单词的整词高亮。

## 结构约束

1. **唯一包** `com.ax.sqltoy`；类名统一 `SqlToy` 前缀。
2. 新增 / 改名 / 删除类，必须同步 [`docs/reference/class-catalog.md`](docs/reference/class-catalog.md)。
3. 与 IDE 平台的交互**只通过 `plugin.xml` 的扩展点**，不要在代码里做全局注册。
4. 所有解析入口都要考虑索引未就绪的情况 —— 现有解析器统一用 `DumbService.isDumb(project)` 短路返回空，新增的解析入口照此处理。
5. 跨文件收集 XML 定义必须走 `CachedValuesManager` 缓存并声明正确的依赖项，否则会出现"改了 XML 但不刷新"的幽灵 bug。
6. 不引入运行时第三方依赖（插件体积敏感）。
