package com.ax.sqltoy;

import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.intellij.lang.ASTNode;
import com.intellij.lang.ParserDefinition;
import com.intellij.lang.PsiBuilder;
import com.intellij.lang.PsiParser;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.project.Project;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.TokenType;
import com.intellij.psi.tree.IFileElementType;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.annotations.NotNull;

/**
 * 嵌入式 SqlToy SQL 片段的最小解析器定义。
 *
 * @author ax
 * @date 2026-05-30
 */
public final class SqlToySqlParserDefinition implements ParserDefinition {

    /**
     * 作为注入 SQL 片段根节点的文件元素类型。
     */
    private static final IFileElementType FILE = new IFileElementType(SqlToySqlLanguage.INSTANCE);

    /**
     * 暴露给 IntelliJ 语言服务的注释词法单元集合。
     */
    private static final TokenSet COMMENTS = TokenSet.create(
            SqlToySqlTokenTypes.LINE_COMMENT,
            SqlToySqlTokenTypes.BLOCK_COMMENT
    );

    /**
     * 暴露给 IntelliJ 语言服务的字符串词法单元集合。
     */
    private static final TokenSet STRINGS = TokenSet.create(SqlToySqlTokenTypes.STRING);

    /**
     * 创建解析器使用的词法分析器。
     *
     * @param project 当前项目
     * @return 新的 SqlToy SQL 词法分析器
     */
    @Override
    public @NotNull Lexer createLexer(Project project) {
        return new SqlToySqlLexer();
    }

    /**
     * 创建宽松解析器，将所有词法单元消费到同一个文件节点中。
     *
     * @param project 当前项目
     * @return 轻量级解析器
     */
    @Override
    public @NotNull PsiParser createParser(Project project) {
        return (root, builder) -> {
            // 此插件只做 SQL 高亮，不构建完整 SQL AST。
            PsiBuilder.Marker rootMarker = builder.mark();
            while (!builder.eof()) {
                builder.advanceLexer();
            }
            rootMarker.done(root);
            return builder.getTreeBuilt();
        };
    }

    /**
     * 返回根文件元素类型。
     *
     * @return 文件节点类型
     */
    @Override
    public @NotNull IFileElementType getFileNodeType() {
        return FILE;
    }

    /**
     * 返回空白字符词法单元类型。
     *
     * @return 空白字符词法单元集合
     */
    @Override
    public @NotNull TokenSet getWhitespaceTokens() {
        return TokenSet.create(TokenType.WHITE_SPACE);
    }

    /**
     * 返回注释词法单元类型。
     *
     * @return 注释词法单元集合
     */
    @Override
    public @NotNull TokenSet getCommentTokens() {
        return COMMENTS;
    }

    /**
     * 返回字符串字面量词法单元类型。
     *
     * @return 字符串词法单元集合
     */
    @Override
    public @NotNull TokenSet getStringLiteralElements() {
        return STRINGS;
    }

    /**
     * 将 AST 节点包装成通用 PSI 元素。
     *
     * @param node 要包装的 AST 节点
     * @return PSI 包装元素
     */
    @Override
    public @NotNull PsiElement createElement(ASTNode node) {
        return new ASTWrapperPsiElement(node);
    }

    /**
     * 为注入的 SQL 片段创建 PSI 文件。
     *
     * @param viewProvider 文件视图提供器
     * @return SqlToy SQL PSI 文件
     */
    @Override
    public @NotNull PsiFile createFile(@NotNull FileViewProvider viewProvider) {
        return new SqlToySqlFile(viewProvider);
    }

    /**
     * 定义词法单元之间的空格要求。
     *
     * @param left 左侧 AST 节点
     * @param right 右侧 AST 节点
     * @return 宽松的空格规则
     */
    @Override
    public @NotNull SpaceRequirements spaceExistenceTypeBetweenTokens(ASTNode left, ASTNode right) {
        return SpaceRequirements.MAY;
    }
}
