package com.ax.sqltoy;

import com.intellij.lang.injection.MultiHostInjector;
import com.intellij.lang.injection.MultiHostRegistrar;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLanguageInjectionHost;
import com.intellij.psi.xml.XmlText;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public final class SqlToyXmlSqlLanguageInjector implements MultiHostInjector {

    private static final List<Class<? extends PsiElement>> XML_TEXT_ELEMENTS = List.of(XmlText.class);

    @Override
    public void getLanguagesToInject(
            @NotNull MultiHostRegistrar registrar,
            @NotNull PsiElement context
    ) {
        if (!(context instanceof XmlText xmlText) || !(context instanceof PsiLanguageInjectionHost host)) {
            return;
        }

        if (SqlToyXmlSqlTextRanges.getSqlToySqlTag(xmlText) == null) {
            return;
        }

        TextRange textRange = SqlToyXmlSqlTextRanges.getSqlTextRange(xmlText.getText());
        if (textRange.isEmpty()) {
            return;
        }

        registrar.startInjecting(SqlToySqlLanguage.INSTANCE)
                .addPlace(null, null, host, textRange)
                .doneInjecting();
    }

    @Override
    public @NotNull List<? extends Class<? extends PsiElement>> elementsToInjectIn() {
        return XML_TEXT_ELEMENTS;
    }
}
