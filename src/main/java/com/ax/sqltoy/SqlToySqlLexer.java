package com.ax.sqltoy;

import com.intellij.lexer.LexerBase;
import com.intellij.psi.TokenType;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

final class SqlToySqlLexer extends LexerBase {

    private static final Set<String> KEYWORDS = Set.of(
            "ADD", "ALL", "ALTER", "AND", "ANY", "AS", "ASC", "BETWEEN", "BY", "CASE",
            "CAST", "CHECK", "COLUMN", "COUNT", "CREATE", "CROSS", "DELETE", "DESC", "DISTINCT",
            "DROP", "ELSE", "END", "EXCEPT", "EXISTS", "FALSE", "FETCH", "FIRST", "FOR", "FROM",
            "FULL", "GROUP", "HAVING", "IN", "INDEX", "INNER", "INSERT", "INTERSECT", "INTO",
            "IS", "JOIN", "LEFT", "LIKE", "LIMIT", "MAX", "MIN", "NOT", "NULL", "OFFSET", "ON",
            "OR", "ORDER", "OUTER", "PRIMARY", "PROCEDURE", "RIGHT", "ROW", "SELECT", "SET",
            "SUM", "TABLE", "THEN", "TOP", "TRUE", "UNION", "UNIQUE", "UPDATE", "VALUES",
            "VIEW", "WHEN", "WHERE", "WITH"
    );

    private static final Set<String> FUNCTION_KEYWORDS = Set.of(
            "CAST", "COUNT", "MAX", "MIN", "SUM"
    );

    private static final Set<String> TABLE_INTRODUCERS = Set.of(
            "FROM", "JOIN", "UPDATE", "INTO"
    );

    private static final Set<String> TABLE_CONTEXT_ENDERS = Set.of(
            "WHERE", "ON", "SET", "VALUES", "GROUP", "ORDER", "HAVING", "LIMIT", "OFFSET",
            "UNION", "EXCEPT", "INTERSECT", "RETURNING"
    );

    private CharSequence buffer = "";
    private int startOffset;
    private int endOffset;
    private int tokenStart;
    private int tokenEnd;
    private IElementType tokenType;
    private boolean expectingTableName;
    private boolean expectingAlias;
    private boolean expectingTableAlias;
    private boolean expectingParameterName;
    private boolean tableContextActive;
    private boolean justReadTableName;

    @Override
    public void start(
            @NotNull CharSequence buffer,
            int startOffset,
            int endOffset,
            int initialState
    ) {
        this.buffer = buffer;
        this.startOffset = startOffset;
        this.endOffset = endOffset;
        this.tokenStart = startOffset;
        this.expectingTableName = false;
        this.expectingAlias = false;
        this.expectingTableAlias = false;
        this.expectingParameterName = false;
        this.tableContextActive = false;
        this.justReadTableName = false;
        locateToken();
    }

    @Override
    public int getState() {
        return 0;
    }

    @Override
    public @Nullable IElementType getTokenType() {
        return tokenType;
    }

    @Override
    public int getTokenStart() {
        return tokenStart;
    }

    @Override
    public int getTokenEnd() {
        return tokenEnd;
    }

    @Override
    public void advance() {
        updateContextAfterCurrentToken();
        tokenStart = tokenEnd;
        locateToken();
    }

    @Override
    public @NotNull CharSequence getBufferSequence() {
        return buffer;
    }

    @Override
    public int getBufferEnd() {
        return endOffset;
    }

    private void locateToken() {
        if (tokenStart >= endOffset) {
            tokenType = null;
            tokenEnd = tokenStart;
            return;
        }

        char current = buffer.charAt(tokenStart);

        if (Character.isWhitespace(current)) {
            tokenEnd = scanWhitespace(tokenStart);
            tokenType = TokenType.WHITE_SPACE;
            return;
        }

        if (current == '-' && hasNext('-')) {
            tokenEnd = scanLineComment(tokenStart + 2);
            tokenType = SqlToySqlTokenTypes.LINE_COMMENT;
            return;
        }

        if (current == '/' && hasNext('*')) {
            tokenEnd = scanBlockComment(tokenStart + 2);
            tokenType = SqlToySqlTokenTypes.BLOCK_COMMENT;
            return;
        }

        if (current == '\'' || current == '"') {
            tokenEnd = scanQuoted(tokenStart, current);
            tokenType = SqlToySqlTokenTypes.STRING;
            return;
        }

        if (Character.isDigit(current)) {
            tokenEnd = scanNumber(tokenStart);
            tokenType = SqlToySqlTokenTypes.NUMBER;
            return;
        }

        if (isIdentifierStart(current)) {
            tokenEnd = scanIdentifier(tokenStart);
            String identifier = getTokenText(tokenStart, tokenEnd);
            boolean keyword = KEYWORDS.contains(identifier);

            if (expectingParameterName) {
                tokenType = SqlToySqlTokenTypes.PARAMETER;
            } else if ((expectingAlias || expectingTableAlias) && !keyword) {
                tokenType = SqlToySqlTokenTypes.ALIAS;
            } else if (isFunctionName(identifier, tokenEnd)) {
                tokenType = SqlToySqlTokenTypes.FUNCTION;
            } else if (keyword) {
                tokenType = SqlToySqlTokenTypes.KEYWORD;
            } else if (expectingTableName) {
                tokenType = SqlToySqlTokenTypes.TABLE;
            } else {
                tokenType = SqlToySqlTokenTypes.IDENTIFIER;
            }
            return;
        }

        if (isOperator(current)) {
            tokenEnd = scanOperator(tokenStart);
            tokenType = SqlToySqlTokenTypes.OPERATOR;
            return;
        }

        if (isPunctuation(current)) {
            tokenEnd = tokenStart + 1;
            tokenType = SqlToySqlTokenTypes.PUNCTUATION;
            return;
        }

        tokenEnd = tokenStart + 1;
        tokenType = TokenType.BAD_CHARACTER;
    }

    private boolean hasNext(char expected) {
        return tokenStart + 1 < endOffset && buffer.charAt(tokenStart + 1) == expected;
    }

    private int scanWhitespace(int offset) {
        while (offset < endOffset && Character.isWhitespace(buffer.charAt(offset))) {
            offset++;
        }
        return offset;
    }

    private int scanLineComment(int offset) {
        while (offset < endOffset) {
            char c = buffer.charAt(offset);
            if (c == '\n' || c == '\r') {
                break;
            }
            offset++;
        }
        return offset;
    }

    private int scanBlockComment(int offset) {
        while (offset + 1 < endOffset) {
            if (buffer.charAt(offset) == '*' && buffer.charAt(offset + 1) == '/') {
                return offset + 2;
            }
            offset++;
        }
        return endOffset;
    }

    private int scanQuoted(int offset, char quote) {
        offset++;
        while (offset < endOffset) {
            char c = buffer.charAt(offset);
            if (c == quote) {
                if (offset + 1 < endOffset && buffer.charAt(offset + 1) == quote) {
                    offset += 2;
                    continue;
                }
                return offset + 1;
            }
            offset++;
        }
        return endOffset;
    }

    private int scanNumber(int offset) {
        while (offset < endOffset && Character.isDigit(buffer.charAt(offset))) {
            offset++;
        }

        if (offset < endOffset && buffer.charAt(offset) == '.') {
            offset++;
            while (offset < endOffset && Character.isDigit(buffer.charAt(offset))) {
                offset++;
            }
        }

        return offset;
    }

    private int scanIdentifier(int offset) {
        while (offset < endOffset && isIdentifierPart(buffer.charAt(offset))) {
            offset++;
        }
        return offset;
    }

    private boolean isKeyword(int start, int end) {
        return KEYWORDS.contains(buffer.subSequence(start, end).toString().toUpperCase());
    }

    private void updateContextAfterCurrentToken() {
        if (tokenType == null || tokenType == TokenType.WHITE_SPACE
                || tokenType == SqlToySqlTokenTypes.LINE_COMMENT
                || tokenType == SqlToySqlTokenTypes.BLOCK_COMMENT) {
            return;
        }

        if (tokenType == SqlToySqlTokenTypes.KEYWORD) {
            String keyword = getTokenText(tokenStart, tokenEnd);
            if ("AS".equals(keyword)) {
                expectingAlias = true;
                expectingTableAlias = false;
                expectingParameterName = false;
                expectingTableName = false;
                justReadTableName = false;
            } else if (TABLE_INTRODUCERS.contains(keyword)) {
                expectingAlias = false;
                expectingTableAlias = false;
                expectingParameterName = false;
                expectingTableName = true;
                tableContextActive = true;
                justReadTableName = false;
            } else if (TABLE_CONTEXT_ENDERS.contains(keyword)) {
                expectingAlias = false;
                expectingTableAlias = false;
                expectingParameterName = false;
                expectingTableName = false;
                tableContextActive = false;
                justReadTableName = false;
            } else {
                expectingAlias = false;
                expectingTableAlias = false;
                expectingParameterName = false;
                expectingTableName = false;
                justReadTableName = false;
            }
            return;
        }

        if (tokenType == SqlToySqlTokenTypes.TABLE) {
            expectingAlias = false;
            expectingTableAlias = true;
            expectingParameterName = false;
            expectingTableName = false;
            justReadTableName = true;
            return;
        }

        if (tokenType == SqlToySqlTokenTypes.ALIAS) {
            expectingAlias = false;
            expectingTableAlias = false;
            expectingParameterName = false;
            expectingTableName = false;
            justReadTableName = false;
            return;
        }

        if (tokenType == SqlToySqlTokenTypes.PARAMETER) {
            expectingAlias = false;
            expectingTableAlias = false;
            expectingParameterName = false;
            expectingTableName = false;
            justReadTableName = false;
            return;
        }

        if (tokenType == SqlToySqlTokenTypes.PUNCTUATION) {
            updateContextAfterPunctuation();
            return;
        }

        if (tokenType == SqlToySqlTokenTypes.IDENTIFIER || tokenType == SqlToySqlTokenTypes.FUNCTION) {
            expectingAlias = false;
            expectingTableAlias = false;
            expectingParameterName = false;
            justReadTableName = false;
            expectingTableName = false;
        }
    }

    private void updateContextAfterPunctuation() {
        char punctuation = buffer.charAt(tokenStart);

        if (punctuation == '.' && justReadTableName) {
            expectingTableName = true;
            expectingTableAlias = false;
            expectingParameterName = false;
            justReadTableName = false;
            return;
        }

        if (punctuation == ',' && tableContextActive) {
            expectingTableName = true;
            expectingTableAlias = false;
            expectingParameterName = false;
            justReadTableName = false;
            return;
        }

        if (punctuation == ':' && tokenStart + 1 < endOffset && isIdentifierStart(buffer.charAt(tokenStart + 1))) {
            expectingAlias = false;
            expectingTableAlias = false;
            expectingParameterName = true;
            expectingTableName = false;
            justReadTableName = false;
            return;
        }

        if (punctuation != '.') {
            expectingTableAlias = false;
            expectingParameterName = false;
            justReadTableName = false;
        }
    }

    private boolean isFunctionName(@NotNull String identifier, int identifierEnd) {
        if (KEYWORDS.contains(identifier) && !FUNCTION_KEYWORDS.contains(identifier)) {
            return false;
        }

        int next = skipWhitespace(identifierEnd);
        return next < endOffset && buffer.charAt(next) == '(';
    }

    private int skipWhitespace(int offset) {
        while (offset < endOffset && Character.isWhitespace(buffer.charAt(offset))) {
            offset++;
        }
        return offset;
    }

    private @NotNull String getTokenText(int start, int end) {
        return buffer.subSequence(start, end).toString().toUpperCase();
    }

    private int scanOperator(int offset) {
        while (offset < endOffset && isOperator(buffer.charAt(offset))) {
            offset++;
        }
        return offset;
    }

    private static boolean isIdentifierStart(char c) {
        return Character.isLetter(c) || c == '_' || c == '$';
    }

    private static boolean isIdentifierPart(char c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == '$';
    }

    private static boolean isOperator(char c) {
        return "=<>!+-*/%|&^~".indexOf(c) >= 0;
    }

    private static boolean isPunctuation(char c) {
        return ".,;:()[]{}".indexOf(c) >= 0;
    }
}
