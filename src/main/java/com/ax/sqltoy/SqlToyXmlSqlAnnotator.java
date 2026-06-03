package com.ax.sqltoy;

import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.TokenType;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlText;
import com.intellij.psi.xml.XmlToken;
import com.intellij.psi.xml.XmlTokenType;
import org.jetbrains.annotations.NotNull;

import java.awt.Color;

/**
 * Directly highlights SQL text inside SqlToy XML tags.
 *
 * @author ax
 * @date 2026-05-30
 */
public final class SqlToyXmlSqlAnnotator implements Annotator {

    /**
     * Shared highlighter used to classify XML text as SqlToy SQL.
     */
    private static final SqlToySqlSyntaxHighlighter HIGHLIGHTER = new SqlToySqlSyntaxHighlighter();

    // 函数
    private static final Color FUNCTION_COLOR = new Color(86, 156, 214);
    // 参数
    private static final Color PARAMETER_COLOR = new Color(220, 220, 120);
    private static final Color UNUSED_SQL_ID_COLOR = new Color(128, 128, 128);

    /**
     * Adds SQL token highlighting to XML text nodes inside SqlToy SQL tags.
     *
     * @param element XML PSI element currently being annotated
     * @param holder annotation holder used to add text attributes
     */
    @Override
    public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
        annotateUnusedXmlSqlId(element, holder);

        if (!(element instanceof XmlText xmlText)) {
            return;
        }

        if (SqlToyXmlSqlTextRanges.getSqlToySqlTag(xmlText) == null) {
            return;
        }

        String text = xmlText.getText();
        TextRange sqlTextRange = SqlToyXmlSqlTextRanges.getSqlTextRange(text);
        if (sqlTextRange.isEmpty()) {
            return;
        }

        // Run the same lexer as the injected language highlighter for consistent token classes.
        Lexer lexer = HIGHLIGHTER.getHighlightingLexer();
        lexer.start(text, sqlTextRange.getStartOffset(), sqlTextRange.getEndOffset(), 0);

        while (lexer.getTokenType() != null) {
            highlightToken(xmlText, holder, lexer);
            lexer.advance();
        }
    }

    /**
     * Grays out XML SQL ids that are not referenced from Java code.
     *
     * @param element XML PSI element currently being annotated
     * @param holder annotation holder used to add text attributes
     */
    private void annotateUnusedXmlSqlId(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
        if (!(element instanceof XmlToken xmlToken) || xmlToken.getTokenType() != XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN) {
            return;
        }

        if (!(xmlToken.getParent() instanceof XmlAttributeValue valueElement)) {
            return;
        }

        var tag = SqlToySqlIdXmlResolver.getSqlTagForIdValue(valueElement);
        if (tag == null) {
            return;
        }

        String sqlId = SqlToySqlIdXmlResolver.getSqlId(tag);
        if (sqlId == null || !SqlToySqlIdXmlResolver.maybeSqlId(sqlId)) {
            return;
        }

        if (!SqlToyJavaSqlIdResolver.findLiteralTargets(element.getProject(), sqlId).isEmpty()) {
            return;
        }

        holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                .range(element.getTextRange())
                .enforcedTextAttributes(createForegroundAttributes(UNUSED_SQL_ID_COLOR))
                .create();
    }

    /**
     * Applies a color annotation for a single lexer token.
     *
     * @param xmlText XML text host that owns the token
     * @param holder annotation holder used to add highlighting
     * @param lexer lexer positioned on the token to highlight
     */
    private static void highlightToken(
            @NotNull XmlText xmlText,
            @NotNull AnnotationHolder holder,
            @NotNull Lexer lexer
    ) {
        IElementType tokenType = lexer.getTokenType();
        if (tokenType == TokenType.WHITE_SPACE) {
            return;
        }

        // Empty attributes mean the plugin intentionally leaves the token uncolored.
        TextAttributesKey[] attributes = HIGHLIGHTER.getTokenHighlights(tokenType);
        if (attributes.length == 0) {
            return;
        }

        // Convert token offsets from XML-text-relative to file-level offsets.
        TextRange tokenRange = TextRange
                .create(lexer.getTokenStart(), lexer.getTokenEnd())
                .shiftRight(xmlText.getTextRange().getStartOffset());

        var builder = holder.newSilentAnnotation(HighlightSeverity.INFORMATION).range(tokenRange);
        // Enforced attributes are used only for colors the plugin must keep stable across themes.
        if (tokenType == SqlToySqlTokenTypes.FUNCTION) {
            builder.enforcedTextAttributes(createForegroundAttributes(FUNCTION_COLOR));
        }
        // else if (tokenType == SqlToySqlTokenTypes.ALIAS) {
        //     builder.enforcedTextAttributes(ALIAS_ATTRIBUTES);
        // }
        else if (tokenType == SqlToySqlTokenTypes.PARAMETER) {
            builder.enforcedTextAttributes(createForegroundAttributes(PARAMETER_COLOR));
        } else {
            builder.textAttributes(attributes[0]);
        }
        builder.create();
    }

    /**
     * Builds foreground-only text attributes.
     *
     * @param color foreground color
     * @return text attributes with only foreground configured
     */
    private static TextAttributes createForegroundAttributes(@NotNull Color color) {
        TextAttributes attributes = new TextAttributes();
        attributes.setForegroundColor(color);
        return attributes;
    }
}
