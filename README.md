# SqlToy Navigator

SqlToy Navigator 是一个 IntelliJ IDEA 插件，把 Java 代码里的 SqlToy `sqlId` 字符串和 XML 中的
SQL 定义连起来：双向跳转、双向 gutter 图标、XML 内 SQL 的编辑增强，以及引用了不存在的 `sqlId`
时的提示。

本文件面向**插件使用者**。要参与开发，从 [`AGENTS.md`](AGENTS.md) 进。

## 快速示例

Java 代码中：

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

在 `"view_user_list"` 上 `Ctrl + Click`（macOS 为 `Command + Click`）即可跳到 XML 定义；
在 XML 的 `id` 属性上反向跳回 Java 使用处。

## 功能

跳转与导航：

- Java `sqlId` 字符串 ↔ XML `<sql id="...">` 定义双向跳转。
- Java 字符串与 XML `id` 属性旁的**双向 gutter 图标**，并提供已有 `sqlId` 的补全候选。
- `@include("sqlId")` 引用跳转。
- 常量形式的 `sqlId`（`static final String` 作为 DAO 方法首参）也能跳转。
- 多数据源：`gaussdb_` / `postgresql_` / `vastbase_` 前缀与 Oracle 无前缀写法互为等价。

XML 内 SQL 的编辑增强：

- 把 `<sql>` 标签正文注入为 IDEA SQL 语言；Database Tools 不可用时回退到插件内置轻量 SQL 语言。
- SQL token 高亮、括号匹配、参数与表别名识别。
- 当前 SQL 块内选中单词的整词高亮。

提示：

- 未被 Java 引用的 XML `sqlId` **置灰**。
- 引用了不存在的 `sqlId` 时**标为错误**（红色波浪线 + 编辑器右侧错误条纹，并进入 Problems 面板）。
  判定范围与关闭方式见下一节。

## 引用了不存在的 sqlId

SqlToy 的 `sqlId` 是普通字符串，没有语法载体 —— 如果对所有像 `sqlId` 的字符串都报错，
`"UTF-8"`、`"2026-10-14"` 这类普通字符串会全部标红。因此插件按置信度分两档：

| 位置 | 表现 |
|------|------|
| SqlToy DAO 方法首参字面量（`find` / `findOne` / `findByMap` / `getSingleValue`） | 标为**错误**：红字 + 红色波浪线 + 右侧红色条纹 |
| XML 中 `@include("sqlId")` 指向不存在的定义 | 标为**错误**：同上 |
| 其它位置的候选字符串 | 仅**置灰**，不报错 |

报错范围与可跳转范围始终一致：能在某处跳过去，就不会在该处报错。

调整或关闭（默认开启、级别为 Error）：

```text
Settings -> Editor -> Inspections -> SqlToy Navigator
  Unresolved SqlToy sqlId in Java code
  Unresolved SqlToy sqlId in @include
```

索引尚未建好时（如刚打开项目）不会报错，避免扫描期间满屏红。

## 匹配规则

当前只把带 `id` 的 `<sql>` 标签视为 SqlToy SQL 定义：

```xml
<sql id="xxx">...</sql>
```

若项目里的 SqlToy XML 使用了其他标签名，可以修改
[`SqlToySqlIdXmlResolver#isSqlToySqlTag`](src/main/java/com/ax/sqltoy/SqlToySqlIdXmlResolver.java)。
例如放宽为「所有带 `id` 属性的标签」：

```java
static boolean isSqlToySqlTag(@NotNull XmlTag tag) {
    return tag.getAttribute("id") != null;
}
```

## 开发与打包

**Gradle Wrapper 已提交到本仓库**，直接用仓库自带的 `./gradlew`（Windows：`gradlew.bat`），
不要依赖本机安装的 Gradle。

| 目的 | 命令 |
|------|------|
| 只编译（最快的语法/类型校验） | `./gradlew compileJava` |
| 起一个带本插件的沙箱 IDE | `./gradlew runIde` |
| 打包插件 | `./gradlew buildPlugin` → 产物在 `build/distributions/` |
| 无网络环境 | 上述命令追加 `--offline` |

环境要求：

- JDK 17 或更高（本仓库用 JDK 17 验证通过；字节码目标为 Java 17）
- IntelliJ IDEA 2024.1 及以上（平台 SDK 版本为 2024.1.1，`sinceBuild = 241.15989.150`）
- 构建：Gradle 9.5.1（Wrapper）+ IntelliJ Platform Gradle Plugin 2.16.0
- 运行时零第三方依赖 —— 插件体积敏感，不引入 Lombok / Hutool / Guava 等

在 IDE 中安装：

```text
Settings -> Plugins -> 齿轮图标 -> Install Plugin from Disk...
```

选择 `build/distributions/` 下生成的 zip 文件。

## 开发者从这里进

| 想看什么 | 去哪 |
|----------|------|
| 怎么在这个仓库里干活（命令、硬约束、任务导航） | [`AGENTS.md`](AGENTS.md) |
| 代码长什么样（子系统划分、类间依赖、主链路） | [`ARCHITECTURE.md`](ARCHITECTURE.md) |
| 某个类是干什么的 | [`docs/reference/class-catalog.md`](docs/reference/class-catalog.md) —— 类职责的唯一事实来源 |
| 全部开发者文档 | [`docs/index.md`](docs/index.md) |
| 在做什么、做过什么 | [`PLANS.md`](PLANS.md) |

> 本文件**不再维护类清单**。源码结构与类职责统一由 `docs/reference/class-catalog.md` 承载，
> 两处维护必然漂移。

## 后续扩展

尚未实现：

1. 只在 SqlToy DAO 方法参数中启用**跳转**（目前该方法名判定只用于「未定义 sqlId」的报错范围）。
2. 支持 Kotlin 字符串。
3. XML 中 `sqlId` 的 Find Usages。
4. XML 中 SQL 的语法校验与补全。

已实现（原计划项）：

- ~~给未解析的 `sqlId` 增加 Inspection 警告~~ → 已实现，且级别为 Error，见上文「引用了不存在的 sqlId」。
- ~~支持 `sqlId` 自动补全和重复 `sqlId` 检查~~ → 已有 XML `sqlId` 补全候选
  （`SqlToySqlIdReference#getVariants`）；重复 `sqlId` 会在**同一个 XML 文件内**按大小写不敏感检出并报错。
