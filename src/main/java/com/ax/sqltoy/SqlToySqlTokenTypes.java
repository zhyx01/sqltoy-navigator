package com.ax.sqltoy;

import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * 轻量级 SqlToy SQL 词法分析器的词法单元类型注册表。
 *
 * @author ax
 * @date 2026-05-30
 */
final class SqlToySqlTokenTypes {

    // 语法高亮器使用的 SQL 语义词法单元。
    static final IElementType KEYWORD = new SqlToySqlTokenType("KEYWORD");
    static final IElementType IDENTIFIER = new SqlToySqlTokenType("IDENTIFIER");
    static final IElementType FUNCTION = new SqlToySqlTokenType("FUNCTION");
    static final IElementType TABLE = new SqlToySqlTokenType("TABLE");
    static final IElementType TABLE_ALIAS = new SqlToySqlTokenType("TABLE_ALIAS");
    static final IElementType ALIAS = new SqlToySqlTokenType("ALIAS");
    static final IElementType PARAMETER = new SqlToySqlTokenType("PARAMETER");
    static final IElementType STRING = new SqlToySqlTokenType("STRING");
    static final IElementType NUMBER = new SqlToySqlTokenType("NUMBER");
    static final IElementType LINE_COMMENT = new SqlToySqlTokenType("LINE_COMMENT");
    static final IElementType BLOCK_COMMENT = new SqlToySqlTokenType("BLOCK_COMMENT");
    static final IElementType OPERATOR = new SqlToySqlTokenType("OPERATOR");
    static final IElementType PUNCTUATION = new SqlToySqlTokenType("PUNCTUATION");

    // 括号词法单元保持无色，以便由 Rainbow Brackets 接管颜色。
    static final IElementType LPAREN = new SqlToySqlTokenType("LPAREN");
    static final IElementType RPAREN = new SqlToySqlTokenType("RPAREN");
    static final IElementType LBRACKET = new SqlToySqlTokenType("LBRACKET");
    static final IElementType RBRACKET = new SqlToySqlTokenType("RBRACKET");
    static final IElementType LBRACE = new SqlToySqlTokenType("LBRACE");
    static final IElementType RBRACE = new SqlToySqlTokenType("RBRACE");

    /**
     * 工具类，不需要创建实例。
     */
    private SqlToySqlTokenTypes() {
    }

    /**
     * 绑定到 SqlToy SQL 语言实例的词法单元类型。
     *
     * @author ax
     * @date 2026-05-30
     */
    private static final class SqlToySqlTokenType extends IElementType {
        /**
         * 使用可读的调试名称创建词法单元类型。
         *
         * @param debugName 在 PSI 诊断中显示的词法单元调试名称
         */
        private SqlToySqlTokenType(@NotNull @NonNls String debugName) {
            super(debugName, SqlToySqlLanguage.INSTANCE);
        }
    }
}
