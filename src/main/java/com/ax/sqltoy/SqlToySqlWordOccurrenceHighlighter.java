package com.ax.sqltoy;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.event.CaretEvent;
import com.intellij.openapi.editor.event.CaretListener;
import com.intellij.openapi.editor.event.EditorFactoryEvent;
import com.intellij.openapi.editor.event.EditorFactoryListener;
import com.intellij.openapi.editor.event.SelectionEvent;
import com.intellij.openapi.editor.event.SelectionListener;
import com.intellij.openapi.editor.markup.HighlighterLayer;
import com.intellij.openapi.editor.markup.HighlighterTargetArea;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlText;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * 在当前 SqlToy SQL 块内高亮所选文本的整词出现位置。
 *
 * @author ax
 * @date 2026-06-01
 */
public final class SqlToySqlWordOccurrenceHighlighter implements EditorFactoryListener, DumbAware {

    /**
     * 选择变化后自动扫描的 SQL 文本范围上限。
     */
    private static final int MAX_SQL_LENGTH = 200_000;

    /**
     * 自动高亮的出现次数上限。
     */
    private static final int MAX_OCCURRENCES = 1_000;

    /**
     * 将这些高亮放在真实编辑器选区层下方。
     */
    private static final int HIGHLIGHT_LAYER = HighlighterLayer.SELECTION - 1;

    /**
     * 按编辑器保存的监听器和高亮器。
     */
    private final Map<Editor, EditorState> editorStates = new WeakHashMap<>();

    /**
     * 为新创建的编辑器安装选区监听器。
     *
     * @param event 编辑器创建事件
     */
    @Override
    public void editorCreated(@NotNull EditorFactoryEvent event) {
        Editor editor = event.getEditor();
        SelectionListener selectionListener = new SelectionListener() {
            /**
             * 选区内容变化时刷新当前编辑器内的整词高亮。
             *
             * @param event 选区变化事件
             */
            @Override
            public void selectionChanged(@NotNull SelectionEvent event) {
                updateHighlights(editor);
            }
        };
        CaretListener caretListener = new CaretListener() {
            /**
             * 光标移动且没有选区时清理当前编辑器内的整词高亮。
             *
             * @param event 光标变化事件
             */
            @Override
            public void caretPositionChanged(@NotNull CaretEvent event) {
                if (!editor.getSelectionModel().hasSelection()) {
                    clearHighlights(editor);
                }
            }
        };

        editorStates.put(editor, new EditorState(selectionListener, caretListener, new ArrayList<>()));
        editor.getSelectionModel().addSelectionListener(selectionListener);
        editor.getCaretModel().addCaretListener(caretListener);
    }

    /**
     * 编辑器释放时移除监听器和高亮器。
     *
     * @param event 编辑器释放事件
     */
    @Override
    public void editorReleased(@NotNull EditorFactoryEvent event) {
        Editor editor = event.getEditor();
        EditorState state = editorStates.remove(editor);
        if (state == null) {
            return;
        }

        removeHighlights(editor, state);
        editor.getSelectionModel().removeSelectionListener(state.selectionListener());
        editor.getCaretModel().removeCaretListener(state.caretListener());
    }

    /**
     * 根据当前编辑器选区重新计算出现位置高亮。
     *
     * @param editor 当前编辑器
     */
    private void updateHighlights(@NotNull Editor editor) {
        EditorState state = editorStates.get(editor);
        if (state == null) {
            return;
        }

        // 选区每次变化都先清掉旧高亮，避免残留范围跟当前选区不一致。
        removeHighlights(editor, state);
        String selectedText = editor.getSelectionModel().getSelectedText();
        if (!isHighlightableWord(selectedText)) {
            return;
        }

        int selectionStart = editor.getSelectionModel().getSelectionStart();
        int selectionEnd = editor.getSelectionModel().getSelectionEnd();
        TextRange sqlRange = findCurrentSqlRange(editor, selectionStart, selectionEnd);
        if (sqlRange == null || !containsRange(sqlRange, selectionStart, selectionEnd)) {
            // 只在同一个 SqlToy SQL 片段内部高亮，跨出 SQL 范围的选区直接忽略。
            return;
        }

        if (sqlRange.getLength() > MAX_SQL_LENGTH) {
            // 大 SQL 片段上限用于保护编辑器，不让选区变化触发过重扫描。
            return;
        }

        List<TextRange> occurrences = findOccurrences(
                editor.getDocument().getCharsSequence(),
                sqlRange,
                selectedText
        );
        if (occurrences.size() > MAX_OCCURRENCES) {
            // 出现次数过多时不画高亮，避免创建大量 RangeHighlighter。
            return;
        }

        TextAttributes attributes = getOccurrenceAttributes(editor);
        for (TextRange occurrence : occurrences) {
            // 使用精确范围高亮，避免整行或额外文本被渲染。
            RangeHighlighter highlighter = editor.getMarkupModel().addRangeHighlighter(
                    occurrence.getStartOffset(),
                    occurrence.getEndOffset(),
                    HIGHLIGHT_LAYER,
                    attributes,
                    HighlighterTargetArea.EXACT_RANGE
            );
            state.highlighters().add(highlighter);
        }
    }

    /**
     * 清理编辑器中的出现位置高亮。
     *
     * @param editor 当前编辑器
     */
    private void clearHighlights(@NotNull Editor editor) {
        EditorState state = editorStates.get(editor);
        if (state != null) {
            removeHighlights(editor, state);
        }
    }

    /**
     * 从编辑器中移除所有已保存的范围高亮器。
     *
     * @param editor 当前编辑器
     * @param state 编辑器状态
     */
    private static void removeHighlights(@NotNull Editor editor, @NotNull EditorState state) {
        for (RangeHighlighter highlighter : state.highlighters()) {
            editor.getMarkupModel().removeHighlighter(highlighter);
        }
        state.highlighters().clear();
    }

    /**
     * 查找选区所在的当前 SqlToy SQL 范围。
     *
     * @param editor 当前编辑器
     * @param selectionStart 选区起始偏移量
     * @param selectionEnd 选区结束偏移量
     * @return 编辑器偏移量中的 SQL 文本范围；不在 SqlToy SQL 中时返回 null
     */
    private static @Nullable TextRange findCurrentSqlRange(
            @NotNull Editor editor,
            int selectionStart,
            int selectionEnd
    ) {
        Document document = editor.getDocument();
        Project project = editor.getProject();
        if (project == null) {
            return null;
        }

        PsiFile psiFile = PsiDocumentManager.getInstance(project).getPsiFile(document);
        if (psiFile == null) {
            return null;
        }

        if (psiFile instanceof SqlToySqlFile) {
            // 已经处在注入后的 SqlToy SQL 虚拟文件中时，整个文档都是 SQL 范围。
            return TextRange.create(0, document.getTextLength());
        }

        XmlText xmlText = findXmlTextAt(psiFile, selectionStart);
        if (xmlText == null && selectionEnd > selectionStart) {
            // 选区结束偏移量是开区间，用 end - 1 才能落到实际选中文本上。
            xmlText = findXmlTextAt(psiFile, selectionEnd - 1);
        }

        if (xmlText == null || SqlToyXmlSqlTextRanges.getSqlToySqlTag(xmlText) == null) {
            return null;
        }

        TextRange sqlTextRange = SqlToyXmlSqlTextRanges.getSqlTextRange(xmlText.getText());
        if (sqlTextRange.isEmpty()) {
            return null;
        }

        // XML 文本内的相对范围需要平移到编辑器文档坐标。
        return sqlTextRange.shiftRight(xmlText.getTextRange().getStartOffset());
    }

    /**
     * 查找文档偏移量处的 XML 文本节点。
     *
     * @param psiFile 当前 PSI 文件
     * @param offset 文档偏移量
     * @return XML 文本节点；偏移量不在 XML 文本中时返回 null
     */
    private static @Nullable XmlText findXmlTextAt(@NotNull PsiFile psiFile, int offset) {
        if (psiFile.getTextLength() == 0) {
            return null;
        }

        int safeOffset = Math.max(0, Math.min(offset, psiFile.getTextLength() - 1));
        PsiElement current = psiFile.findElementAt(safeOffset);
        while (current != null) {
            if (current instanceof XmlText xmlText) {
                return xmlText;
            }
            current = current.getParent();
        }

        return null;
    }

    /**
     * 在 SQL 范围内查找忽略大小写的整词出现位置。
     *
     * @param text 完整编辑器文本
     * @param sqlRange 要扫描的 SQL 范围
     * @param word 所选单词
     * @return 编辑器偏移量中的出现位置范围
     */
    private static @NotNull List<TextRange> findOccurrences(
            @NotNull CharSequence text,
            @NotNull TextRange sqlRange,
            @NotNull String word
    ) {
        List<TextRange> occurrences = new ArrayList<>();
        int wordLength = word.length();
        int lastStart = sqlRange.getEndOffset() - wordLength;

        for (int offset = sqlRange.getStartOffset(); offset <= lastStart; offset++) {
            if (!isWholeWordMatch(text, offset, word, sqlRange)) {
                continue;
            }

            occurrences.add(TextRange.create(offset, offset + wordLength));
            if (occurrences.size() > MAX_OCCURRENCES) {
                return occurrences;
            }

            // 已匹配的单词内部不用重复检查，下一个循环从匹配末尾之后继续。
            offset += wordLength - 1;
        }

        return occurrences;
    }

    /**
     * 检查指定偏移量处是否为忽略大小写的整词匹配。
     *
     * @param text 完整编辑器文本
     * @param offset 可能匹配的偏移量
     * @param word 所选单词
     * @param sqlRange 正在扫描的 SQL 范围
     * @return 所选单词在该偏移量完整匹配时返回 true
     */
    private static boolean isWholeWordMatch(
            @NotNull CharSequence text,
            int offset,
            @NotNull String word,
            @NotNull TextRange sqlRange
    ) {
        int endOffset = offset + word.length();
        // 前后都是标识符字符时属于更长单词的一部分，不算整词匹配。
        if (offset > sqlRange.getStartOffset() && isIdentifierPart(text.charAt(offset - 1))) {
            return false;
        }
        if (endOffset < sqlRange.getEndOffset() && isIdentifierPart(text.charAt(endOffset))) {
            return false;
        }

        for (int i = 0; i < word.length(); i++) {
            if (!equalsIgnoreCase(text.charAt(offset + i), word.charAt(i))) {
                return false;
            }
        }

        return true;
    }

    /**
     * 检查所选文本是否适合做整词出现位置高亮。
     *
     * @param text 所选文本
     * @return 所选文本是单个类似 SQL 标识符的单词时返回 true
     */
    private static boolean isHighlightableWord(@Nullable String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }

        for (int i = 0; i < text.length(); i++) {
            if (!isIdentifierPart(text.charAt(i))) {
                return false;
            }
        }

        return true;
    }

    /**
     * 检查一个范围是否包含另一个范围。
     *
     * @param container 外层范围
     * @param start 内层起始偏移量
     * @param end 内层结束偏移量
     * @return 内层范围完全位于外层范围内时返回 true
     */
    private static boolean containsRange(@NotNull TextRange container, int start, int end) {
        return start >= container.getStartOffset() && end <= container.getEndOffset();
    }

    /**
     * 检查字符是否属于类似 SQL 标识符的单词。
     *
     * @param c 要检查的字符
     * @return 字符属于类似标识符的单词时返回 true
     */
    private static boolean isIdentifierPart(char c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == '$';
    }

    /**
     * 忽略大小写比较两个字符。
     *
     * @param left 第一个字符
     * @param right 第二个字符
     * @return 两个字符忽略大小写后相等时返回 true
     */
    private static boolean equalsIgnoreCase(char left, char right) {
        return Character.toUpperCase(left) == Character.toUpperCase(right)
                || Character.toLowerCase(left) == Character.toLowerCase(right);
    }

    /**
     * 从当前配色方案解析出现位置高亮属性。
     *
     * @param editor 当前编辑器
     * @return 出现位置高亮的文本属性
     */
    private static @NotNull TextAttributes getOccurrenceAttributes(@NotNull Editor editor) {
        // 优先复用 IDE 当前主题的“光标下标识符”样式，缺失时再用备用背景色。
        TextAttributes attributes = editor.getColorsScheme().getAttributes(
                EditorColors.IDENTIFIER_UNDER_CARET_ATTRIBUTES
        );
        return attributes == null ? createFallbackAttributes() : attributes;
    }

    /**
     * 创建不依赖配色方案的备用背景高亮。
     *
     * @return 备用文本属性
     */
    private static @NotNull TextAttributes createFallbackAttributes() {
        TextAttributes attributes = new TextAttributes();
        attributes.setBackgroundColor(new Color(255, 236, 150));
        return attributes;
    }

    /**
     * 单个编辑器拥有的监听器和高亮器。
     *
     * @param selectionListener 安装在编辑器上的选区监听器
     * @param caretListener 安装在编辑器上的光标监听器
     * @param highlighters 当前活动的范围高亮器
     */
    private record EditorState(
            @NotNull SelectionListener selectionListener,
            @NotNull CaretListener caretListener,
            @NotNull List<RangeHighlighter> highlighters
    ) {
    }
}
