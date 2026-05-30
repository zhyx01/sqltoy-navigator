package com.ax.sqltoy;

import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

final class SqlToySqlTokenTypes {

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

    private SqlToySqlTokenTypes() {
    }

    private static final class SqlToySqlTokenType extends IElementType {
        private SqlToySqlTokenType(@NotNull @NonNls String debugName) {
            super(debugName, SqlToySqlLanguage.INSTANCE);
        }
    }
}
