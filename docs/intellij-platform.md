# IntelliJ 平台知识

> 本文件记录**本项目实际用到的平台 API、这些 API 的能力边界，以及已经踩过的坑**。
>
> 不重复扩展点清单 —— 那是 `reference/class-catalog.md` 附录 1 的职责（唯一事实来源）。
> 本文件讲的是「为什么这样写」和「改成别的会怎样」。
>
> 内容基于 IntelliJ Platform **2024.1.1**（`sinceBuild = 241.15989.150`）。

## 1. 扩展点注册的三条规则

| 规则 | 说明 |
|------|------|
| 实现类必须 **`public`** | IntelliJ 靠反射实例化。包级可见的类注册上去会静默失效 |
| 实现类必须有**无参构造** | 同上。需要状态就在字段里自己 `new`（如 `SqlToyXmlSqlAnnotator` 持有 `includeParser`） |
| 属性名**不统一**，照抄现有写法 | `localInspection` / `annotator` / `lang.*` 用 `implementationClass`，而 `psi.referenceContributor` 用 **`implementation`**。写错不会报错，只是不生效 |

**`language="SqlToySQL"` 必须等于 `SqlToySqlLanguage` 构造函数传入的 id**（硬约束）。这个字符串在 `plugin.xml` 里出现 3 次（`lang.parserDefinition` / `lang.syntaxHighlighterFactory` / `lang.braceMatcher`），在 Java 里出现 1 次（`super("SqlToySQL")`）。改一处忘一处 → 高亮/括号配对整体失效，且**没有报错**，只有表现异常。

## 2. 语言与注入：高亮表现依赖用户环境 ⚠️

本项目有一个**内置轻量 SQL 语言**（`SqlToySqlLanguage` + `SqlToySqlLexer`），但它默认**不被使用**：

```java
// SqlToyXmlSqlLanguageInjector.java:64-68
Language ideaSqlLanguage = Language.findLanguageByID("SQL");
// Database Tools 不可用时，仍使用插件自带的轻量语言保证基础高亮。
return ideaSqlLanguage == null ? SqlToySqlLanguage.INSTANCE : ideaSqlLanguage;
```

`Language.findLanguageByID("SQL")` 只有装了 **Database Tools** 插件才拿得到。所以：

| 用户环境 | XML 里 SQL 的高亮来源 |
|---------|---------------------|
| 装了 Database Tools（IDEA Ultimate 默认有） | **IDEA 自带 SQL 语言**，插件自己的 lexer 不参与 |
| 没装（如 Community / 精简安装） | 回退到 `SqlToySqlLanguage`，用 `SqlToySqlLexer` + `SqlToySqlSyntaxHighlighter` |

**后果：任何涉及 SQL 词法/高亮的改动，必须在两种环境下各验证一次**，否则很容易出现「我这好着呢」而用户那边完全没效果。

另一个容易误会的点：`SqlToySqlFileType` 的扩展名 `sqltoy-sql` 是**合成**的，磁盘上不存在这种文件。它只是给 parser / syntax highlighter 提供注册载体。

## 3. 索引尚未就绪：两种策略，别混用

IntelliJ 启动后会先建索引（Dumb Mode）。**索引期间所有"全项目查找"都返回空结果**，此时任何基于查找结果的判断都是错的。

本项目用了两种策略，各有适用面：

### 策略 A：`DumbAware` 标记 —— 声明「索引期间可以跑」

```java
public final class SqlToySqlWordOccurrenceHighlighter implements EditorFactoryListener, DumbAware {
```

适用于**不依赖索引**的功能（这个类只在当前编辑器里扫文本范围，不需要索引）。

### 策略 B：`DumbService.isDumb` 短路 —— 索引期间直接放弃

实测 **8 处调用，分布在 6 个文件**：

| 文件 | 处数 | 位置 |
|------|------|------|
| `SqlToyJavaSqlIdResolver` | 2 | `findLiteralTargets`、`findDialectLiteralTargets` |
| `SqlToySqlIdXmlResolver` | 2 | `findTargets`、`findDialectTargets` |
| `SqlToySqlIncludeResolver` | 1 | `findIncludeNavigationTargets` |
| `SqlToyXmlSqlAnnotator` | 1 | `annotateUnusedXmlSqlId` |
| `SqlToyUnresolvedSqlIdInspection` | 1 | `visitLiteralExpression` |
| `SqlToyUnresolvedIncludeInspection` | 1 | `visitXmlText` |

**这是本仓库最容易出事的一条规则。** 所有「反向查找」入口（XML → Java 引用、XML → include 引用）都必须短路，因为它们的调用方会拿空结果当「没有引用」：

- 空结果 → XML 的 `sqlId` 被判定「没人用」→ **全项目置灰**
- 空结果 → Java 的 `sqlId` 被判定「解析不到」→ **全项目标红报错**

**新增任何全项目查找时，第一件事就是加这个短路。**

## 4. 缓存：`CachedValuesManager`

实测 **4 处，4 个 Key，三个文件**：

| Key | 所在类 | 缓存内容 |
|-----|--------|---------|
| `JAVA_LITERAL_TARGETS_CACHE` | `SqlToyJavaSqlIdResolver` | 单个 Java 文件内 `sqlId → 字面量列表` |
| `XML_TARGETS_CACHE` | `SqlToySqlIdXmlResolver` | 单个 XML 文件内 `sqlId → 定义列表` |
| `XML_NORMALIZED_TARGETS_CACHE` | `SqlToySqlIdXmlResolver` | 同上，但按**小写**归一化分组（用于重复检测） |
| `XML_INCLUDE_TARGETS_CACHE` | `SqlToySqlIncludeResolver` | 单个 XML 文件内 `sqlId → include 引用位置` |

统一写法：

```java
return CachedValuesManager.getManager(javaFile.getProject()).getCachedValue(
        javaFile,                                  // 挂在哪个 PSI 元素上
        JAVA_LITERAL_TARGETS_CACHE,                // Key
        () -> CachedValueProvider.Result.create(collectLiteralTargetsById(javaFile), javaFile),
        false                                      // trackValue = false
);
```

要点：

- **依赖对象必须是 PSI 文件本身**，这样文件一改、IntelliJ 自动让缓存失效。依赖写错（比如写 `project`）→ 缓存永不失效，编辑后结果不更新。
- `trackValue = false`：不追踪返回值内部变化，只认依赖对象。本项目所有 4 处都是 `false`。
- **不要用 static `Map` 自己缓存**。PSI 会随编辑重建，手写缓存几乎必然持有已失效的元素。

## 5. 全项目查找：Java 与 XML 走不同的索引

| 目标 | API | 位置 |
|------|-----|------|
| Java 字符串字面量 | `PsiSearchHelper.processAllFilesWithWordInLiterals(word, scope, proc)` | `SqlToyJavaSqlIdResolver.java:277` |
| XML 文本 | `PsiSearchHelper.processAllFilesWithWordInText(word, scope, proc, true)` | `SqlToySqlIdXmlResolver.java:241` |

两处都先调 `SqlToySqlIdXmlResolver.getIndexSearchWord(sqlId)` 取**最长可索引片段**来缩小范围（理由写在注释里：命中文件更少，少解析 PSI），拿不到片段才回退到 `FileTypeIndex.getFiles(...)` 全量遍历。

这是纯**读**操作，可以在后台线程跑。

## 6. 高亮：annotator 与 inspection 的能力边界 ⭐

**这是本项目最重要的一条工程知识**，两条路径的能力完全不同，选错会做不到想要的效果。

| 能力 | `Annotator` | `LocalInspectionTool` |
|------|-------------|----------------------|
| 精确指定文字效果 | ✅ `enforcedTextAttributes(TextAttributes)`，可任意配前景色 / 下划线 | ❌ 只能给 `TextAttributesKey`（配色方案里的键） |
| 画波浪线 | ✅ 非 silent 标注 | ✅ |
| **右侧错误条纹** | ✅ 但要求级别非 `INFORMATION` 且非 silent | ✅ |
| 进 Problems 面板 | ❌ | ✅ |
| 用户可在 Settings 里关闭 / 改级别 | ❌ 硬编码 | ✅ 走 profile 级别 |
| 挂 Quick Fix | ❌ | ✅ |

### 关键结论

1. **右侧红色条纹由「级别」驱动，与文字颜色无关。** `newSilentAnnotation(HighlightSeverity.INFORMATION)` 意味着：不画波浪线、**不进右侧条纹**、不进 Problems。这正是改造前「未定义 sqlId 只有灰字、没有右侧红条」的原因 —— 缺的不是颜色，是级别。

2. **inspection 做不到「精确指定灰字 + 红波浪」。** `ProblemDescriptor.setAttributes(TextAttributesKey)` 只接受配色键，要落地必须随插件新增 `<additionalTextAttributes scheme="..." file="..."/>` + 配色 XML，且非默认配色下会退化。所以本项目**放弃了「保留灰色文字」**，改用标准错误观感（红字 + 红波浪 + 红条纹）。

3. **两者不要覆盖同一区间。** 同一文本区间上叠两层高亮，谁盖谁没有保证。本项目的处理方式是让 annotator 主动退场：

   ```java
   // SqlToySqlIdAnnotator.java:60 —— 由 inspection 接管，这里不再置灰
   if (SqlToyJavaSqlIdResolver.isFirstSqlIdArgument(literalExpression)) {
       return;
   }
   ```

4. 级别选择：inspection 用 `ProblemHighlightType.GENERIC_ERROR_OR_WARNING`，实际级别由 `plugin.xml` 的 `level="ERROR"` 给定、用户可改。

### 选择建议

- 观感需要精确控制 → **annotator**
- 需要用户可关闭 / 进 Problems / 挂 Quick Fix / 右侧条纹 → **inspection**

## 7. 引用与导航

| 项 | 写法 | 说明 |
|----|------|------|
| 引用注册 | `PlatformPatterns.psiElement(PsiLiteralExpression.class)` 广注册，在 provider 内用 `maybeSqlId` 过滤 | 「注册放宽、内部收敛」——避免为每种形态各写一个 pattern |
| 引用基类 | `PsiPolyVariantReferenceBase(element, range, **true**)` | 第 3 参是 `soft`。**`soft=true` 时平台不产出 "Cannot resolve"**，这是刻意的：否则平台会自己报一个错，跟插件的 inspection 消息打架 |
| 多目标 | `multiResolve` 返回全部候选，IDEA 自动弹选择框 | 同名 XML 定义分散在多个文件时 |
| 补全 | 在 `getVariants()` 里返回 `LookupElementBuilder`，用 `withTypeText(fileName)` 显示来源文件 | |
| gutter 图标 | 挂在 **silent annotation** 上：`newSilentAnnotation(...).gutterIconRenderer(...)` | |

## 8. 可测试性

**现状：`src/test` 不存在，仓库没有任何自动化测试。** 阶段 4 会补结构校验测试。

按可测试难度分级：

| 级别 | 对象 | 需要的环境 |
|------|------|-----------|
| 纯 JUnit，无平台依赖 | `SqlToySqlIdXmlResolver.maybeSqlId` / `getDialectSqlIdCandidates` / `getIndexSearchWord`、`SqlToyJavaSqlIdResolver.getStringContentRange` | 普通 JVM |
| 需要 PSI fixture | `SqlToySqlIncludeParser.findIncludes`、`SqlToySqlIdXmlResolver` 的 XML 扫描 | IntelliJ Test Framework + fixture 文件 |
| 只能手工冒烟 | annotator / inspection 的实际观感、gutter 图标、悬浮提示、语言注入 | `./gradlew runIde` |

⚠️ **一个会绊住人的细节**：本仓库大量工具类是**包级可见**（`final class SqlToyJavaSqlIdResolver`）。测试类**必须放在同一个包 `com.ax.sqltoy` 下**才能访问它们；放在 `com.ax.sqltoy.harness` 里的测试只能测 `public` 的类。

## 附录：不跑 IDE 就确认平台 API 是否存在

改插件时经常需要确认某个 API 在当前平台版本里存不存在、签名是什么。不必启动 IDE，直接反编译本地产物：

```bash
ROOT=~/.gradle/caches/9.5.1/transforms/<hash>/transformed/ideaIU-2024.1.1-win
# 平台类在 $ROOT/lib/*.jar，Java 插件类在 $ROOT/plugins/java/lib/*.jar

CP=""
for f in "$ROOT/lib"/*.jar "$ROOT/plugins/java/lib"/*.jar; do CP="$CP$(cygpath -w "$f");"; done

javap -cp "$CP" com.intellij.codeInspection.LocalInspectionTool
javap -cp "$CP" -p -c com.intellij.codeInsight.daemon.impl.LocalInspectionsPass | grep -i attribute
```

- `<hash>` 目录名不固定，用 `find ~/.gradle/caches -maxdepth 6 -name "ideaIU-*" -type d` 找。
- **必须用 `cygpath -w` 转 Windows 路径**：`javap` 是 Windows 可执行文件，不认 `C:/...` 之外的 MSYS 路径。
- 两个技巧：`javap -p` 看私有成员、`javap -c` 看字节码（用来确认某个方法**是否真的被调用**，例如 `LocalInspectionsPass` 里有没有 `getEnforcedTextAttributes`）。

本文第 6 节的结论就是这么得出的。
