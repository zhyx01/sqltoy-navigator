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
 * Highlights whole-word occurrences of the selected text inside the current SqlToy SQL block.
 *
 * @author ax
 * @date 2026-06-01
 */
public final class SqlToySqlWordOccurrenceHighlighter implements EditorFactoryListener, DumbAware {

    /**
     * Maximum SQL text range scanned automatically after a selection change.
     */
    private static final int MAX_SQL_LENGTH = 200_000;

    /**
     * Maximum occurrence count highlighted automatically.
     */
    private static final int MAX_OCCURRENCES = 1_000;

    /**
     * Keep these highlights below the real editor selection layer.
     */
    private static final int HIGHLIGHT_LAYER = HighlighterLayer.SELECTION - 1;

    /**
     * Per-editor listeners and highlighters.
     */
    private final Map<Editor, EditorState> editorStates = new WeakHashMap<>();

    /**
     * Installs selection listeners for newly created editors.
     *
     * @param event editor creation event
     */
    @Override
    public void editorCreated(@NotNull EditorFactoryEvent event) {
        Editor editor = event.getEditor();
        SelectionListener selectionListener = new SelectionListener() {
            @Override
            public void selectionChanged(@NotNull SelectionEvent event) {
                updateHighlights(editor);
            }
        };
        CaretListener caretListener = new CaretListener() {
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
     * Removes listeners and highlighters when an editor is released.
     *
     * @param event editor release event
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
     * Recomputes occurrence highlights for the current editor selection.
     *
     * @param editor current editor
     */
    private void updateHighlights(@NotNull Editor editor) {
        EditorState state = editorStates.get(editor);
        if (state == null) {
            return;
        }

        removeHighlights(editor, state);
        String selectedText = editor.getSelectionModel().getSelectedText();
        if (!isHighlightableWord(selectedText)) {
            return;
        }

        int selectionStart = editor.getSelectionModel().getSelectionStart();
        int selectionEnd = editor.getSelectionModel().getSelectionEnd();
        TextRange sqlRange = findCurrentSqlRange(editor, selectionStart, selectionEnd);
        if (sqlRange == null || !containsRange(sqlRange, selectionStart, selectionEnd)) {
            return;
        }

        if (sqlRange.getLength() > MAX_SQL_LENGTH) {
            return;
        }

        List<TextRange> occurrences = findOccurrences(
                editor.getDocument().getCharsSequence(),
                sqlRange,
                selectedText
        );
        if (occurrences.size() > MAX_OCCURRENCES) {
            return;
        }

        TextAttributes attributes = getOccurrenceAttributes(editor);
        for (TextRange occurrence : occurrences) {
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
     * Clears occurrence highlights for an editor.
     *
     * @param editor current editor
     */
    private void clearHighlights(@NotNull Editor editor) {
        EditorState state = editorStates.get(editor);
        if (state != null) {
            removeHighlights(editor, state);
        }
    }

    /**
     * Removes all stored range highlighters from an editor.
     *
     * @param editor current editor
     * @param state editor state
     */
    private static void removeHighlights(@NotNull Editor editor, @NotNull EditorState state) {
        for (RangeHighlighter highlighter : state.highlighters()) {
            editor.getMarkupModel().removeHighlighter(highlighter);
        }
        state.highlighters().clear();
    }

    /**
     * Finds the current SqlToy SQL range for the selection.
     *
     * @param editor current editor
     * @param selectionStart selection start offset
     * @param selectionEnd selection end offset
     * @return SQL text range in editor offsets, or null outside SqlToy SQL
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
            return TextRange.create(0, document.getTextLength());
        }

        XmlText xmlText = findXmlTextAt(psiFile, selectionStart);
        if (xmlText == null && selectionEnd > selectionStart) {
            xmlText = findXmlTextAt(psiFile, selectionEnd - 1);
        }

        if (xmlText == null || SqlToyXmlSqlTextRanges.getSqlToySqlTag(xmlText) == null) {
            return null;
        }

        TextRange sqlTextRange = SqlToyXmlSqlTextRanges.getSqlTextRange(xmlText.getText());
        if (sqlTextRange.isEmpty()) {
            return null;
        }

        return sqlTextRange.shiftRight(xmlText.getTextRange().getStartOffset());
    }

    /**
     * Finds the XML text node at a document offset.
     *
     * @param psiFile current PSI file
     * @param offset document offset
     * @return XML text node, or null when the offset is outside XML text
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
     * Finds case-insensitive whole-word occurrences inside a SQL range.
     *
     * @param text full editor text
     * @param sqlRange SQL range to scan
     * @param word selected word
     * @return occurrence ranges in editor offsets
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

            offset += wordLength - 1;
        }

        return occurrences;
    }

    /**
     * Checks a case-insensitive whole-word match at an offset.
     *
     * @param text full editor text
     * @param offset possible match offset
     * @param word selected word
     * @param sqlRange SQL range being scanned
     * @return true when the selected word fully matches at the offset
     */
    private static boolean isWholeWordMatch(
            @NotNull CharSequence text,
            int offset,
            @NotNull String word,
            @NotNull TextRange sqlRange
    ) {
        int endOffset = offset + word.length();
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
     * Checks whether a selected text is eligible for whole-word occurrence highlighting.
     *
     * @param text selected text
     * @return true when the selected text is one SQL identifier-like word
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
     * Checks whether a range contains another range.
     *
     * @param container outer range
     * @param start inner start offset
     * @param end inner end offset
     * @return true when the inner range is fully inside the outer range
     */
    private static boolean containsRange(@NotNull TextRange container, int start, int end) {
        return start >= container.getStartOffset() && end <= container.getEndOffset();
    }

    /**
     * Checks whether a character is part of a SQL identifier-like word.
     *
     * @param c character to check
     * @return true when the character belongs to an identifier-like word
     */
    private static boolean isIdentifierPart(char c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == '$';
    }

    /**
     * Compares two characters case-insensitively.
     *
     * @param left first character
     * @param right second character
     * @return true when characters match ignoring case
     */
    private static boolean equalsIgnoreCase(char left, char right) {
        return Character.toUpperCase(left) == Character.toUpperCase(right)
                || Character.toLowerCase(left) == Character.toLowerCase(right);
    }

    /**
     * Resolves occurrence highlight attributes from the current color scheme.
     *
     * @param editor current editor
     * @return text attributes for occurrence highlights
     */
    private static @NotNull TextAttributes getOccurrenceAttributes(@NotNull Editor editor) {
        TextAttributes attributes = editor.getColorsScheme().getAttributes(
                EditorColors.IDENTIFIER_UNDER_CARET_ATTRIBUTES
        );
        return attributes == null ? createFallbackAttributes() : attributes;
    }

    /**
     * Creates a scheme-independent fallback background highlight.
     *
     * @return fallback text attributes
     */
    private static @NotNull TextAttributes createFallbackAttributes() {
        TextAttributes attributes = new TextAttributes();
        attributes.setBackgroundColor(new Color(255, 236, 150));
        return attributes;
    }

    /**
     * Listeners and highlighters owned by a single editor.
     *
     * @param selectionListener selection listener installed on the editor
     * @param caretListener caret listener installed on the editor
     * @param highlighters active range highlighters
     */
    private record EditorState(
            @NotNull SelectionListener selectionListener,
            @NotNull CaretListener caretListener,
            @NotNull List<RangeHighlighter> highlighters
    ) {
    }
}
