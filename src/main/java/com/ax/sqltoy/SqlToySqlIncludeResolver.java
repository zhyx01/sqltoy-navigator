package com.ax.sqltoy;

import com.intellij.ide.highlighter.XmlFileType;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiRecursiveElementWalkingVisitor;
import com.intellij.psi.search.FileTypeIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlText;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 解析 XML SQL 中 @include("sqlId") 对 XML SQL 定义的引用。
 *
 * @author ax
 * @date 2026-07-09
 */
final class SqlToySqlIncludeResolver {

    /**
     * 单个 XML 文件内按 include sqlId 分组缓存的引用位置。
     */
    private static final Key<CachedValue<Map<String, List<SqlToyNavigationTarget>>>> XML_INCLUDE_TARGETS_CACHE =
            Key.create("SqlToyXmlIncludeTargetsCache");

    private final SqlToySqlIncludeParser includeParser = new SqlToySqlIncludeParser();

    /**
     * 查找引用指定 sqlId 的 XML include 位置。
     *
     * @param project 当前项目
     * @param sqlId   被 include 引用的 sqlId
     * @return XML include 使用位置
     */
    List<PsiElement> findIncludeTargets(@NotNull Project project, @NotNull String sqlId) {
        return findIncludeNavigationTargets(project, sqlId).stream()
                .map(SqlToyNavigationTarget::getElement)
                .toList();
    }

    /**
     * 查找引用指定 sqlId 的 XML include 精确导航位置。
     *
     * @param project 当前项目
     * @param sqlId   被 include 引用的 sqlId
     * @return XML include 精确导航目标
     */
    List<SqlToyNavigationTarget> findIncludeNavigationTargets(@NotNull Project project, @NotNull String sqlId) {
        if (DumbService.isDumb(project)) {
            return List.of();
        }

        List<SqlToyNavigationTarget> result = new ArrayList<>();
        for (XmlFile xmlFile : findCandidateXmlFiles(project, sqlId)) {
            result.addAll(getIncludeNavigationTargetsById(xmlFile).getOrDefault(sqlId, List.of()));
        }

        return result;
    }

    /**
     * 返回包含 sqlId 可索引片段的 XML 文件。
     *
     * @param project 当前项目
     * @param sqlId   要查找的 sqlId
     * @return 候选 XML PSI 文件
     */
    private List<XmlFile> findCandidateXmlFiles(@NotNull Project project, @NotNull String sqlId) {
        String searchWord = SqlToySqlIdXmlResolver.getIndexSearchWord(sqlId);
        if (Objects.isNull(searchWord)) {
            return findAllXmlFiles(project);
        }

        List<XmlFile> result = new ArrayList<>();
        GlobalSearchScope scope = GlobalSearchScope.projectScope(project);
        PsiSearchHelper.getInstance(project).processAllFilesWithWordInText(
                searchWord,
                scope,
                file -> {
                    if (file instanceof XmlFile xmlFile) {
                        result.add(xmlFile);
                    }
                    return true;
                },
                true
        );

        return result;
    }

    /**
     * 返回项目范围内的全部 XML 文件。
     *
     * @param project 当前项目
     * @return XML PSI 文件
     */
    private List<XmlFile> findAllXmlFiles(@NotNull Project project) {
        List<XmlFile> result = new ArrayList<>();
        GlobalSearchScope scope = GlobalSearchScope.projectScope(project);
        Collection<VirtualFile> xmlFiles = FileTypeIndex.getFiles(XmlFileType.INSTANCE, scope);
        PsiManager psiManager = PsiManager.getInstance(project);

        for (VirtualFile virtualFile : xmlFiles) {
            PsiFile psiFile = psiManager.findFile(virtualFile);
            if (psiFile instanceof XmlFile xmlFile) {
                result.add(xmlFile);
            }
        }

        return result;
    }

    /**
     * 返回单个 XML 文件内按 include sqlId 分组的精确导航位置。
     *
     * @param xmlFile 要检查的 XML 文件
     * @return include sqlId 到 XML 精确导航目标列表的映射
     */
    private Map<String, List<SqlToyNavigationTarget>> getIncludeNavigationTargetsById(@NotNull XmlFile xmlFile) {
        return CachedValuesManager.getManager(xmlFile.getProject()).getCachedValue(
                xmlFile,
                XML_INCLUDE_TARGETS_CACHE,
                () -> CachedValueProvider.Result.create(collectIncludeNavigationTargetsById(xmlFile), xmlFile),
                false
        );
    }

    /**
     * 在单个 XML 文件内收集所有 include 使用位置。
     *
     * @param xmlFile 要检查的 XML 文件
     * @return include sqlId 到 XML 精确导航目标列表的映射
     */
    private Map<String, List<SqlToyNavigationTarget>> collectIncludeNavigationTargetsById(@NotNull XmlFile xmlFile) {
        Map<String, List<SqlToyNavigationTarget>> result = new HashMap<>();
        xmlFile.accept(new PsiRecursiveElementWalkingVisitor() {
            /**
             * 访问 XML 文本节点并收集 include 使用位置。
             *
             * @param element 当前 PSI 元素
             */
            @Override
            public void visitElement(@NotNull PsiElement element) {
                if (element instanceof XmlText xmlText) {
                    collectXmlTextIncludes(xmlText, result);
                }

                super.visitElement(element);
            }
        });

        return result;
    }

    /**
     * 收集单个 XML 文本节点中的 include 使用位置。
     *
     * @param xmlText XML 文本节点
     * @param result  include sqlId 到 XML 使用位置列表的映射
     */
    private void collectXmlTextIncludes(
            @NotNull XmlText xmlText,
            @NotNull Map<String, List<SqlToyNavigationTarget>> result
    ) {
        for (SqlToySqlInclude include : includeParser.findIncludes(xmlText)) {
            int targetOffset = getIncludeTargetOffset(xmlText, include);
            PsiElement target = findIncludeTargetElement(xmlText, targetOffset);
            result.computeIfAbsent(include.sqlId(), ignored -> new ArrayList<>())
                    .add(new SqlToyNavigationTarget(include.sqlId(), target, targetOffset));
        }
    }

    /**
     * 查找 include sqlId 对应的可导航 PSI 元素。
     *
     * @param xmlText XML 文本节点
     * @param include include 引用信息
     * @return 可导航的 PSI 元素
     */
    private PsiElement findIncludeTargetElement(@NotNull XmlText xmlText, int targetOffset) {
        PsiElement target = xmlText.getContainingFile().findElementAt(targetOffset);
        return Objects.nonNull(target) ? target : xmlText;
    }

    /**
     * 返回 include sqlId 在文件中的精确起始偏移量。
     *
     * @param xmlText XML 文本节点
     * @param include include 引用信息
     * @return 文件级绝对偏移量
     */
    private int getIncludeTargetOffset(@NotNull XmlText xmlText, @NotNull SqlToySqlInclude include) {
        return xmlText.getTextRange().getStartOffset()
                + include.sqlIdRangeInElement().getStartOffset();
    }
}
