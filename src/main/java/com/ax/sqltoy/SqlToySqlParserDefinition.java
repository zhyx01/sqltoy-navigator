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
 * Minimal parser definition for embedded SqlToy SQL fragments.
 *
 * @author ax
 * @date 2026-05-30
 */
public final class SqlToySqlParserDefinition implements ParserDefinition {

    /**
     * File element type used as the root for injected SQL fragments.
     */
    private static final IFileElementType FILE = new IFileElementType(SqlToySqlLanguage.INSTANCE);

    /**
     * Comment token set exposed to IntelliJ language services.
     */
    private static final TokenSet COMMENTS = TokenSet.create(
            SqlToySqlTokenTypes.LINE_COMMENT,
            SqlToySqlTokenTypes.BLOCK_COMMENT
    );

    /**
     * String token set exposed to IntelliJ language services.
     */
    private static final TokenSet STRINGS = TokenSet.create(SqlToySqlTokenTypes.STRING);

    /**
     * Creates the lexer used by the parser.
     *
     * @param project current project
     * @return new SqlToy SQL lexer
     */
    @Override
    public @NotNull Lexer createLexer(Project project) {
        return new SqlToySqlLexer();
    }

    /**
     * Creates a permissive parser that consumes all tokens into one file node.
     *
     * @param project current project
     * @return lightweight parser
     */
    @Override
    public @NotNull PsiParser createParser(Project project) {
        return (root, builder) -> {
            // This plugin highlights SQL but does not build a full SQL AST.
            PsiBuilder.Marker rootMarker = builder.mark();
            while (!builder.eof()) {
                builder.advanceLexer();
            }
            rootMarker.done(root);
            return builder.getTreeBuilt();
        };
    }

    /**
     * Returns the root file element type.
     *
     * @return file node type
     */
    @Override
    public @NotNull IFileElementType getFileNodeType() {
        return FILE;
    }

    /**
     * Returns whitespace token types.
     *
     * @return whitespace token set
     */
    @Override
    public @NotNull TokenSet getWhitespaceTokens() {
        return TokenSet.create(TokenType.WHITE_SPACE);
    }

    /**
     * Returns comment token types.
     *
     * @return comment token set
     */
    @Override
    public @NotNull TokenSet getCommentTokens() {
        return COMMENTS;
    }

    /**
     * Returns string literal token types.
     *
     * @return string token set
     */
    @Override
    public @NotNull TokenSet getStringLiteralElements() {
        return STRINGS;
    }

    /**
     * Wraps AST nodes in generic PSI elements.
     *
     * @param node AST node to wrap
     * @return PSI wrapper element
     */
    @Override
    public @NotNull PsiElement createElement(ASTNode node) {
        return new ASTWrapperPsiElement(node);
    }

    /**
     * Creates a PSI file for an injected SQL fragment.
     *
     * @param viewProvider file view provider
     * @return SqlToy SQL PSI file
     */
    @Override
    public @NotNull PsiFile createFile(@NotNull FileViewProvider viewProvider) {
        return new SqlToySqlFile(viewProvider);
    }

    /**
     * Defines spacing requirements between tokens.
     *
     * @param left left AST node
     * @param right right AST node
     * @return permissive spacing rule
     */
    @Override
    public @NotNull SpaceRequirements spaceExistenceTypeBetweenTokens(ASTNode left, ASTNode right) {
        return SpaceRequirements.MAY;
    }
}
