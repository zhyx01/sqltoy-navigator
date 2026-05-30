package com.ax.sqltoy;

import com.intellij.extapi.psi.PsiFileBase;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.psi.FileViewProvider;
import org.jetbrains.annotations.NotNull;

final class SqlToySqlFile extends PsiFileBase {

    SqlToySqlFile(@NotNull FileViewProvider viewProvider) {
        super(viewProvider, SqlToySqlLanguage.INSTANCE);
    }

    @Override
    public @NotNull FileType getFileType() {
        return SqlToySqlFileType.INSTANCE;
    }

    @Override
    public String toString() {
        return "SqlToy SQL File";
    }
}
