package com.ax.sqltoy;

import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.editor.markup.EffectType;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLiteralExpression;
import org.jetbrains.annotations.NotNull;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.util.List;

import javax.swing.JLabel;

/**
 * 为能解析到 XML 定义的 Java sqlId 字符串添加可视下划线。
 *
 * @author ax
 * @date 2026-05-30
 */
public final class SqlToySqlIdAnnotator implements Annotator {

    private static final Color UNDERLINE_COLOR = new Color(104, 168, 113);
    private static final Color UNRESOLVED_SQL_ID_COLOR = new Color(128, 128, 128);
    private static final int TOOLTIP_MAX_WIDTH = 960;
    private static final int TOOLTIP_PADDING = 4;
    private static final String TOOLTIP_MAX_HEIGHT = "60vh";

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
            holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                    .range(SqlToyJavaSqlIdResolver.getStringContentTextRange(literalExpression))
                    .enforcedTextAttributes(createForegroundAttributes(UNRESOLVED_SQL_ID_COLOR))
                    .create();
            return;
        }

        var builder = holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                .range(SqlToyJavaSqlIdResolver.getStringContentTextRange(literalExpression))
                .enforcedTextAttributes(createUnderlineAttributes());
        builder = builder.tooltip(createSqlTooltip(targets.get(0).sqlText()));
        builder.create();
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

    /**
     * 创建用于悬浮提示的 SQL 纯文本内容。
     *
     * @param sqlText SQL 纯文本
     * @return IDEA tooltip HTML
     */
    private String createSqlTooltip(@NotNull String sqlText) {
        int tooltipWidth = calculateTooltipWidth(sqlText);
        return "<html><body style='margin:0;'>"
                + "<div style='"
                + "width:" + tooltipWidth + "px;"
                + "max-height:" + TOOLTIP_MAX_HEIGHT + ";"
                + "overflow-y:auto;"
                + "overflow-x:auto;"
                + "box-shadow:none;"
                + "'>"
                + "<pre style='margin:0;white-space:pre;box-shadow:none;'>"
                + StringUtil.escapeXmlEntities(sqlText).replace(" ", "&nbsp;")
                + "&nbsp;"
                + "</pre>"
                + "</div>"
                + "</body></html>";
    }

    private int calculateTooltipWidth(@NotNull String sqlText) {
        FontMetrics fontMetrics = new JLabel().getFontMetrics(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        int contentWidth = 0;
        String[] lines = sqlText.split("\\R", -1);
        for (String line : lines) {
            contentWidth = Math.max(contentWidth, fontMetrics.stringWidth(line.replace("\t", "    ")));
        }
        return Math.min(TOOLTIP_MAX_WIDTH, contentWidth + TOOLTIP_PADDING);
    }
}
