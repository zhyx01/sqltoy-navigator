package com.ax.sqltoy;

import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementResolveResult;
import com.intellij.psi.PsiPolyVariantReferenceBase;
import com.intellij.psi.ResolveResult;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * 从 XML SQL 文本中的 @include("sqlId") 指向匹配的 SqlToy XML SQL 定义。
 *
 * @author ax
 * @date 2026-07-09
 */
final class SqlToySqlIncludeReference extends PsiPolyVariantReferenceBase<PsiElement> {

    /**
     * include 引用的目标 sqlId。
     */
    private final String sqlId;

    /**
     * 创建 XML include sqlId 引用。
     *
     * @param element XML SQL 文本叶子节点
     * @param sqlId include 中的 sqlId
     * @param rangeInElement sqlId 在 XML 文本叶子节点中的相对范围
     */
    SqlToySqlIncludeReference(
            @NotNull PsiElement element,
            @NotNull String sqlId,
            @NotNull TextRange rangeInElement
    ) {
        super(element, rangeInElement, true);
        this.sqlId = sqlId;
    }

    /**
     * 解析 include 指向的 XML SQL 定义。
     *
     * @param incompleteCode 是否处于未完成代码解析场景
     * @return 所有匹配的 XML SQL 定义
     */
    @Override
    public ResolveResult @NotNull [] multiResolve(boolean incompleteCode) {
        List<SqlToySqlIdXmlResolver.SqlIdTarget> targets =
                SqlToySqlIdXmlResolver.findTargets(getElement().getProject(), sqlId);

        return targets.stream()
                .map(target -> new PsiElementResolveResult(target.element()))
                .toArray(ResolveResult[]::new);
    }

    /**
     * 为 include 参数提供项目内 XML sqlId 补全候选。
     *
     * @return XML SQL 定义中的 sqlId 候选
     */
    @Override
    public Object @NotNull [] getVariants() {
        return SqlToySqlIdXmlResolver.findAllTargets(getElement().getProject())
                .stream()
                .map(target -> LookupElementBuilder
                        .create(target.sqlId())
                        .withTypeText(target.fileName(), true))
                .toArray(Object[]::new);
    }
}
