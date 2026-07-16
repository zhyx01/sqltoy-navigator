package com.ax.sqltoy;

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaRecursiveElementWalkingVisitor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiExpressionList;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiJavaToken;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiReferenceExpression;
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
import java.util.Set;

/**
 * 从 Java 字符串字面量中解析 SqlToy sqlId。
 *
 * @author ax
 * @date 2026-05-30
 */
final class SqlToyJavaSqlIdResolver {

    /**
     * 单个 Java 文件内按 sqlId 分组缓存的字符串字面量引用。
     */
    private static final Key<CachedValue<Map<String, List<PsiElement>>>> JAVA_LITERAL_TARGETS_CACHE =
            Key.create("SqlToyJavaLiteralTargetsCache");

    /**
     * 第一个参数为 SqlToy sqlId 的 DAO 方法名称。
     * todo：后续扩展，或者拿掉，业务场景，非常规引用
     */
    private static final Set<String> SQL_ID_FIRST_ARGUMENT_METHOD_NAMES = Set.of(
            "find",
            "findOne",
            "findByMap",
            "getSingleValue"
    );

    /**
     * 工具类，不需要创建实例。
     */
    private SqlToyJavaSqlIdResolver() {
    }

    /**
     * 根据 Java 词法单元返回对应的 sqlId 字符串字面量。
     *
     * @param element 正在检查的 PSI 元素
     * @return 匹配的字面量表达式；如果该元素不是 sqlId，则返回 null
     */
    static PsiLiteralExpression getSqlIdLiteral(@NotNull PsiElement element) {
        if (!(element instanceof PsiJavaToken) || !(element.getParent() instanceof PsiLiteralExpression literalExpression)) {
            return null;
        }

        // 只暴露求值结果看起来像 SqlToy sqlId 的字面量。
        return getSqlId(literalExpression) != null ? literalExpression : null;
    }

    /**
     * 根据 Java PSI 元素返回 SQL 方法首参中的 sqlId 常量引用。
     *
     * @param element 正在检查的 PSI 元素
     * @return 匹配的常量引用表达式；不符合条件时返回 null
     */
    static PsiReferenceExpression getSqlIdConstantArgument(@NotNull PsiElement element) {
        if (!(element.getParent() instanceof PsiReferenceExpression referenceExpression)) {
            return null;
        }

        if (referenceExpression.getReferenceNameElement() != element) {
            return null;
        }

        return getSqlIdFromConstantArgument(referenceExpression) != null ? referenceExpression : null;
    }

    /**
     * 从 Java 字面量中提取 SqlToy sqlId。
     *
     * @param literalExpression Java 字面量表达式
     * @return sqlId 值；如果字面量不是有效候选，则返回 null
     */
    static String getSqlId(@NotNull PsiLiteralExpression literalExpression) {
        Object value = literalExpression.getValue();

        if (!(value instanceof String sqlId)) {
            return null;
        }

        // PsiLiteralExpression#getValue 会处理转义字符，后续只需要校验值是否像 sqlId。
        return SqlToySqlIdXmlResolver.maybeSqlId(sqlId) ? sqlId : null;
    }

    /**
     * 从 SQL 方法首参中的 Java 常量引用中解析 SqlToy sqlId。
     *
     * @param referenceExpression Java 常量引用表达式
     * @return sqlId 值；不是可支持的常量参数时返回 null
     */
    static String getSqlIdFromConstantArgument(@NotNull PsiReferenceExpression referenceExpression) {
        if (!isFirstSqlIdArgument(referenceExpression)) {
            return null;
        }

        PsiElement resolvedElement = referenceExpression.resolve();
        if (!(resolvedElement instanceof PsiField field)) {
            return null;
        }

        if (!field.hasModifierProperty(PsiModifier.STATIC) || !field.hasModifierProperty(PsiModifier.FINAL)) {
            return null;
        }

        if (!(field.getInitializer() instanceof PsiLiteralExpression literalExpression)) {
            return null;
        }

        return getSqlId(literalExpression);
    }

    /**
     * 返回排除引号后的字面量内容范围。
     *
     * @param literalExpression Java 字符串字面量
     * @return 相对于字面量表达式的范围
     */
    static TextRange getStringContentRange(@NotNull PsiLiteralExpression literalExpression) {
        String text = literalExpression.getText();

        if (text == null || text.isEmpty()) {
            return TextRange.EMPTY_RANGE;
        }

        // Java 文本块：""" ... """
        if (text.startsWith("\"\"\"") && text.endsWith("\"\"\"") && text.length() >= 6) {
            return TextRange.create(3, text.length() - 3);
        }

        // 普通 Java 字符串字面量："..."
        if (text.startsWith("\"") && text.endsWith("\"") && text.length() >= 2) {
            return TextRange.create(1, text.length() - 1);
        }

        return TextRange.create(0, text.length());
    }

    /**
     * 返回字面量内容在文件中的绝对文本范围。
     *
     * @param literalExpression Java 字符串字面量
     * @return 文件坐标中的文本范围
     */
    static TextRange getStringContentTextRange(@NotNull PsiLiteralExpression literalExpression) {
        return getStringContentRange(literalExpression).shiftRight(literalExpression.getTextRange().getStartOffset());
    }

    /**
     * 检查参数是否位于已支持的 SQL 方法首参位置。
     *
     * @param expression 要检查的 Java 表达式
     * @return 是 SQL 方法第一个参数时返回 true
     */
    private static boolean isFirstSqlIdArgument(@NotNull PsiExpression expression) {
        if (!(expression.getParent() instanceof PsiExpressionList expressionList)) {
            return false;
        }

        if (!(expressionList.getParent() instanceof PsiMethodCallExpression methodCallExpression)) {
            return false;
        }

        PsiExpression[] expressions = expressionList.getExpressions();
        if (expressions.length == 0 || expressions[0] != expression) {
            return false;
        }

        String methodName = methodCallExpression.getMethodExpression().getReferenceName();
        return methodName != null && SQL_ID_FIRST_ARGUMENT_METHOD_NAMES.contains(methodName);
    }

    /**
     * 查找使用指定 sqlId 的 Java 字符串字面量。
     *
     * @param project 当前项目
     * @param sqlId 要搜索的 sqlId
     * @return 匹配的 Java 字面量 PSI 元素
     */
    static List<PsiElement> findLiteralTargets(@NotNull Project project, @NotNull String sqlId) {
        if (DumbService.isDumb(project)) {
            return List.of();
        }

        return findLiteralTargetsInProject(project, sqlId);
    }

    /**
     * 按数据库方言等价规则查找使用指定 sqlId 的 Java 字符串字面量。
     *
     * @param project 当前项目
     * @param sqlId 要搜索的 sqlId
     * @return 匹配的 Java 字面量 PSI 元素
     */
    static List<PsiElement> findDialectLiteralTargets(@NotNull Project project, @NotNull String sqlId) {
        if (DumbService.isDumb(project)) {
            return List.of();
        }

        List<PsiElement> result = new ArrayList<>();
        for (String candidateSqlId : SqlToySqlIdXmlResolver.getDialectSqlIdCandidates(sqlId)) {
            result.addAll(findLiteralTargetsInProject(project, candidateSqlId));
        }

        return result;
    }

    /**
     * 在项目中精确查找使用指定 sqlId 的 Java 字符串字面量。
     *
     * @param project 当前项目
     * @param sqlId 要搜索的 sqlId
     * @return 匹配的 Java 字面量 PSI 元素
     */
    private static List<PsiElement> findLiteralTargetsInProject(@NotNull Project project, @NotNull String sqlId) {
        List<PsiElement> result = new ArrayList<>();
        for (PsiJavaFile javaFile : findCandidateJavaFiles(project, sqlId)) {
            result.addAll(getLiteralTargetsById(javaFile).getOrDefault(sqlId, List.of()));
        }

        return result;
    }

    /**
     * 返回包含 sqlId 可索引片段的 Java 文件。
     *
     * @param project 当前项目
     * @param sqlId 要查找的 sqlId
     * @return 候选 Java PSI 文件
     */
    private static List<PsiJavaFile> findCandidateJavaFiles(@NotNull Project project, @NotNull String sqlId) {
        // 先用 sqlId 中最长的普通单词片段走字面量索引，减少后续 PSI 遍历范围。
        String searchWord = SqlToySqlIdXmlResolver.getIndexSearchWord(sqlId);
        if (searchWord == null) {
            // 极少数 sqlId 没有可索引片段时，只能回退到项目内所有 Java 文件。
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
     * 返回项目范围内的所有 Java 文件。
     *
     * @param project 当前项目
     * @return Java PSI 文件
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
     * 返回单个 Java 文件内按 sqlId 分组缓存的字符串字面量引用。
     *
     * @param javaFile 要检查的 Java 文件
     * @return sqlId 到 Java 字面量列表的映射
     */
    private static Map<String, List<PsiElement>> getLiteralTargetsById(@NotNull PsiJavaFile javaFile) {
        // 缓存依赖当前 Java PSI 文件；文件内容变化时 IntelliJ 会自动让缓存失效。
        return CachedValuesManager.getManager(javaFile.getProject()).getCachedValue(
                javaFile,
                JAVA_LITERAL_TARGETS_CACHE,
                () -> CachedValueProvider.Result.create(collectLiteralTargetsById(javaFile), javaFile),
                false
        );
    }

    /**
     * 在单个文件中查找看起来像 SqlToy sqlId 的 Java 字符串字面量，并按值分组。
     *
     * @param javaFile 要检查的 Java 文件
     * @return sqlId 到 Java 字面量列表的映射
     */
    private static Map<String, List<PsiElement>> collectLiteralTargetsById(@NotNull PsiJavaFile javaFile) {
        Map<String, List<PsiElement>> result = new HashMap<>();

        javaFile.accept(new JavaRecursiveElementWalkingVisitor() {
            /**
             * 访问 Java 字面量并收集精确匹配的 sqlId。
             *
             * @param expression 要检查的字面量表达式
             */
            @Override
            public void visitLiteralExpression(@NotNull PsiLiteralExpression expression) {
                String candidate = getSqlId(expression);
                if (candidate != null) {
                    // 同一个 sqlId 可能在一个文件中出现多次，全部保留用于反向导航。
                    result.computeIfAbsent(candidate, ignored -> new ArrayList<>()).add(expression);
                }

                super.visitLiteralExpression(expression);
            }
        });

        return result;
    }
}
