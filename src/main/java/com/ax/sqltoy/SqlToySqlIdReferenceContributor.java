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
 * 在 Java 字符串字面量上注册引用。
 * <p>
 * 示例：
 * multiLightDao.findByMap(..., "trace_param_fault_moduleId", ...);
 * <p>
 * 按 Ctrl 并点击 "trace_param_fault_moduleId" 可跳转到：
 * <sql id="trace_param_fault_moduleId">...</sql>
 *
 * @author ax
 * @date 2026-05-30
 */
public final class SqlToySqlIdReferenceContributor extends PsiReferenceContributor {

    /**
     * 为 Java 字符串字面量注册引用提供器。
     *
     * @param registrar IntelliJ 引用注册器
     */
    @Override
    public void registerReferenceProviders(@NotNull PsiReferenceRegistrar registrar) {
        // 先注册到所有 Java 字符串字面量，再在 provider 内用 sqlId 规则过滤候选。
        registrar.registerReferenceProvider(
                PlatformPatterns.psiElement(PsiLiteralExpression.class),
                new PsiReferenceProvider() {
                    /**
                     * 为符合条件的 Java 字面量表达式创建 sqlId 引用。
                     *
                     * @param element 被模式匹配到的 Java PSI 元素
                     * @param context IntelliJ 传入的处理上下文
                     * @return 该元素的引用；没有引用时返回空数组
                     */
                    @Override
                    public PsiReference @NotNull [] getReferencesByElement(
                            @NotNull PsiElement element,
                            @NotNull ProcessingContext context
                    ) {
                        PsiLiteralExpression literalExpression = (PsiLiteralExpression) element;
                        Object value = literalExpression.getValue();

                        // 只有 Java 字符串字面量才能作为 SqlToy sqlId 引用。
                        if (!(value instanceof String sqlId)) {
                            return PsiReference.EMPTY_ARRAY;
                        }

                        // 忽略不可能是 SqlToy sqlId 的普通字符串。
                        if (!SqlToySqlIdXmlResolver.maybeSqlId(sqlId)) {
                            return PsiReference.EMPTY_ARRAY;
                        }

                        // 返回引用后，IDEA 才能提供 Ctrl+Click、Find Usages 和补全联动。
                        return new PsiReference[]{
                                new SqlToySqlIdReference(literalExpression, sqlId)
                        };
                    }
                }
        );
    }
}
