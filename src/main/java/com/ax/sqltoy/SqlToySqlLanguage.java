package com.ax.sqltoy;

import com.intellij.lang.Language;
import org.jetbrains.annotations.NotNull;

/**
 * Defines the lightweight embedded SQL language used inside SqlToy XML tags.
 *
 * @author ax
 * @date 2026-05-30
 */
public final class SqlToySqlLanguage extends Language {

    /**
     * Singleton language instance registered in plugin.xml.
     */
    public static final SqlToySqlLanguage INSTANCE = new SqlToySqlLanguage();

    /**
     * Creates the language with a stable ID used by IntelliJ extension points.
     */
    private SqlToySqlLanguage() {
        super("SqlToySQL");
    }

    /**
     * Returns the human-readable language name shown by IntelliJ.
     *
     * @return display name for the embedded SQL language
     */
    @Override
    public @NotNull String getDisplayName() {
        return "SqlToy SQL";
    }
}
