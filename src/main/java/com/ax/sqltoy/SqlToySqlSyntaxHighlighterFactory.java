package com.ax.sqltoy;

import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 为 SqlToy SQL 片段提供语法高亮器的工厂。
 *
 * @author ax
 * @date 2026-05-30
 */
public final class SqlToySqlSyntaxHighlighterFactory extends SyntaxHighlighterFactory {

    /**
     * 为编辑器上下文创建语法高亮器。
     *
     * @param project 当前项目，可能为空
     * @param virtualFile 当前虚拟文件，可能为空
     * @return 新的 SqlToy SQL 语法高亮器
     */
    @Override
    public @NotNull SyntaxHighlighter getSyntaxHighlighter(
            @Nullable Project project,
            @Nullable VirtualFile virtualFile
    ) {
        return new SqlToySqlSyntaxHighlighter();
    }
}
