# 分支与发布

> 本文件把 `release.sh` / `release.bat` 里的**隐含规则写成文档**。在此之前的现状是：分支模型只存在于脚本的 `show_help()` 输出里，脚本之外没人知道该往哪个分支提。
>
> 远程：`https://github.com/zhyx01/sqltoy-navigator.git`

## 1. 分支现状（2026-10-15 实测）

| 分支 | tip | upstream | 角色 |
|------|-----|----------|------|
| `develop` | `ec09069` feat(1.1.0) | `origin/develop` | **集成分支**，日常开发提交到这里 |
| `master` | — | `origin/master` | **发布分支**，只由发布脚本合入 |
| `v20260831-optimization` | `9d004ce` feat: optimization | `origin/v20260831-optimization` | 功能分支，**未合并** |
| `release/v1.0.5` | — | `origin/release/v1.0.5` | 历史发布分支（已冻结） |
| `main` | `0580807` Merge branch 'develop' | **`origin/main` 已不存在** | ⚠️ 历史遗留，**不要使用** |

**`main` 是废弃分支**：上游已被删除（`git branch -vv` 显示 `[origin/main: gone]`），且 `release.sh` 完全不涉及它 —— 脚本用的是 `master`。看到 `main` 请不要往上提交。

## 2. 模型

```
功能分支（任意命名）
      │  PR / 直接合并
      ▼
   develop ──────────── 集成分支，日常提交
      │  发布时由 release 脚本合并
      ▼
   master ───────────── 发布分支
      │
      ├── tag  vX.Y.Z          发布标记
      └── release/vX.Y.Z       冻结分支
```

| 分支 | 命名规则 | 谁往里提 |
|------|---------|---------|
| 集成分支 | 固定 `develop` | 所有人，日常 |
| 发布分支 | 固定 `master` | **只由发布脚本**，不要手工推送 |
| 功能分支 | 自由（现有：`v20260831-optimization`、`harness-bootstrap`） | 开发者自己，做完合回 `develop` |
| 冻结分支 | `release/vX.Y.Z` | 发布脚本创建，创建后不再接受新功能 |

## 3. 发布流程：`release.sh <版本号>`

```bash
sh release.sh 1.1.1        # 或 Windows: release.bat 1.1.1
```

脚本共 10 步，核心逻辑：

| # | 动作 |
|---|------|
| 1 | 检查当前目录是 Git 仓库 |
| 2 | **检查工作区干净**（`git status --porcelain` 非空就退出） |
| 3 | `git fetch origin` |
| 4 | 校验 `release/vX.Y.Z` 在**本地与远程都不存在** |
| 5 | 校验 tag `vX.Y.Z` 在**本地与远程都不存在** |
| 6 | `git checkout master` + `git pull origin master` |
| 7 | **`git merge --no-ff origin/develop`**（注意合的是**远程** develop，不是本地） |
| 8 | 合并冲突 → `git merge --abort` 并退出（**不会留下半成品**） |
| 9 | `git push origin master` → `git tag -a vX.Y.Z -m "Release X.Y.Z"` → `git push origin vX.Y.Z` |
| 10 | `git checkout -b release/vX.Y.Z` → `git push -u origin release/vX.Y.Z` |

`release.bat` 的变量与步骤顺序**与 `.sh` 完全一致**，改一个必须同步改另一个。

两个容易误判的细节：

- 第 7 步合并的是 **`origin/develop`**，不是本地 `develop`。本地有未推送的提交时，它们**不会**进入发布。
- 第 9 步的 tag 建在 **`master`** 上，不是 `develop`。

## 4. 版本号有四个地方，必须一起改 ⚠️

| 位置 | 内容 | 谁维护 |
|------|------|-------|
| `build.gradle.kts` 的 `version` | `1.1.1` | **手工改** |
| `build.gradle.kts` 的 `changeNotes` | 更新说明 | **手工改** |
| tag 名 | `v` + 版本号 | 发布脚本 |
| `plugin.xml` 的 `<version>` | — | **Gradle 注入，不要手工加** |

`plugin.xml` 的源码里**没有** `<version>` 与 `<change-notes>`，它们由 IntelliJ Platform Gradle Plugin 的 `patchPluginXml` 任务在构建时注入（值来自 `build.gradle.kts`）。所以：

- 想改包里的版本号或更新说明 → **改 `build.gradle.kts`**，不要去找 `plugin.xml`
- **只改 `version` 不改 `changeNotes`** → 打出来的包里更新说明还是上一版的内容。这是真实踩过的坑：`changeNotes` 曾长期停留在 1.1.0 的「置灰」描述上，而代码已经改成报错了。

验证方式（不用装进 IDE）：

```bash
./gradlew buildPlugin --offline
# zip 是套壳结构，plugin.xml 在内层 jar 里
python -c "import zipfile,io; z=zipfile.ZipFile('build/distributions/sqltoy-navigator-1.1.1.zip'); \
i=zipfile.ZipFile(io.BytesIO(z.read('sqltoy-navigator/lib/sqltoy-navigator-1.1.1.jar'))); \
print(i.read('META-INF/plugin.xml').decode('utf-8')[:600])"
```

## 5. tag 命名历史不一致

实测现有 tag：`0.0.1`、`0.1.1`、`1.0.0`、`v1.0.5`。

前三个没有 `v` 前缀，`v1.0.5` 有，而发布脚本生成的是 **`v$VERSION`**。所以：

- **新发布一律用 `vX.Y.Z`**（与脚本一致）
- 不要去「统一」历史 tag —— 已推送的 tag 改名会破坏引用

## 6. 本仓库特有的注意事项

### 6.1 `.gitignore` 里的 wrapper 规则

```
*.jar
!gradle/wrapper/gradle-wrapper.jar
!gradle/wrapper/gradle-wrapper.properties
```

- `!gradle/wrapper/gradle-wrapper.jar` 这一行是**必需的** —— 上面有 `*.jar` 规则会误伤它，靠这行否定规则救回来。
- `!gradle/wrapper/gradle-wrapper.properties` 是**冗余的** —— 没有任何规则会忽略它。

结论：**Gradle Wrapper 必须提交**（`gradlew` / `gradlew.bat` / `gradle/wrapper/*`）。仓库一律用 `./gradlew`，不要依赖本机安装的 gradle。

### 6.2 带斜杠的分支名在沙箱里会「假成功」

在当前工作环境（Windows 沙箱）中实测：**`.git/refs/heads/` 下无法新建子目录**，直接后果是带 `/` 的分支名创建失败但**不报错**：

```bash
git checkout -b release/v1.1.1
# → Switched to a new branch 'release/v1.1.1'   ← 打印成功
# → 但 .git/refs/heads/release/v1.1.1 并不存在
# → HEAD 指向「未出生分支」：git status 报 "No commits yet"，把全部文件显示成「已暂存的新增」
```

不带斜杠的名字（`harness-bootstrap`）则正常。

**这不是仓库的问题**，但会影响在这里执行发布脚本（第 10 步要建 `release/vX.Y.Z`）。若命中：

```bash
# 1. 先把 HEAD 救回来
git symbolic-ref HEAD refs/heads/develop && git reset -q
# 2. 复核分支是否真的落盘
cat .git/refs/heads/<分支名>
# 3. 必要时改用不带斜杠的名字，或在沙箱外执行发布
```

### 6.3 发布脚本的前置门槛

`release.sh` 第 2 步要求**工作区干净**。所以发布前必须先把改动提交。实测中经常卡在这里 —— 尤其是 `build/` 下的产物（如 `build/distributions/*.zip` 曾被提交过，删掉它就成了一个待提交的改动）。

## 7. 发布前 checklist

1. `develop` 上功能已合并、已推送（脚本合的是 `origin/develop`）
2. 工作区干净：`git status --porcelain` 无输出
3. `build.gradle.kts` 的 `version` 已更新
4. `build.gradle.kts` 的 `changeNotes` 已更新（**不是上一版的说明**）
5. `./gradlew buildPlugin --offline` 通过，且产物版本号正确
6. `docs/reference/class-catalog.md` 与源码一致（新增类已登记）
7. `PLANS.md` 里进行中的计划状态已更新
8. tag 名与 `release/vX.Y.Z` 分支在本地与远程都不存在
