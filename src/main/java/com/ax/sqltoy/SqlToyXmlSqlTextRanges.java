package com.ax.sqltoy;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlText;
import org.jetbrains.annotations.NotNull;

/**
 * Calculates SQL text ranges inside SqlToy XML text nodes.
 *
 * @author ax
 * @date 2026-05-30
 */
final class SqlToyXmlSqlTextRanges {

    /**
     * CDATA opening marker.
     */
    private static final String CDATA_START = "<![CDATA[";

    /**
     * CDATA closing marker.
     */
    private static final String CDATA_END = "]]>";

    /**
     * Utility class; instances are not needed.
     */
    private SqlToyXmlSqlTextRanges() {
    }

    /**
     * Finds the nearest parent SqlToy SQL tag for an XML text node.
     *
     * @param xmlText XML text node
     * @return matching SQL tag, or null when the text is outside SqlToy SQL
     */
    static XmlTag getSqlToySqlTag(@NotNull XmlText xmlText) {
        PsiElement current = xmlText;
        while (current != null) {
            // Walk upward because XML text can be wrapped by intermediate PSI nodes.
            if (current instanceof XmlTag tag && SqlToySqlIdXmlResolver.isSqlToySqlTag(tag)) {
                return tag;
            }

            current = current.getParent();
        }

        return null;
    }

    /**
     * Returns the meaningful SQL range inside an XML text node.
     *
     * @param text XML text content
     * @return range excluding surrounding whitespace and optional CDATA wrapper
     */
    static TextRange getSqlTextRange(@NotNull String text) {
        int start = 0;
        int end = text.length();

        // Trim outer whitespace before checking for CDATA markers.
        start = skipLeadingWhitespace(text, start, end);
        end = skipTrailingWhitespace(text, start, end);

        if (hasCDataWrapper(text, start, end)) {
            start += CDATA_START.length();
            end -= CDATA_END.length();

            // Trim whitespace inside CDATA so only SQL tokens are highlighted.
            start = skipLeadingWhitespace(text, start, end);
            end = skipTrailingWhitespace(text, start, end);
        }

        return TextRange.create(start, end);
    }

    /**
     * Checks whether the current trimmed range is wrapped in CDATA.
     *
     * @param text source text
     * @param start trimmed start offset
     * @param end trimmed end offset
     * @return true when CDATA markers wrap the range
     */
    private static boolean hasCDataWrapper(@NotNull String text, int start, int end) {
        return end - start >= CDATA_START.length() + CDATA_END.length()
                && text.startsWith(CDATA_START, start)
                && text.startsWith(CDATA_END, end - CDATA_END.length());
    }

    /**
     * Skips whitespace from the start of a range.
     *
     * @param text source text
     * @param start start offset
     * @param end end offset
     * @return first non-whitespace offset
     */
    private static int skipLeadingWhitespace(@NotNull String text, int start, int end) {
        while (start < end && Character.isWhitespace(text.charAt(start))) {
            start++;
        }

        return start;
    }

    /**
     * Skips whitespace from the end of a range.
     *
     * @param text source text
     * @param start start offset
     * @param end end offset
     * @return end offset after trimming
     */
    private static int skipTrailingWhitespace(@NotNull String text, int start, int end) {
        while (end > start && Character.isWhitespace(text.charAt(end - 1))) {
            end--;
        }

        return end;
    }
}
