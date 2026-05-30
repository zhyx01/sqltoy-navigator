package com.ax.sqltoy;

import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.psi.PsiElementResolveResult;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.PsiPolyVariantReferenceBase;
import com.intellij.psi.ResolveResult;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Reference implementation for one Java string literal.
 */
public final class SqlToySqlIdReference extends PsiPolyVariantReferenceBase<PsiLiteralExpression> {

    private final String sqlId;

    public SqlToySqlIdReference(@NotNull PsiLiteralExpression element, @NotNull String sqlId) {
        super(element, SqlToyJavaSqlIdResolver.getStringContentRange(element), true);
        this.sqlId = sqlId;
    }

    /**
     * If duplicate sqlId definitions exist, IDEA will show a target chooser.
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
     * Basic completion variants.
     * When editing a string literal, IDEA may suggest existing sqlId values.
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
