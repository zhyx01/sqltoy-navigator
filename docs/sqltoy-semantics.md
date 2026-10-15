# SqlToy 语义

> 本文件描述**插件所理解的 SqlToy 业务语义**：什么算一个 `sqlId`、怎么找到它、什么情况下提示什么。
>
> 这些规则全部来自源码实测，不是从 SqlToy 官方文档抄的 —— 插件只实现了其中一个子集，本文件写的是**插件实际认的那部分**。

## 1. 两个世界

SqlToy 项目的 SQL 分散在两个地方，插件的工作就是在它们之间架桥：

```xml
<!-- XML：定义 -->
<sql id="order_queryList">
    select * from orders where status = :status
</sql>
```

```java
// Java：引用（注意 id 是纯字符串，没有任何语法载体）
multiLightDao.findByMap("order_queryList", params);
```

**「没有语法载体」是理解本项目一切设计取舍的起点。** 插件无法像普通 IDE 那样靠类型系统判断「这个字符串是不是 sqlId」，只能靠**形态匹配 + 位置推断**。所有的误报风险都源自这里。

## 2. 什么算一个 sqlId：`maybeSqlId`

```
值非 null
且 非空白字符串
且 长度 ≤ 200
且 完整匹配 [A-Za-z0-9_.$:/\-]+
```

实现在 `SqlToySqlIdXmlResolver.maybeSqlId`（`:89-100`）。允许的形态包括：

| 形态 | 示例 |
|------|------|
| 下划线 | `sql_id`、`order_queryList` |
| 模块前缀 + 点 | `module.queryList` |
| 混合符号 | `a-b/c:test` |

**这个判定刻意很宽。** 它是所有入口（Ctrl+Click、补全、置灰、报错）的第一道过滤，宽一点只会带来「候选变多」，不会带来错误提示；收窄反而会漏掉合法 id。

但宽判定的代价必须由别处承担：`"UTF-8"`、`"2026-10-14"`、`"com.ax.sqltoy"`、`"a.b"` **全都符合 `maybeSqlId`**。所以「宽判定」只能用于「给个灰色提示」，**不能直接用于报错** —— 这是第 6 节的分档存在的原因。

## 3. Java 侧：两种形态

### 3.1 字面量（最常见）

```java
multiLightDao.findByMap("order_queryList", params);
```

`SqlToyJavaSqlIdResolver.getSqlId(PsiLiteralExpression)`：取 `getValue()`（**已处理转义**），值必须是 `String`，再过 `maybeSqlId`。

### 3.2 静态常量

```java
private static final String SQL_ID_ORDER = "order_queryList";
...
multiLightDao.findByMap(SQL_ID_ORDER, params);
```

`getSqlIdFromConstantArgument` 要求**全部满足**：

1. 引用位于 SQL 方法首参位置（见下）
2. `resolve()` 得到的是一个 `PsiField`
3. 该字段同时是 `static` 且 `final`
4. 初始化器是**字面量**（不是方法调用、不是拼接）

第 4 条意味着 `static final String X = "a" + "b";` 这类也算（编译期常量折叠后仍是字面量），但 `= calc()` 不算。

### 3.3 「高置信位置」= `isFirstSqlIdArgument` ⭐

这是本项目**最关键的一个谓词**。判定条件（`SqlToyJavaSqlIdResolver.java:191-207`）：

| 条件 | 说明 |
|------|------|
| 父节点是 `PsiExpressionList` | 说明在实参列表里 |
| 实参列表的父节点是 `PsiMethodCallExpression` | 说明是方法调用 |
| 该表达式是 `getExpressions()[0]` | **必须是第一个实参** |
| 方法名 ∈ `SQL_ID_FIRST_ARGUMENT_METHOD_NAMES` | 见下 |

```java
// SqlToyJavaSqlIdResolver.java:55-60
private static final Set<String> SQL_ID_FIRST_ARGUMENT_METHOD_NAMES = Set.of(
        "find",
        "findOne",
        "findByMap",
        "getSingleValue"
);
```

⚠️ **这个集合是唯一开关。** 它同时决定两件事：

1. 哪些位置会被 inspection **标红报错**
2. 哪些位置会让 annotator **放弃置灰**（交给 inspection）

改它 = 改报错范围。作者自己在上一行留了注释：

```java
// todo：后续扩展，或者拿掉，业务场景，非常规引用
```

这个 todo 现在比写下时更重要了 —— 因为「未定义 sqlId 报错」已经落地，集合不准会**直接表现为误报或漏报**。若要扩展，请一并想清楚：新增的方法名会不会把普通字符串拖进报错范围。

## 4. XML 侧：什么算一个定义

```java
// SqlToySqlIdXmlResolver.isSqlToySqlTag —— 两个条件缺一不可
return "sql".equals(tag.getName()) && tag.getAttribute("id") != null;
```

| 特性 | 行为 |
|------|------|
| 标签名 | 必须是 `sql`（区分大小写） |
| 必须有 `id` 属性 | 没有 `id` 的 `<sql>` 不会被识别 |
| 嵌套结构 | **递归收集子标签**，兼容 SQL 定义被分组/包装的 XML 结构 |
| 导航目标 | 优先落在 `id` **属性值元素**上，让光标正好停在 id 文本 |
| SQL 正文提取 | 只取 `XmlText` 子节点（**跳过标签名**，避免把 `<value>` 这类包装标签名混进 SQL），再去掉 CDATA 包装与公共缩进 |

**SQL 正文范围的计算**（`SqlToyXmlSqlTextRanges.getSqlTextRange`）：先裁外围空白 → 若被 `<![CDATA[` … `]]>` 包裹则去掉标记 → 再裁内部空白。这是给高亮、include 扫描、悬浮提示共用的范围定义。

### 大小写：两套规则并存 ⚠️

| 用途 | 大小写 |
|------|-------|
| **查找定义**（跳转 / 解析 / 报错） | **区分大小写**（精确匹配） |
| **重复检测**（同一 XML 文件内） | **不区分大小写**（`toLowerCase(Locale.ROOT)` 归一化后比较） |

所以 `order_query` 与 `ORDER_QUERY` 在同一个文件里会被判为**重复**（报 `Duplicate sqlId`），但 Java 里写 `"ORDER_QUERY"` **跳不到** `order_query` 的定义。这个不一致是现状，不是笔误；改动前请先确认使用者预期。

## 5. 数据库方言等价：多数据源

SqlToy 用**前缀**区分不同数据库的同一 SQL：**Oracle 无前缀，其它数据库加固定前缀**。

```
gaussdb_       → GaussDB
postgresql_    → PostgreSQL
vastbase_      → Vastbase
（无前缀）      → Oracle
```

前缀表在 `SqlToySqlIdXmlResolver.DIALECT_SQL_ID_PREFIXES`（`:59-63`）。

`getDialectSqlIdCandidates(sqlId)` 返回的候选（**去重、保序**）：

1. 原值
2. 去掉已知前缀后的 base
3. `gaussdb_` + base
4. `postgresql_` + base
5. `vastbase_` + base

**后果**：`queryList`、`gaussdb_queryList`、`postgresql_queryList`、`vastbase_queryList` 被当作**同一个 sqlId**。

| 影响 | 表现 |
|------|------|
| Java 跳转 | 写 `"order_queryList"` 能跳到 `gaussdb_order_queryList` 的定义 |
| Java 报错 | 四个候选**全部找不到**才报错（`findDialectTargets` 为空） |
| XML 置灰 | 只要任一候选被 Java 引用，就不置灰 |

**增加一个方言**：改 `DIALECT_SQL_ID_PREFIXES` 一处即可生效；同时确认 `plugin.xml` 的 `<description>` 里方言列表也已更新（当前写的是 `oracle/vastbase/gaussdb/postgresql`）。

### 一个容易踩的不一致 ⚠️

| 侧 | 用的方法 | 是否走方言等价 |
|----|---------|--------------|
| Java | `findDialectTargets` | ✅ 走 |
| XML `@include` | `findTargets` | ❌ **不走** |

所以 `@include("queryList")` **不会**匹配到 `<sql id="gaussdb_queryList">`。这是现状，改之前先确认是否是有意为之。

## 6. `@include("sqlId")`

```xml
<sql id="order_list">
    <include sqlId="order_base"/>       <!-- 这不是被支持的写法 -->
</sql>
```

**实际支持的语法是写在 SQL 正文里的函数式引用**：

```
@include("order_base")
@include('order_base')
```

正则（`SqlToySqlIncludeParser.java:24-27`）：

```
@include\s*\(\s*(["'])([A-Za-z0-9_.$:/\-]+)\1\s*\)
```

| 特性 | 行为 |
|------|------|
| 大小写 | **不敏感**（`@Include` / `@INCLUDE` 都认） |
| 引号 | 单引号与双引号都支持，且**前后必须配对**（反向引用 `\1`） |
| 空白 | 括号内外的空白任意 |
| 生效范围 | **只在 `<sql id>` 标签的 SQL 正文内**匹配，普通 XML 文本不处理 |
| 二次校验 | 捕获到的值还要过一遍 `maybeSqlId` |
| 目标 | 与 sqlId 跳转**共用同一套 XML 定义索引**（include 的目标也是 `<sql id="...">`） |
| 匹配方式 | **精确匹配**，不走方言等价（见上） |

## 7. 四种视觉状态：完整规则表 ⭐

**这是使用者最容易困惑的地方** —— 同样一个「找不到定义」的字符串，有时标红、有时只是变灰。规则如下：

| # | 场景 | 判定条件 | 表现 | 由谁负责 |
|---|------|---------|------|---------|
| 1 | Java 字符串能解析到定义 | `findDialectTargets` 非空 | **绿色下划线** + 悬浮显示 SQL 正文 | `SqlToySqlIdAnnotator` |
| 2 | Java **高置信位置**解析不到 | 首参 且 `findDialectTargets` 为空 | 🔴 **红字 + 红波浪 + 右侧红条纹**，进 Problems，可在 Inspections 关闭 | `SqlToyUnresolvedSqlIdInspection` |
| 3 | Java **非高置信位置**解析不到 | 非首参 且 为空 | ⚪ **灰色文字**（silent，无波浪、无条纹） | `SqlToySqlIdAnnotator` |
| 4 | XML sqlId 无人引用 | Java 引用与 include 引用**都**找不到 | ⚪ **灰色文字** | `SqlToyXmlSqlAnnotator` |
| 5 | XML sqlId 在本文件内重复 | 归一化后同文件内 > 1 | 🔴 **`Duplicate sqlId in this XML file: xxx`**（ERROR） | `SqlToyXmlSqlAnnotator` |
| 6 | XML `@include` 解析不到 | `findTargets` 为空 | 🔴 **红波浪 + 右侧红条纹**，进 Problems | `SqlToyUnresolvedIncludeInspection` |
| 7 | `@include` 能解析到 | `findTargets` 非空 | **绿色文字 + 下划线** + gutter 跳转图标 | `SqlToyXmlSqlAnnotator` |
| 8 | **索引未就绪**（Dumb Mode） | `DumbService.isDumb` | **规则 2 / 3 / 4 / 6 全部不触发** | 各处的短路 |

第 8 条是刻意的：索引期间查找必然返回空，此时报错会变成「打开项目满屏红」。代价是**刚打开项目时会短暂没有任何提示**，等索引建完才出现。

### 设计上的两条硬规则

1. **报错范围必须与可跳转范围一致。** 能跳过去的地方不该报错，报错的地方应该跳不过去。所以两处 inspection 复用的解析方法与跳转完全同源（Java 用 `findDialectTargets`，XML 用 `findTargets`）。
2. **置灰与报错互斥。** 同一区间不叠两层高亮，观感才确定。这就是`SqlToySqlIdAnnotator` 与 `SqlToyXmlSqlAnnotator` 在对应位置上「主动 return 什么都不做」的原因。

## 8. 已知未覆盖的场景（诚实清单）

| 场景 | 现状 |
|------|------|
| **静态常量形式报错** | ❌ 不报错。声明处无法判断用途，使用处报错位置又不在字符串上，所以暂时不纳入 |
| XML 侧「引用了不存在的 sqlId」 | 只覆盖 `@include`，不覆盖其它形式 |
| Quick Fix / 一键修复 | ❌ 无。报错只提示，不提供「创建定义」等操作 |
| 大小写不一致的跨大小写跳转 | ❌ 跳不到（见第 4 节） |
| `@include` 的方言等价 | ❌ 不走（见第 5 节） |

## 9. 改这一段代码前该问的问题

| 我要改… | 先确认 |
|---------|-------|
| `maybeSqlId` 的宽判定 | 谁在用它？改窄会漏掉合法 id，改宽会让 `"UTF-8"` 更多出现在候选里 |
| `SQL_ID_FIRST_ARGUMENT_METHOD_NAMES` | **报错范围会跟着变**。新增方法名会不会把普通字符串拖进报错？ |
| 方言前缀表 | `plugin.xml` 的 `<description>` 也要同步 |
| 大小写规则 | 查找与去重目前不一致，统一会改变现有行为 |
| 把某处「置灰」改成「报错」 | 是否已有别的高亮覆盖同一区间？是否需要同时让 annotator 退场？ |
| 新增全项目查找 | 入口必须加 `DumbService.isDumb` 短路，否则索引期间全项目误报 |
