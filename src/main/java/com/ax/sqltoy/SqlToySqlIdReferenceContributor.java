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
 *
 * Example:
 *     multiLightDao.findByMap(..., "trace_param_fault_moduleId", ...);
 *
 * Ctrl + click on "trace_param_fault_moduleId" can jump to:
 *     <sql id="trace_param_fault_moduleId">...</sql>
 */
public final class SqlToySqlIdReferenceContributor extends PsiReferenceContributor {

    @Override
    public void registerReferenceProviders(@NotNull PsiReferenceRegistrar registrar) {
        registrar.registerReferenceProvider(
                PlatformPatterns.psiElement(PsiLiteralExpression.class),
                new PsiReferenceProvider() {
                    @Override
                    public PsiReference @NotNull [] getReferencesByElement(
                            @NotNull PsiElement element,
                            @NotNull ProcessingContext context
                    ) {
                        PsiLiteralExpression literalExpression = (PsiLiteralExpression) element;
                        Object value = literalExpression.getValue();

                        if (!(value instanceof String sqlId)) {
                            return PsiReference.EMPTY_ARRAY;
                        }

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
