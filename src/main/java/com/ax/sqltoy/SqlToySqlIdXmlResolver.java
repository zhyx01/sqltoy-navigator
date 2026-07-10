package com.ax.sqltoy;

import com.intellij.ide.highlighter.XmlFileType;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.FileTypeIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlText;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 按 sqlId 解析 SqlToy XML SQL 定义。
 *
 * @author ax
 * @date 2026-05-30
 */
final class SqlToySqlIdXmlResolver {

    /**
     * 对常见 SqlToy id 保持较宽松的过滤规则：
     * sql_id
     * module.queryList
     * a-b/c:test
     */
    private static final Pattern SQL_ID_PATTERN = Pattern.compile("[A-Za-z0-9_.$:/\\-]+");

    /**
     * SqlToy sqlId 中适合单词索引的片段。
     */
    private static final Pattern INDEXABLE_WORD_PATTERN = Pattern.compile("[A-Za-z0-9_]+");

    /**
     * 单个 XML 文件内按 sqlId 分组缓存的 XML 定义。
     */
    private static final Key<CachedValue<Map<String, List<SqlIdTarget>>>> XML_TARGETS_CACHE =
            Key.create("SqlToyXmlTargetsCache");

    /**
     * 单个 XML 文件内按大小写不敏感 sqlId 分组缓存的 XML 定义。
     */
    private static final Key<CachedValue<Map<String, List<SqlIdTarget>>>> XML_NORMALIZED_TARGETS_CACHE =
            Key.create("SqlToyXmlNormalizedTargetsCache");

    /**
     * 工具类，不需要创建实例。
     */
    private SqlToySqlIdXmlResolver() {
    }

    /**
     * 检查字符串是否看起来像 SqlToy sqlId。
     *
     * @param value 候选值
     * @return 值是合理 sqlId 候选时返回 true
     */
    static boolean maybeSqlId(String value) {
        if (Objects.isNull(value)) {
            return false;
        }

        // 过长或空白字符串更可能是 SQL 正文或普通文本，不应参与 sqlId 导航。
        if (value.isBlank() || value.length() > 200) {
            return false;
        }

        return SQL_ID_PATTERN.matcher(value).matches();
    }

    /**
     * 查找指定 sqlId 的 XML 定义。
     *
     * @param project 当前项目
     * @param sqlId   要查找的 sqlId
     * @return 匹配的 XML 目标
     */
    static List<SqlIdTarget> findTargets(@NotNull Project project, @NotNull String sqlId) {
        // 依赖文件索引和 PSI 缓存，索引未完成时直接返回空结果。
        if (DumbService.isDumb(project)) {
            return List.of();
        }

        List<SqlIdTarget> result = new ArrayList<>();
        for (XmlFile xmlFile : findCandidateXmlFiles(project, sqlId)) {
            result.addAll(getTargetsById(xmlFile, xmlFile.getName()).getOrDefault(sqlId, List.of()));
        }

        return result;
    }

    /**
     * 查找项目 XML 文件中的全部 SqlToy SQL 定义。
     *
     * @param project 当前项目
     * @return 所有发现的 XML sqlId 目标
     */
    static List<SqlIdTarget> findAllTargets(@NotNull Project project) {
        List<SqlIdTarget> result = new ArrayList<>();

        GlobalSearchScope scope = GlobalSearchScope.projectScope(project);
        Collection<VirtualFile> xmlFiles = FileTypeIndex.getFiles(XmlFileType.INSTANCE, scope);
        PsiManager psiManager = PsiManager.getInstance(project);

        for (VirtualFile virtualFile : xmlFiles) {
            PsiFile psiFile = psiManager.findFile(virtualFile);
            if (!(psiFile instanceof XmlFile xmlFile)) {
                continue;
            }

            getTargetsById(xmlFile, virtualFile.getName()).values().forEach(result::addAll);
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
    private static List<XmlFile> findCandidateXmlFiles(@NotNull Project project, @NotNull String sqlId) {
        // 用可索引片段先缩小 XML 文件范围，再在候选文件里精确解析 <sql id="...">。
        String searchWord = getIndexSearchWord(sqlId);
        if (Objects.isNull(searchWord)) {
            // 没有可索引片段时只能遍历项目内所有 XML 文件。
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
     * 返回项目范围内的所有 XML 文件。
     *
     * @param project 当前项目
     * @return XML PSI 文件
     */
    private static List<XmlFile> findAllXmlFiles(@NotNull Project project) {
        List<XmlFile> result = new ArrayList<>();
        GlobalSearchScope scope = GlobalSearchScope.projectScope(project);
        Collection<VirtualFile> xmlFiles = FileTypeIndex.getFiles(XmlFileType.INSTANCE, scope);
        PsiManager psiManager = PsiManager.getInstance(project);

        for (VirtualFile virtualFile : xmlFiles) {
            PsiFile psiFile = psiManager.findFile(virtualFile);

            if (!(psiFile instanceof XmlFile xmlFile)) {
                continue;
            }

            result.add(xmlFile);
        }

        return result;
    }

    /**
     * 返回单个 XML 文件内按 sqlId 分组缓存的 XML 定义。
     *
     * @param xmlFile  要检查的 XML 文件
     * @param fileName 源 XML 文件名
     * @return sqlId 到 XML 目标列表的映射
     */
    private static Map<String, List<SqlIdTarget>> getTargetsById(
            @NotNull XmlFile xmlFile,
            @NotNull String fileName
    ) {
        // 缓存依赖 XML PSI 文件，编辑 XML 后会自动重新收集 sqlId 定义。
        return CachedValuesManager.getManager(xmlFile.getProject()).getCachedValue(
                xmlFile,
                XML_TARGETS_CACHE,
                () -> CachedValueProvider.Result.create(collectTargetsById(xmlFile, fileName), xmlFile),
                false
        );
    }

    /**
     * 检查指定 XML SQL 标签的 sqlId 是否在当前 XML 文件内重复。
     *
     * @param tag 要检查的 XML SQL 标签
     * @return 当前文件存在大小写不敏感的重复 sqlId 时返回 true
     */
    static boolean hasDuplicateSqlIdInFile(@NotNull XmlTag tag) {
        String sqlId = getSqlId(tag);
        if (Objects.isNull(sqlId)) {
            return false;
        }

        if (!(tag.getContainingFile() instanceof XmlFile xmlFile)) {
            return false;
        }

        List<SqlIdTarget> targets = getTargetsByNormalizedId(xmlFile, xmlFile.getName())
                .get(normalizeSqlId(sqlId));
        return targets != null && targets.size() > 1;
    }

    /**
     * 返回单个 XML 文件内按大小写不敏感 sqlId 分组缓存的 XML 定义。
     *
     * @param xmlFile  要检查的 XML 文件
     * @param fileName 源 XML 文件名
     * @return 规范化 sqlId 到 XML 目标列表的映射
     */
    private static Map<String, List<SqlIdTarget>> getTargetsByNormalizedId(
            @NotNull XmlFile xmlFile,
            @NotNull String fileName
    ) {
        return CachedValuesManager.getManager(xmlFile.getProject()).getCachedValue(
                xmlFile,
                XML_NORMALIZED_TARGETS_CACHE,
                () -> CachedValueProvider.Result.create(
                        groupTargetsByNormalizedId(getTargetsById(xmlFile, fileName)),
                        xmlFile
                ),
                false
        );
    }

    /**
     * 将精确 sqlId 分组转换为大小写不敏感分组。
     *
     * @param targetsById 精确 sqlId 分组
     * @return 规范化 sqlId 到 XML 目标列表的映射
     */
    private static Map<String, List<SqlIdTarget>> groupTargetsByNormalizedId(
            @NotNull Map<String, List<SqlIdTarget>> targetsById
    ) {
        Map<String, List<SqlIdTarget>> targetsByNormalizedId = new HashMap<>();
        for (List<SqlIdTarget> targets : targetsById.values()) {
            for (SqlIdTarget target : targets) {
                targetsByNormalizedId
                        .computeIfAbsent(normalizeSqlId(target.sqlId()), ignored -> new ArrayList<>())
                        .add(target);
            }
        }

        return targetsByNormalizedId;
    }

    /**
     * 对 sqlId 做大小写不敏感比较用的规范化处理。
     *
     * @param sqlId 原始 sqlId
     * @return 规范化后的 sqlId
     */
    private static String normalizeSqlId(@NotNull String sqlId) {
        return sqlId.toLowerCase(Locale.ROOT);
    }

    /**
     * 在单个 XML 文件中查找全部 SqlToy SQL 定义，并按 sqlId 分组。
     *
     * @param xmlFile  要检查的 XML 文件
     * @param fileName 源 XML 文件名
     * @return sqlId 到 XML 目标列表的映射
     */
    private static Map<String, List<SqlIdTarget>> collectTargetsById(
            @NotNull XmlFile xmlFile,
            @NotNull String fileName
    ) {
        List<SqlIdTarget> result = new ArrayList<>();
        XmlTag rootTag = xmlFile.getRootTag();
        if (Objects.isNull(rootTag)) {
            // 非完整 XML 或空文件没有根标签，直接返回空映射。
            return Map.of();
        }

        collectSqlIds(rootTag, fileName, result);

        Map<String, List<SqlIdTarget>> targetsById = new HashMap<>();
        for (SqlIdTarget target : result) {
            targetsById.computeIfAbsent(target.sqlId(), ignored -> new ArrayList<>()).add(target);
        }

        return targetsById;
    }

    /**
     * 选择 sqlId 中最长的可索引单词片段。
     *
     * @param sqlId 要搜索的 sqlId
     * @return 可索引单词；没有单词片段时返回 null
     */
    static String getIndexSearchWord(@NotNull String sqlId) {
        String bestWord = null;
        Matcher matcher = INDEXABLE_WORD_PATTERN.matcher(sqlId);
        while (matcher.find()) {
            String word = matcher.group();
            if (bestWord == null || word.length() > bestWord.length()) {
                // 选最长片段通常能让索引命中文件更少，降低后续 PSI 解析成本。
                bestWord = word;
            }
        }

        return bestWord;
    }

    /**
     * 递归收集 XML 标签下的 sqlId 定义。
     *
     * @param tag      要检查的 XML 标签
     * @param fileName 源 XML 文件名
     * @param result   用于追加目标的列表
     */
    private static void collectSqlIds(
            @NotNull XmlTag tag,
            @NotNull String fileName,
            @NotNull List<SqlIdTarget> result
    ) {
        String sqlId = getSqlId(tag);
        if (sqlId != null) {
            XmlAttribute idAttribute = tag.getAttribute("id");
            XmlAttributeValue valueElement = getSqlIdValueElement(tag);
            // 尽量导航到属性值元素，使光标落在 id 文本上。
            PsiElement navigationTarget = valueElement != null ? valueElement : idAttribute;

            result.add(new SqlIdTarget(sqlId, navigationTarget, fileName, getSqlText(tag)));
        }

        for (XmlTag subTag : tag.getSubTags()) {
            // 递归处理嵌套标签，兼容 SQL 定义被分组或包装的 XML 结构。
            collectSqlIds(subTag, fileName, result);
        }
    }

    /**
     * 返回拥有 XML id 属性值的 SqlToy SQL 标签。
     *
     * @param valueElement XML 属性值元素
     * @return 所属的 SqlToy SQL 标签；如果该值不是 sqlId，则返回 null
     */
    static XmlTag getSqlTagForIdValue(@NotNull XmlAttributeValue valueElement) {
        if (!(valueElement.getParent() instanceof XmlAttribute attribute)) {
            return null;
        }

        // 只接受 id 属性，避免误把其他属性值当成 sqlId。
        if (!"id".equals(attribute.getName())) {
            return null;
        }

        XmlTag tag = attribute.getParent();
        return isSqlToySqlTag(tag) ? tag : null;
    }

    /**
     * 从 SqlToy SQL XML 标签读取 sqlId。
     *
     * @param tag 要检查的 XML 标签
     * @return sqlId 值；如果标签不是 SqlToy SQL 标签，则返回 null
     */
    static String getSqlId(@NotNull XmlTag tag) {
        if (!isSqlToySqlTag(tag)) {
            return null;
        }

        XmlAttribute idAttribute = tag.getAttribute("id");
        return idAttribute != null ? idAttribute.getValue() : null;
    }

    /**
     * 返回包含 sqlId 文本的 XML 属性值元素。
     *
     * @param tag 要检查的 XML 标签
     * @return id 属性值元素；不存在时返回 null
     */
    static XmlAttributeValue getSqlIdValueElement(@NotNull XmlTag tag) {
        XmlAttribute idAttribute = tag.getAttribute("id");
        return idAttribute != null ? idAttribute.getValueElement() : null;
    }

    /**
     * 检查 XML 标签是否为 SqlToy SQL 定义。
     *
     * @param tag 要检查的 XML 标签
     * @return 对 {@code <sql id="...">} 标签返回 true
     */
    static boolean isSqlToySqlTag(@NotNull XmlTag tag) {
        return "sql".equals(tag.getName()) && tag.getAttribute("id") != null;
    }

    /**
     * 提取 SQL 标签中的纯文本 SQL。
     *
     * @param tag SqlToy SQL 标签
     * @return 去除外层空白和 CDATA 包装后的 SQL 文本
     */
    private static String getSqlText(@NotNull XmlTag tag) {
        String text = collectSqlText(tag);
        TextRange textRange = SqlToyXmlSqlTextRanges.getSqlTextRange(text);
        if (textRange.isEmpty()) {
            return "";
        }

        return textRange.substring(text);
    }

    /**
     * 递归收集 XML 标签中的文本节点，排除 value 等 XML 包装标签本身。
     *
     * @param tag SqlToy SQL 标签
     * @return 只包含 XML 文本节点的内容
     */
    private static String collectSqlText(@NotNull XmlTag tag) {
        StringBuilder result = new StringBuilder();
        appendSqlText(tag, result);
        return result.toString();
    }

    /**
     * 按 PSI 子节点顺序收集 SQL 文本，避免把 XML 标签名展示到悬浮提示中。
     *
     * @param tag    当前 XML 标签
     * @param result 文本收集结果
     */
    private static void appendSqlText(@NotNull XmlTag tag, @NotNull StringBuilder result) {
        PsiElement child = tag.getFirstChild();
        while (child != null) {
            if (child instanceof XmlText xmlText) {
                result.append(xmlText.getText());
            } else if (child instanceof XmlTag subTag) {
                appendSqlText(subTag, result);
            }

            child = child.getNextSibling();
        }
    }

    /**
     * 一个 SqlToy XML sqlId 定义的导航目标。
     *
     * @param sqlId    sqlId 值
     * @param element  作为导航目标的 PSI 元素
     * @param fileName 源 XML 文件名
     * @param sqlText  SQL 纯文本内容
     */
    record SqlIdTarget(
            @NotNull String sqlId,
            @NotNull PsiElement element,
            @NotNull String fileName,
            @NotNull String sqlText
    ) {
    }
}
