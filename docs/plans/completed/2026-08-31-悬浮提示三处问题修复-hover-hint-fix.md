# 计划：SqlToySqlIdHoverHintProvider 悬浮提示三处问题修复

- 状态：已完成（但**代码改动已丢失**，见文末）
- 创建日期：2026-08-31
- 完成日期：2026-08-31
- 目标：让 `sqlId` 悬浮提示「窗口够大、能滚动、鼠标移开不误关、点得到复制按钮」
- 录入方式：**回溯录入**（2026-10-14 从工作日志补写），工作发生在提交 `9d004ce` 之后

## 背景与现状

`SqlToySqlIdHoverHintProvider` 用 `JBPopup` + 自定义 Swing 面板实现悬浮提示，在 `editorFactoryListener` 扩展点上注册（分支 `v20260831-optimization`，提交 `9d004ce`）。

功能能弹出，但有三个体验缺陷，用户反馈"能弹出但看不清"。

## 三处问题、根因与改法

### 问题 1：提示窗口太小

- **根因**：`calculateHintMaxWidth` 用 `editorWidth / 3` 算宽度，1920 宽的编辑器只给到约 400px。
- **改法**：宽度改为 `min(960, max(360, editorWidth * 2 / 3))`，并加 `MIN_HINT_WIDTH = 360` / `MIN_HINT_HEIGHT = 96` 兜底，避免窄编辑器下被压成一条。

### 问题 2：滚动条失效

- **根因**：原代码用 `scrollPane.setPreferredSize(sqlTextArea.getPreferredSize())`。而 `getPreferredSize()` 在组件**入树前**调用常常算出错误尺寸，导致窗口被压扁、滚动条不出现。
- **改法**：改用 `FontMetrics` 显式计算完整内容宽高。文本区承载完整内容，滚动面板只按可视上限裁剪，超界时双向滚动。
- **顺带清理**：删掉因此变为死代码的 `createSqlHtml` / `calculateContentWidth`，以及 `StringUtil`、`JLabel`、`SwingConstants`、`Color` 等死导入。

另确认：「未定义」提示的源码本来就有（`showHint` 内 `targets.isEmpty() ? "未定义" : ...`），用户感受到的问题 1 导致看不清，放大窗口后随之消失，未改该逻辑。

### 问题 3：鼠标一动就消失、点不到复制按钮

- **根因**：`handleMouseMoved` 一旦指针离开 sqlId 字面量就立刻 `hideHint`。
- **第 1 轮改法**：加 700ms 宽限期（`HIDE_DELAY_MS`），并给 popup content 加 `MouseListener`：进入提示框则保持、离开则延时隐藏。`HoverState` 用单一 `Alarm` 互斥调度 `scheduleShow` / `scheduleHide` / `cancelAll`。

### 问题 3 的反复（第 2、3 轮）—— 这段是本案最有价值的教训

**第 2 轮**：发现第 1 轮的 `popup.getContent().addMouseListener` 不可靠 —— 在 Swing 中指针从面板移到它的**子组件**（SQL 文本区 / 复制按钮）会触发父面板的 `mouseExited`，于是"明明在提示框内"也被误判为离开。
改法：删掉 content 级 `MouseListener`，改用**全局 AWT 事件监听**（`Toolkit.addAWTEventListener` 监听 `MOUSE_MOVED` / `MOUSE_PRESSED`），在监听里按屏幕坐标判定指针是否落在「当前 sqlId 字面量矩形」或「提示框矩形（外扩 16px）」内；宽限期调整为 1000ms。

**第 3 轮**：上一轮之后**什么都不弹了**。
根因：全局监听里每次 `MOUSE_MOVED` 都调 `p.getContent().getLocationOnScreen()`，而提示框刚 `show()`、组件尚未完成布局时该调用抛 `IllegalComponentStateException` —— 异常在 EDT 抛出，直接打断显示流程。
改法：**弃用全局 AWT 监听**，改为**递归给提示框整棵组件树装上同一个 `MouseAdapter`**（`installKeepAlive` + `addKeepAliveRecursively`）：
- `mouseEntered` → 取消隐藏
- `mouseExited` → **仅当**指针真正移出整个提示框边界（`content.getLocationOnScreen()` 后 `grow(8, 8)` 判定）才 `scheduleHide`

同时回退了 `HoverState` 的 `keepAliveListener` / `disposeKeepAlive` 字段与 `popup.addListener(onClosed)`，保留 `setCancelOnMouseOutCallback(event -> false)`。

> 关键结论：**不要把"鼠标是否还在提示框上"这个问题交给父子组件的 enter/exit 事件**（会被子组件干扰），也不要交给全局 AWT 监听（组件未布局时会抛异常）。正解是给整棵组件树装同一个监听器，用屏幕边界做判定。

## 验证方式

```bash
./gradlew compileJava --offline --no-daemon
```

期望输出：`BUILD SUCCESSFUL`。
（中途出现过一次失败，原因是 `.gradle` 锁文件被占用，属环境问题，`./gradlew --stop` 后复现通过。）

注意：只验证了编译。**三类交互行为（窗口尺寸、滚动、悬停保持）没有自动化验证，也没有记录人工冒烟结果** —— 这正是本案改动丢失后无法确认程度的根本原因。

## 结果（重要）

- 改动在**工作区完成并编译通过**，但没有提交。
- 提交 `9d004ce`（2026-08-31 15:09）里的 `SqlToySqlIdHoverHintProvider.java` 仍是**修复前版本**：仍含第 1 轮已删除的 `createSqlHtml` / `calculateContentWidth`，且没有 `HIDE_DELAY_MS`、没有 `installKeepAlive`。
- 本工作日志写于 2026-08-31 23:38，即**在提交之后**。
- 切换分支时工作区改动被丢弃，`git fsck --unreachable` 查无悬空对象 → **无法用 git 恢复，需要重做**。

## 教训（写进约定的部分）

1. **过程要立刻落盘。** 这份回溯文档能救回"怎么改"，但救不回"改完的代码"。原文写的是「长任务每一轮结束就提交（哪怕 `wip:` 前缀），不要攒着」。
   - ⚠️ **2026-10-15 修订**：这条结论已被取代。本仓库现在规定**不自动提交**（见 `docs/conventions.md` 第 12 节），所以「靠提交保命」不能作为默认动作。防止丢改动的手段改为：**改动一出就在工作区落盘、并把当轮结论写进对应的计划文档**（`docs/plans/active/` 那份的「当前进度与下次续接」），提交与否交由用户决定。真正被这次事故证明的不是「要勤提交」，而是「**不要把唯一一份结论只留在脑子里或临时文件里**」—— 提交只是当年恰好可选的一种落盘方式。
2. **只验证编译不算验证。** 三条改法全是交互行为，编译通过说明不了任何事。后续应把可验证的部分（如宽度计算、边界判定）抽成不依赖 UI 的纯函数并加测试。
3. **切分支前先看清工作区。** `git status` 有改动时不要直接 `checkout`。

## 归档记录

- 归档时间：2026-10-15
- 归档位置：`docs/plans/completed/`
- 最终结果：三处交互问题的改法都已确定、并在工作区验证过编译，**但代码没提交就丢了**，无法用 Git 恢复。这份文档保住了「为什么这么改」的推理过程 —— 重做时可以照抄方案，不必重新排查一遍。
- 遗留事项：代码需要重做。重做时必须补上三类交互行为的验证方式（本次连人工冒烟结果都没有留下记录，这正是丢失后无法判断完成度的原因）。前置功能位于分支 `v20260831-optimization`（提交 `9d004ce`），**该分支尚未合并进 `develop`**。
- 改名记录：2026-10-15 由 `2026-08-31-hover-hint-fix.md` 改为 `2026-08-31-悬浮提示三处问题修复-hover-hint-fix.md`（计划文件命名规则见 `docs/plans/README.md`），用 `git mv` 保留重命名历史。同日修订了「教训」第 1 条 —— 它原本建议「每一轮结束就提交」，与新增的提交纪律冲突，已改为「落盘 + 写进计划文档」。
