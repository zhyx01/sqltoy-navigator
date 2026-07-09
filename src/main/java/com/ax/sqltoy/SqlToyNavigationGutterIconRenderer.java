package com.ax.sqltoy;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

import javax.swing.Icon;
import java.util.List;
import java.util.Objects;

/**
 * 用于 SqlToy XML include 关系的 gutter 图标。
 *
 * @author ax
 * @date 2026-07-09
 */
final class SqlToyNavigationGutterIconRenderer extends GutterIconRenderer {

    private final Icon icon;
    private final String tooltip;
    private final List<SqlToyNavigationTarget> targets;
    private final AnAction clickAction;

    /**
     * 创建 gutter 图标渲染器。
     *
     * @param icon    图标
     * @param tooltip 鼠标悬浮提示
     * @param sqlId   当前导航关系中的 sqlId
     * @param targets 点击后的导航目标
     */
    SqlToyNavigationGutterIconRenderer(
            @NotNull Icon icon,
            @NotNull String tooltip,
            @NotNull String sqlId,
            @NotNull List<PsiElement> targets
    ) {
        this.icon = icon;
        this.tooltip = tooltip;
        this.targets = targets.stream()
                .map(target -> new SqlToyNavigationTarget(sqlId, target))
                .toList();
        this.clickAction = new SqlToyNavigationAction(tooltip, this.targets);
    }

    /**
     * 创建 gutter 图标渲染器。
     *
     * @param icon    图标
     * @param tooltip 鼠标悬浮提示
     * @param targets 点击后的精确导航目标
     */
    SqlToyNavigationGutterIconRenderer(
            @NotNull Icon icon,
            @NotNull String tooltip,
            @NotNull List<SqlToyNavigationTarget> targets
    ) {
        this.icon = icon;
        this.tooltip = tooltip;
        this.targets = List.copyOf(targets);
        this.clickAction = new SqlToyNavigationAction(tooltip, this.targets);
    }

    /**
     * 返回 gutter 图标。
     *
     * @return 图标
     */
    @Override
    public @NotNull Icon getIcon() {
        return icon;
    }

    /**
     * 返回鼠标悬浮提示。
     *
     * @return tooltip 文本
     */
    @Override
    public String getTooltipText() {
        return tooltip;
    }

    /**
     * 返回点击图标时执行的导航动作。
     *
     * @return 导航动作
     */
    @Override
    public AnAction getClickAction() {
        return clickAction;
    }

    /**
     * 判断两个图标渲染器是否等价。
     *
     * @param object 待比较对象
     * @return 等价时返回 true
     */
    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof SqlToyNavigationGutterIconRenderer renderer)) {
            return false;
        }
        return Objects.equals(icon, renderer.icon)
                && Objects.equals(tooltip, renderer.tooltip)
                && Objects.equals(targets, renderer.targets);
    }

    /**
     * 返回渲染器哈希值。
     *
     * @return 哈希值
     */
    @Override
    public int hashCode() {
        return Objects.hash(icon, tooltip, targets);
    }
}
