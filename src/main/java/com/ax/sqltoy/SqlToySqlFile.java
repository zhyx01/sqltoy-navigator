package com.ax.sqltoy;

import com.intellij.extapi.psi.PsiFileBase;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.psi.FileViewProvider;
import org.jetbrains.annotations.NotNull;

/**
 * PSI file wrapper for injected SqlToy SQL fragments.
 *
 * @author ax
 * @date 2026-05-30
 */
final class SqlToySqlFile extends PsiFileBase {

    /**
     * Creates a PSI file bound to the SqlToy SQL language.
     *
     * @param viewProvider file view provider supplied by IntelliJ
     */
    SqlToySqlFile(@NotNull FileViewProvider viewProvider) {
        super(viewProvider, SqlToySqlLanguage.INSTANCE);
    }

    /**
     * Returns the synthetic file type for embedded SQL fragments.
     *
     * @return SqlToy SQL file type
     */
    @Override
    public @NotNull FileType getFileType() {
        return SqlToySqlFileType.INSTANCE;
    }

    /**
     * Returns a debug-friendly PSI file name.
     *
     * @return PSI file description
     */
    @Override
    public String toString() {
        return "SqlToy SQL File";
    }
}
