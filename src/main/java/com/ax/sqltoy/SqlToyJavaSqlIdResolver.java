package com.ax.sqltoy;

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaRecursiveElementWalkingVisitor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiJavaToken;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.FileTypeIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Resolves SqlToy sqlId values from Java string literals.
 *
 * @author ax
 * @date 2026-05-30
 */
final class SqlToyJavaSqlIdResolver {

    /**
     * Cached Java string literal usages grouped by sqlId for one Java file.
     */
    private static final Key<CachedValue<Map<String, List<PsiElement>>>> JAVA_LITERAL_TARGETS_CACHE =
            Key.create("SqlToyJavaLiteralTargetsCache");

    /**
     * Utility class; instances are not needed.
     */
    private SqlToyJavaSqlIdResolver() {
    }

    /**
     * Returns a sqlId string literal for a Java token.
     *
     * @param element PSI element under inspection
     * @return matching literal expression, or null when the element is not a sqlId
     */
    static PsiLiteralExpression getSqlIdLiteral(@NotNull PsiElement element) {
        if (!(element instanceof PsiJavaToken) || !(element.getParent() instanceof PsiLiteralExpression literalExpression)) {
            return null;
        }

        // Only expose literals whose evaluated value looks like a SqlToy sqlId.
        return getSqlId(literalExpression) != null ? literalExpression : null;
    }

    /**
     * Extracts a SqlToy sqlId value from a Java literal.
     *
     * @param literalExpression Java literal expression
     * @return sqlId value, or null when the literal is not a valid sqlId candidate
     */
    static String getSqlId(@NotNull PsiLiteralExpression literalExpression) {
        Object value = literalExpression.getValue();

        if (!(value instanceof String sqlId)) {
            return null;
        }

        return SqlToySqlIdXmlResolver.maybeSqlId(sqlId) ? sqlId : null;
    }

    /**
     * Returns the text range for the literal content excluding quotes.
     *
     * @param literalExpression Java string literal
     * @return range relative to the literal expression
     */
    static TextRange getStringContentRange(@NotNull PsiLiteralExpression literalExpression) {
        String text = literalExpression.getText();

        if (text == null || text.isEmpty()) {
            return TextRange.EMPTY_RANGE;
        }

        // Java text block: """ ... """
        if (text.startsWith("\"\"\"") && text.endsWith("\"\"\"") && text.length() >= 6) {
            return TextRange.create(3, text.length() - 3);
        }

        // Normal Java string literal: "..."
        if (text.startsWith("\"") && text.endsWith("\"") && text.length() >= 2) {
            return TextRange.create(1, text.length() - 1);
        }

        return TextRange.create(0, text.length());
    }

    /**
     * Returns the absolute file text range for the literal content.
     *
     * @param literalExpression Java string literal
     * @return range in file coordinates
     */
    static TextRange getStringContentTextRange(@NotNull PsiLiteralExpression literalExpression) {
        return getStringContentRange(literalExpression).shiftRight(literalExpression.getTextRange().getStartOffset());
    }

    /**
     * Finds Java string literals that use the given sqlId.
     *
     * @param project current project
     * @param sqlId sqlId to search for
     * @return matching Java literal PSI elements
     */
    static List<PsiElement> findLiteralTargets(@NotNull Project project, @NotNull String sqlId) {
        if (DumbService.isDumb(project)) {
            return List.of();
        }

        List<PsiElement> result = new ArrayList<>();
        for (PsiJavaFile javaFile : findCandidateJavaFiles(project, sqlId)) {
            result.addAll(getLiteralTargetsById(javaFile).getOrDefault(sqlId, List.of()));
        }

        return result;
    }

    /**
     * Returns Java files that contain an indexable fragment of the sqlId.
     *
     * @param project current project
     * @param sqlId sqlId to find
     * @return candidate Java PSI files
     */
    private static List<PsiJavaFile> findCandidateJavaFiles(@NotNull Project project, @NotNull String sqlId) {
        String searchWord = SqlToySqlIdXmlResolver.getIndexSearchWord(sqlId);
        if (searchWord == null) {
            return findAllJavaFiles(project);
        }

        List<PsiJavaFile> result = new ArrayList<>();
        GlobalSearchScope scope = GlobalSearchScope.projectScope(project);
        PsiSearchHelper.getInstance(project).processAllFilesWithWordInLiterals(
                searchWord,
                scope,
                file -> {
                    if (file instanceof PsiJavaFile javaFile) {
                        result.add(javaFile);
                    }
                    return true;
                }
        );

        return result;
    }

    /**
     * Returns all Java files in project scope.
     *
     * @param project current project
     * @return Java PSI files
     */
    private static List<PsiJavaFile> findAllJavaFiles(@NotNull Project project) {
        List<PsiJavaFile> result = new ArrayList<>();
        GlobalSearchScope scope = GlobalSearchScope.projectScope(project);
        Collection<VirtualFile> javaFiles = FileTypeIndex.getFiles(JavaFileType.INSTANCE, scope);
        PsiManager psiManager = PsiManager.getInstance(project);

        for (VirtualFile virtualFile : javaFiles) {
            PsiFile psiFile = psiManager.findFile(virtualFile);

            if (!(psiFile instanceof PsiJavaFile javaFile)) {
                continue;
            }

            result.add(javaFile);
        }

        return result;
    }

    /**
     * Returns cached Java string literal usages grouped by sqlId for one Java file.
     *
     * @param javaFile Java file to inspect
     * @return sqlId to Java literal map
     */
    private static Map<String, List<PsiElement>> getLiteralTargetsById(@NotNull PsiJavaFile javaFile) {
        return CachedValuesManager.getManager(javaFile.getProject()).getCachedValue(
                javaFile,
                JAVA_LITERAL_TARGETS_CACHE,
                () -> CachedValueProvider.Result.create(collectLiteralTargetsById(javaFile), javaFile),
                false
        );
    }

    /**
     * Finds Java string literals that look like SqlToy sqlIds in one file and groups them by value.
     *
     * @param javaFile Java file to inspect
     * @return sqlId to Java literal map
     */
    private static Map<String, List<PsiElement>> collectLiteralTargetsById(@NotNull PsiJavaFile javaFile) {
        Map<String, List<PsiElement>> result = new HashMap<>();

        javaFile.accept(new JavaRecursiveElementWalkingVisitor() {
            /**
             * Visits Java literals and collects exact sqlId matches.
             *
             * @param expression literal expression to inspect
             */
            @Override
            public void visitLiteralExpression(@NotNull PsiLiteralExpression expression) {
                String candidate = getSqlId(expression);
                if (candidate != null) {
                    result.computeIfAbsent(candidate, ignored -> new ArrayList<>()).add(expression);
                }

                super.visitLiteralExpression(expression);
            }
        });

        return result;
    }
}
