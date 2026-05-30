package com.ax.sqltoy;

import com.intellij.ide.highlighter.XmlFileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.FileTypeIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Finds SqlToy XML definitions.
 *
 * Current matching rule:
 *     <sql id="xxx"> ... </sql>
 *
 * If your project has other SqlToy tags, extend isSqlToySqlTag().
 */
final class SqlToySqlIdXmlResolver {

    /**
     * Keep the filter loose enough for common SqlToy ids:
     * trace_param_fault_moduleId
     * module.queryList
     * a-b/c:test
     */
    private static final Pattern SQL_ID_PATTERN = Pattern.compile("[A-Za-z0-9_.$:/\\-]+");

    private SqlToySqlIdXmlResolver() {
    }

    static boolean maybeSqlId(String value) {
        if (value == null) {
            return false;
        }

        if (value.isBlank() || value.length() > 200) {
            return false;
        }

        return SQL_ID_PATTERN.matcher(value).matches();
    }

    static List<SqlIdTarget> findTargets(@NotNull Project project, @NotNull String sqlId) {
        List<SqlIdTarget> matched = new ArrayList<>();

        for (SqlIdTarget target : findAllTargets(project)) {
            if (sqlId.equals(target.sqlId())) {
                matched.add(target);
            }
        }

        return matched;
    }

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

            XmlTag rootTag = xmlFile.getRootTag();
            if (rootTag == null) {
                continue;
            }

            collectSqlIds(rootTag, virtualFile.getName(), result);
        }

        return result;
    }

    private static void collectSqlIds(
            @NotNull XmlTag tag,
            @NotNull String fileName,
            @NotNull List<SqlIdTarget> result
    ) {
        String sqlId = getSqlId(tag);
        if (sqlId != null) {
            XmlAttribute idAttribute = tag.getAttribute("id");
            XmlAttributeValue valueElement = getSqlIdValueElement(tag);
            PsiElement navigationTarget = valueElement != null ? valueElement : idAttribute;

            result.add(new SqlIdTarget(sqlId, navigationTarget, fileName));
        }

        for (XmlTag subTag : tag.getSubTags()) {
            collectSqlIds(subTag, fileName, result);
        }
    }

    static XmlTag getSqlTagForIdValue(@NotNull XmlAttributeValue valueElement) {
        if (!(valueElement.getParent() instanceof XmlAttribute attribute)) {
            return null;
        }

        if (!"id".equals(attribute.getName())) {
            return null;
        }

        XmlTag tag = attribute.getParent();
        return isSqlToySqlTag(tag) ? tag : null;
    }

    static String getSqlId(@NotNull XmlTag tag) {
        if (!isSqlToySqlTag(tag)) {
            return null;
        }

        XmlAttribute idAttribute = tag.getAttribute("id");
        return idAttribute != null ? idAttribute.getValue() : null;
    }

    static XmlAttributeValue getSqlIdValueElement(@NotNull XmlTag tag) {
        XmlAttribute idAttribute = tag.getAttribute("id");
        return idAttribute != null ? idAttribute.getValueElement() : null;
    }

    static boolean isSqlToySqlTag(@NotNull XmlTag tag) {
        return "sql".equals(tag.getName()) && tag.getAttribute("id") != null;
    }

    record SqlIdTarget(
            @NotNull String sqlId,
            @NotNull PsiElement element,
            @NotNull String fileName
    ) {
    }
}
