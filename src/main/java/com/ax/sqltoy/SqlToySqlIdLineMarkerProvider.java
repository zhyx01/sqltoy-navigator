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

public final class SqlToySqlIdLineMarkerProvider extends RelatedItemLineMarkerProvider {

    @Override
    protected void collectNavigationMarkers(
            @NotNull PsiElement element,
            @NotNull Collection<? super RelatedItemLineMarkerInfo<?>> result
    ) {
        collectJavaSqlIdMarker(element, result);
        collectXmlSqlMarker(element, result);
    }

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
