package com.ax.sqltoy;

import com.intellij.lang.Language;
import org.jetbrains.annotations.NotNull;

public final class SqlToySqlLanguage extends Language {

    public static final SqlToySqlLanguage INSTANCE = new SqlToySqlLanguage();

    private SqlToySqlLanguage() {
        super("SqlToySQL");
    }

    @Override
    public @NotNull String getDisplayName() {
        return "SqlToy SQL";
    }
}
