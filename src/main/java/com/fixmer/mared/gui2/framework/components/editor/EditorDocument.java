package com.fixmer.mared.gui2.framework.components.editor;

import java.util.ArrayList;
import java.util.List;

/**
 * Модель текстового документа.
 *
 * 0.3.0 (Phase D1): вынесена из MaredMultiLineEditBox.
 *
 * Содержит:
 *   - строки (List<StringBuilder>);
 *   - курсор (line, col);
 *   - селекцию (start/end);
 *   - edit-примитивы (insert/delete/move/merge/snapshot);
 *   - contentVersion для инвалидации кэшей.
 *
 * НЕ содержит:
 *   - undo/redo (это EditorHistory);
 *   - авто-индентацию (это controller — знает про MaredSettings);
 *   - рендер, wrap, input, Minecraft.
 *
 * Все мутирующие методы инкрементируют contentVersion.
 * Caller обязан вызвать history.pushBefore(doc) ДО мутации.
 */
public final class EditorDocument {

    private final List<StringBuilder> lines = new ArrayList<>(64);

    private int cursorLine = 0;
    private int cursorCol  = 0;

    private int selStartLine = -1, selStartCol = -1;
    private int selEndLine = -1, selEndCol = -1;

    private int contentVersion = 0;

    public EditorDocument() {
        lines.add(new StringBuilder());
    }

    // ============================================================
    //  Version / Queries
    // ============================================================

    public int contentVersion() { return contentVersion; }

    private void touch() { contentVersion++; }

    public int lineCount() { return lines.size(); }

    public int lineLength(int i) {
        if (i < 0 || i >= lines.size()) return 0;
        return lines.get(i).length();
    }

    public CharSequence line(int i) {
        if (i < 0 || i >= lines.size()) return "";
        return lines.get(i);
    }

    public int cursorLine() { return cursorLine; }
    public int cursorCol()  { return cursorCol; }

    public boolean hasSelection() {
        return selStartLine >= 0 && selEndLine >= 0
            && (selStartLine != selEndLine || selStartCol != selEndCol);
    }

    /** [sl, sc, el, ec], где (sl,sc) <= (el,ec). Или null, если нет селекции. */
    public int[] orderedSelection() {
        if (!hasSelection()) return null;
        boolean forward = selStartLine < selEndLine
            || (selStartLine == selEndLine && selStartCol <= selEndCol);
        return forward
            ? new int[]{selStartLine, selStartCol, selEndLine, selEndCol}
            : new int[]{selEndLine, selEndCol, selStartLine, selStartCol};
    }

    public String getValue() {
        int n = lines.size();
        if (n == 0) return "";
        int totalLen = n - 1;
        for (int i = 0; i < n; i++) totalLen += lines.get(i).length();
        StringBuilder sb = new StringBuilder(totalLen);
        for (int i = 0; i < n; i++) {
            if (i > 0) sb.append('\n');
            sb.append(lines.get(i));
        }
        return sb.toString();
    }

    public String getSelectedText() {
        int[] r = orderedSelection();
        if (r == null) return "";
        int sl = r[0], sc = r[1], el = r[2], ec = r[3];
        if (sl == el) return lines.get(sl).substring(sc, ec);
        StringBuilder sb = new StringBuilder(64);
        sb.append(lines.get(sl).substring(sc));
        for (int i = sl + 1; i < el; i++) sb.append('\n').append(lines.get(i));
        sb.append('\n').append(lines.get(el).substring(0, ec));
        return sb.toString();
    }

    // ============================================================
    //  Cursor / Selection
    // ============================================================

    public void setCursor(int line, int col) {
        cursorLine = clamp(line, 0, lines.size() - 1);
        cursorCol  = clamp(col, 0, lines.get(cursorLine).length());
    }

    public void clearSelection() {
        selStartLine = -1; selStartCol = -1;
        selEndLine = -1; selEndCol = -1;
    }

    public void startSelection(int line, int col) {
        selStartLine = line; selStartCol = col;
        selEndLine = line; selEndCol = col;
    }

    public void extendSelection(int line, int col) {
        selEndLine = line; selEndCol = col;
    }

    public void selectAll() {
        selStartLine = 0; selStartCol = 0;
        selEndLine = lines.size() - 1;
        selEndCol = lines.get(selEndLine).length();
    }

    // ============================================================
    //  Value (замена целиком)
    // ============================================================

    public void setValue(String text) {
        if (text == null) text = "";
        text = text.replace("\r", "");

        lines.clear();
        String[] parts = text.split("\n", -1);
        for (String p : parts) lines.add(new StringBuilder(p));
        if (lines.isEmpty()) lines.add(new StringBuilder());

        cursorLine = 0;
        cursorCol = 0;
        clearSelection();
        touch();
    }

    // ============================================================
    //  Editing primitives (caller делает history.pushBefore до)
    // ============================================================

    public void insertChar(char c) {
        if (hasSelection()) deleteSelectionInternal();
        lines.get(cursorLine).insert(cursorCol, c);
        cursorCol++;
        touch();
    }

    /**
     * Вставка строки, возможно содержащей '\n'.
     * Многострочная вставка обрабатывается как paste (split + cursor в конце).
     */
    public void insertString(String s) {
        if (s == null || s.isEmpty()) return;
        if (hasSelection()) deleteSelectionInternal();

        String[] parts = s.replace("\r", "").split("\n", -1);
        StringBuilder cur = lines.get(cursorLine);

        if (parts.length == 1) {
            cur.insert(cursorCol, parts[0]);
            cursorCol += parts[0].length();
            touch();
            return;
        }

        String tail = cur.substring(cursorCol);
        cur.delete(cursorCol, cur.length());
        cur.append(parts[0]);

        int insertAt = cursorLine + 1;
        for (int i = 1; i < parts.length - 1; i++) {
            lines.add(insertAt++, new StringBuilder(parts[i]));
        }
        String lastLine = parts[parts.length - 1] + tail;
        lines.add(insertAt, new StringBuilder(lastLine));

        cursorLine = insertAt;
        cursorCol = parts[parts.length - 1].length();
        touch();
    }

    public void deleteBeforeCursor() {
        if (cursorCol > 0) {
            lines.get(cursorLine).deleteCharAt(cursorCol - 1);
            cursorCol--;
            touch();
        }
    }

    public void deleteNBeforeCursor(int n) {
        if (n <= 0) return;
        int actual = Math.min(n, cursorCol);
        if (actual <= 0) return;
        lines.get(cursorLine).delete(cursorCol - actual, cursorCol);
        cursorCol -= actual;
        touch();
    }

    public void deleteAfterCursor() {
        StringBuilder cur = lines.get(cursorLine);
        if (cursorCol < cur.length()) {
            cur.deleteCharAt(cursorCol);
            touch();
        }
    }

    public void mergeWithPreviousLine() {
        if (cursorLine <= 0) return;
        StringBuilder prev = lines.get(cursorLine - 1);
        StringBuilder cur = lines.get(cursorLine);
        cursorCol = prev.length();
        prev.append(cur);
        lines.remove(cursorLine);
        cursorLine--;
        touch();
    }

    public void mergeWithNextLine() {
        if (cursorLine >= lines.size() - 1) return;
        lines.get(cursorLine).append(lines.get(cursorLine + 1));
        lines.remove(cursorLine + 1);
        touch();
    }

    /**
     * Разбить текущую строку по курсору. indent — префикс новой строки
     * (auto-indent — вычисляет controller).
     */
    public void insertNewLine(String indent) {
        if (hasSelection()) deleteSelectionInternal();
        if (indent == null) indent = "";

        StringBuilder line = lines.get(cursorLine);
        String rest = line.substring(cursorCol);
        line.delete(cursorCol, line.length());

        lines.add(cursorLine + 1, new StringBuilder(indent + rest));
        cursorLine++;
        cursorCol = indent.length();
        touch();
    }

    public void deleteSelection() {
        if (!hasSelection()) return;
        deleteSelectionInternal();
        touch();
    }

    private void deleteSelectionInternal() {
        int[] r = orderedSelection();
        if (r == null) return;
        int sl = r[0], sc = r[1], el = r[2], ec = r[3];

        StringBuilder first = lines.get(sl);
        String tail = lines.get(el).substring(ec);
        first.delete(sc, first.length());
        first.append(tail);
        for (int i = el; i > sl; i--) lines.remove(i);

        cursorLine = sl;
        cursorCol = sc;
        clearSelection();
    }

    // ============================================================
    //  Cursor movement
    // ============================================================

    public void moveLeft() {
        if (hasSelection()) { clearSelection(); return; }
        if (cursorCol > 0) cursorCol--;
        else if (cursorLine > 0) {
            cursorLine--;
            cursorCol = lines.get(cursorLine).length();
        }
    }

    public void moveRight() {
        if (hasSelection()) { clearSelection(); return; }
        StringBuilder line = lines.get(cursorLine);
        if (cursorCol < line.length()) cursorCol++;
        else if (cursorLine < lines.size() - 1) {
            cursorLine++;
            cursorCol = 0;
        }
    }

    public void moveUp() {
        if (cursorLine > 0) {
            cursorLine--;
            cursorCol = Math.min(cursorCol, lines.get(cursorLine).length());
        }
    }

    public void moveDown() {
        if (cursorLine < lines.size() - 1) {
            cursorLine++;
            cursorCol = Math.min(cursorCol, lines.get(cursorLine).length());
        }
    }

    public void moveHome() { cursorCol = 0; }
    public void moveEnd()  { cursorCol = lines.get(cursorLine).length(); }

    // ============================================================
    //  Snapshot (для EditorHistory)
    // ============================================================

    public String[] snapshotLines() {
        int n = lines.size();
        String[] arr = new String[n];
        for (int i = 0; i < n; i++) arr[i] = lines.get(i).toString();
        return arr;
    }

    public void applySnapshot(String[] arr, int cl, int cc) {
        lines.clear();
        for (String s : arr) lines.add(new StringBuilder(s));
        if (lines.isEmpty()) lines.add(new StringBuilder());
        cursorLine = clamp(cl, 0, lines.size() - 1);
        cursorCol  = clamp(cc, 0, lines.get(cursorLine).length());
        clearSelection();
        touch();
    }

    private static int clamp(int v, int lo, int hi) {
        if (v < lo) return lo;
        if (v > hi) return hi;
        return v;
    }
}