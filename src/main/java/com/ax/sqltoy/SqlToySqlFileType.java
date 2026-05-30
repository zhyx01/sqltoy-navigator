package com.ax.sqltoy;

import com.intellij.openapi.fileTypes.LanguageFileType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.Icon;

/**
 * File type facade for the embedded SqlToy SQL language.
 *
 * @author ax
 * @date 2026-05-30
 */
final class SqlToySqlFileType extends LanguageFileType {

    /**
     * Singleton file type used by the parser definition.
     */
    static final SqlToySqlFileType INSTANCE = new SqlToySqlFileType();

    /**
     * Binds this file type to the SqlToy SQL language.
     */
    private SqlToySqlFileType() {
        super(SqlToySqlLanguage.INSTANCE);
    }

    /**
     * Returns the file type name.
     *
     * @return file type name
     */
    @Override
    public @NotNull String getName() {
        return "SqlToy SQL";
    }

    /**
     * Returns a short description for UI surfaces.
     *
     * @return file type description
     */
    @Override
    public @NotNull String getDescription() {
        return "SqlToy embedded SQL";
    }

    /**
     * Returns a synthetic extension for embedded SQL fragments.
     *
     * @return default extension
     */
    @Override
    public @NotNull String getDefaultExtension() {
        return "sqltoy-sql";
    }

    /**
     * Returns the icon used for SqlToy SQL fragments.
     *
     * @return SQL marker icon
     */
    @Override
    public @Nullable Icon getIcon() {
        return SqlToyIcons.SQL_MARKER;
    }
}
