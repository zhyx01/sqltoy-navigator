package com.ax.sqltoy;

import com.intellij.extapi.psi.PsiFileBase;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.psi.FileViewProvider;
import org.jetbrains.annotations.NotNull;

/**
 * 注入式 SqlToy SQL 片段的 PSI 文件包装器。
 *
 * @author ax
 * @date 2026-05-30
 */
final class SqlToySqlFile extends PsiFileBase {

    /**
     * 创建绑定到 SqlToy SQL 语言的 PSI 文件。
     *
     * @param viewProvider IntelliJ 提供的文件视图提供器
     */
    SqlToySqlFile(@NotNull FileViewProvider viewProvider) {
        super(viewProvider, SqlToySqlLanguage.INSTANCE);
    }

    /**
     * 返回嵌入式 SQL 片段的合成文件类型。
     *
     * @return SqlToy SQL 文件类型
     */
    @Override
    public @NotNull FileType getFileType() {
        return SqlToySqlFileType.INSTANCE;
    }

    /**
     * 返回便于调试的 PSI 文件名称。
     *
     * @return PSI 文件描述
     */
    @Override
    public String toString() {
        return "SqlToy SQL File";
    }
}
