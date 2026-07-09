package com.ax.sqltoy;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlText;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.Icon;
import java.util.Objects;

/**
 * SqlToy 导航候选目标。
 *
 * @author ax
 * @date 2026-07-09
 */
final class SqlToyNavigationTarget {

    private final String fallbackSqlId;
    private final PsiElement element;
    private final int navigationOffset;

    /**
     * 创建导航候选目标。
     *
     * @param sqlId   当前导航关系中的 sqlId
     * @param element 目标 PSI 元素
     */
    SqlToyNavigationTarget(@NotNull String sqlId, @NotNull PsiElement element) {
        this(sqlId, element, -1);
    }

    /**
     * 创建导航候选目标。
     *
     * @param sqlId            当前导航关系中的 sqlId
     * @param element          目标 PSI 元素
     * @param navigationOffset 精确导航偏移量；小于 0 时使用 PSI 元素起点
     */
    SqlToyNavigationTarget(
            @NotNull String sqlId,
            @NotNull PsiElement element,
            int navigationOffset
    ) {
        this.fallbackSqlId = sqlId;
        this.element = element;
        this.navigationOffset = navigationOffset;
    }

    /**
     * 返回候选弹窗中展示的文本。
     *
     * @return sqlId 和目标行号
     */
    @NotNull String getDisplayText() {
        String displaySqlId = getDisplaySqlId();
        int lineNumber = getLineNumber();
        if (lineNumber < 1) {
            return displaySqlId + " : line -";
        }

        return displaySqlId + " : line " + lineNumber;
    }

    /**
     * 返回目标文件图标。
     *
     * @return 文件类型图标；无法获取文件时返回 null
     */
    @Nullable Icon getIcon() {
        PsiFile containingFile = getContainingFile();
        return containingFile != null ? containingFile.getFileType().getIcon() : null;
    }

    /**
     * 判断当前候选是否可导航。
     *
     * @return 可导航时返回 true
     */
    boolean canNavigate() {
        OpenFileDescriptor descriptor = createDescriptor();
        return descriptor != null && descriptor.canNavigate();
    }

    /**
     * 跳转到当前候选目标。
     */
    void navigate() {
        OpenFileDescriptor descriptor = createDescriptor();
        if (descriptor != null && descriptor.canNavigate()) {
            descriptor.navigate(true);
        }
    }

    /**
     * 返回目标 PSI 元素。
     *
     * @return 目标 PSI 元素
     */
    @NotNull PsiElement getElement() {
        return element;
    }

    /**
     * 判断两个导航候选是否等价。
     *
     * @param object 待比较对象
     * @return sqlId 和 PSI 元素都一致时返回 true
     */
    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof SqlToyNavigationTarget target)) {
            return false;
        }

        return Objects.equals(fallbackSqlId, target.fallbackSqlId)
                && Objects.equals(element, target.element)
                && navigationOffset == target.navigationOffset;
    }

    /**
     * 返回导航候选哈希值。
     *
     * @return 哈希值
     */
    @Override
    public int hashCode() {
        return Objects.hash(fallbackSqlId, element, navigationOffset);
    }

    /**
     * 返回候选弹窗中应展示的 sqlId。
     *
     * @return 引用方 sqlId；无法识别引用方时返回兜底 sqlId
     */
    @NotNull
    private String getDisplaySqlId() {
        String xmlOwnerSqlId = getContainingXmlSqlId();
        if (Objects.nonNull(xmlOwnerSqlId)) {
            return xmlOwnerSqlId;
        }

        String javaLiteralSqlId = getJavaLiteralSqlId();
        if (Objects.nonNull(javaLiteralSqlId)) {
            return javaLiteralSqlId;
        }

        return fallbackSqlId;
    }

    /**
     * 解析 XML 引用位置所属的 SQL 定义 id。
     *
     * @return 引用方 SQL id；目标不在 XML SQL 文本内时返回 null
     */
    @Nullable
    private String getContainingXmlSqlId() {
        XmlText xmlText = findParentXmlText();
        if (Objects.isNull(xmlText)) {
            return null;
        }

        XmlTag sqlTag = SqlToyXmlSqlTextRanges.getSqlToySqlTag(xmlText);
        if (Objects.isNull(sqlTag)) {
            return null;
        }

        String sqlId = SqlToySqlIdXmlResolver.getSqlId(sqlTag);
        return SqlToySqlIdXmlResolver.maybeSqlId(sqlId) ? sqlId : null;
    }

    /**
     * 解析 Java 字符串字面量自身的 sqlId。
     *
     * @return 字面量 sqlId；目标不是 Java 字面量时返回 null
     */
    @Nullable
    private String getJavaLiteralSqlId() {
        PsiElement current = element;
        while (current != null) {
            if (current instanceof PsiLiteralExpression literalExpression) {
                return SqlToyJavaSqlIdResolver.getSqlId(literalExpression);
            }

            current = current.getParent();
        }

        return null;
    }

    /**
     * 查找目标所在的 XML 文本节点。
     *
     * @return XML 文本节点；不存在时返回 null
     */
    @Nullable
    private XmlText findParentXmlText() {
        PsiElement current = element;
        while (current != null) {
            if (current instanceof XmlText xmlText) {
                return xmlText;
            }

            current = current.getParent();
        }

        return null;
    }

    /**
     * 计算目标元素所在行号。
     *
     * @return 1 基行号；无法计算时返回 -1
     */
    int getLineNumber() {
        PsiFile containingFile = getContainingFile();
        if (containingFile == null) {
            return -1;
        }

        Document document = PsiDocumentManager.getInstance(element.getProject()).getDocument(containingFile);
        if (document == null) {
            return -1;
        }

        return document.getLineNumber(getNavigationOffset()) + 1;
    }

    /**
     * 为当前候选创建文件导航描述符。
     *
     * @return 可导航文件描述符；目标无效时返回 null
     */
    @Nullable OpenFileDescriptor createDescriptor() {
        if (!element.isValid()) {
            return null;
        }

        PsiFile containingFile = getContainingFile();
        if (containingFile == null) {
            return null;
        }

        VirtualFile virtualFile = containingFile.getVirtualFile();
        if (virtualFile == null) {
            return null;
        }

        return new OpenFileDescriptor(element.getProject(), virtualFile, getNavigationOffset());
    }

    /**
     * 返回目标所在 PSI 文件。
     *
     * @return PSI 文件；无法获取时返回 null
     */
    @Nullable PsiFile getContainingFile() {
        return element.isValid() ? element.getContainingFile() : null;
    }

    /**
     * 返回实际导航使用的文件偏移量。
     *
     * @return 文件内偏移量
     */
    private int getNavigationOffset() {
        return navigationOffset >= 0 ? navigationOffset : element.getTextOffset();
    }
}
