package com.ax.sqltoy;

import com.intellij.lang.BracePair;
import com.intellij.lang.PairedBraceMatcher;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Provides paired brace metadata for the embedded SqlToy SQL language.
 *
 * @author ax
 * @date 2026-05-30
 */
public final class SqlToySqlBraceMatcher implements PairedBraceMatcher {

    /**
     * Supported SQL fragment bracket pairs.
     */
    private static final BracePair[] PAIRS = new BracePair[]{
            new BracePair(SqlToySqlTokenTypes.LPAREN, SqlToySqlTokenTypes.RPAREN, false),
            new BracePair(SqlToySqlTokenTypes.LBRACKET, SqlToySqlTokenTypes.RBRACKET, false),
            new BracePair(SqlToySqlTokenTypes.LBRACE, SqlToySqlTokenTypes.RBRACE, false)
    };

    /**
     * Returns all bracket pairs known to SqlToy SQL.
     *
     * @return supported brace pairs
     */
    @Override
    public BracePair @NotNull [] getPairs() {
        return PAIRS;
    }

    /**
     * Allows opening braces before any token so incomplete SQL remains editable.
     *
     * @param lbraceType opening brace token type
     * @param contextType token type after the opening brace, if any
     * @return always true for lightweight embedded SQL
     */
    @Override
    public boolean isPairedBracesAllowedBeforeType(
            @NotNull IElementType lbraceType,
            @Nullable IElementType contextType
    ) {
        return true;
    }

    /**
     * Uses the opening brace itself as the construct start.
     *
     * @param file PSI file that owns the injected fragment
     * @param openingBraceOffset opening brace offset
     * @return opening brace offset
     */
    @Override
    public int getCodeConstructStart(@NotNull PsiFile file, int openingBraceOffset) {
        return openingBraceOffset;
    }
}
