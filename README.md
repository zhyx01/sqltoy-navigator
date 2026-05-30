# SqlToy SqlId Navigator

一个最小可用的 IntelliJ IDEA 插件，用于支持 Java 代码中的 SqlToy `sqlId` 跳转到 XML SQL 定义。

## 支持效果

Java 代码：

```java
lightDao.find("view_user_list", queryParam, User.class);
```

XML 文件：

```xml
<sqls>
    <sql id="view_user_list">
        SELECT * FROM TEST_TABLE
    </sql>
</sqls>
```

在 Java 字符串 `"view_user_list"` 上 `Ctrl + Click` / `Command + Click`，可以跳转到 XML 中的 `id` 属性。

## 当前匹配规则

只匹配：

```xml
<sql id="xxx">...</sql>
```

如果你项目里的 SqlToy XML 使用了其他标签名，修改：

```java
SqlToySqlIdXmlResolver#isSqlToySqlTag
```

例如要支持所有带 `id` 的标签：

```java
private static boolean isSqlToySqlTag(@NotNull XmlTag tag) {
    return tag.getAttribute("id") != null;
}
```

## 运行插件

要求：

- JDK 21
- IntelliJ IDEA 2025.2+ 或兼容版本
- Gradle 9+

运行开发 IDE：

```bash
./gradlew runIde
```

构建插件安装包：

```bash
./gradlew buildPlugin
```

构建结果：

```text
build/distributions/sqltoy-sqlid-navigator-0.1.0.zip
```

在 IDEA 中安装：

```text
Settings -> Plugins -> 齿轮 -> Install Plugin from Disk...
```

选择上面的 zip 文件即可。

## 后续可扩展点

1. 只在 SqlToy DAO 方法参数中启用跳转，避免普通字符串被识别。
2. 支持 Kotlin 字符串。
3. 给未解析的 sqlId 增加 Inspection 警告。
4. 支持 XML 中 sqlId 的 Find Usages。
5. 支持 sqlId 自动补全和重复 sqlId 检查。
