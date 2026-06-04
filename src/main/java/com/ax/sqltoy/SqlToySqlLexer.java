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
 * 用于 SqlToy 嵌入式 SQL 片段的轻量有状态词法分析器。
 *
 * @author ax
 * @date 2026-05-30
 */
final class SqlToySqlLexer extends LexerBase {

    /**
     * 作为语言关键字高亮的 SQL 关键字。
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
     * 后接 '(' 时仍应按函数处理的关键字。
     */
    private static final Set<String> FUNCTION_KEYWORDS = Set.of(
            "CAST", "COUNT", "MAX", "MIN", "SUM"
    );

    /**
     * 其后下一个标识符通常是表名的关键字。
     */
    private static final Set<String> TABLE_INTRODUCERS = Set.of(
            "FROM", "JOIN", "UPDATE", "INTO"
    );

    /**
     * 结束表名扫描上下文的关键字。
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
     * 开始词法分析一个新的 SQL 片段。
     *
     * @param buffer 要分析的文本缓冲区
     * @param startOffset 开始分析的第一个偏移量
     * @param endOffset 结束偏移量，不包含该位置
     * @param initialState 被忽略，因为该词法分析器只维护本地状态
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
        // 每次重新开始词法分析都必须清空上下文状态，避免上一个 SQL 片段污染当前片段。
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
     * 返回用于增量词法分析的状态。
     *
     * @return 始终返回 0，因为该词法分析器有意设计为单状态
     */
    @Override
    public int getState() {
        return 0;
    }

    /**
     * 返回当前词法单元类型。
     *
     * @return 当前词法单元类型；到达缓冲区末尾时返回 null
     */
    @Override
    public @Nullable IElementType getTokenType() {
        return tokenType;
    }

    /**
     * 返回当前词法单元起始偏移量。
     *
     * @return 词法单元起始偏移量
     */
    @Override
    public int getTokenStart() {
        return tokenStart;
    }

    /**
     * 返回当前词法单元结束偏移量。
     *
     * @return 词法单元结束偏移量，不包含该位置
     */
    @Override
    public int getTokenEnd() {
        return tokenEnd;
    }

    /**
     * 前进到下一个词法单元，并更新 SQL 上下文标记。
     */
    @Override
    public void advance() {
        updateContextAfterCurrentToken();
        tokenStart = tokenEnd;
        locateToken();
    }

    /**
     * 返回底层缓冲区。
     *
     * @return 当前缓冲区序列
     */
    @Override
    public @NotNull CharSequence getBufferSequence() {
        return buffer;
    }

    /**
     * 返回配置的缓冲区结束位置。
     *
     * @return 结束偏移量，不包含该位置
     */
    @Override
    public int getBufferEnd() {
        return endOffset;
    }

    /**
     * 定位并分类 {@link #tokenStart} 处的词法单元。
     */
    private void locateToken() {
        if (tokenStart >= endOffset) {
            tokenType = null;
            tokenEnd = tokenStart;
            return;
        }

        char current = buffer.charAt(tokenStart);

        // 先识别有明确边界的词法单元，再处理依赖上下文的标识符。
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

            // 先检查上下文标记，再进行通用关键字/标识符分类。
            if (expectingParameterName) {
                tokenType = SqlToySqlTokenTypes.PARAMETER;
            } else if (expectingTableAlias && !keyword) {
                tokenType = SqlToySqlTokenTypes.TABLE_ALIAS;
            } else if (expectingAlias && !keyword) {
                tokenType = SqlToySqlTokenTypes.ALIAS;
            } else if (expectingTableName) {
                tokenType = SqlToySqlTokenTypes.TABLE;
            } else if (!keyword && isQualifierBeforeDot(tokenEnd)) {
                // 在 alias.column 里，点号前的标识符按表别名高亮。
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

        // 括号单独成词法单元，插件本身不为其着色。
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
     * 在不推进词法分析器的情况下检查下一个字符。
     *
     * @param expected 期望的下一个字符
     * @return 下一个字符匹配时返回 true
     */
    private boolean hasNext(char expected) {
        return tokenStart + 1 < endOffset && buffer.charAt(tokenStart + 1) == expected;
    }

    /**
     * 扫描连续的空白字符。
     *
     * @param offset 第一个空白字符的偏移量
     * @return 第一个非空白字符的偏移量
     */
    private int scanWhitespace(int offset) {
        while (offset < endOffset && Character.isWhitespace(buffer.charAt(offset))) {
            offset++;
        }
        return offset;
    }

    /**
     * 扫描 SQL 行注释。
     *
     * @param offset '--' 之后的第一个偏移量
     * @return 行注释结束偏移量
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
     * 扫描 SQL 块注释。
     *
     * @param offset '/*' 之后的第一个偏移量
     * @return 块注释结束偏移量
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
     * 扫描带引号的 SQL 字符串或带引号标识符。
     *
     * @param offset 引号起始偏移量
     * @param quote 引号字符
     * @return 引号结束偏移量
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
     * 扫描整数或简单小数。
     *
     * @param offset 第一个数字的偏移量
     * @return 数字结束偏移量
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
     * 扫描类似 SQL 标识符的词法单元。
     *
     * @param offset 标识符起始偏移量
     * @return 标识符结束偏移量
     */
    private int scanIdentifier(int offset) {
        while (offset < endOffset && isIdentifierPart(buffer.charAt(offset))) {
            offset++;
        }
        return offset;
    }

    /**
     * 检查文本范围是否为已知 SQL 关键字。
     *
     * @param start 范围起始偏移量
     * @param end 范围结束偏移量
     * @return 范围内容是关键字时返回 true
     */
    private boolean isKeyword(int start, int end) {
        return KEYWORDS.contains(buffer.subSequence(start, end).toString().toUpperCase());
    }

    /**
     * 根据当前词法单元更新表名、别名和参数上下文。
     */
    private void updateContextAfterCurrentToken() {
        if (tokenType == null || tokenType == TokenType.WHITE_SPACE
                || tokenType == SqlToySqlTokenTypes.LINE_COMMENT
                || tokenType == SqlToySqlTokenTypes.BLOCK_COMMENT) {
            // 空白和注释不改变 SQL 语义上下文。
            return;
        }

        if (tokenType == SqlToySqlTokenTypes.KEYWORD) {
            String keyword = getTokenText(tokenStart, tokenEnd);
            // AS 会根据上下文引入查询结果别名或表别名。
            if ("AS".equals(keyword)) {
                boolean tableAliasContext = expectingTableAlias || justReadTableName;
                expectingAlias = !tableAliasContext;
                expectingTableAlias = tableAliasContext;
                expectingParameterName = false;
                expectingTableName = false;
                justReadTableName = false;
            } else if (TABLE_INTRODUCERS.contains(keyword)) {
                // FROM/JOIN/UPDATE/INTO 后的下一个标识符优先按表名处理。
                expectingAlias = false;
                expectingTableAlias = false;
                expectingParameterName = false;
                expectingTableName = true;
                tableContextActive = true;
                justReadTableName = false;
            } else if (TABLE_CONTEXT_ENDERS.contains(keyword)) {
                // WHERE/ON/GROUP 等关键字出现后，表名列表上下文结束。
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

        // 表名后面可能紧跟表别名。
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

        if (tokenType == SqlToySqlTokenTypes.OPERATOR) {
            expectingAlias = false;
            expectingTableAlias = false;
            expectingParameterName = false;
            justReadTableName = false;
            expectingTableName = false;
            return;
        }

        if (tokenType == SqlToySqlTokenTypes.IDENTIFIER || tokenType == SqlToySqlTokenTypes.FUNCTION) {
            expectingTableAlias = false;
            expectingParameterName = false;
            justReadTableName = false;
            expectingTableName = false;
            // 普通表达式标识符后可能跟着选择列别名，函数名本身不触发别名期待。
            expectingAlias = tokenType == SqlToySqlTokenTypes.IDENTIFIER;
            return;
        }

        if (tokenType == SqlToySqlTokenTypes.STRING || tokenType == SqlToySqlTokenTypes.NUMBER) {
            // SELECT 'x' name 或 SELECT 1 count 这类写法允许字面量后直接跟列别名。
            expectingAlias = true;
            expectingTableAlias = false;
            expectingParameterName = false;
            justReadTableName = false;
            expectingTableName = false;
        }
    }

    /**
     * 在括号词法单元后更新词法分析上下文。
     */
    private void updateContextAfterBracket() {
        char bracket = buffer.charAt(tokenStart);

        if (bracket == '(') {
            parenthesisDepth++;

            if (expectingTableName) {
                // FROM (SELECT ...) 进入派生表：内部不应继续沿用外层表名列表状态。
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
                // 派生表关闭后通常需要表别名，例如 FROM (SELECT ...) t。
                expectingAlias = false;
                expectingTableAlias = true;
                expectingParameterName = false;
                expectingTableName = false;
                tableContextActive = previousState.tableContextActive();
                justReadTableName = true;
            } else {
                // 普通表达式括号关闭后可能跟列别名，例如 SELECT (a + b) total。
                expectingAlias = true;
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
     * 在标点词法单元后更新词法分析上下文。
     */
    private void updateContextAfterPunctuation() {
        char punctuation = buffer.charAt(tokenStart);

        // schema.table 会让下一个标识符继续保持表名上下文。
        if (punctuation == '.' && justReadTableName) {
            expectingTableName = true;
            expectingTableAlias = false;
            expectingParameterName = false;
            justReadTableName = false;
            return;
        }

        // FROM/JOIN 列表中的多个表名用逗号分隔。
        if (punctuation == ',' && tableContextActive) {
            expectingTableName = true;
            expectingTableAlias = false;
            expectingParameterName = false;
            justReadTableName = false;
            return;
        }

        // SqlToy 命名参数使用 :parameterName 语法。
        if (punctuation == ':' && tokenStart + 1 < endOffset && isIdentifierStart(buffer.charAt(tokenStart + 1))) {
            expectingAlias = false;
            expectingTableAlias = false;
            expectingParameterName = true;
            expectingTableName = false;
            justReadTableName = false;
            return;
        }

        if (punctuation != '.') {
            expectingAlias = false;
            expectingTableAlias = false;
            expectingParameterName = false;
            justReadTableName = false;
        }
    }

    /**
     * 判断标识符是否应按函数高亮。
     *
     * @param identifier 已转为大写的标识符文本
     * @param identifierEnd 标识符结束偏移量
     * @return 标识符后跟左括号时返回 true
     */
    private boolean isFunctionName(@NotNull String identifier, int identifierEnd) {
        if (KEYWORDS.contains(identifier) && !FUNCTION_KEYWORDS.contains(identifier)) {
            // 大多数关键字即使后面跟左括号，也不按函数名高亮。
            return false;
        }

        int next = skipWhitespace(identifierEnd);
        return next < endOffset && buffer.charAt(next) == '(';
    }

    /**
     * 检查标识符是否为限定列引用中的限定符。
     *
     * @param identifierEnd 标识符结束偏移量
     * @return 标识符后跟点号时返回 true
     */
    private boolean isQualifierBeforeDot(int identifierEnd) {
        int next = skipWhitespace(identifierEnd);
        return next < endOffset && buffer.charAt(next) == '.';
    }

    /**
     * 从指定偏移量跳过空白字符。
     *
     * @param offset 起始偏移量
     * @return 第一个非空白字符的偏移量
     */
    private int skipWhitespace(int offset) {
        while (offset < endOffset && Character.isWhitespace(buffer.charAt(offset))) {
            offset++;
        }
        return offset;
    }

    /**
     * 读取并标准化词法单元文本。
     *
     * @param start 词法单元起始偏移量
     * @param end 词法单元结束偏移量
     * @return 转为大写后的词法单元文本
     */
    private @NotNull String getTokenText(int start, int end) {
        return buffer.subSequence(start, end).toString().toUpperCase();
    }

    /**
     * 扫描一个或多个操作符字符。
     *
     * @param offset 操作符起始偏移量
     * @return 操作符结束偏移量
     */
    private int scanOperator(int offset) {
        while (offset < endOffset && isOperator(buffer.charAt(offset))) {
            offset++;
        }
        return offset;
    }

    /**
     * 检查字符是否可以作为标识符开头。
     *
     * @param c 要检查的字符
     * @return 字符可以作为标识符开头时返回 true
     */
    private static boolean isIdentifierStart(char c) {
        return Character.isLetter(c) || c == '_' || c == '$';
    }

    /**
     * 检查字符是否可以作为标识符后续字符。
     *
     * @param c 要检查的字符
     * @return 字符可以作为标识符后续字符时返回 true
     */
    private static boolean isIdentifierPart(char c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == '$';
    }

    /**
     * 检查字符是否为 SQL 操作符。
     *
     * @param c 要检查的字符
     * @return 字符是操作符时返回 true
     */
    private static boolean isOperator(char c) {
        return "=<>!+-*/%|&^~".indexOf(c) >= 0;
    }

    /**
     * 检查字符是否为非括号标点。
     *
     * @param c 要检查的字符
     * @return 字符是标点时返回 true
     */
    private static boolean isPunctuation(char c) {
        return ".,;:#".indexOf(c) >= 0;
    }

    /**
     * 检查词法单元类型是否表示任意括号。
     *
     * @param tokenType 要检查的词法单元类型
     * @return 词法单元类型是括号时返回 true
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
     * 将括号字符映射为专用括号词法单元类型。
     *
     * @param c 要映射的字符
     * @return 括号词法单元类型；非括号时返回 null
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
     * 派生表括号外的表扫描状态。
     *
     * @param tableContextActive 逗号是否仍会引入更多表名
     */
    private record TableContextState(boolean tableContextActive) {
    }
}
