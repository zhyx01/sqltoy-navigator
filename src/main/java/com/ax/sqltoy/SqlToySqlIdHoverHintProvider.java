package com.ax.sqltoy;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.event.EditorFactoryEvent;
import com.intellij.openapi.editor.event.EditorFactoryListener;
import com.intellij.openapi.editor.event.EditorMouseEvent;
import com.intellij.openapi.editor.event.EditorMouseMotionListener;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.Alarm;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Point;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * 鼠标悬停在 Java sqlId 字符串上时显示 SQL 预览提示，右上角带复制按钮。
 * 原生 annotator tooltip 不支持点击交互，因此这里用 JBPopup 显示自定义组件。
 *
 * @author ax
 * @date 2026-08-31
 */
public final class SqlToySqlIdHoverHintProvider implements EditorFactoryListener, DumbAware {

    /**
     * 悬停多少毫秒后显示提示，对齐 IDEA tooltip 的触发节奏。
     */
    private static final int HOVER_DELAY_MS = 500;

    /**
     * 提示面板内容区的最大宽度，与原 tooltip 宽度上限保持一致。
     */
    private static final int HINT_MAX_WIDTH = 960;

    /**
     * 宽度余量，与原 tooltip 的 +4px 一致。
     */
    private static final int HINT_PADDING = 4;

    /**
     * 按编辑器保存的悬停状态。
     */
    private final Map<Editor, HoverState> hoverStates = new WeakHashMap<>();

    /**
     * 为新创建的编辑器安装鼠标移动监听器。
     *
     * @param event 编辑器创建事件
     */
    @Override
    public void editorCreated(@NotNull EditorFactoryEvent event) {
        Editor editor = event.getEditor();
        EditorMouseMotionListener listener = new EditorMouseMotionListener() {
            /**
             * 鼠标在编辑器内移动时判断是否悬停在 sqlId 字符串内容上。
             *
             * @param event 编辑器鼠标事件
             */
            @Override
            public void mouseMoved(@NotNull EditorMouseEvent event) {
                handleMouseMoved(editor, event);
            }
        };
        hoverStates.put(editor, new HoverState(listener));
        editor.addEditorMouseMotionListener(listener);
    }

    /**
     * 编辑器释放时移除监听器和提示。
     *
     * @param event 编辑器释放事件
     */
    @Override
    public void editorReleased(@NotNull EditorFactoryEvent event) {
        Editor editor = event.getEditor();
        HoverState state = hoverStates.remove(editor);
        if (state == null) {
            return;
        }

        editor.removeEditorMouseMotionListener(state.listener());
        state.cancelPendingRequest();
        hideHint(state);
    }

    /**
     * 鼠标移动时调度或取消悬停提示。
     *
     * @param editor 当前编辑器
     * @param event  编辑器鼠标事件
     */
    private void handleMouseMoved(@NotNull Editor editor, @NotNull EditorMouseEvent event) {
        HoverState state = hoverStates.get(editor);
        if (state == null) {
            return;
        }

        if (event.getMouseEvent() == null) {
            return;
        }

        TextRange literalRange = state.literalRange();
        int offset = editor.logicalPositionToOffset(event.getLogicalPosition());
        if (literalRange != null && literalRange.contains(offset)) {
            // 仍悬停在同一个 sqlId 上，保持当前提示不动。
            return;
        }

        state.cancelPendingRequest();
        hideHint(state);

        PsiLiteralExpression literal = findSqlIdLiteralAt(editor, offset);
        if (literal == null) {
            return;
        }

        TextRange contentRange = SqlToyJavaSqlIdResolver.getStringContentTextRange(literal);
        state.literalRange(contentRange);
        state.schedule(() -> showHint(editor, literal, state));
    }

    /**
     * 查找偏移量处的 sqlId 字符串字面量。
     *
     * @param editor 当前编辑器
     * @param offset 文档偏移量
     * @return sqlId 字面量；该位置不是 sqlId 时返回 null
     */
    private static @Nullable PsiLiteralExpression findSqlIdLiteralAt(@NotNull Editor editor, int offset) {
        Project project = editor.getProject();
        if (project == null) {
            return null;
        }

        PsiFile psiFile = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
        if (psiFile == null || offset < 0 || offset >= psiFile.getTextLength()) {
            return null;
        }

        PsiElement element = psiFile.findElementAt(offset);
        return element == null ? null : SqlToyJavaSqlIdResolver.getSqlIdLiteral(element);
    }

    /**
     * 解析 sqlId 对应的 SQL 并显示带复制按钮的提示。
     *
     * @param editor  当前编辑器
     * @param literal sqlId 字面量
     * @param state   当前编辑器的悬停状态
     */
    private void showHint(@NotNull Editor editor, @NotNull PsiLiteralExpression literal, @NotNull HoverState state) {
        String sqlId = SqlToyJavaSqlIdResolver.getSqlId(literal);
        if (sqlId == null || editor.isDisposed() || state.literalRange() == null) {
            return;
        }

        List<SqlToySqlIdXmlResolver.SqlIdTarget> targets =
                SqlToySqlIdXmlResolver.findDialectTargets(literal.getProject(), sqlId);
        if (targets.isEmpty()) {
            return;
        }

        JComponent panel = createHintPanel(targets.get(0).sqlText());
        Point point = editor.visualPositionToXY(
                editor.offsetToVisualPosition(state.literalRange().getStartOffset()));
        Dimension preferredSize = panel.getPreferredSize();
        // 优先展示在 sqlId 上方，更贴近原生 tooltip 的位置；上方空间不足时落在下方。
        int aboveY = point.y - preferredSize.height - editor.getLineHeight();
        if (aboveY >= 0) {
            point.y = aboveY;
        }

        JBPopup popup = JBPopupFactory.getInstance()
                .createComponentPopupBuilder(panel, panel)
                .setFocusable(false)
                .setRequestFocus(false)
                .setCancelOnMouseOutCallback(event -> false)
                .setCancelOnClickOutside(true)
                .setCancelOnWindowDeactivation(true)
                .setCancelOnOtherWindowOpen(true)
                .setCancelKeyEnabled(true)
                .setMovable(false)
                .createPopup();
        state.popup(popup);
        popup.show(new RelativePoint(editor.getContentComponent(), point));
    }

    /**
     * 创建 SQL 预览提示面板：中间等宽 SQL 文本，右上角一个复制图标按钮。
     *
     * @param sqlText SQL 纯文本
     * @return 提示面板
     */
    private static @NotNull JComponent createHintPanel(@NotNull String sqlText) {
        JBLabel sqlLabel = new JBLabel(createSqlHtml(sqlText));
        sqlLabel.setVerticalAlignment(SwingConstants.TOP);
        JBScrollPane scrollPane = new JBScrollPane(sqlLabel);
        Dimension preferred = sqlLabel.getPreferredSize();
        // 宽高行为与原 tooltip 对齐：宽度上限 960、高度上限约 60vh，超出时双向滚动。
        scrollPane.setPreferredSize(new Dimension(
                Math.min(preferred.width, HINT_MAX_WIDTH),
                Math.min(preferred.height, getMaxHintHeight())
        ));

        JPanel hintPanel = new JPanel(new BorderLayout());
        hintPanel.setBackground(UIUtil.getToolTipBackground());
        hintPanel.add(scrollPane, BorderLayout.CENTER);
        hintPanel.add(createCopyToolbar(sqlText, hintPanel), BorderLayout.NORTH);
        return hintPanel;
    }

    /**
     * 按屏幕高度的 60% 计算提示高度上限，等价于原 tooltip 的 max-height:60vh。
     *
     * @return 提示内容区最大高度（像素）
     */
    private static int getMaxHintHeight() {
        return (int) (Toolkit.getDefaultToolkit().getScreenSize().height * 0.6);
    }

    /**
     * 创建右上角的复制工具条。
     *
     * @param sqlText   要复制的 SQL 纯文本
     * @param hintPanel 提示面板
     * @return 工具条组件
     */
    private static @NotNull JComponent createCopyToolbar(@NotNull String sqlText, @NotNull JPanel hintPanel) {
        CopySqlAction copyAction = new CopySqlAction(sqlText);
        DefaultActionGroup group = new DefaultActionGroup(copyAction);
        ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar(
                "SqlToySqlIdHoverHint", group, true);
        toolbar.setTargetComponent(hintPanel);

        JPanel toolbarPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        toolbarPanel.setOpaque(false);
        toolbarPanel.add(toolbar.getComponent());
        return toolbarPanel;
    }

    /**
     * 复制 SQL 到系统剪贴板，并把图标切换为完成状态。
     */
    private static final class CopySqlAction extends AnAction {
        private final String sqlText;
        private boolean copied;

        private CopySqlAction(@NotNull String sqlText) {
            super("复制 SQL", "复制 SQL 到剪贴板", AllIcons.Actions.Copy);
            this.sqlText = sqlText;
        }

        @Override
        public void actionPerformed(@NotNull AnActionEvent e) {
            CopyPasteManager.getInstance().setContents(new StringSelection(sqlText));
            copied = true;
            getTemplatePresentation().setIcon(AllIcons.General.InspectionsOK);
            update(e);
        }

        @Override
        public void update(@NotNull AnActionEvent e) {
            e.getPresentation().setIcon(copied
                    ? AllIcons.General.InspectionsOK
                    : AllIcons.Actions.Copy);
            e.getPresentation().setEnabled(true);
        }

        @Override
        public @NotNull ActionUpdateThread getActionUpdateThread() {
            return ActionUpdateThread.EDT;
        }
    }

    /**
     * 创建仅用 pre 保留空格和换行的 SQL HTML 内容。
     * Swing HTML 中 &nbsp; 在等宽字体下渲染比普通空格宽，会把每个字段名“顶开”，因此不替换。
     *
     * @param sqlText SQL 纯文本
     * @return 悬浮提示 HTML
     */
    private static @NotNull String createSqlHtml(@NotNull String sqlText) {
        int contentWidth = calculateContentWidth(sqlText);
        Color background = UIUtil.getToolTipBackground();
        return "<html><body style='margin:0;background:rgb("
                + background.getRed() + "," + background.getGreen() + "," + background.getBlue() + ");'>"
                + "<div style='width:" + contentWidth + "px;'>"
                + "<pre style='margin:0;white-space:pre;'>"
                + StringUtil.escapeXmlEntities(sqlText)
                + "</pre>"
                + "</div>"
                + "</body></html>";
    }

    /**
     * 按等宽字体估算 SQL 内容宽度，用于固定提示宽度避免折行。
     *
     * @param sqlText SQL 纯文本
     * @return 内容宽度（像素）
     */
    private static int calculateContentWidth(@NotNull String sqlText) {
        FontMetrics fontMetrics = new JLabel().getFontMetrics(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        int contentWidth = 0;
        for (String line : sqlText.split("\\R", -1)) {
            contentWidth = Math.max(contentWidth, fontMetrics.stringWidth(line.replace("\t", "    ")));
        }
        return Math.min(HINT_MAX_WIDTH, contentWidth + HINT_PADDING);
    }

    /**
     * 隐藏当前提示。
     *
     * @param state 当前编辑器的悬停状态
     */
    private static void hideHint(@NotNull HoverState state) {
        if (state.popup() != null) {
            state.popup().cancel();
            state.popup(null);
        }
        state.literalRange(null);
    }

    /**
     * 单个编辑器的悬停监听器与活动提示状态。
     */
    private static final class HoverState {
        private final EditorMouseMotionListener listener;
        private final Alarm alarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD);
        private TextRange literalRange;
        private JBPopup popup;

        private HoverState(@NotNull EditorMouseMotionListener listener) {
            this.listener = listener;
        }

        private EditorMouseMotionListener listener() {
            return listener;
        }

        private @Nullable TextRange literalRange() {
            return literalRange;
        }

        private void literalRange(@Nullable TextRange range) {
            this.literalRange = range;
        }

        private @Nullable JBPopup popup() {
            return popup;
        }

        private void popup(@Nullable JBPopup popup) {
            this.popup = popup;
        }

        private void schedule(@NotNull Runnable runnable) {
            alarm.addRequest(runnable, HOVER_DELAY_MS);
        }

        private void cancelPendingRequest() {
            alarm.cancelAllRequests();
        }
    }
}
