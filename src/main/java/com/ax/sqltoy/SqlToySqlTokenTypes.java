package com.ax.sqltoy;

import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * Token type registry for the lightweight SqlToy SQL lexer.
 *
 * @author ax
 * @date 2026-05-30
 */
final class SqlToySqlTokenTypes {

    // SQL semantic tokens used by the syntax highlighter.
    static final IElementType KEYWORD = new SqlToySqlTokenType("KEYWORD");
    static final IElementType IDENTIFIER = new SqlToySqlTokenType("IDENTIFIER");
    static final IElementType FUNCTION = new SqlToySqlTokenType("FUNCTION");
    static final IElementType TABLE = new SqlToySqlTokenType("TABLE");
    static final IElementType ALIAS = new SqlToySqlTokenType("ALIAS");
    static final IElementType PARAMETER = new SqlToySqlTokenType("PARAMETER");
    static final IElementType STRING = new SqlToySqlTokenType("STRING");
    static final IElementType NUMBER = new SqlToySqlTokenType("NUMBER");
    static final IElementType LINE_COMMENT = new SqlToySqlTokenType("LINE_COMMENT");
    static final IElementType BLOCK_COMMENT = new SqlToySqlTokenType("BLOCK_COMMENT");
    static final IElementType OPERATOR = new SqlToySqlTokenType("OPERATOR");
    static final IElementType PUNCTUATION = new SqlToySqlTokenType("PUNCTUATION");

    // Bracket tokens are kept colorless so Rainbow Brackets can own their colors.
    static final IElementType LPAREN = new SqlToySqlTokenType("LPAREN");
    static final IElementType RPAREN = new SqlToySqlTokenType("RPAREN");
    static final IElementType LBRACKET = new SqlToySqlTokenType("LBRACKET");
    static final IElementType RBRACKET = new SqlToySqlTokenType("RBRACKET");
    static final IElementType LBRACE = new SqlToySqlTokenType("LBRACE");
    static final IElementType RBRACE = new SqlToySqlTokenType("RBRACE");

    /**
     * Utility class; instances are not needed.
     */
    private SqlToySqlTokenTypes() {
    }

    /**
     * Token type bound to the SqlToy SQL language instance.
     *
     * @author ax
     * @date 2026-05-30
     */
    private static final class SqlToySqlTokenType extends IElementType {
        /**
         * Creates a token type with a readable debug name.
         *
         * @param debugName token debug name displayed in PSI diagnostics
         */
        private SqlToySqlTokenType(@NotNull @NonNls String debugName) {
            super(debugName, SqlToySqlLanguage.INSTANCE);
        }
    }
}
