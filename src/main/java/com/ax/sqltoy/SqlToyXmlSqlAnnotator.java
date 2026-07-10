package com.ax.sqltoy;

import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.markup.EffectType;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.project.DumbService;
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
import java.util.List;
import java.util.Objects;

/**
 * 为 SqlToy SQL 定义添加 XML 侧标注。
 *
 * @author ax
 * @date 2026-05-30
 */
public final class SqlToyXmlSqlAnnotator implements Annotator {

    /**
     * 用于把 XML 文本分类为 SqlToy SQL 的共享高亮器。
     */
    private static final SqlToySqlSyntaxHighlighter HIGHLIGHTER = new SqlToySqlSyntaxHighlighter();

    private static final Color FUNCTION_COLOR = new Color(86, 156, 214);
    private static final Color PARAMETER_COLOR = new Color(220, 220, 120);
    private static final Color INCLUDE_SQL_ID_COLOR = new Color(104, 168, 113);
    private static final Color UNUSED_SQL_ID_COLOR = new Color(128, 128, 128);

    private final SqlToySqlIncludeParser includeParser = new SqlToySqlIncludeParser();
    private final SqlToySqlIncludeResolver includeResolver = new SqlToySqlIncludeResolver();

    /**
     * 为 SqlToy SQL 标签内的 XML 文本节点添加 SQL 词法高亮。
     *
     * @param element 当前正在标注的 XML PSI 元素
     * @param holder  用于添加文本属性的标注容器
     */
    @Override
    public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
        // 同一个 annotator 同时负责 sqlId 未引用提示和 SQL 正文语法高亮。
        annotateDuplicateXmlSqlId(element, holder);
        annotateUnusedXmlSqlId(element, holder);
        annotateIncludedXmlSqlId(element, holder);

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

        Lexer lexer = HIGHLIGHTER.getHighlightingLexer();
        lexer.start(text, sqlTextRange.getStartOffset(), sqlTextRange.getEndOffset(), 0);

        while (Objects.nonNull(lexer.getTokenType())) {
            highlightToken(xmlText, holder, lexer);
            lexer.advance();
        }

        annotateSqlIncludes(xmlText, holder);
    }

    /**
     * 将当前 XML 文件内重复的 XML SQL id 标记为错误。
     *
     * @param element 当前正在标注的 XML PSI 元素
     * @param holder  用于添加错误标注的容器
     */
    private void annotateDuplicateXmlSqlId(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
        if (!(element instanceof XmlToken xmlToken) || xmlToken.getTokenType() != XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN) {
            return;
        }

        if (!(xmlToken.getParent() instanceof XmlAttributeValue valueElement)) {
            return;
        }

        var tag = SqlToySqlIdXmlResolver.getSqlTagForIdValue(valueElement);
        if (Objects.isNull(tag) || !SqlToySqlIdXmlResolver.hasDuplicateSqlIdInFile(tag)) {
            return;
        }

        String sqlId = SqlToySqlIdXmlResolver.getSqlId(tag);
        holder.newAnnotation(HighlightSeverity.ERROR, "Duplicate sqlId in this XML file: " + sqlId)
                .range(element.getTextRange())
                .create();
    }

    /**
     * 将没有被 Java 代码引用的 XML SQL id 置灰。
     *
     * @param element 当前正在标注的 XML PSI 元素
     * @param holder  用于添加文本属性的标注容器
     */
    private void annotateUnusedXmlSqlId(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
        if (!(element instanceof XmlToken xmlToken) || xmlToken.getTokenType() != XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN) {
            return;
        }

        if (!(xmlToken.getParent() instanceof XmlAttributeValue valueElement)) {
            return;
        }

        var tag = SqlToySqlIdXmlResolver.getSqlTagForIdValue(valueElement);
        if (Objects.isNull(tag)) {
            return;
        }

        String sqlId = SqlToySqlIdXmlResolver.getSqlId(tag);
        if (Objects.isNull(sqlId) || !SqlToySqlIdXmlResolver.maybeSqlId(sqlId)) {
            return;
        }

        // 反向查找 Java 引用依赖索引，Dumb Mode 中不提示未使用，避免误报。
        if (DumbService.isDumb(element.getProject())) {
            return;
        }

        if (!SqlToyJavaSqlIdResolver.findLiteralTargets(element.getProject(), sqlId).isEmpty()
                || !includeResolver.findIncludeTargets(element.getProject(), sqlId).isEmpty()) {
            return;
        }

        holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                .range(element.getTextRange())
                .enforcedTextAttributes(createForegroundAttributes(UNUSED_SQL_ID_COLOR))
                .create();
    }

    /**
     * 为单个词法单元应用颜色标注。
     *
     * @param xmlText 拥有该词法单元的 XML 文本宿主
     * @param holder  用于添加高亮的标注容器
     * @param lexer   已定位到待高亮词法单元的词法分析器
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

        TextAttributesKey[] attributes = HIGHLIGHTER.getTokenHighlights(tokenType);
        if (attributes.length == 0) {
            return;
        }

        TextRange tokenRange = TextRange
                .create(lexer.getTokenStart(), lexer.getTokenEnd())
                .shiftRight(xmlText.getTextRange().getStartOffset());

        var builder = holder.newSilentAnnotation(HighlightSeverity.INFORMATION).range(tokenRange);
        if (tokenType == SqlToySqlTokenTypes.FUNCTION) {
            // IDEA 的默认函数颜色在 XML 注解里不一定稳定，这里强制函数前景色。
            builder.enforcedTextAttributes(createForegroundAttributes(FUNCTION_COLOR));
        } else if (tokenType == SqlToySqlTokenTypes.PARAMETER) {
            // SqlToy 命名参数用单独颜色突出显示，方便和普通标识符区分。
            builder.enforcedTextAttributes(createForegroundAttributes(PARAMETER_COLOR));
        } else {
            builder.textAttributes(attributes[0]);
        }
        builder.create();
    }

    /**
     * 为 @include("sqlId") 中的 sqlId 文本增加独立颜色标识。
     *
     * @param xmlText XML SQL 文本节点
     * @param holder  用于添加高亮的标注容器
     */
    private void annotateSqlIncludes(@NotNull XmlText xmlText, @NotNull AnnotationHolder holder) {
        for (SqlToySqlInclude include : includeParser.findIncludes(xmlText)) {
            TextRange includeRange = include.sqlIdRangeInElement()
                    .shiftRight(xmlText.getTextRange().getStartOffset());

            List<PsiElement> targets = SqlToySqlIdXmlResolver.findTargets(xmlText.getProject(), include.sqlId())
                    .stream()
                    .map(SqlToySqlIdXmlResolver.SqlIdTarget::element)
                    .toList();

            var builder = holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                    .range(includeRange)
                    .enforcedTextAttributes(createIncludeSqlIdAttributes());
            if (!targets.isEmpty()) {
                builder.gutterIconRenderer(new SqlToyNavigationGutterIconRenderer(
                        SqlToyIcons.JUMP_MARKER,
                        "Navigate to XML SqlToy SQL: " + include.sqlId(),
                        include.sqlId(),
                        targets
                ));
            }
            builder.create();
        }
    }

    /**
     * 为被 XML include 引用的 XML SQL id 添加同款颜色、下划线和跳转图标。
     *
     * @param element 当前正在标注的 XML PSI 元素
     * @param holder  用于添加高亮的标注容器
     */
    private void annotateIncludedXmlSqlId(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
        if (!(element instanceof XmlToken xmlToken) || xmlToken.getTokenType() != XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN) {
            return;
        }

        if (!(xmlToken.getParent() instanceof XmlAttributeValue valueElement)) {
            return;
        }

        var tag = SqlToySqlIdXmlResolver.getSqlTagForIdValue(valueElement);
        if (Objects.isNull(tag)) {
            return;
        }

        String sqlId = SqlToySqlIdXmlResolver.getSqlId(tag);
        if (Objects.isNull(sqlId) || !SqlToySqlIdXmlResolver.maybeSqlId(sqlId)) {
            return;
        }

        List<SqlToyNavigationTarget> targets = includeResolver.findIncludeNavigationTargets(element.getProject(), sqlId);
        if (targets.isEmpty()) {
            return;
        }

        holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                .range(element.getTextRange())
                .gutterIconRenderer(new SqlToyNavigationGutterIconRenderer(
                        SqlToyIcons.JUMP_MARKER,
                        "Navigate to XML SqlToy include: " + sqlId,
                        targets
                ))
                .create();
    }

    /**
     * 构建 include sqlId 使用的颜色和下划线标识。
     *
     * @return include sqlId 文本属性
     */
    private static TextAttributes createIncludeSqlIdAttributes() {
        TextAttributes attributes = createForegroundAttributes(INCLUDE_SQL_ID_COLOR);
        attributes.setEffectColor(INCLUDE_SQL_ID_COLOR);
        attributes.setEffectType(EffectType.LINE_UNDERSCORE);
        return attributes;
    }

    /**
     * 构建只设置前景色的文本属性。
     *
     * @param color 前景色
     * @return 仅配置前景色的文本属性
     */
    private static TextAttributes createForegroundAttributes(@NotNull Color color) {
        TextAttributes attributes = new TextAttributes();
        attributes.setForegroundColor(color);
        return attributes;
    }
}
