package com.ax.sqltoy;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListSeparator;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.ui.awt.RelativePoint;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.Icon;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * 导航到一组 PSI 目标；多个目标时先展示候选弹窗。
 *
 * @author ax
 * @date 2026-07-09
 */
final class SqlToyNavigationAction extends AnAction {

    private final String title;
    private final List<SqlToyNavigationTarget> targets;

    /**
     * 创建导航动作。
     *
     * @param text    动作显示文本
     * @param sqlId   当前导航关系中的 sqlId
     * @param targets 导航目标
     */
    SqlToyNavigationAction(
            @NotNull String text,
            @NotNull String sqlId,
            @NotNull List<PsiElement> targets
    ) {
        this(text, targets.stream()
                .map(target -> new SqlToyNavigationTarget(sqlId, target))
                .toList());
    }

    /**
     * 创建导航动作。
     *
     * @param text    动作显示文本
     * @param targets 导航候选目标
     */
    SqlToyNavigationAction(@NotNull String text, @NotNull List<SqlToyNavigationTarget> targets) {
        super(text);
        this.title = text;
        this.targets = List.copyOf(targets);
    }

    /**
     * 执行导航。
     *
     * @param event 当前动作事件
     */
    @Override
    public void actionPerformed(@NotNull AnActionEvent event) {
        List<SqlToyNavigationTarget> navigableTargets = getNavigableTargets();
        if (navigableTargets.isEmpty()) {
            return;
        }

        List<SqlToyNavigationTarget> orderedTargets = sortTargets(navigableTargets, getCurrentFile(event));
        if (orderedTargets.size() == 1) {
            orderedTargets.get(0).navigate();
            return;
        }

        showTargetPopup(event, orderedTargets);
    }

    /**
     * 根据是否存在可导航目标更新动作状态。
     *
     * @param event 当前动作事件
     */
    @Override
    public void update(@NotNull AnActionEvent event) {
        event.getPresentation().setEnabled(!getNavigableTargets().isEmpty());
    }

    /**
     * 返回可导航的候选目标。
     *
     * @return 可导航目标列表
     */
    private List<SqlToyNavigationTarget> getNavigableTargets() {
        return targets.stream()
                .filter(SqlToyNavigationTarget::canNavigate)
                .toList();
    }

    /**
     * 展示多目标候选弹窗。
     *
     * @param event   当前动作事件
     * @param targets 可导航候选目标
     */
    private void showTargetPopup(
            @NotNull AnActionEvent event,
            @NotNull List<SqlToyNavigationTarget> targets
    ) {
        List<SqlToyNavigationPopupItem> popupItems = createPopupItems(targets);
        ListPopup popup = JBPopupFactory.getInstance().createListPopup(
                new BaseListPopupStep<>(title, popupItems) {
                    @Override
                    public @NotNull String getTextFor(SqlToyNavigationPopupItem value) {
                        return value.getDisplayText();
                    }

                    @Override
                    public Icon getIconFor(SqlToyNavigationPopupItem value) {
                        return value.getIcon();
                    }

                    @Override
                    public ListSeparator getSeparatorAbove(SqlToyNavigationPopupItem value) {
                        int index = popupItems.indexOf(value);
                        if (index <= 0) {
                            return null;
                        }

                        SqlToyNavigationPopupItem previous = popupItems.get(index - 1);
                        return previous.isSameFile(value) ? null : new ListSeparator("");
                    }

                    @Override
                    public PopupStep<?> onChosen(SqlToyNavigationPopupItem selectedValue, boolean finalChoice) {
                        return doFinalStep(selectedValue::navigate);
                    }
                }
        );

        InputEvent inputEvent = event.getInputEvent();
        if (inputEvent instanceof MouseEvent mouseEvent) {
            // gutter 图标点击时，弹窗应贴近实际点击的双向箭头位置。
            popup.show(new RelativePoint(mouseEvent));
            return;
        }

        popup.showInBestPositionFor(event.getDataContext());
    }

    /**
     * 在读操作中预先生成弹窗展示项，避免 Swing 渲染阶段读取 PSI。
     *
     * @param targets 可导航候选目标
     * @return 弹窗展示项
     */
    @NotNull
    private List<SqlToyNavigationPopupItem> createPopupItems(@NotNull List<SqlToyNavigationTarget> targets) {
        return ApplicationManager.getApplication().runReadAction(
                (Computable<List<SqlToyNavigationPopupItem>>) () -> targets.stream()
                        .map(SqlToyNavigationPopupItem::new)
                        .toList()
        );
    }

    /**
     * 对候选目标排序：当前文件优先，同一文件内按行号升序。
     *
     * @param targets     原始候选目标
     * @param currentFile 当前编辑器文件
     * @return 排序后的候选目标
     */
    private List<SqlToyNavigationTarget> sortTargets(
            @NotNull List<SqlToyNavigationTarget> targets,
            @Nullable PsiFile currentFile
    ) {
        return targets.stream()
                .sorted(Comparator
                        .comparingInt((SqlToyNavigationTarget target) -> isSameFile(target.getContainingFile(), currentFile) ? 0 : 1)
                        .thenComparing(target -> getFilePath(target.getContainingFile()))
                        .thenComparingInt(SqlToyNavigationTarget::getLineNumber))
                .toList();
    }

    /**
     * 从动作上下文中读取当前文件。
     *
     * @param event 当前动作事件
     * @return 当前 PSI 文件；无法获取时返回 null
     */
    @Nullable
    private PsiFile getCurrentFile(@NotNull AnActionEvent event) {
        return event.getData(CommonDataKeys.PSI_FILE);
    }

    /**
     * 判断两个 PSI 文件是否指向同一文件。
     *
     * @param first  第一个 PSI 文件
     * @param second 第二个 PSI 文件
     * @return 指向同一文件时返回 true
     */
    private boolean isSameFile(@Nullable PsiFile first, @Nullable PsiFile second) {
        if (Objects.isNull(first) || Objects.isNull(second)) {
            return false;
        }

        VirtualFile firstFile = first.getVirtualFile();
        VirtualFile secondFile = second.getVirtualFile();
        if (Objects.nonNull(firstFile) && Objects.nonNull(secondFile)) {
            return Objects.equals(firstFile, secondFile);
        }

        return Objects.equals(first, second);
    }

    /**
     * 返回 PSI 文件路径，用于跨文件候选排序。
     *
     * @param file PSI 文件
     * @return 文件路径；无法获取时返回空字符串
     */
    @NotNull
    private String getFilePath(@Nullable PsiFile file) {
        if (Objects.isNull(file)) {
            return "";
        }

        VirtualFile virtualFile = file.getVirtualFile();
        return Objects.nonNull(virtualFile) ? virtualFile.getPath() : file.getName();
    }
}
