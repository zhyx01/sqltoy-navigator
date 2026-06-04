package com.ax.sqltoy;

import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.psi.PsiElementResolveResult;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.PsiPolyVariantReferenceBase;
import com.intellij.psi.ResolveResult;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * 从 Java sqlId 字符串字面量指向匹配 SqlToy XML SQL 定义的引用。
 *
 * @author ax
 * @date 2026-05-30
 */
public final class SqlToySqlIdReference extends PsiPolyVariantReferenceBase<PsiLiteralExpression> {

    /**
     * 此 Java 字面量引用表示的 sqlId 值。
     */
    private final String sqlId;

    /**
     * 为一个 Java 字符串字面量创建引用。
     *
     * @param element Java 字符串字面量表达式
     * @param sqlId 从字面量中提取的 sqlId 值
     */
    public SqlToySqlIdReference(@NotNull PsiLiteralExpression element, @NotNull String sqlId) {
        super(element, SqlToyJavaSqlIdResolver.getStringContentRange(element), true);
        this.sqlId = sqlId;
    }

    /**
     * 如果存在重复的 sqlId 定义，IDEA 会显示目标选择器。
     *
     * @param incompleteCode 在未完成代码编辑过程中解析时为 true
     * @return 所有匹配的 XML 定义目标
     */
    @Override
    public ResolveResult @NotNull [] multiResolve(boolean incompleteCode) {
        // 解析时保持多目标结果，允许同名 XML 定义由 IDEA 弹窗让用户选择。
        List<SqlToySqlIdXmlResolver.SqlIdTarget> targets =
                SqlToySqlIdXmlResolver.findTargets(getElement().getProject(), sqlId);

        return targets.stream()
                // 将每个 XML 目标包装成 IntelliJ 解析结果。
                .map(target -> new PsiElementResolveResult(target.element()))
                .toArray(ResolveResult[]::new);
    }

    /**
     * 基础补全候选。
     * 编辑字符串字面量时，IDEA 可以提示已有的 sqlId 值。
     *
     * @return 来自 XML 定义的 sqlId 补全候选
     */
    @Override
    public Object @NotNull [] getVariants() {
        // 补全直接复用项目内 XML sqlId 定义，并在右侧显示来源文件名。
        return SqlToySqlIdXmlResolver.findAllTargets(getElement().getProject())
                .stream()
                .map(target -> LookupElementBuilder
                        .create(target.sqlId())
                        .withTypeText(target.fileName(), true))
                .toArray(Object[]::new);
    }

}
