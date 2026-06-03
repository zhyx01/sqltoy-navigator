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
 * Adds a visual underline to Java sqlId strings that resolve to XML definitions.
 *
 * @author ax
 * @date 2026-05-30
 */
public final class SqlToySqlIdAnnotator implements Annotator {

    private static final Color UNDERLINE_COLOR = new Color(104, 168, 113);

    /**
     * Annotates Java sqlId literals that have matching XML targets.
     *
     * @param element PSI element currently being annotated
     * @param holder annotation holder used to add highlighting
     */
    @Override
    public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
        PsiLiteralExpression literalExpression = SqlToyJavaSqlIdResolver.getSqlIdLiteral(element);
        if (literalExpression == null) {
            return;
        }

        String sqlId = SqlToyJavaSqlIdResolver.getSqlId(literalExpression);
        // Do not underline unresolved candidates; they may be ordinary strings.
        if (sqlId == null || SqlToySqlIdXmlResolver.findTargets(element.getProject(), sqlId).isEmpty()) {
            return;
        }

        holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                .range(SqlToyJavaSqlIdResolver.getStringContentTextRange(literalExpression))
                .enforcedTextAttributes(createUnderlineAttributes())
                .create();
    }

    /**
     * Creates underline-only text attributes for sqlId literals.
     *
     * @return underline text attributes
     */
    private TextAttributes createUnderlineAttributes() {
        TextAttributes attributes = new TextAttributes();
        attributes.setEffectColor(UNDERLINE_COLOR);
        attributes.setEffectType(EffectType.LINE_UNDERSCORE);
        return attributes;
    }
}
