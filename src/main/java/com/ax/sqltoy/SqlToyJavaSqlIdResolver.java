package com.ax.sqltoy;

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.project.Project;
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
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

final class SqlToyJavaSqlIdResolver {

    private SqlToyJavaSqlIdResolver() {
    }

    static PsiLiteralExpression getSqlIdLiteral(@NotNull PsiElement element) {
        if (!(element instanceof PsiJavaToken) || !(element.getParent() instanceof PsiLiteralExpression literalExpression)) {
            return null;
        }

        return getSqlId(literalExpression) != null ? literalExpression : null;
    }

    static String getSqlId(@NotNull PsiLiteralExpression literalExpression) {
        Object value = literalExpression.getValue();

        if (!(value instanceof String sqlId)) {
            return null;
        }

        return SqlToySqlIdXmlResolver.maybeSqlId(sqlId) ? sqlId : null;
    }

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

    static TextRange getStringContentTextRange(@NotNull PsiLiteralExpression literalExpression) {
        return getStringContentRange(literalExpression).shiftRight(literalExpression.getTextRange().getStartOffset());
    }

    static List<PsiElement> findLiteralTargets(@NotNull Project project, @NotNull String sqlId) {
        List<PsiElement> result = new ArrayList<>();

        GlobalSearchScope scope = GlobalSearchScope.projectScope(project);
        Collection<VirtualFile> javaFiles = FileTypeIndex.getFiles(JavaFileType.INSTANCE, scope);
        PsiManager psiManager = PsiManager.getInstance(project);

        for (VirtualFile virtualFile : javaFiles) {
            PsiFile psiFile = psiManager.findFile(virtualFile);

            if (!(psiFile instanceof PsiJavaFile javaFile)) {
                continue;
            }

            javaFile.accept(new JavaRecursiveElementWalkingVisitor() {
                @Override
                public void visitLiteralExpression(@NotNull PsiLiteralExpression expression) {
                    String candidate = getSqlId(expression);
                    if (sqlId.equals(candidate)) {
                        result.add(expression);
                    }

                    super.visitLiteralExpression(expression);
                }
            });
        }

        return result;
    }
}
