# 计划：把 sqltoy-navigator 改造成 Harness 工程

- 状态：进行中（阶段 3 / 6 完成，阶段 4 待确认）
- 创建日期：2026-10-14
- 目标：让在这个仓库里干活的 AI agent 与新人，能**自己找到路径**、并且能**自己验证结果**，而不依赖口头传递的默契。

---

## 一、现状诊断

仓库当前状态：IntelliJ 插件工程，`develop` 基线 **29** 个 Java 类（截至本计划创建日；2026-10-15 新增 2 个检查类后为 31），Gradle 9.5.1 + IntelliJ Platform Gradle Plugin 2.16.0，47 次提交，字节码 target 17。改造分支：`harness-bootstrap`（从 `develop` 切出）。

> 分支名说明：原计划写的是 `harness/bootstrap`，实际落地为 `harness-bootstrap`。原因是当前环境的 `.git/refs/heads/` 无法新建子目录，带 `/` 的分支名会「假成功」（`git checkout -b` 打印成功但 ref 不落盘，HEAD 变成未出生分支）。详见 `.workbuddy/memory/2026-10-14.md`。

以下 7 点是从实际文件里核出来的，不是泛泛而谈：

| # | 问题 | 证据 |
|---|------|------|
| 1 | 没有入口地图 | 仓库根目录只有 `README.md`，且它是面向插件使用者的市场文案风格（"功能 / 本地运行 / 后续扩展"），开发者需要的"我该改哪个文件"被埋在长清单里 |
| 2 | 文档与代码已漂移 | README「源码结构」列了 **20** 个类，`src/main/java` 实际有 **29** 个。缺失的 9 个：`SqlToyNavigationAction`、`SqlToyNavigationGutterIconRenderer`、`SqlToyNavigationPopupItem`、`SqlToyNavigationTarget`、`SqlToySqlInclude`、`SqlToySqlIncludeParser`、`SqlToySqlIncludeReference`、`SqlToySqlIncludeReferenceContributor`、`SqlToySqlIncludeResolver` |
| 3 | 文档与现实直接矛盾 | README 写「仓库不需要提交 Gradle Wrapper……不要求提交到源码仓库」，但 `gradlew`、`gradlew.bat`、`gradle/wrapper/*` 都在，`.gitignore` 还专门用 `!gradle/wrapper/...` 保留它们。agent 照 README 走会绕开 wrapper |
| 4 | 约定只存在于代码里、没有一处文字 | 中文 Javadoc + `@author ax` + `@date yyyy-MM-dd`、方法级全注释（`@param`/`@return`）、`final class` + 私有构造、`Objects.isNull/nonNull`、内部 `record` 作值对象、`@NotNull` 注解、注释解释"为什么"而非"是什么"。一致性极强，但一旦 agent 新增类，最容易被破坏的正是这些 |
| 5 | 零机械化校验 | 无 `src/test`、无 CI、无 lint。`build.gradle.kts` 里没有 test 相关配置。唯一校验是人肉跑 `buildPlugin` 看能否编过 |
| 6 | 硬契约裸奔 | `plugin.xml` 的 11 处扩展点注册（`implementationClass`/`implementation`）指向 Java 类，属于编译期查不出、运行期才炸的契约；`language="SqlToySQL"` 这类字符串与 `SqlToySqlLanguage` 的 id 也是隐式耦合 |
| 7 | 发布知识埋在脚本注释里 | `release.sh` 里的分支模型（`develop` → 合并到 `master` → 打 `vX.Y.Z` tag → 切 `release/vX.Y.Z`）只存在于脚本的 `show_help()` 输出中，没有分支策略文档，agent 不知道该往哪个分支提 |

> **进展注记（2026-10-15）：第 2、3 两项已在阶段 2 修正。** README 已重写：不再维护类清单（改为指向 `docs/reference/class-catalog.md`）、wrapper 说法改为「Wrapper 已提交，用 `./gradlew`」、功能列表与 `plugin.xml` 对齐。上表保留原文，用于记录诊断时的真实事实。

补充事实：

- 远程为 `https://github.com/zhyx01/sqltoy-navigator.git`（GitHub，可上 Actions）。
- `develop` 与 `origin/develop` 同步，tip 为 `ec09069`（2026-07-23）。
- 分支 `v20260831-optimization` 比 `develop` 多 **1 个**提交 `9d004ce`（2026-08-31 15:09），差异仅 3 个文件：新增 `SqlToySqlIdHoverHintProvider.java`（399 行）、修改 `SqlToySqlIdAnnotator.java`（+74/-49）、`plugin.xml` +2 行。**该分支尚未合并**，因此 `develop` 上没有悬浮提示功能。
- 改造分支 `harness-bootstrap` 已从 `develop` 切出（原名 `harness/bootstrap`，因环境限制去掉斜杠）。

---

## 二、目标结构

```
sqltoy-navigator/
├── AGENTS.md                        # 入口地图：命令、硬约束、任务导航（目标 ≤120 行）
├── ARCHITECTURE.md                  # 架构与代码结构：子系统划分、类间依赖、三条主链路
├── PLANS.md                         # 计划台账：进行中 / 已完成
├── README.md                        # 保留，但降级为「插件使用者文档」，与事实对齐
├── docs/
│   ├── index.md                     # 文档总索引（AGENTS.md 的展开）
│   ├── conventions.md               # 代码约定（Javadoc 模板、命名、工具类、record、判空）
│   ├── intellij-platform.md         # 平台知识：扩展点注册、PSI 访问、CachedValuesManager、DumbService、可测试性
│   ├── sqltoy-semantics.md          # 业务语义：sqlId 形态、@include、多方言前缀、常量 sqlId、置灰规则
│   ├── branches.md                  # 分支与发布模型（从 release.sh 反推成文档）
│   ├── reference/
│   │   └── class-catalog.md         # 29 个类 → 职责 / 扩展点 / 从属子系统（唯一事实来源）
│   ├── quality/
│   │   ├── golden-principles.md     # 每条原则 → 对应哪条机械化校验（可执行，不是口号）
│   │   └── verification.md          # 分层验证手册 + runIde 手工冒烟清单
│   └── plans/
│       ├── README.md                # 计划的命名、生命周期与模板
│       ├── active/                  # 没结束的计划（待开工 / 进行中）
│       │   └── 2026-10-14-Harness-工程改造-harness-bootstrap.md
│       └── completed/               # 已结束的计划（已完成 / 已放弃），归档
│           └── 2026-08-31-悬浮提示三处问题修复-hover-hint-fix.md
├── scripts/
│   ├── verify.sh                    # 单一验证入口（与现有 release.sh 风格一致）
│   └── verify.bat                   # Windows 对应版本
├── src/test/java/com/ax/sqltoy/harness/
│   ├── PluginXmlConsistencyTest.java
│   ├── SourceConventionsTest.java
│   └── ClassCatalogTest.java
└── .github/workflows/verify.yml     # CI：一条命令跑 verify
```

### 设计取舍

- **`AGENTS.md` 只做索引，不做百科。** 单文件膨胀是 agent 失焦的头号原因，所以细节一律下沉到 `docs/`，`AGENTS.md` 里只保留：定位一句话、命令速查、仓库地图、硬约束摘要、任务导航表、禁区。
- **`docs/reference/class-catalog.md` 是源码结构的唯一事实来源。** README 里那份清单改为指向它，从根上消除"两处维护、必然漂移"。
- **`docs/quality/golden-principles.md` 里每条原则都必须挂一个能失败的校验。** 挂不上校验的原则不写进去 —— 写进文档靠自觉的规则等于没有规则。
- **根目录三份入口文档各司其职、互不重复。** `AGENTS.md` 管「怎么在这里干活」，`ARCHITECTURE.md` 管「代码长什么样」，`PLANS.md` 管「在做什么、做过什么」。
- **计划文档的留痕比结果重要。** 台账一行不删，放弃的也保留并写明原因；进行中的计划必须写明验证方式。
- **计划文档按状态分目录存。** 没结束的放 `docs/plans/active/`，一结束就移入 `docs/plans/completed/` 归档。好处是任何时候中断，下次回来只要看 `active/` 就知道有哪些事没做完 —— 不必靠记忆，也不必翻台账。
- **计划文件名三段式：`YYYY-MM-DD-<中文描述>-<英文短横线主题>.md`。** 中文段给人看（不打开文件就知道是什么事），英文段给工具看（`grep`、URL、跨平台检索的安全键），日期段给排序看。只写英文 slug 的问题在实践中会暴露：过两周回来看 `unresolved-sqlid-error` 还得打开文件确认自己当时想干什么。改名一律 `git mv` 并同步全部引用。
- **提交纪律是规范的一部分，且默认动作是「不提交」。** 落盘与提交分离：执行者只负责把改动写进工作区，提交的时机与范围由人指定。这样工作区常驻「已改未提交」是正常状态而不是欠债 —— 计划文档里写的「已提交 / 未提交」因此只记事实，不驱动动作。全文见 `docs/conventions.md` 第 12 节。

---

## 三、机械化约束设计（L2，本次改造的核心）

原则：**规则要么能自动失败，要么不配叫规则。**

| 不变量 | 校验方式 | 挡住的真实事故 |
|--------|----------|----------------|
| plugin.xml 引用的类必须存在 | JUnit 解析 plugin.xml（XPath），反射校验每个 `implementationClass`/`implementation` 对应的类可加载 | 改名/删类后忘改 XML，运行期抛 `ClassNotFoundException` |
| 扩展点实现类必须可无参实例化、不得为 abstract/interface | 同上，反射校验 modifier 与构造函数 | 给实现类加了带参构造 |
| `language="SqlToySQL"` 必须等于 `SqlToySqlLanguage` 注册的 id | 反射读取 Language 实例的 `getID()` 做比对 | 语言 id 字符串写错，高亮/解析静默失效 |
| `depends` 声明与 `import com.intellij.*` 实际用到的模块一致 | 源码 import 扫描 vs plugin.xml `depends` 列表 | 用了 XML/Java 模块却没声明，插件在纯净 IDE 里加载失败 |
| `com.ax.sqltoy` 下类名必须以 `SqlToy` 开头 | 源码文本校验 | 命名体系被新类打破 |
| 每个类型必须有类级 Javadoc，非 `record` 还须含 `@author` 与 `@date` | 源码文本校验 | agent 产出裸类（本仓库现有 29 个类 100% 符合，说明这是既有约定） |
| 源码与 `class-catalog.md` 双向一致 | `src/main/java` 文件枚举 vs 文档中登记的类名，双向比对 | **README 缺 9 个类**这类漂移再次发生 |
| 禁止 `System.out` / `printStackTrace` / 残留 `TODO`·`FIXME` | 源码文本校验 | 调试代码被提交 |
| 源文件 UTF-8 且无 BOM | 字节校验 | 中文注释乱码（本仓库注释全中文，风险真实） |

实现方式：**写成 JUnit5 测试**（放在 `src/test/java/com/ax/sqltoy/harness/`），因为 `gradlew test` 是现成的机械化通道，本地和 CI 天然会跑。`scripts/verify.sh` 只是把这些通道串成一条命令。

`scripts/verify.sh` 的执行顺序（快速失败优先）：

1. `ClassCatalogTest`（纯文本，毫秒级）
2. `SourceConventionsTest`（纯文本，毫秒级）
3. `./gradlew compileJava`（编译）
4. `PluginXmlConsistencyTest` + `./gradlew test`（需要 classpath，稍慢）
5. 输出结论：通过/失败 + 失败项定位到文件与行

---

## 四、验证闭环设计（L3）

`docs/quality/verification.md` 定义分层验证，`runIde` 的手工冒烟清单**逐条对应 plugin.xml 里的扩展点**，改哪个扩展点就勾哪几条，避免"改了 A 顺手弄坏 B"：

| 冒烟项 | 对应扩展点 | 操作 |
|--------|-----------|------|
| Java sqlId 跳 XML | `psi.referenceContributor(JAVA)` | `"view_user_list"` 上 Ctrl+Click |
| XML 跳回 Java 使用处 | `codeInsight.lineMarkerProvider` | `<sql id="...">` 旁 gutter 图标 |
| `@include("sqlId")` 跳转 | `psi.referenceContributor(XML)` | XML 正文 include 上 Ctrl+Click |
| 多数据源前缀跳转 | `SqlToySqlIdXmlResolver` 方言候选 | `postgresql_xxx` 能否落到 `xxx` |
| 未使用 sqlId 置灰 | `annotator(XML)` | 无引用的 `<sql>` id 是否灰显 |
| Java 侧不存在 sqlId 置灰 | `annotator(JAVA)` | 引用不存在的 sqlId 是否灰显 |
| SQL 高亮/括号匹配 | `lang.syntaxHighlighterFactory` / `lang.braceMatcher` | 打开 XML SQL 看关键字着色与括号配对 |
| 单词出现位置高亮 | `editorFactoryListener` | 选中 SQL 单词看整块内高亮 |
| 悬浮提示 | `editorFactoryListener` | 悬停 sqlId 看提示内容 |

---

## 五、执行阶段

每阶段结束给 diff 清单，确认后再进入下一阶段。

| 阶段 | 内容 | 风险 | 产出 |
|------|------|------|------|
| **0** | 本方案（当前） | 无（纯新增 1 个 md） | `docs/plans/active/2026-10-14-Harness-工程改造-harness-bootstrap.md` |
| **1** | 入口层 + 计划台账 + 类清单：`AGENTS.md`、`ARCHITECTURE.md`、`PLANS.md`、`docs/index.md`、`docs/plans/README.md`、`docs/plans/completed/2026-08-31-悬浮提示三处问题修复-hover-hint-fix.md`、`docs/reference/class-catalog.md`（全量登记 29 个类） | 无（纯新增） | 7 个新文件 —— ✅ **已完成** |
| **2** | 修 README 与现实的矛盾，类清单改为指向 catalog | 低（只改文档） | `README.md` 重写 —— ✅ **已完成** |
| **3** | 约定与领域知识：`conventions.md`、`intellij-platform.md`、`sqltoy-semantics.md`、`branches.md`（不再另建 `docs/architecture.md` —— 架构与结构内容统一由根目录 `ARCHITECTURE.md` 承载，避免两处维护） | 无（纯新增） | 4 个新文件 —— ✅ **已完成** |
| **4** | 机械化约束：`scripts/verify.sh` + `verify.bat`、3 个结构测试、`build.gradle.kts` 加 JUnit5 依赖。**验收标准：先让它全绿** | 中（改构建配置） | 脚本 + 测试，`verify` 输出全绿 |
| **5** | CI：`.github/workflows/verify.yml` | 低 | workflow 文件 |
| **6** | 收尾：把这批计划文档从 `docs/plans/active/` 移入 `docs/plans/completed/` 归档，文档头部状态改为「已完成」 | 无 | 2 个文件（移动） |

---

## 六、需要拍板的决策点

1. ~~**分支**：当前在 `v20260831-optimization`，且工作区不干净。建议从 `develop` 切新分支 `harness/bootstrap` 做改造。~~ **已定：从 `develop` 切出 `harness/bootstrap`。**
2. **是否允许动 `build.gradle.kts`**：阶段 4 需要引入 JUnit5 测试依赖。若不想让"文档/引擎改造"和"构建改造"混在一起，可以把阶段 4 拆成独立分支。
3. **CI 是否上 GitHub Actions**：远程是 GitHub。若仓库私有或不想接 CI，阶段 5 可跳过，只保留本地 `scripts/verify.sh` 入口。
4. **是否兼容其他 agent 入口**：除 `AGENTS.md` 外，是否需要同时提供 `CLAUDE.md` / `.cursor/rules` 等软链或指针文件？（不增加维护成本，只加一行指针）
5. **plugin.xml 契约校验的严格度**：`depends` 声明一致性校验是"全量严格比对"还是"仅校验用到的模块都已声明"（后者更宽松、误报更少，建议后者）。

---

## 七、不做的事（边界）

- 不动 `build/`、`.gradle/`、`.idea/`、`.intellijPlatform/`、`gradle/wrapper/` 这些生成或工具目录。
- 不改插件运行时行为、不改 `plugin.xml` 的扩展点注册内容（阶段 4 的测试只读校验它）。
- 不引入新的运行时依赖（插件体积敏感）。
- 不重写 `release.sh` / `release.bat`（只把它们承载的分支知识抽成 `docs/branches.md`）。

---

## 八、变更原因

| 日期 | 变更 |
|------|------|
| 2026-10-15 | 新增计划文件命名规则（日期 + 中文描述 + 英文短横线主题），并据此改名：`2026-10-14-harness-bootstrap.md` → `2026-10-14-Harness-工程改造-harness-bootstrap.md`。同步 `docs/plans/README.md` 的「命名」一节、本文件目录树与产出表、以及 `AGENTS.md` / `PLANS.md` / `docs/index.md` / `ARCHITECTURE.md` 的引用。 |
| 2026-10-15 | 新增提交纪律要求：执行过程中**不自动提交**，`git add` / `git commit` / `git push` 等用户指示，范围也由用户指定。规则落在 `docs/conventions.md` 第 12 节（唯一事实来源），并在 `AGENTS.md` 禁区、`PLANS.md`、`docs/branches.md`、`docs/plans/README.md` 各留一条指回它的引用。 |
| 2026-10-15 | 修正事实：进度一节记录的 `harness-bootstrap` tip 由 `a2b2710` 更新为 `1b4e7be`（中间还有 `2dd16cd`）。 |
| 2026-10-15 | 阶段 3 的 `conventions.md` 扩了两节：第 12 节「提交纪律」、第 13 节「个人代码习惯」（承接作者本人的写法偏好，含「习惯 / 现状 / 迁移」三要素模板，并划清与 `quality/golden-principles.md`、计划文档「决策记录」的边界）。同时在 `AGENTS.md` 的任务导航表加一行「加一条代码规范 / 记一个写法习惯」，让落位路径本身可被检索。 |
| 2026-10-15 | `conventions.md` 再加第 14 节「改动纪律：最小修改」（只改被点到的代码 / 最小改动优先 / 不做想当然的推断）。**这一条没有机械化校验，永远是人工审查项** —— 判的是意图而非代码形态，写不成断言，所以 `AGENTS.md` 的硬约束清单里单独标注它「不受阶段 4 影响」。原禁区里那条「不要为顺手优化而重构无关的类」是它的特例，已并入。 |

---

## 九、当前进度与下次续接

- **做到哪了**：阶段 1 ~ 3 已完成并提交在分支 `harness-bootstrap` 上（提交 `a2b2710` 落入口三件套与 `docs/` 骨架，`2dd16cd` 落分支约定与计划台账映射，`1b4e7be` 落计划目录分 active/completed 与中文续接说明；当前 tip 为 `1b4e7be`）。入口三份文档、五份领域文档、类目录、计划目录约定均已就位。
- **下次从哪接着做**：阶段 4 —— 机械化校验。先写 `scripts/verify.sh` 与 `verify.bat`，再写 3 个结构测试（`PluginXmlConsistencyTest`、`SourceConventionsTest`、`ClassCatalogTest`），验收标准是 `verify` 全绿。
- **卡在哪**：阶段 4 需要往 `build.gradle.kts` 加 JUnit5 测试依赖，尚未获得许可。备选方案是退化成纯 `scripts/verify.sh`（命令行文本校验、零依赖、可离线跑），代价是断言能力弱一档。
- **另有两项待表态**：阶段 5 是否接 GitHub Actions CI；是否一并放 `CLAUDE.md` / `.cursor/rules` 指针文件。

> **恢复工作的第一步**：`git checkout harness-bootstrap`，然后**立刻回读 `git status`**。本环境下切分支偶尔会把工作区文件误删成「已删除」状态（索引与 HEAD 都是好的），执行 `git checkout -- .` 即可完整恢复；随后建议做一次逐文件哈希比对确认。
