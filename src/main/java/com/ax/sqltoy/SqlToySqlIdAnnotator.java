package com.ax.sqltoy;

import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.editor.markup.EffectType;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLiteralExpression;
import org.jetbrains.annotations.NotNull;

import java.awt.Color;

/**
 * 为能解析到 XML 定义的 Java sqlId 字符串添加可视下划线。
 *
 * @author ax
 * @date 2026-05-30
 */
public final class SqlToySqlIdAnnotator implements Annotator {

    private static final Color UNDERLINE_COLOR = new Color(104, 168, 113);

    /**
     * 标注存在匹配 XML 目标的 Java sqlId 字面量。
     *
     * @param element 当前正在标注的 PSI 元素
     * @param holder 用于添加高亮的标注容器
     */
    @Override
    public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
        PsiLiteralExpression literalExpression = SqlToyJavaSqlIdResolver.getSqlIdLiteral(element);
        if (literalExpression == null) {
            return;
        }

        String sqlId = SqlToyJavaSqlIdResolver.getSqlId(literalExpression);
        // 不给未解析的候选值加下划线；它们可能只是普通字符串。
        if (sqlId == null || SqlToySqlIdXmlResolver.findTargets(element.getProject(), sqlId).isEmpty()) {
            return;
        }

        holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                .range(SqlToyJavaSqlIdResolver.getStringContentTextRange(literalExpression))
                .enforcedTextAttributes(createUnderlineAttributes())
                .create();
    }

    /**
     * 创建只包含下划线效果的 sqlId 文本属性。
     *
     * @return 下划线文本属性
     */
    private TextAttributes createUnderlineAttributes() {
        TextAttributes attributes = new TextAttributes();
        attributes.setEffectColor(UNDERLINE_COLOR);
        attributes.setEffectType(EffectType.LINE_UNDERSCORE);
        return attributes;
    }
}
