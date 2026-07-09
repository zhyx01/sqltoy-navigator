package com.ax.sqltoy;

import com.intellij.openapi.util.TextRange;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceContributor;
import com.intellij.psi.PsiReferenceProvider;
import com.intellij.psi.PsiReferenceRegistrar;
import com.intellij.psi.xml.XmlText;
import com.intellij.psi.xml.XmlToken;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * 在 XML SQL 文本中的 @include("sqlId") 上注册引用。
 *
 * @author ax
 * @date 2026-07-09
 */
public final class SqlToySqlIncludeReferenceContributor extends PsiReferenceContributor {

    private final SqlToySqlIncludeParser includeParser = new SqlToySqlIncludeParser();

    /**
     * 为 XML 文本叶子节点注册 include 引用提供器。
     *
     * @param registrar IntelliJ 引用注册器
     */
    @Override
    public void registerReferenceProviders(@NotNull PsiReferenceRegistrar registrar) {
        registrar.registerReferenceProvider(
                PlatformPatterns.psiElement(XmlToken.class),
                new PsiReferenceProvider() {
                    /**
                     * 为 XML SQL 文本中的 include 参数创建引用。
                     *
                     * @param element 被匹配到的 XML 文本叶子节点
                     * @param context IntelliJ 处理上下文
                     * @return include 参数引用数组
                     */
                    @Override
                    public PsiReference @NotNull [] getReferencesByElement(
                            @NotNull PsiElement element,
                            @NotNull ProcessingContext context
                    ) {
                        return createTokenReferences(element);
                    }
                }
        );
    }

    /**
     * 为当前 XML token 创建 include 引用。
     *
     * @param tokenElement 当前 XML token
     * @return 当前 token 覆盖到的 include 引用
     */
    private PsiReference @NotNull [] createTokenReferences(@NotNull PsiElement tokenElement) {
        XmlText xmlText = findXmlTextParent(tokenElement);
        if (xmlText == null) {
            return PsiReference.EMPTY_ARRAY;
        }

        int tokenStartInXmlText = tokenElement.getTextRange().getStartOffset()
                - xmlText.getTextRange().getStartOffset();
        int tokenEndInXmlText = tokenElement.getTextRange().getEndOffset()
                - xmlText.getTextRange().getStartOffset();

        List<PsiReference> result = new ArrayList<>();
        for (SqlToySqlInclude include : includeParser.findIncludes(xmlText)) {
            TextRange sqlIdRange = include.sqlIdRangeInElement();
            if (sqlIdRange.getStartOffset() < tokenStartInXmlText
                    || sqlIdRange.getEndOffset() > tokenEndInXmlText) {
                continue;
            }

            result.add(new SqlToySqlIncludeReference(
                    tokenElement,
                    include.sqlId(),
                    sqlIdRange.shiftLeft(tokenStartInXmlText)
            ));
        }

        return result.toArray(PsiReference[]::new);
    }

    /**
     * 查找当前 XML token 所属的 XML 文本节点。
     *
     * @param element 当前 PSI 元素
     * @return XML 文本节点；不在 XML 文本中时返回 null
     */
    private @Nullable XmlText findXmlTextParent(@NotNull PsiElement element) {
        PsiElement current = element;
        while (current != null) {
            if (current instanceof XmlText xmlText) {
                return xmlText;
            }

            current = current.getParent();
        }

        return null;
    }
}
