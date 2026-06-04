package com.ax.sqltoy;

import com.intellij.lang.Language;
import com.intellij.lang.injection.MultiHostInjector;
import com.intellij.lang.injection.MultiHostRegistrar;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLanguageInjectionHost;
import com.intellij.psi.xml.XmlText;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * 在 Database Tools 插件可用时，将 IDEA 的 SQL 语言注入 XML SQL 标签文本。
 *
 * @author ax
 * @date 2026-05-30
 */
public final class SqlToyXmlSqlLanguageInjector implements MultiHostInjector {

    /**
     * XML 文本节点是此注入器唯一处理的注入宿主。
     */
    private static final List<Class<? extends PsiElement>> XML_TEXT_ELEMENTS = List.of(XmlText.class);

    /**
     * 将 SqlToy SQL 注入匹配的 XML 文本范围。
     *
     * @param registrar 语言注入注册器
     * @param context 正在考虑注入的 PSI 元素
     */
    @Override
    public void getLanguagesToInject(
            @NotNull MultiHostRegistrar registrar,
            @NotNull PsiElement context
    ) {
        if (!(context instanceof XmlText xmlText) || !(context instanceof PsiLanguageInjectionHost host)) {
            // IntelliJ 只允许向语言注入宿主添加注入片段，其他 PSI 元素直接跳过。
            return;
        }

        if (SqlToyXmlSqlTextRanges.getSqlToySqlTag(xmlText) == null) {
            // 只处理 <sql id="..."> 内部文本，避免影响普通 XML 文本节点。
            return;
        }

        TextRange textRange = SqlToyXmlSqlTextRanges.getSqlTextRange(xmlText.getText());
        if (textRange.isEmpty()) {
            return;
        }

        // 只注入实际 SQL 主体，排除 CDATA 包裹和周围空白。
        registrar.startInjecting(getInjectedSqlLanguage())
                .addPlace(null, null, host, textRange)
                .doneInjecting();
    }

    /**
     * IDEA 的 SQL 语言已加载时使用它，否则回退到插件的轻量级 SQL 语言。
     *
     * @return 用于 XML SQL 注入的语言
     */
    private @NotNull Language getInjectedSqlLanguage() {
        Language ideaSqlLanguage = Language.findLanguageByID("SQL");
        // Database Tools 不可用时，仍使用插件自带的轻量语言保证基础高亮。
        return ideaSqlLanguage == null ? SqlToySqlLanguage.INSTANCE : ideaSqlLanguage;
    }

    /**
     * 返回可参与注入的 PSI 元素类。
     *
     * @return XML 文本元素类列表
     */
    @Override
    public @NotNull List<? extends Class<? extends PsiElement>> elementsToInjectIn() {
        return XML_TEXT_ELEMENTS;
    }
}
