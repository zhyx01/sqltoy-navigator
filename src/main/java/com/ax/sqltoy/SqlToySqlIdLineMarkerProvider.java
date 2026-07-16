package com.ax.sqltoy;

import com.intellij.codeInsight.daemon.RelatedItemLineMarkerInfo;
import com.intellij.codeInsight.daemon.RelatedItemLineMarkerProvider;
import com.intellij.codeInsight.navigation.NavigationGutterIconBuilder;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlToken;
import com.intellij.psi.xml.XmlTokenType;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

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
        collectJavaSqlIdConstantMarker(element, result);
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
        if (Objects.isNull(literalExpression)) {
            return;
        }

        String sqlId = SqlToyJavaSqlIdResolver.getSqlId(literalExpression);
        if (Objects.isNull(sqlId)) {
            return;
        }

        // 重复的 XML 定义都会作为导航目标暴露。
        List<PsiElement> targets = SqlToySqlIdXmlResolver.findDialectTargets(element.getProject(), sqlId)
                .stream()
                .map(SqlToySqlIdXmlResolver.SqlIdTarget::element)
                .toList();

        if (targets.isEmpty()) {
            return;
        }

        // 目标列表非空时才创建图标，避免普通字符串被误显示为可导航 sqlId。
        NavigationGutterIconBuilder<PsiElement> builder = createSqlNavigationBuilder(targets, sqlId);

        result.add(builder.createLineMarkerInfo(element));
    }

    /**
     * 添加从 Java sqlId 常量参数跳转到 XML SQL 定义的边栏标记。
     *
     * @param element Java PSI 元素
     * @param result 用于追加标记的集合
     */
    private void collectJavaSqlIdConstantMarker(
            @NotNull PsiElement element,
            @NotNull Collection<? super RelatedItemLineMarkerInfo<?>> result
    ) {
        PsiReferenceExpression constantExpression = SqlToyJavaSqlIdResolver.getSqlIdConstantArgument(element);
        if (Objects.isNull(constantExpression)) {
            return;
        }

        String sqlId = SqlToyJavaSqlIdResolver.getSqlIdFromConstantArgument(constantExpression);
        if (Objects.isNull(sqlId)) {
            return;
        }

        List<PsiElement> targets = SqlToySqlIdXmlResolver.findDialectTargets(element.getProject(), sqlId)
                .stream()
                .map(SqlToySqlIdXmlResolver.SqlIdTarget::element)
                .toList();

        if (targets.isEmpty()) {
            return;
        }

        NavigationGutterIconBuilder<PsiElement> builder = createSqlNavigationBuilder(targets, sqlId);
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
        if (Objects.isNull(tag)) {
            return;
        }

        String sqlId = SqlToySqlIdXmlResolver.getSqlId(tag);
        if (Objects.isNull(sqlId) || !SqlToySqlIdXmlResolver.maybeSqlId(sqlId)) {
            return;
        }

        Project project = element.getProject();
        List<PsiElement> targets = SqlToyJavaSqlIdResolver.findDialectLiteralTargets(project, sqlId);
        if (targets.isEmpty()) {
            return;
        }

        // XML 到 Java 是反向导航，目标是所有引用该 sqlId 的字符串字面量。
        NavigationGutterIconBuilder<PsiElement> builder = NavigationGutterIconBuilder
                .create(SqlToyIcons.JAVA_MARKER)
                .setTargets(targets)
                .setNamer(target -> createNavigationTargetName(sqlId, target))
                .setTooltipText("Navigate to Java SqlToy sqlId: " + sqlId);

        result.add(builder.createLineMarkerInfo(element));
    }

    /**
     * 创建从 Java sqlId 跳转到 XML SQL 定义的边栏图标构造器。
     *
     * @param targets XML SQL 定义目标
     * @param sqlId SqlToy sqlId
     * @return 边栏图标构造器
     */
    private NavigationGutterIconBuilder<PsiElement> createSqlNavigationBuilder(
            @NotNull List<PsiElement> targets,
            @NotNull String sqlId
    ) {
        return NavigationGutterIconBuilder
                .create(SqlToyIcons.SQL_MARKER)
                .setTargets(targets)
                .setNamer(target -> createNavigationTargetName(sqlId, target))
                .setTooltipText("Navigate to SqlToy SQL: " + sqlId);
    }

    /**
     * 创建多目标弹窗中的候选文案。
     *
     * @param sqlId  当前导航关系中的 sqlId
     * @param target 候选 PSI 目标
     * @return sqlId 和目标行号
     */
    private String createNavigationTargetName(@NotNull String sqlId, PsiElement target) {
        if (Objects.isNull(target)) {
            return sqlId + " : line -";
        }

        return new SqlToyNavigationTarget(sqlId, target).getDisplayText();
    }

}
