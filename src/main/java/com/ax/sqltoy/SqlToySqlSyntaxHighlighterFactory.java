package com.ax.sqltoy;

import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Factory that supplies syntax highlighters for SqlToy SQL fragments.
 *
 * @author ax
 * @date 2026-05-30
 */
public final class SqlToySqlSyntaxHighlighterFactory extends SyntaxHighlighterFactory {

    /**
     * Creates a syntax highlighter for an editor context.
     *
     * @param project current project, if available
     * @param virtualFile current virtual file, if available
     * @return new SqlToy SQL syntax highlighter
     */
    @Override
    public @NotNull SyntaxHighlighter getSyntaxHighlighter(
            @Nullable Project project,
            @Nullable VirtualFile virtualFile
    ) {
        return new SqlToySqlSyntaxHighlighter();
    }
}
