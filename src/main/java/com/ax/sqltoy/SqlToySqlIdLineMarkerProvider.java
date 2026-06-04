package com.ax.sqltoy;

import com.intellij.codeInsight.daemon.RelatedItemLineMarkerInfo;
import com.intellij.codeInsight.daemon.RelatedItemLineMarkerProvider;
import com.intellij.codeInsight.navigation.NavigationGutterIconBuilder;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlToken;
import com.intellij.psi.xml.XmlTokenType;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;

/**
 * 在 Java sqlId 字面量和 XML SQL 定义之间添加边栏导航标记。
 *
 * @author ax
 * @date 2026-05-30
 */
public final class SqlToySqlIdLineMarkerProvider extends RelatedItemLineMarkerProvider {

    /**
     * 为 Java 和 XML PSI 元素收集导航标记。
     *
     * @param element 当前正在访问的 PSI 元素
     * @param result 用于追加标记的集合
     */
    @Override
    protected void collectNavigationMarkers(
            @NotNull PsiElement element,
            @NotNull Collection<? super RelatedItemLineMarkerInfo<?>> result
    ) {
        // 两个方向共用一个提供器，因为图标和解析器相同。
        collectJavaSqlIdMarker(element, result);
        collectXmlSqlMarker(element, result);
    }

    /**
     * 添加从 Java sqlId 字面量跳转到 XML SQL 定义的边栏标记。
     *
     * @param element Java PSI 元素
     * @param result 用于追加标记的集合
     */
    private void collectJavaSqlIdMarker(
            @NotNull PsiElement element,
            @NotNull Collection<? super RelatedItemLineMarkerInfo<?>> result
    ) {
        // 只在字符串内容对应的 Java token 上放图标，避免一个字面量生成多个边栏标记。
        PsiLiteralExpression literalExpression = SqlToyJavaSqlIdResolver.getSqlIdLiteral(element);
        if (literalExpression == null) {
            return;
        }

        String sqlId = SqlToyJavaSqlIdResolver.getSqlId(literalExpression);
        if (sqlId == null) {
            return;
        }

        // 重复的 XML 定义都会作为导航目标暴露。
        List<PsiElement> targets = SqlToySqlIdXmlResolver.findTargets(element.getProject(), sqlId)
                .stream()
                .map(SqlToySqlIdXmlResolver.SqlIdTarget::element)
                .toList();

        if (targets.isEmpty()) {
            return;
        }

        // 目标列表非空时才创建图标，避免普通字符串被误显示为可导航 sqlId。
        NavigationGutterIconBuilder<PsiElement> builder = NavigationGutterIconBuilder
                .create(SqlToyIcons.SQL_MARKER)
                .setTargets(targets)
                .setTooltipText("Navigate to SqlToy SQL: " + sqlId);

        result.add(builder.createLineMarkerInfo(element));
    }

    /**
     * 添加从 XML sqlId 定义跳转到 Java 使用处的边栏标记。
     *
     * @param element XML PSI 元素
     * @param result 用于追加标记的集合
     */
    private void collectXmlSqlMarker(
            @NotNull PsiElement element,
            @NotNull Collection<? super RelatedItemLineMarkerInfo<?>> result
    ) {
        // XML 侧只在属性值 token 上放图标，让图标和 sqlId 文本位置对齐。
        if (!(element instanceof XmlToken xmlToken) || xmlToken.getTokenType() != XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN) {
            return;
        }

        if (!(xmlToken.getParent() instanceof XmlAttributeValue valueElement)) {
            return;
        }

        XmlTag tag = SqlToySqlIdXmlResolver.getSqlTagForIdValue(valueElement);
        if (tag == null) {
            return;
        }

        String sqlId = SqlToySqlIdXmlResolver.getSqlId(tag);
        if (sqlId == null || !SqlToySqlIdXmlResolver.maybeSqlId(sqlId)) {
            return;
        }

        Project project = element.getProject();
        List<PsiElement> targets = SqlToyJavaSqlIdResolver.findLiteralTargets(project, sqlId);
        if (targets.isEmpty()) {
            return;
        }

        // XML 到 Java 是反向导航，目标是所有引用该 sqlId 的字符串字面量。
        NavigationGutterIconBuilder<PsiElement> builder = NavigationGutterIconBuilder
                .create(SqlToyIcons.SQL_MARKER)
                .setTargets(targets)
                .setTooltipText("Navigate to Java SqlToy sqlId: " + sqlId);

        result.add(builder.createLineMarkerInfo(element));
    }
}
