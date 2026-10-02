package com.fixmer.mared.gui2.framework.components.editor;

import com.fixmer.mared.MaredSettings;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;

/**
 * Input-контроллер редактора.
 *
 * 0.3.0 (Phase D2): вынесен из MaredMultiLineEditBox.
 *
 * Содержит:
 *   - charTyped / keyPressed / mouse events;
 *   - editing-операции: backspace, delete, insertNewLine, paste, copy, cut;
 *   - auto-indent (знает про MaredSettings);
 *   - clipboard access;
 *   - прокидку к view (ensureCursorVisible / scroll).
 *
 * Не содержит состояния документа (мутирует EditorDocument).
 * Не содержит view-состояния (делегирует в EditorView).
 */
public final class EditorController {

    private final EditorHost host;
    private final EditorDocument document;
    private final EditorHistory history;
    private final EditorView view;
    private final Runnable onChanged;

    private boolean selecting = false;

    /**
     * Абстракция над виджетом — что контроллеру нужно от него.
     * Позволяет тестировать контроллер без Minecraft.
     */
    public interface EditorHost {
        int x();
        int y();
        int w();
        int h();
        boolean focused();
        void requestFocus();
        boolean editable();
        String clipboardGet();
        void clipboardSet(String s);
    }

    public EditorController(EditorHost host,
                            EditorDocument document,
                            EditorHistory history,
                            EditorView view,
                            Runnable onChanged) {
        this.host = host;
        this.document = document;
        this.history = history;
        this.view = view;
        this.onChanged = onChanged;
    }

    private Font font() { return Minecraft.getInstance().font; }

    // ============================================================
    //  charTyped / keyPressed
    // ============================================================

    public boolean charTyped(char codePoint, int modifiers) {
        if (!host.focused() || !host.editable()) return false;
        if (codePoint == '\r') return true;
        if (codePoint == '\t') return false;
        if (codePoint < 32) return false;

        if (codePoint == '{' && shouldAutoIndentOnBrace()) {
            history.pushBefore(document);
            insertBraceBlock();
            onChanged.run();
            return true;
        }

        history.pushBefore(document);
        document.insertChar(codePoint);
        onChanged.run();
        return true;
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!host.focused() || !host.editable()) return false;

        boolean ctrl = (modifiers & 2) != 0;

        if (ctrl) {
            switch (keyCode) {
                case 67: copySelection(); return true;
                case 86: pasteFromClipboard(); return true;
                case 88: cutSelection(); return true;
                case 65: document.selectAll(); return true;
                case 90: undo(); return true;
                case 89: redo(); return true;
                default: return false;
            }
        }

        switch (keyCode) {
            case 257: case 335: insertNewLine();                          return true;
            case 259: backspace();                                        return true;
            case 261: delete();                                           return true;
            case 262: document.moveRight();        ensureCursorVisible();  return true;
            case 263: document.moveLeft();         ensureCursorVisible();  return true;
            case 264: document.moveDown();         ensureCursorVisible();  return true;
            case 265: document.moveUp();           ensureCursorVisible();  return true;
            case 268: document.moveHome();                                return true;
            case 269: document.moveEnd();                                 return true;
            default:  return false;
        }
    }

    // ============================================================
    //  Undo / redo
    // ============================================================

    private void undo() {
        if (history.undo(document)) {
            ensureCursorVisible();
            onChanged.run();
        }
    }

    private void redo() {
        if (history.redo(document)) {
            ensureCursorVisible();
            onChanged.run();
        }
    }

    // ============================================================
    //  Clipboard
    // ============================================================

    private void copySelection() {
        if (!document.hasSelection()) return;
        host.clipboardSet(document.getSelectedText());
    }

    private void cutSelection() {
        if (!document.hasSelection()) return;
        history.pushBefore(document);
        host.clipboardSet(document.getSelectedText());
        document.deleteSelection();
        onChanged.run();
    }

    private void pasteFromClipboard() {
        String clip = host.clipboardGet();
        if (clip == null || clip.isEmpty()) return;
        history.pushBefore(document);
        document.insertString(clip);
        ensureCursorVisible();
        onChanged.run();
    }

    // ============================================================
    //  Editing operations
    // ============================================================

    private void insertNewLine() {
        String indent = shouldAutoIndentOnEnter() ? computeIndentAtCursor() : "";
        history.pushBefore(document);
        document.insertNewLine(indent);
        ensureCursorVisible();
        onChanged.run();
    }

    private void backspace() {
        if (document.hasSelection()) {
            history.pushBefore(document);
            document.deleteSelection();
            onChanged.run();
            return;
        }

        int cl = document.cursorLine();
        int cc = document.cursorCol();

        if (cc > 0 && MaredSettings.isBackspaceRemovesIndent()) {
            String line = document.line(cl).toString();
            String upToCursor = line.substring(0, cc);
            if (isIndentOnly(upToCursor)) {
                String unit = MaredSettings.indentUnit();
                int removeLen;
                if (upToCursor.endsWith(unit)) removeLen = unit.length();
                else if (upToCursor.endsWith("\t")) removeLen = 1;
                else {
                    int end = upToCursor.length();
                    int start = end;
                    while (start > 0 && upToCursor.charAt(start - 1) == ' ') start--;
                    removeLen = Math.max(1, Math.min(unit.length(), end - start));
                }
                history.pushBefore(document);
                document.deleteNBeforeCursor(removeLen);
                onChanged.run();
                return;
            }
        }

        if (cc > 0) {
            history.pushBefore(document);
            document.deleteBeforeCursor();
            onChanged.run();
        } else if (cl > 0) {
            history.pushBefore(document);
            document.mergeWithPreviousLine();
            ensureCursorVisible();
            onChanged.run();
        }
    }

    private void delete() {
        if (document.hasSelection()) {
            history.pushBefore(document);
            document.deleteSelection();
            onChanged.run();
            return;
        }
        int cl = document.cursorLine();
        int cc = document.cursorCol();
        int lineLen = document.lineLength(cl);

        if (cc < lineLen) {
            history.pushBefore(document);
            document.deleteAfterCursor();
            onChanged.run();
        } else if (cl < document.lineCount() - 1) {
            history.pushBefore(document);
            document.mergeWithNextLine();
            onChanged.run();
        }
    }

    // ============================================================
    //  Auto-format
    // ============================================================

    private boolean shouldAutoIndentOnBrace() {
        var m = MaredSettings.getAutoIndent();
        return m == MaredSettings.AutoIndent.SIMPLE
            || m == MaredSettings.AutoIndent.FULL;
    }

    private boolean shouldAutoIndentOnEnter() {
        var m = MaredSettings.getAutoIndent();
        return m == MaredSettings.AutoIndent.SMART
            || m == MaredSettings.AutoIndent.FULL;
    }

    private void insertBraceBlock() {
        String unit = MaredSettings.indentUnit();
        int cl = document.cursorLine();
        String currentLine = document.line(cl).toString();
        String currentIndent = extractIndent(currentLine);

        document.insertChar('{');
        String bodyIndent = currentIndent + unit;
        document.insertNewLine(bodyIndent);
        document.insertString("\n" + currentIndent + "}");
        document.moveUp();

        ensureCursorVisible();
    }

    private String computeIndentAtCursor() {
        int cl = document.cursorLine();
        int cc = document.cursorCol();
        String currentLine = document.line(cl).toString();
        String currentIndent = extractIndent(currentLine);
        String unit = MaredSettings.indentUnit();

        String beforeCursor = currentLine.substring(0, cc);
        String restAfterCursor = currentLine.substring(cc).trim();

        if (beforeCursor.trim().endsWith("{")) return currentIndent + unit;
        if (restAfterCursor.startsWith("}")) return currentIndent;
        return currentIndent;
    }

    private static String extractIndent(String line) {
        int i = 0;
        int n = line.length();
        while (i < n && (line.charAt(i) == ' ' || line.charAt(i) == '\t')) i++;
        return line.substring(0, i);
    }

    private static boolean isIndentOnly(String s) {
        int n = s.length();
        for (int i = 0; i < n; i++) {
            char c = s.charAt(i);
            if (c != ' ' && c != '\t') return false;
        }
        return true;
    }

    // ============================================================
    //  Mouse
    // ============================================================

    public void onMouseClicked(double mx, double my) {
        if (!host.editable()) return;

        int x = host.x();
        int y = host.y();
        int w = host.w();
        int h = host.h();

        if (view.isOverScrollbar(mx, my, x, y, w, h, document, font())) {
            view.beginScrollbarDrag(my);
            return;
        }

        host.requestFocus();
        int[] pos = view.pixelToPos(mx, my, x, y, w, h, document, font());
        document.setCursor(pos[0], pos[1]);
        document.startSelection(pos[0], pos[1]);
        selecting = true;
        ensureCursorVisible();
    }

    public boolean onMouseDragged(double mx, double my, int button,
                                  double dx, double dy) {
        int w = host.w();
        int h = host.h();

        if (view.isDraggingScrollbar()) {
            view.dragScrollbar(my, w, h, document, font());
            return true;
        }
        if (!host.editable() || !selecting) return false;

        int x = host.x();
        int y = host.y();

        int[] pos = view.pixelToPos(mx, my, x, y, w, h, document, font());
        document.setCursor(pos[0], pos[1]);
        document.extendSelection(pos[0], pos[1]);
        ensureCursorVisible();
        return true;
    }

    public void onMouseReleased(double mx, double my, int button) {
        selecting = false;
        view.endScrollbarDrag();
    }

    public boolean onMouseScrolled(double deltaY) {
        return view.mouseScrolled(deltaY, host.w(), host.h(), document, font());
    }

    // ============================================================
    //  Cursor visibility — delegate
    // ============================================================

    private void ensureCursorVisible() {
        view.ensureCursorVisible(host.w(), host.h(), document, font());
    }
}