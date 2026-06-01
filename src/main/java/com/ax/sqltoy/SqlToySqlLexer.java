package com.ax.sqltoy;

import com.intellij.lexer.LexerBase;
import com.intellij.psi.TokenType;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Set;

/**
 * Lightweight stateful lexer for SqlToy embedded SQL fragments.
 *
 * @author ax
 * @date 2026-05-30
 */
final class SqlToySqlLexer extends LexerBase {

    /**
     * SQL keywords highlighted as language keywords.
     */
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

    /**
     * Keywords that should still be treated as functions when followed by '('.
     */
    private static final Set<String> FUNCTION_KEYWORDS = Set.of(
            "CAST", "COUNT", "MAX", "MIN", "SUM"
    );

    /**
     * Keywords after which the next identifier is normally a table name.
     */
    private static final Set<String> TABLE_INTRODUCERS = Set.of(
            "FROM", "JOIN", "UPDATE", "INTO"
    );

    /**
     * Keywords that end a table-name scanning context.
     */
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
    private int parenthesisDepth;
    private final Deque<Integer> derivedTableParenthesisDepths = new ArrayDeque<>();
    private final Deque<TableContextState> tableContextStates = new ArrayDeque<>();

    /**
     * Starts lexing a new SQL fragment.
     *
     * @param buffer text buffer to lex
     * @param startOffset first offset to lex
     * @param endOffset end offset, exclusive
     * @param initialState ignored because this lexer keeps only local state
     */
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
        this.parenthesisDepth = 0;
        this.derivedTableParenthesisDepths.clear();
        this.tableContextStates.clear();
        locateToken();
    }

    /**
     * Returns lexer state for incremental lexing.
     *
     * @return always zero because the lexer is intentionally single-state
     */
    @Override
    public int getState() {
        return 0;
    }

    /**
     * Returns the current token type.
     *
     * @return current token type, or null at the end of the buffer
     */
    @Override
    public @Nullable IElementType getTokenType() {
        return tokenType;
    }

    /**
     * Returns the current token start offset.
     *
     * @return token start offset
     */
    @Override
    public int getTokenStart() {
        return tokenStart;
    }

    /**
     * Returns the current token end offset.
     *
     * @return token end offset, exclusive
     */
    @Override
    public int getTokenEnd() {
        return tokenEnd;
    }

    /**
     * Advances to the next token and updates contextual SQL flags.
     */
    @Override
    public void advance() {
        updateContextAfterCurrentToken();
        tokenStart = tokenEnd;
        locateToken();
    }

    /**
     * Returns the backing buffer.
     *
     * @return current buffer sequence
     */
    @Override
    public @NotNull CharSequence getBufferSequence() {
        return buffer;
    }

    /**
     * Returns the configured buffer end.
     *
     * @return end offset, exclusive
     */
    @Override
    public int getBufferEnd() {
        return endOffset;
    }

    /**
     * Locates and classifies the token at {@link #tokenStart}.
     */
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

            // Context flags are checked before generic keyword/identifier classification.
            if (expectingParameterName) {
                tokenType = SqlToySqlTokenTypes.PARAMETER;
            } else if (expectingTableAlias && !keyword) {
                tokenType = SqlToySqlTokenTypes.TABLE_ALIAS;
            } else if (expectingAlias && !keyword) {
                tokenType = SqlToySqlTokenTypes.ALIAS;
            } else if (expectingTableName) {
                tokenType = SqlToySqlTokenTypes.TABLE;
            } else if (!keyword && isQualifierBeforeDot(tokenEnd)) {
                tokenType = SqlToySqlTokenTypes.TABLE_ALIAS;
            } else if (isFunctionName(identifier, tokenEnd)) {
                tokenType = SqlToySqlTokenTypes.FUNCTION;
            } else if (keyword) {
                tokenType = SqlToySqlTokenTypes.KEYWORD;
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

        // Brackets are separate from punctuation so this plugin does not color them.
        IElementType bracketTokenType = getBracketTokenType(current);
        if (bracketTokenType != null) {
            tokenEnd = tokenStart + 1;
            tokenType = bracketTokenType;
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

    /**
     * Checks the next character without advancing the lexer.
     *
     * @param expected expected next character
     * @return true when the next character matches
     */
    private boolean hasNext(char expected) {
        return tokenStart + 1 < endOffset && buffer.charAt(tokenStart + 1) == expected;
    }

    /**
     * Scans a contiguous whitespace run.
     *
     * @param offset first whitespace offset
     * @return first non-whitespace offset
     */
    private int scanWhitespace(int offset) {
        while (offset < endOffset && Character.isWhitespace(buffer.charAt(offset))) {
            offset++;
        }
        return offset;
    }

    /**
     * Scans a SQL line comment.
     *
     * @param offset first offset after '--'
     * @return line comment end offset
     */
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

    /**
     * Scans a SQL block comment.
     *
     * @param offset first offset after '/*'
     * @return block comment end offset
     */
    private int scanBlockComment(int offset) {
        while (offset + 1 < endOffset) {
            if (buffer.charAt(offset) == '*' && buffer.charAt(offset + 1) == '/') {
                return offset + 2;
            }
            offset++;
        }
        return endOffset;
    }

    /**
     * Scans a quoted SQL string or quoted identifier.
     *
     * @param offset quote start offset
     * @param quote quote character
     * @return quote end offset
     */
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

    /**
     * Scans an integer or simple decimal number.
     *
     * @param offset first digit offset
     * @return number end offset
     */
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

    /**
     * Scans an SQL identifier-like token.
     *
     * @param offset identifier start offset
     * @return identifier end offset
     */
    private int scanIdentifier(int offset) {
        while (offset < endOffset && isIdentifierPart(buffer.charAt(offset))) {
            offset++;
        }
        return offset;
    }

    /**
     * Checks whether a text range is a known SQL keyword.
     *
     * @param start range start offset
     * @param end range end offset
     * @return true when the range is a keyword
     */
    private boolean isKeyword(int start, int end) {
        return KEYWORDS.contains(buffer.subSequence(start, end).toString().toUpperCase());
    }

    /**
     * Updates table, alias, and parameter context after the current token.
     */
    private void updateContextAfterCurrentToken() {
        if (tokenType == null || tokenType == TokenType.WHITE_SPACE
                || tokenType == SqlToySqlTokenTypes.LINE_COMMENT
                || tokenType == SqlToySqlTokenTypes.BLOCK_COMMENT) {
            return;
        }

        if (tokenType == SqlToySqlTokenTypes.KEYWORD) {
            String keyword = getTokenText(tokenStart, tokenEnd);
            // AS introduces either a select alias or a table alias, depending on context.
            if ("AS".equals(keyword)) {
                boolean tableAliasContext = expectingTableAlias || justReadTableName;
                expectingAlias = !tableAliasContext;
                expectingTableAlias = tableAliasContext;
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

        // A table name may be immediately followed by a table alias.
        if (tokenType == SqlToySqlTokenTypes.TABLE) {
            expectingAlias = false;
            expectingTableAlias = true;
            expectingParameterName = false;
            expectingTableName = false;
            justReadTableName = true;
            return;
        }

        if (tokenType == SqlToySqlTokenTypes.TABLE_ALIAS) {
            expectingAlias = false;
            expectingTableAlias = false;
            expectingParameterName = false;
            expectingTableName = false;
            justReadTableName = false;
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

        if (isBracketToken(tokenType)) {
            updateContextAfterBracket();
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

    /**
     * Updates lexer context after bracket tokens.
     */
    private void updateContextAfterBracket() {
        char bracket = buffer.charAt(tokenStart);

        if (bracket == '(') {
            parenthesisDepth++;

            if (expectingTableName) {
                derivedTableParenthesisDepths.push(parenthesisDepth);
                tableContextStates.push(new TableContextState(tableContextActive));
                expectingAlias = false;
                expectingTableAlias = false;
                expectingParameterName = false;
                expectingTableName = false;
                tableContextActive = false;
                justReadTableName = false;
                return;
            }

            expectingParameterName = false;
            justReadTableName = false;
            return;
        }

        if (bracket == ')') {
            boolean closesDerivedTable = parenthesisDepth > 0
                    && !derivedTableParenthesisDepths.isEmpty()
                    && derivedTableParenthesisDepths.peek() == parenthesisDepth;

            if (closesDerivedTable) {
                derivedTableParenthesisDepths.pop();
                TableContextState previousState = tableContextStates.isEmpty()
                        ? new TableContextState(false)
                        : tableContextStates.pop();
                expectingAlias = false;
                expectingTableAlias = true;
                expectingParameterName = false;
                expectingTableName = false;
                tableContextActive = previousState.tableContextActive();
                justReadTableName = true;
            } else {
                expectingParameterName = false;
                justReadTableName = false;
            }

            if (parenthesisDepth > 0) {
                parenthesisDepth--;
            }
            return;
        }

        expectingParameterName = false;
        justReadTableName = false;
    }

    /**
     * Updates lexer context after punctuation.
     */
    private void updateContextAfterPunctuation() {
        char punctuation = buffer.charAt(tokenStart);

        // schema.table keeps the next identifier in table-name context.
        if (punctuation == '.' && justReadTableName) {
            expectingTableName = true;
            expectingTableAlias = false;
            expectingParameterName = false;
            justReadTableName = false;
            return;
        }

        // Multiple table names in FROM/JOIN lists are separated by commas.
        if (punctuation == ',' && tableContextActive) {
            expectingTableName = true;
            expectingTableAlias = false;
            expectingParameterName = false;
            justReadTableName = false;
            return;
        }

        // SqlToy named parameters use :parameterName syntax.
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

    /**
     * Determines whether an identifier should be highlighted as a function.
     *
     * @param identifier upper-case identifier text
     * @param identifierEnd identifier end offset
     * @return true when the identifier is followed by an opening parenthesis
     */
    private boolean isFunctionName(@NotNull String identifier, int identifierEnd) {
        if (KEYWORDS.contains(identifier) && !FUNCTION_KEYWORDS.contains(identifier)) {
            return false;
        }

        int next = skipWhitespace(identifierEnd);
        return next < endOffset && buffer.charAt(next) == '(';
    }

    /**
     * Checks whether an identifier is the qualifier in a qualified column reference.
     *
     * @param identifierEnd identifier end offset
     * @return true when the identifier is followed by a dot
     */
    private boolean isQualifierBeforeDot(int identifierEnd) {
        int next = skipWhitespace(identifierEnd);
        return next < endOffset && buffer.charAt(next) == '.';
    }

    /**
     * Skips whitespace from the given offset.
     *
     * @param offset start offset
     * @return first non-whitespace offset
     */
    private int skipWhitespace(int offset) {
        while (offset < endOffset && Character.isWhitespace(buffer.charAt(offset))) {
            offset++;
        }
        return offset;
    }

    /**
     * Reads and normalizes the token text.
     *
     * @param start token start offset
     * @param end token end offset
     * @return upper-case token text
     */
    private @NotNull String getTokenText(int start, int end) {
        return buffer.subSequence(start, end).toString().toUpperCase();
    }

    /**
     * Scans one or more operator characters.
     *
     * @param offset operator start offset
     * @return operator end offset
     */
    private int scanOperator(int offset) {
        while (offset < endOffset && isOperator(buffer.charAt(offset))) {
            offset++;
        }
        return offset;
    }

    /**
     * Checks whether a character can start an identifier.
     *
     * @param c character to check
     * @return true when the character can start an identifier
     */
    private static boolean isIdentifierStart(char c) {
        return Character.isLetter(c) || c == '_' || c == '$';
    }

    /**
     * Checks whether a character can continue an identifier.
     *
     * @param c character to check
     * @return true when the character can continue an identifier
     */
    private static boolean isIdentifierPart(char c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == '$';
    }

    /**
     * Checks whether a character is an SQL operator.
     *
     * @param c character to check
     * @return true when the character is an operator
     */
    private static boolean isOperator(char c) {
        return "=<>!+-*/%|&^~".indexOf(c) >= 0;
    }

    /**
     * Checks whether a character is non-bracket punctuation.
     *
     * @param c character to check
     * @return true when the character is punctuation
     */
    private static boolean isPunctuation(char c) {
        return ".,;:#".indexOf(c) >= 0;
    }

    /**
     * Checks whether a token type represents any bracket token.
     *
     * @param tokenType token type to check
     * @return true when token type is a bracket
     */
    private static boolean isBracketToken(@NotNull IElementType tokenType) {
        return tokenType == SqlToySqlTokenTypes.LPAREN
                || tokenType == SqlToySqlTokenTypes.RPAREN
                || tokenType == SqlToySqlTokenTypes.LBRACKET
                || tokenType == SqlToySqlTokenTypes.RBRACKET
                || tokenType == SqlToySqlTokenTypes.LBRACE
                || tokenType == SqlToySqlTokenTypes.RBRACE;
    }

    /**
     * Maps bracket characters to dedicated bracket token types.
     *
     * @param c character to map
     * @return bracket token type, or null for non-brackets
     */
    private static IElementType getBracketTokenType(char c) {
        return switch (c) {
            case '(' -> SqlToySqlTokenTypes.LPAREN;
            case ')' -> SqlToySqlTokenTypes.RPAREN;
            case '[' -> SqlToySqlTokenTypes.LBRACKET;
            case ']' -> SqlToySqlTokenTypes.RBRACKET;
            case '{' -> SqlToySqlTokenTypes.LBRACE;
            case '}' -> SqlToySqlTokenTypes.RBRACE;
            default -> null;
        };
    }

    /**
     * Table scanning state outside a derived-table parenthesis.
     *
     * @param tableContextActive whether commas still introduce more table names
     */
    private record TableContextState(boolean tableContextActive) {
    }
}
