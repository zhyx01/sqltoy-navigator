package com.ax.sqltoy;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.xml.XmlText;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 解析 SqlToy SQL 文本中的 @include("sqlId") 引用。
 *
 * @author ax
 * @date 2026-07-09
 */
final class SqlToySqlIncludeParser {

    /**
     * 匹配 SqlToy include 语法，并只捕获引号内的 sqlId 文本。
     */
    private static final Pattern INCLUDE_PATTERN = Pattern.compile(
            "@include\\s*\\(\\s*([\"'])([A-Za-z0-9_.$:/\\-]+)\\1\\s*\\)",
            Pattern.CASE_INSENSITIVE
    );

    /**
     * 从 XML 文本节点中提取 include 引用。
     *
     * @param xmlText XML 文本节点
     * @return include 引用列表
     */
    List<SqlToySqlInclude> findIncludes(@NotNull XmlText xmlText) {
        if (Objects.isNull(SqlToyXmlSqlTextRanges.getSqlToySqlTag(xmlText))) {
            return List.of();
        }

        String text = xmlText.getText();
        TextRange sqlTextRange = SqlToyXmlSqlTextRanges.getSqlTextRange(text);
        if (sqlTextRange.isEmpty()) {
            return List.of();
        }

        List<SqlToySqlInclude> result = new ArrayList<>();
        Matcher matcher = INCLUDE_PATTERN.matcher(text);
        matcher.region(sqlTextRange.getStartOffset(), sqlTextRange.getEndOffset());

        while (matcher.find()) {
            String sqlId = matcher.group(2);
            if (!SqlToySqlIdXmlResolver.maybeSqlId(sqlId)) {
                continue;
            }

            result.add(new SqlToySqlInclude(
                    sqlId,
                    TextRange.create(matcher.start(2), matcher.end(2))
            ));
        }

        return result;
    }
}
