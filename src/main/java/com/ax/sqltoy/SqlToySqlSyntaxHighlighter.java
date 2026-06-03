package com.ax.sqltoy;

import com.intellij.lexer.Lexer;
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors;
import com.intellij.openapi.editor.HighlighterColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase;
import com.intellij.psi.TokenType;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

/**
 * Maps SqlToy SQL lexer token types to IntelliJ editor color attributes.
 *
 * @author ax
 * @date 2026-05-30
 */
public final class SqlToySqlSyntaxHighlighter extends SyntaxHighlighterBase {

    // Theme-backed attributes keep most colors aligned with the current IDE scheme.
    private static final TextAttributesKey KEYWORD = TextAttributesKey.createTextAttributesKey(
            "SQLTOY_SQL_KEYWORD",
            DefaultLanguageHighlighterColors.KEYWORD
    );
    private static final TextAttributesKey IDENTIFIER = TextAttributesKey.createTextAttributesKey(
            "SQLTOY_SQL_IDENTIFIER",
            DefaultLanguageHighlighterColors.INSTANCE_FIELD
    );
    private static final TextAttributesKey FUNCTION = TextAttributesKey.createTextAttributesKey(
            "SQLTOY_SQL_FUNCTION",
            DefaultLanguageHighlighterColors.FUNCTION_CALL
    );
    private static final TextAttributesKey TABLE = TextAttributesKey.createTextAttributesKey(
            "SQLTOY_SQL_TABLE",
            DefaultLanguageHighlighterColors.CLASS_NAME
    );
    private static final TextAttributesKey ALIAS = TextAttributesKey.createTextAttributesKey(
            "SQLTOY_SQL_ALIAS",
            DefaultLanguageHighlighterColors.LOCAL_VARIABLE
    );
    private static final TextAttributesKey PARAMETER = TextAttributesKey.createTextAttributesKey(
            "SQLTOY_SQL_PARAMETER",
            DefaultLanguageHighlighterColors.CONSTANT
    );
    private static final TextAttributesKey STRING = TextAttributesKey.createTextAttributesKey(
            "SQLTOY_SQL_STRING",
            DefaultLanguageHighlighterColors.STRING
    );
    private static final TextAttributesKey NUMBER = TextAttributesKey.createTextAttributesKey(
            "SQLTOY_SQL_NUMBER",
            DefaultLanguageHighlighterColors.NUMBER
    );
    private static final TextAttributesKey COMMENT = TextAttributesKey.createTextAttributesKey(
            "SQLTOY_SQL_COMMENT",
            DefaultLanguageHighlighterColors.LINE_COMMENT
    );
    private static final TextAttributesKey OPERATOR = TextAttributesKey.createTextAttributesKey(
            "SQLTOY_SQL_OPERATOR",
            DefaultLanguageHighlighterColors.OPERATION_SIGN
    );
    private static final TextAttributesKey PUNCTUATION = TextAttributesKey.createTextAttributesKey(
            "SQLTOY_SQL_PUNCTUATION",
            DefaultLanguageHighlighterColors.COMMA
    );
    private static final TextAttributesKey BAD_CHARACTER = TextAttributesKey.createTextAttributesKey(
            "SQLTOY_SQL_BAD_CHARACTER",
            HighlighterColors.BAD_CHARACTER
    );

    // IntelliJ expects arrays of attributes for each token type.
    private static final TextAttributesKey[] KEYWORD_KEYS = pack(KEYWORD);
    private static final TextAttributesKey[] IDENTIFIER_KEYS = pack(IDENTIFIER);
    private static final TextAttributesKey[] FUNCTION_KEYS = pack(FUNCTION);
    private static final TextAttributesKey[] TABLE_KEYS = pack(TABLE);
    private static final TextAttributesKey[] ALIAS_KEYS = pack(ALIAS);
    private static final TextAttributesKey[] PARAMETER_KEYS = pack(PARAMETER);
    private static final TextAttributesKey[] STRING_KEYS = pack(STRING);
    private static final TextAttributesKey[] NUMBER_KEYS = pack(NUMBER);
    private static final TextAttributesKey[] COMMENT_KEYS = pack(COMMENT);
    private static final TextAttributesKey[] OPERATOR_KEYS = pack(OPERATOR);
    private static final TextAttributesKey[] PUNCTUATION_KEYS = pack(PUNCTUATION);
    private static final TextAttributesKey[] BAD_CHARACTER_KEYS = pack(BAD_CHARACTER);
    private static final TextAttributesKey[] EMPTY_KEYS = TextAttributesKey.EMPTY_ARRAY;

    /**
     * Creates a lexer for syntax highlighting.
     *
     * @return fresh SqlToy SQL lexer
     */
    @Override
    public @NotNull Lexer getHighlightingLexer() {
        return new SqlToySqlLexer();
    }

    /**
     * Returns editor attributes for a lexer token type.
     *
     * @param tokenType token type produced by {@link SqlToySqlLexer}
     * @return attributes for the token, or an empty array when this plugin should not color it
     */
    @Override
    public TextAttributesKey @NotNull [] getTokenHighlights(IElementType tokenType) {
        // Bracket tokens intentionally fall through to EMPTY_KEYS for Rainbow Brackets compatibility.
        if (tokenType == SqlToySqlTokenTypes.KEYWORD) {
            return KEYWORD_KEYS;
        }
        if (tokenType == SqlToySqlTokenTypes.IDENTIFIER) {
            return IDENTIFIER_KEYS;
        }
        if (tokenType == SqlToySqlTokenTypes.FUNCTION) {
            return FUNCTION_KEYS;
        }
        if (tokenType == SqlToySqlTokenTypes.TABLE) {
            return TABLE_KEYS;
        }
        if (tokenType == SqlToySqlTokenTypes.TABLE_ALIAS) {
            return ALIAS_KEYS;
        }
        if (tokenType == SqlToySqlTokenTypes.ALIAS) {
            return ALIAS_KEYS;
        }
        if (tokenType == SqlToySqlTokenTypes.PARAMETER) {
            return PARAMETER_KEYS;
        }
        if (tokenType == SqlToySqlTokenTypes.STRING) {
            return STRING_KEYS;
        }
        if (tokenType == SqlToySqlTokenTypes.NUMBER) {
            return NUMBER_KEYS;
        }
        if (tokenType == SqlToySqlTokenTypes.LINE_COMMENT || tokenType == SqlToySqlTokenTypes.BLOCK_COMMENT) {
            return COMMENT_KEYS;
        }
        if (tokenType == SqlToySqlTokenTypes.OPERATOR) {
            return OPERATOR_KEYS;
        }
        if (tokenType == SqlToySqlTokenTypes.PUNCTUATION) {
            return PUNCTUATION_KEYS;
        }
        if (tokenType == TokenType.BAD_CHARACTER) {
            return BAD_CHARACTER_KEYS;
        }

        return EMPTY_KEYS;
    }
}
