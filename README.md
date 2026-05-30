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
src/
  main/
    java/com/ax/sqltoy/
      SqlToyJavaSqlIdResolver.java
      SqlToyIcons.java
      SqlToySqlBraceMatcher.java
      SqlToySqlIdAnnotator.java
      SqlToySqlIdLineMarkerProvider.java
      SqlToySqlIdReference.java
      SqlToySqlIdReferenceContributor.java
      SqlToySqlIdXmlResolver.java
      SqlToySqlFile.java
      SqlToySqlFileType.java
      SqlToySqlLanguage.java
      SqlToySqlLexer.java
      SqlToySqlParserDefinition.java
      SqlToySqlSyntaxHighlighter.java
      SqlToySqlSyntaxHighlighterFactory.java
      SqlToySqlTokenTypes.java
      SqlToyXmlSqlAnnotator.java
      SqlToyXmlSqlLanguageInjector.java
      SqlToyXmlSqlTextRanges.java
    resources/
      META-INF/plugin.xml
      icons/sqlToyMarker.svg
```

## 类作用

- `SqlToyJavaSqlIdResolver`：解析 Java 字符串中的 SqlToy `sqlId`，并查找 Java 用法。
- `SqlToySqlIdXmlResolver`：扫描 XML 中的 `<sql id="...">` 定义。
- `SqlToySqlIdReference`：实现 Java `sqlId` 到 XML 定义的引用跳转和补全。
- `SqlToySqlIdReferenceContributor`：为 Java 字符串注册 `sqlId` 引用。
- `SqlToySqlIdAnnotator`：给可解析的 Java `sqlId` 字符串添加下划线提示。
- `SqlToySqlIdLineMarkerProvider`：提供 Java 和 XML 之间的双向 gutter 图标跳转。
- `SqlToyIcons`：统一加载插件图标。
- `SqlToySqlLanguage`：定义插件内置的轻量 SQL 语言。
- `SqlToySqlFileType`：定义内置 SQL 片段的文件类型。
- `SqlToySqlFile`：定义内置 SQL 片段的 PSI 文件。
- `SqlToySqlTokenTypes`：定义内置 SQL lexer 使用的 token 类型。
- `SqlToySqlLexer`：识别 SQL 关键字、字段、表名、别名、参数、注释和括号。
- `SqlToySqlSyntaxHighlighter`：把 SQL token 映射到编辑器颜色。
- `SqlToySqlSyntaxHighlighterFactory`：创建内置 SQL 高亮器。
- `SqlToySqlParserDefinition`：提供内置 SQL 语言的最小解析定义。
- `SqlToySqlBraceMatcher`：提供 SQL 括号匹配信息，便于括号插件接管颜色。
- `SqlToyXmlSqlLanguageInjector`：把 XML `<sql>` 内容注入为内置 SQL 语言。
- `SqlToyXmlSqlTextRanges`：计算 XML SQL 文本、CDATA 和空白裁剪范围。
- `SqlToyXmlSqlAnnotator`：在 XML `<sql>` 内容中直接应用 SQL 语法高亮。

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
