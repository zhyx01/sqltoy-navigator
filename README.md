# SqlToy Navigator

SqlToy Navigator 是一个 IntelliJ IDEA 插件源码，用于在 Java 代码里的 SqlToy `sqlId`
字符串和 XML SQL 定义之间跳转。

## 功能

在 Java 代码中：

```java
lightDao.find("view_user_list", queryParam, User.class);
```

对应 XML 中：

```xml
<sqls>
    <sql id="view_user_list">
        SELECT * FROM TEST_TABLE
    </sql>
</sqls>
```

在 Java 字符串 `"view_user_list"` 上使用 `Ctrl + Click`，或 macOS 上使用
`Command + Click`，可以跳转到 XML 中对应的 `<sql id="view_user_list">` 定义。

插件也会在 Java `sqlId` 字符串和 XML `id` 属性旁显示行标记，方便从两边互相定位。

## 源码结构

```text
src/main/java/com/ax/sqltoy/
  图标与基础类型
    SqlToyIcons.java
    SqlToySqlLanguage.java
    SqlToySqlFileType.java
    SqlToySqlFile.java

  Java 与 XML sqlId 导航
    SqlToyJavaSqlIdResolver.java
    SqlToySqlIdXmlResolver.java
    SqlToySqlIdReference.java
    SqlToySqlIdReferenceContributor.java
    SqlToySqlIdAnnotator.java
    SqlToySqlIdLineMarkerProvider.java

  嵌入式 SQL 语言与高亮
    SqlToySqlTokenTypes.java
    SqlToySqlLexer.java
    SqlToySqlParserDefinition.java
    SqlToySqlSyntaxHighlighter.java
    SqlToySqlSyntaxHighlighterFactory.java
    SqlToySqlBraceMatcher.java

  XML SQL 注入与编辑器增强
    SqlToyXmlSqlLanguageInjector.java
    SqlToyXmlSqlTextRanges.java
    SqlToyXmlSqlAnnotator.java
    SqlToySqlWordOccurrenceHighlighter.java

src/main/resources/
  META-INF/plugin.xml
  META-INF/pluginIcon.svg
  icons/sqlToyMarker.svg
```

## 类作用

### 图标与基础类型

- `SqlToyIcons`：统一加载插件图标，供行标记和文件类型复用。
- `SqlToySqlLanguage`：定义插件内置的轻量 SQL 语言标识。
- `SqlToySqlFileType`：定义嵌入式 SQL 片段的合成文件类型、扩展名和图标。
- `SqlToySqlFile`：定义嵌入式 SQL 片段的 PSI 文件包装。

### Java 与 XML sqlId 导航

- `SqlToyJavaSqlIdResolver`：从 Java 字符串字面量中识别 SqlToy `sqlId`，并按 `sqlId` 查找 Java 使用处。
- `SqlToySqlIdXmlResolver`：扫描项目 XML 文件中的 `<sql id="...">` 定义，按 `sqlId` 缓存并返回导航目标。
- `SqlToySqlIdReferenceContributor`：把 Java 字符串字面量注册为可能的 `sqlId` 引用入口。
- `SqlToySqlIdReference`：实现 Java `sqlId` 到 XML 定义的解析跳转，并提供已有 XML `sqlId` 的补全候选。
- `SqlToySqlIdAnnotator`：为能解析到 XML 定义的 Java `sqlId` 字符串添加下划线提示。
- `SqlToySqlIdLineMarkerProvider`：在 Java 字符串和 XML `id` 属性之间提供双向 gutter 图标跳转。

### 嵌入式 SQL 语言与高亮

- `SqlToySqlTokenTypes`：定义轻量 SQL lexer 输出的 token 类型，例如关键字、表名、别名、参数和括号。
- `SqlToySqlLexer`：对 SqlToy SQL 片段做词法分析，并根据上下文识别表名、表别名、列别名和命名参数。
- `SqlToySqlParserDefinition`：提供轻量 SQL 语言的最小解析定义，只消费 token，不构建完整 SQL AST。
- `SqlToySqlSyntaxHighlighter`：把 SQL token 类型映射到 IntelliJ 编辑器颜色属性。
- `SqlToySqlSyntaxHighlighterFactory`：按 IntelliJ 扩展点要求创建 `SqlToySqlSyntaxHighlighter`。
- `SqlToySqlBraceMatcher`：提供 SQL 小括号、中括号和大括号的配对信息，并让括号颜色交给括号插件处理。

### XML SQL 注入与编辑器增强

- `SqlToyXmlSqlLanguageInjector`：把 XML `<sql id="...">` 标签正文注入为 IDEA SQL 语言；不可用时回退到插件内置轻量 SQL 语言。
- `SqlToyXmlSqlTextRanges`：计算 XML SQL 正文范围，排除外围空白和可选的 CDATA 包裹。
- `SqlToyXmlSqlAnnotator`：在 XML `<sql>` 正文中应用 SQL token 高亮，并把未被 Java 引用的 XML `sqlId` 置灰。
- `SqlToySqlWordOccurrenceHighlighter`：在当前 SqlToy SQL 块内，对选中的 SQL 单词做忽略大小写的整词出现位置高亮。

## 匹配规则

当前只匹配 XML 中带 `id` 的 `<sql>` 标签：

```xml
<sql id="xxx">...</sql>
```

如果项目里的 SqlToy XML 使用了其他标签名，可以修改：

```java
SqlToySqlIdXmlResolver#isSqlToySqlTag
```

例如，支持所有带 `id` 属性的标签：

```java
static boolean isSqlToySqlTag(@NotNull XmlTag tag) {
    return tag.getAttribute("id") != null;
}
```

## 本地运行

仓库不需要提交 Gradle Wrapper。如果需要在本地运行或打包插件，可以在本地 IntelliJ 插件
工程中使用这份 `src/` 源码，并准备自己的 Gradle 配置。下面两个 Gradle 文件仅作为本地
构建参考，不要求提交到源码仓库。

本地环境要求：

- JDK 21
- IntelliJ IDEA 2025.2+ 或兼容版本
- 本机已安装 Gradle，或在本地自行生成 Gradle Wrapper

运行开发 IDE：

```bash
gradle runIde
```

构建插件安装包：

```bash
gradle buildPlugin
```

构建结果通常位于：

```text
build/distributions/
```

在 IDEA 中安装：

```text
Settings -> Plugins -> Gear Icon -> Install Plugin from Disk...
```

选择 `build/distributions/` 下生成的 zip 文件即可。

## 后续扩展

1. 只在 SqlToy DAO 方法参数中启用跳转，避免普通字符串被识别。
2. 支持 Kotlin 字符串。
3. 给未解析的 `sqlId` 增加 Inspection 警告。
4. 支持 XML 中 `sqlId` 的 Find Usages。
5. 支持 `sqlId` 自动补全和重复 `sqlId` 检查。
6. XML 中支持 SQL 语法提示
