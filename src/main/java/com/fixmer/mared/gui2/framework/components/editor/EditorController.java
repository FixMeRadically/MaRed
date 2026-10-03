package com.fixmer.mared.gui2.framework.components.editor;

import com.fixmer.mared.MaredSettings;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;

/**
 * Input-контроллер редактора.
 *
 * 0.3.1:
 *   - Перед каждой мутацией документа строит Edit и передаёт в
 *     EditorHistory. Никаких "pushBefore" со снапшотом.
 *   - Коалесинг: набор/backspace через recordCoalescible,
 *     paste/replace/merge lines — атомарные record.
 *   - Во всех точках — view.resetBlink().
 */
public final class EditorController {

    private final EditorHost host;
    private final EditorDocument document;
    private final EditorHistory history;
    private final EditorView view;
    private final Runnable onChanged;

    private boolean selecting = false;

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
    //  Typing
    // ============================================================

    public boolean charTyped(char codePoint, int modifiers) {
        if (!host.focused() || !host.editable()) return false;
        if (codePoint == '\r') return true;
        if (codePoint == '\t') return false;
        if (codePoint < 32) return false;

        if (codePoint == '{' && shouldAutoIndentOnBrace()) {
            insertBraceBlock();
            view.resetBlink();
            onChanged.run();
            return true;
        }

        insertCharWithHistory(codePoint);
        view.resetBlink();
        onChanged.run();
        return true;
    }

    private void insertCharWithHistory(char c) {
        String text = String.valueOf(c);

        if (document.hasSelection()) {
            int[] sel = document.orderedSelection();
            String removed = document.getSelectedText();
            int[] after = Edit.advancePosition(sel[0], sel[1], text);

            Edit e = new Edit(
                sel[0], sel[1], removed, text,
                document.cursorLine(), document.cursorCol(),
                after[0], after[1]
            );
            history.record(e); // атомарно (removed непустой)
            document.insertChar(c);
            return;
        }

        int cl = document.cursorLine(), cc = document.cursorCol();
        Edit e = new Edit(
            cl, cc, "", text,
            cl, cc,
            cl, cc + 1
        );
        history.recordCoalescible(e);
        document.insertChar(c);
    }

    // ============================================================
    //  Key handling
    // ============================================================

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!host.focused() || !host.editable()) return false;

        boolean ctrl = (modifiers & 2) != 0;

        if (ctrl) {
            switch (keyCode) {
                case 67: copySelection(); return true;
                case 86: pasteFromClipboard(); return true;
                case 88: cutSelection(); return true;
                case 65: document.selectAll(); view.resetBlink(); return true;
                case 90: undo(); return true;
                case 89: redo(); return true;
                default: return false;
            }
        }

        switch (keyCode) {
            case 257: case 335: insertNewLine();                          return true;
            case 259: backspace();                                        return true;
            case 261: delete();                                           return true;
            case 262: document.moveRight(); view.resetBlink(); ensureCursorVisible(); return true;
            case 263: document.moveLeft();  view.resetBlink(); ensureCursorVisible(); return true;
            case 264: document.moveDown();  view.resetBlink(); ensureCursorVisible(); return true;
            case 265: document.moveUp();    view.resetBlink(); ensureCursorVisible(); return true;
            case 268: document.moveHome();  view.resetBlink(); return true;
            case 269: document.moveEnd();   view.resetBlink(); return true;
            default:  return false;
        }
    }

    private void undo() {
        if (history.undo(document)) {
            view.resetBlink();
            ensureCursorVisible();
            onChanged.run();
        }
    }

    private void redo() {
        if (history.redo(document)) {
            view.resetBlink();
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
        host.clipboardSet(document.getSelectedText());
        deleteSelectionWithHistory();
        view.resetBlink();
        onChanged.run();
    }

    private void pasteFromClipboard() {
        String clip = host.clipboardGet();
        if (clip == null || clip.isEmpty()) return;

        if (document.hasSelection()) {
            int[] sel = document.orderedSelection();
            String removed = document.getSelectedText();
            int[] after = Edit.advancePosition(sel[0], sel[1], clip);

            Edit e = new Edit(
                sel[0], sel[1], removed, clip,
                document.cursorLine(), document.cursorCol(),
                after[0], after[1]
            );
            history.record(e); // атомарно
            document.insertString(clip);
        } else {
            int cl = document.cursorLine(), cc = document.cursorCol();
            int[] after = Edit.advancePosition(cl, cc, clip);

            Edit e = new Edit(
                cl, cc, "", clip,
                cl, cc,
                after[0], after[1]
            );
            history.record(e); // атомарно — не коалесим с набором
            document.insertString(clip);
        }

        view.resetBlink();
        ensureCursorVisible();
        onChanged.run();
    }

    // ============================================================
    //  Editing operations
    // ============================================================

    private void insertNewLine() {
        String indent = shouldAutoIndentOnEnter()
            ? computeIndentAtCursor() : "";
        String text = "\n" + indent;

        int cl = document.cursorLine(), cc = document.cursorCol();
        int beforeL = cl, beforeC = cc;

        if (document.hasSelection()) {
            int[] sel = document.orderedSelection();
            String removed = document.getSelectedText();
            int[] after = Edit.advancePosition(sel[0], sel[1], text);

            Edit e = new Edit(
                sel[0], sel[1], removed, text,
                beforeL, beforeC,
                after[0], after[1]
            );
            history.record(e);
            document.insertNewLine(indent);
        } else {
            int[] after = Edit.advancePosition(cl, cc, text);
            Edit e = new Edit(
                cl, cc, "", text,
                beforeL, beforeC,
                after[0], after[1]
            );
            history.record(e);
            document.insertNewLine(indent);
        }

        view.resetBlink();
        ensureCursorVisible();
        onChanged.run();
    }

    private void backspace() {
        if (document.hasSelection()) {
            deleteSelectionWithHistory();
            view.resetBlink();
            onChanged.run();
            return;
        }

        int cl = document.cursorLine();
        int cc = document.cursorCol();

        // Indent-aware backspace
        if (cc > 0 && MaredSettings.isBackspaceRemovesIndent()) {
            String line = document.line(cl);
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
                removeLen = Math.min(removeLen, cc);
                deleteNWithHistory(removeLen, cl, cc);
                view.resetBlink();
                onChanged.run();
                return;
            }
        }

        if (cc > 0) {
            int startCol = cc - 1;
            String removed = document.line(cl).substring(startCol, cc);
            Edit e = new Edit(
                cl, startCol, removed, "",
                cl, cc,
                cl, cc - 1
            );
            history.recordCoalescible(e);
            document.deleteBeforeCursor();
            view.resetBlink();
            onChanged.run();
        } else if (cl > 0) {
            int prevLen = document.lineLength(cl - 1);
            Edit e = new Edit(
                cl - 1, prevLen, "\n", "",
                cl, 0,
                cl - 1, prevLen
            );
            history.record(e); // атомарно — merge lines не коалесим
            document.mergeWithPreviousLine();
            view.resetBlink();
            ensureCursorVisible();
            onChanged.run();
        }
    }

    private void deleteNWithHistory(int n, int cl, int cc) {
        if (n <= 0) return;
        int startCol = cc - n;
        String removed = document.line(cl).substring(startCol, cc);
        Edit e = new Edit(
            cl, startCol, removed, "",
            cl, cc,
            cl, startCol
        );
        history.recordCoalescible(e);
        document.deleteNBeforeCursor(n);
    }

    private void delete() {
        if (document.hasSelection()) {
            deleteSelectionWithHistory();
            view.resetBlink();
            onChanged.run();
            return;
        }
        int cl = document.cursorLine();
        int cc = document.cursorCol();
        int lineLen = document.lineLength(cl);

        if (cc < lineLen) {
            String removed = document.line(cl).substring(cc, cc + 1);
            Edit e = new Edit(
                cl, cc, removed, "",
                cl, cc,
                cl, cc
            );
            history.recordCoalescible(e);
            document.deleteAfterCursor();
            view.resetBlink();
            onChanged.run();
        } else if (cl < document.lineCount() - 1) {
            Edit e = new Edit(
                cl, cc, "\n", "",
                cl, cc,
                cl, cc
            );
            history.record(e);
            document.mergeWithNextLine();
            view.resetBlink();
            onChanged.run();
        }
    }

    private void deleteSelectionWithHistory() {
        if (!document.hasSelection()) return;
        int[] sel = document.orderedSelection();
        String removed = document.getSelectedText();
        Edit e = new Edit(
            sel[0], sel[1], removed, "",
            document.cursorLine(), document.cursorCol(),
            sel[0], sel[1]
        );
        history.record(e);
        document.deleteSelection();
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

    /**
     * Вставка "{ ... }" одним Edit'ом.
     * Была составная операция — оформляем как один атомарный Edit.
     */
    private void insertBraceBlock() {
        String unit = MaredSettings.indentUnit();
        int cl = document.cursorLine();
        String currentLine = document.line(cl);
        String currentIndent = extractIndent(currentLine);
        int cc = document.cursorCol();

        String bodyIndent = currentIndent + unit;
        String inserted = "{" + "\n" + bodyIndent + "\n" + currentIndent + "}";

        int[] after = Edit.advancePosition(cl, cc, "{" + "\n" + bodyIndent);
        // cursor после вставки тела, до "}" — 1 строка выше конца.

        Edit e = new Edit(
            cl, cc, "", inserted,
            cl, cc,
            after[0], after[1]
        );
        history.record(e);
        document.replaceRange(cl, cc, cl, cc, inserted);

        // Cursor должен остаться на bodyIndent (перед закрывающей "}").
        // replaceRange поставит его в конец inserted — сдвинем на
        // одну строку вверх.
        int targetLine = after[0];
        int targetCol = after[1];
        document.setCursor(targetLine, targetCol);

        ensureCursorVisible();
    }

    private String computeIndentAtCursor() {
        int cl = document.cursorLine();
        int cc = document.cursorCol();
        String currentLine = document.line(cl);
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
        view.resetBlink();
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

    private void ensureCursorVisible() {
        view.ensureCursorVisible(host.w(), host.h(), document, font());
    }
}