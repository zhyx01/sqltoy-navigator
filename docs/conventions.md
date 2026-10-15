# 代码约定

> 本文件描述**仓库里代码实际长什么样**，以及为什么这样。
>
> **注意区分两类约定**：
> - **硬约束** —— 违背后会被机械化校验拦住，清单与对应校验见 `quality/golden-principles.md`（阶段 4 落地）。本文件只引用，不重复。
> - **软约定** —— 靠一致性维护的写法偏好，**不写成硬约束**（写了会产生大量误报）。下文每处都标明属于哪一类。
>
> 文中的数据均为 2026-10-15 在 `develop` 基线（31 个类）上实测所得。
>
> **第 12 节与第 14 节是例外** —— 它们讲的是**提交流程**与**改动范围**，不是代码形态，但同样属于「必须遵守的规范」，所以挂在本文件下，作为这两条规则在仓库里的**唯一事实来源**；其它文档只引用，不复制。

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

## 12. 提交纪律

> 与代码风格无关，但同样是**硬约束** —— 它是这个仓库里唯一一条「做对了没人夸、做错了很难回滚」的规则。

| 规则 | 类别 | 说明 |
|------|------|------|
| **不要自动提交** | 硬约束 | 写完代码或文档，**只落盘到工作区**。`git add` / `git commit` / `git push` 一律等用户明确说了「提交」再做 |
| **提交范围由用户指定** | 硬约束 | 用户可能要求「只提交功能代码，不带 harness 文档」这类切分。按用户点名的范围**逐路径** `git add <显式路径>`，不要用 `git add -A` / `git add .` |
| 落盘后不擅自清理工作区 | 硬约束 | 工作区长时间停在「已改未提交」是**正常状态**，不是待收拾的垃圾。不要为了「让 `git status` 看起来干净」而去提交、`stash` 或回滚。唯一的例外是从 `branches.md` §6.4 那类误删事故中恢复（`git checkout -- .` 从索引找回文件）—— 那是**修复**，不是清理 |
| 提交前逐项确认范围 | 软约定 | 因为工作区可能同时堆着多个任务的改动，提交前先 `git status --short` 逐行确认归属，再按范围加 |

**为什么不自作主张提交**：

1. **范围是判断，不是格式。** 一个改动该进哪个分支、要不要跟手上没做完的工作混在一起提，取决于用户自己的节奏 —— 这个上下文只有用户手上有。把「哪些改动是一次交付」交给工具猜，等于替用户做了决定。
2. **提交污染的是历史，不是工作区。** 提交错了要 `revert` / `reset`，一旦推上去还会影响别人。而工作区改错了随时可以改回来。两者的代价不对称，所以默认偏向「不动」。
3. **本仓库的 git 操作层本身就不可信。** 见 `branches.md` §6.2 / §6.4：这个环境里 `git checkout` 会静默误删工作区文件。在状态不可靠的前提下自动提交，风险被放大。

**推论 —— 文档里怎么写「已提交」**：`PLANS.md` 与 `docs/plans/` 里出现的「已提交 / 未提交 / 提交号」，一律是**事实记录**，不代表后续会自动提交。新改动落下后，对应的计划文档状态应写成「已完成，**待用户指示提交**」，而不是默默再补一次提交。

## 13. 个人代码习惯

> 这一节收**作者本人的写法偏好**：不打算用校验拦住，但希望新代码跟随。
> 细则先按主题并入第 1 ~ 11 节里对应的那一节；只有**找不到归属**的习惯才写到这里。

每一条只写三样东西，缺一样就不写：

| 项 | 要求 |
|----|------|
| 习惯 | 一句话说清「怎么写」，写成祈使句（`xx 用 yy，不用 zz`） |
| 现状 | 仓库里**实测**有多少处这么写，带例子的文件:行号。不写「大致」「通常」 |
| 迁移 | 遇到就顺手改，还是不专门重构。**必须二选一** |

| 习惯 | 现状（实测） | 迁移 |
|------|--------------|------|
| （待补充） | | |

**什么不该放这**：

- **能机械校验的** → 写进 `quality/golden-principles.md`（阶段 4），本节只留一行指向它。理由见 `index.md` 维护规则第 3 条：挂不上校验的「硬约束」会变成背景噪音，最终让真问题被忽略。
- **只对某一次改动成立的** → 写进那份计划文档的「决策记录」。它是一次取舍，不是全局规范 —— 提升成规范会让后人无法判断该不该套用在这版里。
- **与 `docs/intellij-platform.md` / `docs/sqltoy-semantics.md` 重复的** → 写在那两份文件里，本节引用。一份事实只写一处。

**「现状」与「期望」不一致时，必须写清是哪一种**：

1. **已生效** —— 代码基本都这么写，只是没写下来。标注实测处数与例外场景。第 4 节（判空）就是样板：`Objects.isNull` 41 处 vs `== null` 46 处，混用比例直接写进标题，因为把这种状态硬写成规则只会制造误报。
2. **尚未落地** —— 你这么写，但现有代码多数不是。条目标注「期望，尚未全量落地」，并给出迁移策略。缺了迁移策略，读者无法判断这一版该不该跟着改，于是要么不敢动、要么顺手重构跑偏。

**这一节什么时候该拆出去**：条目多到一屏看不完（约 15 条以上），或出现明显聚类（如「日志」「异常处理」「并发」各自成组）时，拆成 `docs/conventions/` 目录并在这里保留指针。现在还在 `conventions.md` 里 —— 插件规模小（31 个类），拆早了两处维护反而更容易漂移。

## 14. 改动纪律：最小修改

> 这一节约束的不是「代码写成什么样」，而是**手能伸多长**。触发时机是每一次动手改代码**之前**。
> 与第 12 节（提交纪律）同属流程类硬约束，并且同样**没有机械化校验** —— 它判的是「该不该动这段代码」，是意图判断，写不成断言。

| 规则 | 类别 | 说明 |
|------|------|------|
| **只改被点到的代码** | 硬约束（人工审查） | **没被提到的代码不能动。** 包括顺手发现的坏味道、笔误、命名不一致、「其实可以少写一行」的写法 |
| **最小改动优先** | 硬约束（人工审查） | 同一需求有多种改法时，选**改动面最小**的那种，而不是「更规范」的那种 |
| **不做想当然的推断** | 硬约束（人工审查） | 需求没说的不替它补。不确定就停下来问，不按「通常应该这样做」自行决定 |
| 改完自检 diff | 软约定 | `git diff` 逐行过一遍，每处改动都要能对应到某句需求或某个验收标准 |

**为什么**

1. **代价不在改动本身，而在验证面。** 改 1 处验证 1 处；顺手多改 5 处，验证面变成 6 处，其中 5 处没人要求过 —— 出了问题也说不清是谁引入的。
2. **「看起来是笔误」的地方往往是刻意的。** 需求方看到的是整条业务链路，改动者看到的是孤立的一行。本仓库就有实例：`SqlToySqlSyntaxHighlighter.java:103` 的 bracket token 故意 fall through 到 `EMPTY_KEYS`，注释写明是为 Rainbow Brackets 兼容 —— 单看代码很像漏了一个 `case`。
3. **混进无关改动的 diff 是不可审查的 diff。** 评审者在噪音里会漏掉真问题。

**边界（别走极端）**

- **必须同步的部分不算超标**：为这一处改动而必须跟着改的签名、调用点、`plugin.xml` 注册、类目录、测试、文档，属于「被要求」的一部分。但要能说清**为什么必须**。
- **范围外的问题报而不改**：在回复里列出来，由用户决定要不要另开一个计划（`PLANS.md` 登记一行）。发现问题的价值保留，改动权不越界。
- **本次改动直接造成的编译错误 / 测试失败必须修**，不算范围外。

**与计划文档的关系**：每份计划的「不做（边界）」一节，是这条规则在那个具体任务上的实例化 —— 开工前先读它。
