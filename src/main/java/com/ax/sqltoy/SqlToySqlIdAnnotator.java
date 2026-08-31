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
import java.util.List;

/**
 * 为能解析到 XML 定义的 Java sqlId 字符串添加可视下划线。
 *
 * @author ax
 * @date 2026-05-30
 */
public final class SqlToySqlIdAnnotator implements Annotator {

    private static final Color UNDERLINE_COLOR = new Color(104, 168, 113);
    private static final Color UNRESOLVED_SQL_ID_COLOR = new Color(128, 128, 128);
    // 红色波浪线颜色
    private static final Color ERROR_WAVE_COLOR = new Color(255, 0, 0);

    /**
     * 标注存在匹配 XML 目标的 Java sqlId 字面量。
     *
     * @param element 当前正在标注的 PSI 元素
     * @param holder  用于添加高亮的标注容器
     */
    @Override
    public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
        PsiLiteralExpression literalExpression = SqlToyJavaSqlIdResolver.getSqlIdLiteral(element);
        if (literalExpression == null) {
            return;
        }

        String sqlId = SqlToyJavaSqlIdResolver.getSqlId(literalExpression);
        // 不给未解析的候选值加下划线；它们可能只是普通字符串。
        if (sqlId == null) {
            return;
        }

        List<SqlToySqlIdXmlResolver.SqlIdTarget> targets = SqlToySqlIdXmlResolver.findDialectTargets(element.getProject(), sqlId);
        if (targets.isEmpty()) {
            // 匹配不到时：置灰 + 红色下波浪线；ERROR 级别让编辑器右侧滚动条出现红色条纹。
            holder.newSilentAnnotation(HighlightSeverity.ERROR)
                    .range(SqlToyJavaSqlIdResolver.getStringContentTextRange(literalExpression))
                    .enforcedTextAttributes(createErrorWaveAttributes())
                    .create();
            return;
        }

        // SQL 悬浮预览由 SqlToySqlIdHoverHintProvider 提供（带复制按钮），这里只负责下划线。
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

    /**
     * 创建红色下波浪线文本属性（用于未解析的 sqlId）。
     *
     * @return 波浪线文本属性
     */
    private TextAttributes createErrorWaveAttributes() {
        TextAttributes attributes = new TextAttributes();
        // 保留灰色前景色，实现“置灰”效果
        attributes.setForegroundColor(UNRESOLVED_SQL_ID_COLOR);
        // 设置红色波浪线效果
        attributes.setEffectColor(ERROR_WAVE_COLOR);
        attributes.setEffectType(EffectType.WAVE_UNDERSCORE);
        return attributes;
    }

    /**
     * 创建仅包含前景色的文本属性。
     *
     * @param color 前景色
     * @return 文本属性
     */
    private TextAttributes createForegroundAttributes(@NotNull Color color) {
        TextAttributes attributes = new TextAttributes();
        attributes.setForegroundColor(color);
        return attributes;
    }
}