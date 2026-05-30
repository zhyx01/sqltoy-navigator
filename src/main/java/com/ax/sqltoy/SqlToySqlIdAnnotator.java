package com.ax.sqltoy;

import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.editor.markup.EffectType;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.ui.JBColor;
import org.jetbrains.annotations.NotNull;

import java.awt.Color;

public final class SqlToySqlIdAnnotator implements Annotator {

    private static final Color UNDERLINE_COLOR = new JBColor(
            new Color(46, 160, 67),
            new Color(46, 160, 67)
    );

    private static final TextAttributes SQL_ID_UNDERLINE = createUnderlineAttributes();

    @Override
    public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
        PsiLiteralExpression literalExpression = SqlToyJavaSqlIdResolver.getSqlIdLiteral(element);
        if (literalExpression == null) {
            return;
        }

        String sqlId = SqlToyJavaSqlIdResolver.getSqlId(literalExpression);
        if (sqlId == null || SqlToySqlIdXmlResolver.findTargets(element.getProject(), sqlId).isEmpty()) {
            return;
        }

        holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                .range(SqlToyJavaSqlIdResolver.getStringContentTextRange(literalExpression))
                .enforcedTextAttributes(SQL_ID_UNDERLINE)
                .create();
    }

    private static TextAttributes createUnderlineAttributes() {
        TextAttributes attributes = new TextAttributes();
        attributes.setEffectColor(UNDERLINE_COLOR);
        attributes.setEffectType(EffectType.LINE_UNDERSCORE);
        return attributes;
    }
}
