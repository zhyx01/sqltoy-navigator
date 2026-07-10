package com.ax.sqltoy;

import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.Icon;
import java.util.Objects;

/**
 * 导航候选弹窗展示项。
 *
 * @author ax
 * @date 2026-07-10
 */
final class SqlToyNavigationPopupItem {

    private final String displayText;
    private final Icon icon;
    private final VirtualFile virtualFile;
    private final String filePath;
    private final OpenFileDescriptor descriptor;

    /**
     * 根据导航目标创建弹窗展示项。
     *
     * @param target 导航目标
     */
    SqlToyNavigationPopupItem(@NotNull SqlToyNavigationTarget target) {
        this.displayText = target.getDisplayText();
        this.icon = target.getIcon();
        this.descriptor = target.createDescriptor();

        PsiFile containingFile = target.getContainingFile();
        if (Objects.isNull(containingFile)) {
            this.virtualFile = null;
            this.filePath = null;
            return;
        }

        this.virtualFile = containingFile.getVirtualFile();
        this.filePath = Objects.nonNull(virtualFile) ? virtualFile.getPath() : containingFile.getName();
    }

    /**
     * 返回弹窗展示文本。
     *
     * @return 展示文本
     */
    @NotNull String getDisplayText() {
        return displayText;
    }

    /**
     * 返回弹窗图标。
     *
     * @return 图标
     */
    @Nullable Icon getIcon() {
        return icon;
    }

    /**
     * 判断两个弹窗项是否属于同一文件。
     *
     * @param other 另一个弹窗项
     * @return 属于同一文件时返回 true
     */
    boolean isSameFile(@NotNull SqlToyNavigationPopupItem other) {
        if (Objects.nonNull(virtualFile) && Objects.nonNull(other.virtualFile)) {
            return Objects.equals(virtualFile, other.virtualFile);
        }
        if (Objects.isNull(filePath) || Objects.isNull(other.filePath)) {
            return false;
        }

        return Objects.equals(filePath, other.filePath);
    }

    /**
     * 跳转到当前弹窗项对应的位置。
     */
    void navigate() {
        if (Objects.nonNull(descriptor) && descriptor.canNavigate()) {
            descriptor.navigate(true);
        }
    }
}
