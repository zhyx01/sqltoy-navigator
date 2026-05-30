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
 * Adds gutter navigation markers between Java sqlId literals and XML SQL definitions.
 *
 * @author ax
 * @date 2026-05-30
 */
public final class SqlToySqlIdLineMarkerProvider extends RelatedItemLineMarkerProvider {

    /**
     * Collects navigation markers for both Java and XML PSI elements.
     *
     * @param element PSI element currently being visited
     * @param result marker collection to append to
     */
    @Override
    protected void collectNavigationMarkers(
            @NotNull PsiElement element,
            @NotNull Collection<? super RelatedItemLineMarkerInfo<?>> result
    ) {
        // Both directions are handled by one provider because the icon and resolver are shared.
        collectJavaSqlIdMarker(element, result);
        collectXmlSqlMarker(element, result);
    }

    /**
     * Adds a gutter marker from a Java sqlId literal to XML SQL definitions.
     *
     * @param element Java PSI element
     * @param result marker collection to append to
     */
    private static void collectJavaSqlIdMarker(
            @NotNull PsiElement element,
            @NotNull Collection<? super RelatedItemLineMarkerInfo<?>> result
    ) {
        PsiLiteralExpression literalExpression = SqlToyJavaSqlIdResolver.getSqlIdLiteral(element);
        if (literalExpression == null) {
            return;
        }

        String sqlId = SqlToyJavaSqlIdResolver.getSqlId(literalExpression);
        if (sqlId == null) {
            return;
        }

        // Duplicate XML definitions are all exposed as navigation targets.
        List<PsiElement> targets = SqlToySqlIdXmlResolver.findTargets(element.getProject(), sqlId)
                .stream()
                .map(SqlToySqlIdXmlResolver.SqlIdTarget::element)
                .toList();

        if (targets.isEmpty()) {
            return;
        }

        NavigationGutterIconBuilder<PsiElement> builder = NavigationGutterIconBuilder
                .create(SqlToyIcons.SQL_MARKER)
                .setTargets(targets)
                .setTooltipText("Navigate to SqlToy SQL: " + sqlId);

        result.add(builder.createLineMarkerInfo(element));
    }

    /**
     * Adds a gutter marker from an XML sqlId definition to Java usages.
     *
     * @param element XML PSI element
     * @param result marker collection to append to
     */
    private static void collectXmlSqlMarker(
            @NotNull PsiElement element,
            @NotNull Collection<? super RelatedItemLineMarkerInfo<?>> result
    ) {
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
        String tooltipText = "SqlToy SQL definition: " + sqlId;

        // When no Java usage exists, keep the marker on the XML definition itself.
        if (targets.isEmpty()) {
            targets = List.of(valueElement);
        } else {
            tooltipText = "Navigate to Java SqlToy sqlId: " + sqlId;
        }

        NavigationGutterIconBuilder<PsiElement> builder = NavigationGutterIconBuilder
                .create(SqlToyIcons.SQL_MARKER)
                .setTargets(targets)
                .setTooltipText(tooltipText);

        result.add(builder.createLineMarkerInfo(element));
    }
}
