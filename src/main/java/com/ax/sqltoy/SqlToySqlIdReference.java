package com.ax.sqltoy;

import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.psi.PsiElementResolveResult;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.PsiPolyVariantReferenceBase;
import com.intellij.psi.ResolveResult;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Reference from a Java sqlId string literal to matching SqlToy XML SQL definitions.
 *
 * @author ax
 * @date 2026-05-30
 */
public final class SqlToySqlIdReference extends PsiPolyVariantReferenceBase<PsiLiteralExpression> {

    /**
     * sqlId value represented by this Java literal reference.
     */
    private final String sqlId;

    /**
     * Creates a reference for one Java string literal.
     *
     * @param element Java string literal expression
     * @param sqlId sqlId value extracted from the literal
     */
    public SqlToySqlIdReference(@NotNull PsiLiteralExpression element, @NotNull String sqlId) {
        super(element, SqlToyJavaSqlIdResolver.getStringContentRange(element), true);
        this.sqlId = sqlId;
    }

    /**
     * If duplicate sqlId definitions exist, IDEA will show a target chooser.
     *
     * @param incompleteCode true when resolving during incomplete code editing
     * @return all matching XML definition targets
     */
    @Override
    public ResolveResult @NotNull [] multiResolve(boolean incompleteCode) {
        List<SqlToySqlIdXmlResolver.SqlIdTarget> targets =
                SqlToySqlIdXmlResolver.findTargets(getElement().getProject(), sqlId);

        return targets.stream()
                // Wrap every XML target as an IntelliJ resolve result.
                .map(target -> new PsiElementResolveResult(target.element()))
                .toArray(ResolveResult[]::new);
    }

    /**
     * Basic completion variants.
     * When editing a string literal, IDEA may suggest existing sqlId values.
     *
     * @return sqlId lookup variants from XML definitions
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
