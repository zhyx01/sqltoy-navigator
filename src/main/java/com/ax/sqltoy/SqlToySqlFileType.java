package com.ax.sqltoy;

import com.intellij.openapi.fileTypes.LanguageFileType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.Icon;

final class SqlToySqlFileType extends LanguageFileType {

    static final SqlToySqlFileType INSTANCE = new SqlToySqlFileType();

    private SqlToySqlFileType() {
        super(SqlToySqlLanguage.INSTANCE);
    }

    @Override
    public @NotNull String getName() {
        return "SqlToy SQL";
    }

    @Override
    public @NotNull String getDescription() {
        return "SqlToy embedded SQL";
    }

    @Override
    public @NotNull String getDefaultExtension() {
        return "sqltoy-sql";
    }

    @Override
    public @Nullable Icon getIcon() {
        return SqlToyIcons.SQL_MARKER;
    }
}
