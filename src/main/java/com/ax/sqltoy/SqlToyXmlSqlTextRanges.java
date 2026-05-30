package com.ax.sqltoy;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlText;
import org.jetbrains.annotations.NotNull;

final class SqlToyXmlSqlTextRanges {

    private static final String CDATA_START = "<![CDATA[";
    private static final String CDATA_END = "]]>";

    private SqlToyXmlSqlTextRanges() {
    }

    static XmlTag getSqlToySqlTag(@NotNull XmlText xmlText) {
        PsiElement current = xmlText;
        while (current != null) {
            if (current instanceof XmlTag tag && SqlToySqlIdXmlResolver.isSqlToySqlTag(tag)) {
                return tag;
            }

            current = current.getParent();
        }

        return null;
    }

    static TextRange getSqlTextRange(@NotNull String text) {
        int start = 0;
        int end = text.length();

        start = skipLeadingWhitespace(text, start, end);
        end = skipTrailingWhitespace(text, start, end);

        if (hasCDataWrapper(text, start, end)) {
            start += CDATA_START.length();
            end -= CDATA_END.length();

            start = skipLeadingWhitespace(text, start, end);
            end = skipTrailingWhitespace(text, start, end);
        }

        return TextRange.create(start, end);
    }

    private static boolean hasCDataWrapper(@NotNull String text, int start, int end) {
        return end - start >= CDATA_START.length() + CDATA_END.length()
                && text.startsWith(CDATA_START, start)
                && text.startsWith(CDATA_END, end - CDATA_END.length());
    }

    private static int skipLeadingWhitespace(@NotNull String text, int start, int end) {
        while (start < end && Character.isWhitespace(text.charAt(start))) {
            start++;
        }

        return start;
    }

    private static int skipTrailingWhitespace(@NotNull String text, int start, int end) {
        while (end > start && Character.isWhitespace(text.charAt(end - 1))) {
            end--;
        }

        return end;
    }
}
