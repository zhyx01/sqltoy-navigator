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
 * Resolves SqlToy XML SQL definitions by sqlId.
 *
 * @author ax
 * @date 2026-05-30
 */
final class SqlToySqlIdXmlResolver {

    /**
     * Keep the filter loose enough for common SqlToy ids:
     * trace_param_fault_moduleId
     * module.queryList
     * a-b/c:test
     */
    private static final Pattern SQL_ID_PATTERN = Pattern.compile("[A-Za-z0-9_.$:/\\-]+");

    /**
     * Utility class; instances are not needed.
     */
    private SqlToySqlIdXmlResolver() {
    }

    /**
     * Checks whether a string looks like a SqlToy sqlId.
     *
     * @param value candidate value
     * @return true when the value is a reasonable sqlId candidate
     */
    static boolean maybeSqlId(String value) {
        if (value == null) {
            return false;
        }

        if (value.isBlank() || value.length() > 200) {
            return false;
        }

        return SQL_ID_PATTERN.matcher(value).matches();
    }

    /**
     * Finds XML definitions for a specific sqlId.
     *
     * @param project current project
     * @param sqlId sqlId to find
     * @return matching XML targets
     */
    static List<SqlIdTarget> findTargets(@NotNull Project project, @NotNull String sqlId) {
        List<SqlIdTarget> matched = new ArrayList<>();

        // Reuse the full scan so duplicate sqlId definitions are preserved.
        for (SqlIdTarget target : findAllTargets(project)) {
            if (sqlId.equals(target.sqlId())) {
                matched.add(target);
            }
        }

        return matched;
    }

    /**
     * Finds every SqlToy SQL definition in project XML files.
     *
     * @param project current project
     * @return all discovered XML sqlId targets
     */
    static List<SqlIdTarget> findAllTargets(@NotNull Project project) {
        List<SqlIdTarget> result = new ArrayList<>();

        // FileTypeIndex avoids scanning non-XML files.
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

    /**
     * Recursively collects sqlId definitions under an XML tag.
     *
     * @param tag XML tag to inspect
     * @param fileName source XML file name
     * @param result target list to append to
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
            // Navigate to the attribute value when possible so the caret lands on the id text.
            PsiElement navigationTarget = valueElement != null ? valueElement : idAttribute;

            result.add(new SqlIdTarget(sqlId, navigationTarget, fileName));
        }

        for (XmlTag subTag : tag.getSubTags()) {
            collectSqlIds(subTag, fileName, result);
        }
    }

    /**
     * Returns the SqlToy SQL tag that owns an XML id attribute value.
     *
     * @param valueElement XML attribute value element
     * @return owning SqlToy SQL tag, or null when the value is not a sqlId
     */
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

    /**
     * Reads the sqlId from a SqlToy SQL XML tag.
     *
     * @param tag XML tag to inspect
     * @return sqlId value, or null when the tag is not a SqlToy SQL tag
     */
    static String getSqlId(@NotNull XmlTag tag) {
        if (!isSqlToySqlTag(tag)) {
            return null;
        }

        XmlAttribute idAttribute = tag.getAttribute("id");
        return idAttribute != null ? idAttribute.getValue() : null;
    }

    /**
     * Returns the XML attribute value element that contains the sqlId text.
     *
     * @param tag XML tag to inspect
     * @return id attribute value element, or null when absent
     */
    static XmlAttributeValue getSqlIdValueElement(@NotNull XmlTag tag) {
        XmlAttribute idAttribute = tag.getAttribute("id");
        return idAttribute != null ? idAttribute.getValueElement() : null;
    }

    /**
     * Checks whether an XML tag is a SqlToy SQL definition.
     *
     * @param tag XML tag to inspect
     * @return true for {@code <sql id="...">} tags
     */
    static boolean isSqlToySqlTag(@NotNull XmlTag tag) {
        return "sql".equals(tag.getName()) && tag.getAttribute("id") != null;
    }

    /**
     * Navigation target for one SqlToy XML sqlId definition.
     *
     * @param sqlId sqlId value
     * @param element PSI element used as the navigation destination
     * @param fileName source XML file name
     */
    record SqlIdTarget(
            @NotNull String sqlId,
            @NotNull PsiElement element,
            @NotNull String fileName
    ) {
    }
}
