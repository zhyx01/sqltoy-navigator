package com.ax.sqltoy;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlText;
import org.jetbrains.annotations.NotNull;

/**
 * 计算 SqlToy XML 文本节点中的 SQL 文本范围。
 *
 * @author ax
 * @date 2026-05-30
 */
final class SqlToyXmlSqlTextRanges {

    /**
     * CDATA 开始标记。
     */
    private static final String CDATA_START = "<![CDATA[";

    /**
     * CDATA 结束标记。
     */
    private static final String CDATA_END = "]]>";

    /**
     * 工具类，不需要创建实例。
     */
    private SqlToyXmlSqlTextRanges() {
    }

    /**
     * 查找 XML 文本节点最近的父级 SqlToy SQL 标签。
     *
     * @param xmlText XML 文本节点
     * @return 匹配的 SQL 标签；文本不在 SqlToy SQL 中时返回 null
     */
    static XmlTag getSqlToySqlTag(@NotNull XmlText xmlText) {
        PsiElement current = xmlText;
        while (current != null) {
            // 向上遍历，因为 XML 文本可能被中间 PSI 节点包裹。
            if (current instanceof XmlTag tag && SqlToySqlIdXmlResolver.isSqlToySqlTag(tag)) {
                return tag;
            }

            current = current.getParent();
        }

        return null;
    }

    /**
     * 返回 XML 文本节点中有意义的 SQL 范围。
     *
     * @param text XML 文本内容
     * @return 排除周围空白和可选 CDATA 包裹后的范围
     */
    static TextRange getSqlTextRange(@NotNull String text) {
        int start = 0;
        int end = text.length();

        // 检查 CDATA 标记前先裁剪外围空白。
        start = skipLeadingWhitespace(text, start, end);
        end = skipTrailingWhitespace(text, start, end);

        if (hasCDataWrapper(text, start, end)) {
            start += CDATA_START.length();
            end -= CDATA_END.length();

            // 裁剪 CDATA 内部空白，使高亮只覆盖 SQL 词法单元。
            start = skipLeadingWhitespace(text, start, end);
            end = skipTrailingWhitespace(text, start, end);
        }

        return TextRange.create(start, end);
    }

    /**
     * 检查当前裁剪后的范围是否被 CDATA 包裹。
     *
     * @param text 源文本
     * @param start 裁剪后的起始偏移量
     * @param end 裁剪后的结束偏移量
     * @return CDATA 标记包裹该范围时返回 true
     */
    private static boolean hasCDataWrapper(@NotNull String text, int start, int end) {
        return end - start >= CDATA_START.length() + CDATA_END.length()
                && text.startsWith(CDATA_START, start)
                && text.startsWith(CDATA_END, end - CDATA_END.length());
    }

    /**
     * 从范围起点跳过空白字符。
     *
     * @param text 源文本
     * @param start 起始偏移量
     * @param end 结束偏移量
     * @return 第一个非空白字符的偏移量
     */
    private static int skipLeadingWhitespace(@NotNull String text, int start, int end) {
        while (start < end && Character.isWhitespace(text.charAt(start))) {
            start++;
        }

        return start;
    }

    /**
     * 从范围终点向前跳过空白字符。
     *
     * @param text 源文本
     * @param start 起始偏移量
     * @param end 结束偏移量
     * @return 裁剪后的结束偏移量
     */
    private static int skipTrailingWhitespace(@NotNull String text, int start, int end) {
        while (end > start && Character.isWhitespace(text.charAt(end - 1))) {
            end--;
        }

        return end;
    }
}
