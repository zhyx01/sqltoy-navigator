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
 * Injects IDEA's SQL language into XML SQL tag text when the Database Tools plugin is available.
 *
 * @author ax
 * @date 2026-05-30
 */
public final class SqlToyXmlSqlLanguageInjector implements MultiHostInjector {

    /**
     * XML text nodes are the only injection hosts this injector handles.
     */
    private static final List<Class<? extends PsiElement>> XML_TEXT_ELEMENTS = List.of(XmlText.class);

    /**
     * Injects SqlToy SQL into matching XML text ranges.
     *
     * @param registrar language injection registrar
     * @param context PSI element considered for injection
     */
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

        // Inject only the actual SQL body, excluding CDATA wrappers and surrounding whitespace.
        registrar.startInjecting(getInjectedSqlLanguage())
                .addPlace(null, null, host, textRange)
                .doneInjecting();
    }

    /**
     * Uses IDEA's SQL language when it is loaded, otherwise falls back to the plugin's lightweight SQL language.
     *
     * @return language used for XML SQL injection
     */
    private static @NotNull Language getInjectedSqlLanguage() {
        Language ideaSqlLanguage = Language.findLanguageByID("SQL");
        return ideaSqlLanguage == null ? SqlToySqlLanguage.INSTANCE : ideaSqlLanguage;
    }

    /**
     * Returns PSI element classes eligible for injection.
     *
     * @return XML text element class list
     */
    @Override
    public @NotNull List<? extends Class<? extends PsiElement>> elementsToInjectIn() {
        return XML_TEXT_ELEMENTS;
    }
}
