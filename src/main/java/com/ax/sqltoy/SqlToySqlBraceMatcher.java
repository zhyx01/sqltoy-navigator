package com.ax.sqltoy;

import com.intellij.lang.BracePair;
import com.intellij.lang.PairedBraceMatcher;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 提供嵌入式 SqlToy SQL 语言的成对括号元数据。
 *
 * @author ax
 * @date 2026-05-30
 */
public final class SqlToySqlBraceMatcher implements PairedBraceMatcher {

    /**
     * 支持的 SQL 片段括号配对。
     */
    private static final BracePair[] PAIRS = new BracePair[]{
            new BracePair(SqlToySqlTokenTypes.LPAREN, SqlToySqlTokenTypes.RPAREN, false),
            new BracePair(SqlToySqlTokenTypes.LBRACKET, SqlToySqlTokenTypes.RBRACKET, false),
            new BracePair(SqlToySqlTokenTypes.LBRACE, SqlToySqlTokenTypes.RBRACE, false)
    };

    /**
     * 返回 SqlToy SQL 已知的全部括号配对。
     *
     * @return 支持的括号配对
     */
    @Override
    public BracePair @NotNull [] getPairs() {
        return PAIRS;
    }

    /**
     * 允许左括号出现在任意词法单元之前，使未完成 SQL 仍可编辑。
     *
     * @param lbraceType 左括号词法单元类型
     * @param contextType 左括号后的词法单元类型，可能为空
     * @return 对轻量嵌入式 SQL 始终返回 true
     */
    @Override
    public boolean isPairedBracesAllowedBeforeType(
            @NotNull IElementType lbraceType,
            @Nullable IElementType contextType
    ) {
        return true;
    }

    /**
     * 使用左括号自身作为代码结构起点。
     *
     * @param file 拥有注入片段的 PSI 文件
     * @param openingBraceOffset 左括号偏移量
     * @return 左括号偏移量
     */
    @Override
    public int getCodeConstructStart(@NotNull PsiFile file, int openingBraceOffset) {
        return openingBraceOffset;
    }
}
