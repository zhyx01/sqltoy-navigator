# 代码约定

> 本文件描述**仓库里代码实际长什么样**，以及为什么这样。
>
> **注意区分两类约定**：
> - **硬约束** —— 违背后会被机械化校验拦住，清单与对应校验见 `quality/golden-principles.md`（阶段 4 落地）。本文件只引用，不重复。
> - **软约定** —— 靠一致性维护的写法偏好，**不写成硬约束**（写了会产生大量误报）。下文每处都标明属于哪一类。
>
> 文中的数据均为 2026-10-15 在 `develop` 基线（31 个类）上实测所得。

## 1. 包与命名

| 约定 | 类别 | 现状 |
|------|------|------|
| 全部源码在唯一包 `com.ax.sqltoy` | 硬约束 | 1 个包 |
| 类名以 `SqlToy` 开头 | 硬约束 | 31 / 31 符合 |
| 不要新增包 | 软约定 | 插件规模小，拆包只增加跳转噪音 |

**类名后缀已形成语义**，新增类时请跟随：

| 后缀 / 形态 | 含义 | 例 |
|------|------|-----|
| `…Resolver` | 无状态查找逻辑 | `SqlToySqlIdXmlResolver`、`SqlToyJavaSqlIdResolver` |
| `…Contributor` | 扩展点入口，只负责注册引用 | `SqlToySqlIdReferenceContributor` |
| `…Provider` / `…Factory` | 平台要求的工厂 | `SqlToySqlSyntaxHighlighterFactory` |
| `…Annotator` | 高亮标注 | `SqlToySqlIdAnnotator` |
| `…Inspection` | 检查项 | `SqlToyUnresolvedSqlIdInspection` |
| `…Target` / `…Item` | 值对象 / 展示项 | `SqlToyNavigationTarget` |
| `…Ranges` | 文本范围计算 | `SqlToyXmlSqlTextRanges` |

## 2. 类型声明与可见性

实测分布：**30 个 `final class`（15 个 `public` + 15 个包级）+ 1 个顶层 `record`**。

| 规则 | 类别 | 说明 |
|------|------|------|
| 所有类都标 `final` | 软约定 | 33 / 33 符合。插件不需要继承，`final` 明确这点 |
| **扩展点实现类必须 `public`** | 硬约束（平台要求） | IntelliJ 靠反射实例化 `implementationClass`，包级可见会直接失败 |
| 非扩展点的工具类尽量包级可见 | 软约定 | 如 `SqlToyJavaSqlIdResolver`、`SqlToySqlIdXmlResolver`：不对外暴露 |
| 工具类私有构造 + Javadoc | 软约定 | 固定写法见下 |

工具类的固定形态（`SqlToyJavaSqlIdResolver.java:62-66`）：

```java
/**
 * 工具类，不需要创建实例。
 */
private SqlToyJavaSqlIdResolver() {
}
```

**值对象用 `record`**，不用 getter 类：

- 顶层 record：`SqlToySqlInclude`（`sqlId` + 在 XML 文本内的相对范围）
- 内嵌 record：`SqlToySqlIdXmlResolver.SqlIdTarget`（`sqlId` + 导航元素 + 文件名 + SQL 文本）

**一处已存在的变体，新代码不必跟随**：`SqlToySqlIncludeParser` 是无状态逻辑（无字段），但被写成可实例化的类并提供实例方法，由 `SqlToyXmlSqlAnnotator`、`SqlToyUnresolvedIncludeInspection`、`SqlToySqlIncludeResolver` 各自 `new` 一个。同一职责的其它类（如两个 `…Resolver`）走的是静态方法。**没有统一**，跟随手边文件即可。

## 3. Javadoc

| 规则 | 类别 | 现状 |
|------|------|------|
| 每个类型必须有类级 Javadoc | 硬约束 | 31 / 31 |
| 类级 Javadoc 含 `@author ax` 与 `@date yyyy-MM-dd` | 硬约束（**`record` 除外**） | 30 / 31；唯一例外是 record `SqlToySqlInclude` |
| 每个方法都写 Javadoc，`@param` / `@return` 齐全 | 软约定 | 含 `private` 方法与**匿名类里的 `@Override`**（见 `SqlToyJavaSqlIdResolver.java:342-347`） |
| 注释与 Javadoc 用中文 | 软约定 | 极少数例外，见下 |

类级模板：

```java
/**
 * 一句话说明这个类做什么。
 * <p>
 * 需要展开的约束、易踩的坑、与其它类的协作关系写在这里。
 *
 * @author ax
 * @date 2026-10-15
 */
```

**已知的英文残留**：`SqlToySqlSyntaxHighlighter.java:99` 的 `@return` 是英文（`attributes for the token, or an empty array…`）。新代码统一写中文，发现老的顺手可改但不强求。

## 4. 判空（软约定，**实测是混用的**）

| 写法 | 出现次数 |
|------|---------|
| `Objects.isNull(...)` / `Objects.nonNull(...)` | 41 |
| `x == null` / `x != null` | 46 |

**AGENTS.md 刻意没有把它写成硬约束** —— 41 : 46 的混用比例意味着硬写会产生大量误报，把校验噪音变成背景音，最终让真问题被忽略。

观察到的分工（不是规定，只是现状，新代码跟随所在文件的就近风格）：

| 场景 | 倾向写法 | 例 |
|------|---------|-----|
| 具名局部变量 / 方法参数的**卫语句** | `Objects.isNull(x)` | `if (Objects.isNull(tag)) { return; }` |
| 局部量的**三元与复合条件** | `Objects.nonNull(x)` | `Objects.nonNull(virtualFile) ? virtualFile.getPath() : file.getName()` |
| 字段 / 属性访问的链式判断 | `x != null` | `idAttribute != null ? idAttribute.getValue() : null` |
| **遍历循环**的推进条件 | `while (cur != null)` | `while (child != null) { … }` |
| 直接判断方法调用结果 | `x == null` | `if (SqlToyXmlSqlTextRanges.getSqlToySqlTag(xmlText) == null)` |

## 5. 注释：解释「为什么」，不复述「做什么」

这是本仓库注释质量的关键。三个真实例子：

```java
// SqlToySqlIdXmlResolver.java:415 —— 解释选择策略的收益
// 选最长片段通常能让索引命中文件更少，降低后续 PSI 解析成本。

// SqlToySqlSyntaxHighlighter.java:103 —— 解释一个看起来像遗漏的写法
// Bracket tokens intentionally fall through to EMPTY_KEYS for Rainbow Brackets compatibility.

// SqlToySqlIdAnnotator.java:58 —— 解释为什么这里「什么都不做」
// 高置信位置（SqlToy DAO 方法首参）的未定义 sqlId 由 SqlToyUnresolvedSqlIdInspection
// 标红报错，这里不再置灰，避免同一区间上叠两层高亮导致观感不确定。
```

**反过来**，`if (Objects.isNull(tag)) { return; }` 这种一眼能看懂的卫语句**不加**注释。

## 6. 常量与魔法值

| 规则 | 类别 | 说明 |
|------|------|------|
| 常量 `private static final` + `UPPER_SNAKE_CASE` | 软约定 | 即使是私有常量也写 Javadoc（见 `SqlToyJavaSqlIdResolver.java:45-47`） |
| **Java 侧**高亮颜色直接写 RGB 常量 | 现状 | `SqlToySqlIdAnnotator` 的 `UNDERLINE_COLOR = new Color(104, 168, 113)` 等 |
| **XML SQL 语法**高亮走 `TextAttributesKey` | 现状 | `SqlToySqlSyntaxHighlighter`：让颜色跟随主题，每类 token 带一个 `DefaultLanguageHighlighterColors` 作为 fallback |

这条差异是刻意的：Java 侧是给 `sqlId` 加**插件自有语义**的标记色，XML 侧是给 SQL 做**语法**着色、理应跟随主题。

## 7. 集合与不可变

| 规则 | 类别 | 现状 |
|------|------|------|
| 不可变集合用 `List.of` / `Set.of` / `Map.of` | 软约定 | 18 处 |
| 需要累积时用 `new ArrayList<>()` + `computeIfAbsent` | 软约定 | 22 处 |
| 不做防御性拷贝 | 现状 | 缓存返回的 Map 直接交给调用方，调用方也**不修改**它 —— 改它就破坏了缓存语义 |

## 8. 依赖

| 规则 | 类别 | 现状 |
|------|------|------|
| **零运行时第三方依赖** | 硬约束 | 源码 import 除 `com.intellij.*` / `org.jetbrains.annotations` / `java.*` / `javax.*` 外为空 |
| `build.gradle.kts` 的依赖块只允许 `intellijPlatform { … }` | 硬约束 | — |
| 不用 Lombok / Hutool / Guava | 硬约束 | 插件体积与平台兼容性敏感 |

**跨类复用方式**（无 DI 框架）：

- 需要复用的**无状态**逻辑 → `static` 方法直接调用（如 `SqlToySqlIdXmlResolver.findDialectTargets`）
- 需要**持有状态**的 → 实例字段自己 `new`（如 `SqlToyXmlSqlAnnotator` 持有 `includeParser` / `includeResolver`）

## 9. 格式

| 项 | 实测 |
|----|------|
| 缩进 | 4 空格，**0 个文件使用 tab** |
| 编码 | UTF-8，**0 个文件带 BOM** |
| 行尾 | 仓库无 `.gitattributes`，`core.autocrlf` 生效（提交时可能提示 LF→CRLF） |
| 格式化配置 | **无** `.editorconfig`、无 IDE 格式化配置 —— 靠人工一致 |

因为没有自动格式化，**提交前请自己确认缩进是 4 空格、文件是 UTF-8**。这两项已列入硬约束（编码错了中文注释直接乱码）。

## 10. 禁止项

| 项 | 类别 | 现状 |
|----|------|------|
| `System.out` / `System.err` | 硬约束 | 0 处 |
| `printStackTrace()` | 硬约束 | 0 处 |
| 残留 `TODO` / `FIXME` | 硬约束 | **1 处，需处理** ⚠️ |

⚠️ **实测发现**：`SqlToyJavaSqlIdResolver.java:53` 有一处**小写** `todo：后续扩展，或者拿掉，业务场景，非常规引用`。AGENTS.md 硬约束第 7 条写的是「不留 `TODO` / `FIXME` 进主干」，若校验按大小写敏感匹配会**漏掉它**。两件事待定：

1. 校验必须**大小写不敏感**，否则这类小写标记永远查不出来；
2. 这处标记本身怎么处置 —— 它标在 `SQL_ID_FIRST_ARGUMENT_METHOD_NAMES` 上，而这个集合现在直接决定「未定义 sqlId」的报错范围，已不是普通待办。见 `sqltoy-semantics.md` 第 3 节。

## 11. 新增一个类的清单

1. 放在 `com.ax.sqltoy`，类名以 `SqlToy` 开头，标 `final`
2. 写类级 Javadoc，含 `@author ax` 与 `@date 今天`（`record` 除外）
3. 每个方法写 Javadoc，`@author` 之外的 `@param` / `@return` 齐全
4. 若是扩展点实现类：标 `public`、提供无参构造、同时登记 `plugin.xml` 与 `../reference/class-catalog.md`
5. 若做「按文件聚合」的查找：走 `CachedValuesManager`，且入口做 `DumbService.isDumb` 短路（见 `intellij-platform.md`）
6. `./gradlew compileJava` 必须过
