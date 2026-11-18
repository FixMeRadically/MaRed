package com.fixmer.mared.gui2.framework.components.editor;

import java.util.ArrayList;
import java.util.List;

/**
 * Модель текстового документа.
 *
 * 0.3.1:
 *   - line(i) возвращает immutable String.
 *   - Валидация курсора и selection.
 *   - applyEdit / revertEdit — низкоуровневые примитивы для
 *     delta-based истории. Работают через replaceRange, не через
 *     посимвольные операции.
 *   - getTextRange — произвольный диапазон как строка.
 */
public final class EditorDocument {

    private final List<StringBuilder> lines = new ArrayList<>(64);

    private int cursorLine = 0;
    private int cursorCol  = 0;

    private int selStartLine = -1, selStartCol = -1;
    private int selEndLine = -1, selEndCol = -1;

    private int contentVersion = 0;
    private String cachedValue;
    private int[] codePointStarts;
    private int desiredColumn = -1;
    private final int[] changedLines = new int[128];

    public EditorDocument() {
        lines.add(new StringBuilder());
    }

    // ============================================================
    //  Version / Queries
    // ============================================================

    public int contentVersion() { return contentVersion; }
    private void touch(int firstLine) { cachedValue=null;codePointStarts=null;contentVersion++;changedLines[contentVersion&127]=Math.max(0,firstLine);desiredColumn=-1; }
    /** Non-consuming edit journal: each view can invalidate from its own observed revision. */
    public int changedLineSince(int version) {
        long distance=(long)contentVersion-version;
        if(distance==0)return lineCount();
        if(distance<0||distance>changedLines.length)return 0;
        int first=lineCount();
        for(int offset=1;offset<=distance;offset++)first=Math.min(first,changedLines[(version+offset)&127]);
        return first;
    }

    public int lineCount() { return lines.size(); }

    public int lineLength(int i) {
        if (i < 0 || i >= lines.size()) return 0;
        return lines.get(i).length();
    }

    public String line(int i) {
        if (i < 0 || i >= lines.size()) return "";
        return lines.get(i).toString();
    }

    public int cursorLine() { return cursorLine; }
    public int cursorCol()  { return cursorCol; }

    public boolean hasSelection() {
        return selStartLine >= 0 && selEndLine >= 0
            && (selStartLine != selEndLine || selStartCol != selEndCol);
    }

    public int[] orderedSelection() {
        if (!hasSelection()) return null;
        boolean forward = selStartLine < selEndLine
            || (selStartLine == selEndLine && selStartCol <= selEndCol);
        return forward
            ? new int[]{selStartLine, selStartCol, selEndLine, selEndCol}
            : new int[]{selEndLine, selEndCol, selStartLine, selStartCol};
    }

    public String getValue() {
        if (cachedValue != null) return cachedValue;
        int n = lines.size();
        if (n == 0) return "";
        int totalLen = n - 1;
        for (int i = 0; i < n; i++) totalLen += lines.get(i).length();
        StringBuilder sb = new StringBuilder(totalLen);
        for (int i = 0; i < n; i++) {
            if (i > 0) sb.append('\n');
            sb.append(lines.get(i));
        }
        return cachedValue = sb.toString();
    }

    private int[] codePointStarts() {
        if (codePointStarts == null) {
            codePointStarts = new int[lines.size() + 1];
            for (int i = 0; i < lines.size(); i++) {
                var line = lines.get(i);
                codePointStarts[i + 1] = codePointStarts[i] + line.codePointCount(0, line.length())
                    + (i + 1 < lines.size() ? 1 : 0);
            }
        }
        return codePointStarts;
    }

    public int codePointOffset(int line, int column) {
        var text = lines.get(line);
        int end = Math.clamp(column, 0, text.length());
        if (end > 0 && end < text.length() && Character.isHighSurrogate(text.charAt(end - 1))
            && Character.isLowSurrogate(text.charAt(end))) end--;
        return codePointStarts()[line] + text.codePointCount(0, end);
    }

    public int[] codePointPosition(int offset) {
        int[] starts = codePointStarts();
        if (lines.isEmpty()) return new int[]{0, 0};
        int clamped = Math.clamp(offset, 0, starts[lines.size()]);
        int row = java.util.Arrays.binarySearch(starts, 0, lines.size(), clamped);
        if (row < 0) row = -row - 2;
        return new int[]{row, Character.offsetByCodePoints(lines.get(row), 0, clamped - starts[row])};
    }

    public String getSelectedText() {
        int[] r = orderedSelection();
        if (r == null) return "";
        return getTextRange(r[0], r[1], r[2], r[3]);
    }

    /**
     * 0.3.1: произвольный диапазон текста.
     */
    public String getTextRange(int sl, int sc, int el, int ec) {
        if (sl < 0 || sl >= lines.size()) return "";
        if (el < 0 || el >= lines.size()) return "";
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

    private int boundary(int line,int column){
        var text=lines.get(line);int c=clamp(column,0,text.length());
        return c>0&&c<text.length()&&Character.isHighSurrogate(text.charAt(c-1))&&Character.isLowSurrogate(text.charAt(c))?c-1:c;
    }
    public void setCursor(int line, int col) {
        desiredColumn=-1;
        cursorLine = clamp(line, 0, lines.size() - 1);
        cursorCol  = boundary(cursorLine,col);
    }

    public void clearSelection() {
        selStartLine = -1; selStartCol = -1;
        selEndLine = -1; selEndCol = -1;
    }

    public void startSelection(int line, int col) {
        int l = clamp(line, 0, lines.size() - 1);
        int c = boundary(l,col);
        selStartLine = l; selStartCol = c;
        selEndLine = l; selEndCol = c;
    }

    public void extendSelection(int line, int col) {
        int l = clamp(line, 0, lines.size() - 1);
        int c = boundary(l,col);
        selEndLine = l; selEndCol = c;
    }

    public void selectAll() {
        selStartLine = 0; selStartCol = 0;
        selEndLine = lines.size() - 1;
        selEndCol = lines.get(selEndLine).length();
        setCursor(selEndLine,selEndCol);
    }

    // ============================================================
    //  Value
    // ============================================================

    /** The document stores LF; CRLF and legacy CR input retain their line boundaries. */
    public static String normalizeLineEndings(String text){return text==null?"":text.replace("\r\n","\n").replace('\r','\n');}

    public void setValue(String text) {
        if (text == null) text = "";
        text = normalizeLineEndings(text);

        lines.clear();
        String[] parts = text.split("\n", -1);
        for (String p : parts) lines.add(new StringBuilder(p));
        if (lines.isEmpty()) lines.add(new StringBuilder());

        cursorLine = 0;
        cursorCol = 0;
        clearSelection();
        touch(cursorLine);
    }

    // ============================================================
    //  Low-level primitives (для Edit)
    // ============================================================

    /**
     * 0.3.1: заменить [sl,sc .. el,ec) на text. Курсор ставится в
     * конец вставленного фрагмента. Selection очищается.
     */
    public void replaceRange(int sl, int sc, int el, int ec, String text) {
        if (sl < 0 || sl >= lines.size()) return;
        if (el < 0 || el >= lines.size()) return;
        if (text == null) text = "";

        // 1. Удалить [sl, sc .. el, ec)
        StringBuilder first = lines.get(sl);
        String tail = lines.get(el).substring(ec);
        first.delete(sc, first.length());
        first.append(tail);
        for (int i = el; i > sl; i--) lines.remove(i);

        // 2. Вставить text в (sl, sc)
        cursorLine = sl;
        cursorCol = sc;
        clearSelection();
        insertStringInternal(text);

        touch(sl);
    }

    /**
     * Применить Edit вперёд (redo).
     */
    public void applyEdit(Edit e) {
        if (e == null || e.isEmpty()) return;

        int[] end = Edit.advancePosition(
            e.startLine(), e.startCol(), e.removedText());

        replaceRange(e.startLine(), e.startCol(),
            end[0], end[1], e.insertedText());

        setCursor(e.cursorAfterLine(), e.cursorAfterCol());
    }

    /**
     * Откатить Edit (undo).
     */
    public void revertEdit(Edit e) {
        if (e == null || e.isEmpty()) return;

        int[] end = Edit.advancePosition(
            e.startLine(), e.startCol(), e.insertedText());

        replaceRange(e.startLine(), e.startCol(),
            end[0], end[1], e.removedText());

        setCursor(e.cursorBeforeLine(), e.cursorBeforeCol());
    }

    /**
     * 0.3.1: insertString без проверки hasSelection — используется из
     * replaceRange (selection уже очищена выше).
     */
    private void insertStringInternal(String s) {
        if (s == null || s.isEmpty()) return;

        String[] parts = normalizeLineEndings(s).split("\n", -1);
        StringBuilder cur = lines.get(cursorLine);

        if (parts.length == 1) {
            cur.insert(cursorCol, parts[0]);
            cursorCol += parts[0].length();
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
    }

    // ============================================================
    //  Editing primitives (совместимость — controller строит Edit)
    // ============================================================

    public void insertChar(char c) {
        int firstChanged=hasSelection()?orderedSelection()[0]:cursorLine;
        if (hasSelection()) deleteSelectionInternal();
        lines.get(cursorLine).insert(cursorCol, c);
        cursorCol++;
        touch(firstChanged);
    }

    public void insertString(String s) {
        if (s == null || s.isEmpty()) return;
        int firstChanged=hasSelection()?orderedSelection()[0]:cursorLine;
        if (hasSelection()) deleteSelectionInternal();
        insertStringInternal(s);
        touch(firstChanged);
    }

    public void deleteBeforeCursor() {
        if (cursorCol > 0) {
            var line=lines.get(cursorLine);int start=Character.offsetByCodePoints(line,cursorCol,-1);line.delete(start,cursorCol);
            cursorCol=start;
            touch(cursorLine);
        }
    }

    public void deleteNBeforeCursor(int n) {
        if (n <= 0) return;
        int actual = Math.min(n, cursorCol);
        if (actual <= 0) return;
        int start=boundary(cursorLine,cursorCol-actual);lines.get(cursorLine).delete(start,cursorCol);
        cursorCol=start;
        touch(cursorLine);
    }

    public void deleteAfterCursor() {
        StringBuilder cur = lines.get(cursorLine);
        if (cursorCol < cur.length()) {
            cur.delete(cursorCol,Character.offsetByCodePoints(cur,cursorCol,1));
            touch(cursorLine);
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
        touch(cursorLine);
    }

    public void mergeWithNextLine() {
        if (cursorLine >= lines.size() - 1) return;
        lines.get(cursorLine).append(lines.get(cursorLine + 1));
        lines.remove(cursorLine + 1);
        touch(cursorLine);
    }

    public void insertNewLine(String indent) {
        int firstChanged=hasSelection()?orderedSelection()[0]:cursorLine;
        if (hasSelection()) deleteSelectionInternal();
        if (indent == null) indent = "";

        StringBuilder line = lines.get(cursorLine);
        String rest = line.substring(cursorCol);
        line.delete(cursorCol, line.length());

        lines.add(cursorLine + 1, new StringBuilder(indent + rest));
        cursorLine++;
        cursorCol = indent.length();
        touch(firstChanged);
    }

    public void deleteSelection() {
        if (!hasSelection()) return;
        deleteSelectionInternal();
        touch(cursorLine);
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

    /** Movement never changes content/history. Shift keeps a stable selection anchor. */
    public boolean navigate(int key, boolean ctrl, boolean shift) {
        if(key<262||key>265&&key!=268&&key!=269)return false;
        if(!shift&&hasSelection()&&!ctrl&&(key==262||key==263)) {
            int[] range=orderedSelection();
            setCursor(key==263?range[0]:range[2],key==263?range[1]:range[3]);
            clearSelection();return true;
        }
        if(shift&&!hasSelection())startSelection(cursorLine,cursorCol);
        if(!shift)clearSelection();
        if(key==262||key==263) {
            desiredColumn=-1;
            if(ctrl)moveWord(key==262);else step(key==262);
        } else if(key==268||key==269) {
            desiredColumn=-1;
            if(ctrl)cursorLine=key==268?0:lines.size()-1;
            cursorCol=key==268?0:lines.get(cursorLine).length();
        } else {
            if(desiredColumn<0)desiredColumn=cursorCol;
            int direction=key==265?-1:1;
            if(ctrl) {
                int next=cursorLine;
                do {next+=direction;}while(next>0&&next<lines.size()-1&&!lines.get(next).toString().isBlank());
                cursorLine=clamp(next,0,lines.size()-1);
            } else cursorLine=clamp(cursorLine+direction,0,lines.size()-1);
            cursorCol=Math.min(desiredColumn,lines.get(cursorLine).length());
        }
        var current=lines.get(cursorLine);
        if(cursorCol>0&&cursorCol<current.length()&&Character.isHighSurrogate(current.charAt(cursorCol-1))&&Character.isLowSurrogate(current.charAt(cursorCol)))cursorCol--;
        if(shift)extendSelection(cursorLine,cursorCol);
        return true;
    }
    private int neighbor(boolean right) {
        var line=lines.get(cursorLine);
        if(right)return cursorCol<line.length()?Character.codePointAt(line,cursorCol):cursorLine<lines.size()-1?'\n':-1;
        return cursorCol>0?Character.codePointBefore(line,cursorCol):cursorLine>0?'\n':-1;
    }
    private void step(boolean right) {
        int code=neighbor(right);if(code<0)return;
        if(right) {
            if(cursorCol==lines.get(cursorLine).length()){cursorLine++;cursorCol=0;}
            else cursorCol+=Character.charCount(code);
        } else {
            if(cursorCol==0){cursorLine--;cursorCol=lines.get(cursorLine).length();}
            else cursorCol-=Character.charCount(code);
        }
    }
    private static int wordKind(int code) {
        return code<0?-1:Character.isWhitespace(code)?0:Character.isJavaIdentifierPart(code)?1:2;
    }
    private void moveWord(boolean right) {
        if(!right)while(wordKind(neighbor(false))==0)step(false);
        int kind=wordKind(neighbor(right));
        if(kind>=0)while(wordKind(neighbor(right))==kind)step(right);
        if(right)while(wordKind(neighbor(true))==0)step(true);
    }

    public void moveLeft() { navigate(263,false,false); }

    public void moveRight() { navigate(262,false,false); }

    public void moveUp() { navigate(265,false,false); }

    public void moveDown() { navigate(264,false,false); }

    public void moveHome() { cursorCol = 0; }
    public void moveEnd()  { cursorCol = lines.get(cursorLine).length(); }

    // ============================================================
    //  Legacy snapshot API
    // ============================================================

    /** @deprecated delta-история больше не использует снапшоты. */
    @Deprecated
    public String[] snapshotLines() {
        int n = lines.size();
        String[] arr = new String[n];
        for (int i = 0; i < n; i++) arr[i] = lines.get(i).toString();
        return arr;
    }

    /** @deprecated delta-история больше не использует снапшоты. */
    @Deprecated
    public void applySnapshot(String[] arr, int cl, int cc) {
        lines.clear();
        for (String s : arr) lines.add(new StringBuilder(s));
        if (lines.isEmpty()) lines.add(new StringBuilder());
        cursorLine = clamp(cl, 0, lines.size() - 1);
        cursorCol  = clamp(cc, 0, lines.get(cursorLine).length());
        clearSelection();
        touch(0);
    }

    private static int clamp(int v, int lo, int hi) {
        if (v < lo) return lo;
        if (v > hi) return hi;
        return v;
    }
}