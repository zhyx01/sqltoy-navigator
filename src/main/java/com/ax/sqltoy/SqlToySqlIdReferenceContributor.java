package com.ax.sqltoy;

import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceContributor;
import com.intellij.psi.PsiReferenceProvider;
import com.intellij.psi.PsiReferenceRegistrar;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;

/**
 * Registers references on Java string literals.
 * <p>
 * Example:
 * multiLightDao.findByMap(..., "trace_param_fault_moduleId", ...);
 * <p>
 * Ctrl + click on "trace_param_fault_moduleId" can jump to:
 * <sql id="trace_param_fault_moduleId">...</sql>
 *
 * @author ax
 * @date 2026-05-30
 */
public final class SqlToySqlIdReferenceContributor extends PsiReferenceContributor {

    /**
     * Registers a reference provider for Java string literals.
     *
     * @param registrar IntelliJ reference registrar
     */
    @Override
    public void registerReferenceProviders(@NotNull PsiReferenceRegistrar registrar) {
        registrar.registerReferenceProvider(
                PlatformPatterns.psiElement(PsiLiteralExpression.class),
                new PsiReferenceProvider() {
                    /**
                     * Creates sqlId references for eligible Java literal expressions.
                     *
                     * @param element Java PSI element matched by the pattern
                     * @param context processing context from IntelliJ
                     * @return references for the element, or an empty array
                     */
                    @Override
                    public PsiReference @NotNull [] getReferencesByElement(
                            @NotNull PsiElement element,
                            @NotNull ProcessingContext context
                    ) {
                        PsiLiteralExpression literalExpression = (PsiLiteralExpression) element;
                        Object value = literalExpression.getValue();

                        // Only Java string literals can be SqlToy sqlId references.
                        if (!(value instanceof String sqlId)) {
                            return PsiReference.EMPTY_ARRAY;
                        }

                        // Ignore ordinary strings that cannot be SqlToy sqlId values.
                        if (!SqlToySqlIdXmlResolver.maybeSqlId(sqlId)) {
                            return PsiReference.EMPTY_ARRAY;
                        }

                        return new PsiReference[]{
                                new SqlToySqlIdReference(literalExpression, sqlId)
                        };
                    }
                }
        );
    }
}
