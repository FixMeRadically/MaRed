package com.fixmer.mared.gui;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import com.fixmer.mared.MaredSettings;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

public class MaredMultiLineEditBox extends AbstractWidget {

    private static final int LINE_HEIGHT    = 10;
    private static final int PADDING        = 4;
    private static final int CURSOR_BLINK   = 500;
    private static final int LINE_NUM_WIDTH = 20;
    private static final int SCROLLBAR_W    = 5;
    private static final int MAX_UNDO       = 100;

    private static final int COLOR_TEXT    = 0xFFDDDDDD;
    private static final int COLOR_NUMBERS = 0xFF666680;
    private static final int COLOR_SELECT  = 0x804466CC;
    private static final int COLOR_BG      = 0xFF0E0E16;
    private static final int COLOR_SB_TRACK = 0xFF15151E;

    private final List<StringBuilder> lines = new ArrayList<>();
    private int cursorLine = 0;
    private int cursorCol  = 0;
    private int scrollLine = 0;

    private int selStartLine = -1, selStartCol = -1;
    private int selEndLine = -1, selEndCol = -1;
    private boolean selecting = false;

    private final Runnable onValueChanged;
    private int accentColor;

    private long lastBlink = 0;
    private boolean cursorVisible = true;
    private boolean editable = true;

    // === Скроллбар drag ===
    private boolean draggingScrollbar = false;
    private double dragStartY = 0;
    private int dragStartScroll = 0;

    // === Undo / Redo ===
    private final Deque<Snapshot> undoStack = new ArrayDeque<>();
    private final Deque<Snapshot> redoStack = new ArrayDeque<>();
    private boolean suppressUndoSnapshot = false;

    // ============================================================
    //  Кэш wrapped-строк
    // ============================================================

    /**
     * Кэш на одну логическую строку:
     *   physical — список физических строк (split по ширине)
     *   charStart — индекс первого символа логической строки в physical[i]
     *               (для pIdx=0 — 0, для pIdx>0 — позиция после пробелов)
     */
    private static final class LineWrap {
        final List<String> physical;
        final int[] charStart;

        LineWrap(List<String> physical, int[] charStart) {
            this.physical = physical;
            this.charStart = charStart;
        }
    }

    /** Кэш wrapped-строк по индексу логической строки. */
    private final List<LineWrap> wrapCache = new ArrayList<>();
    /** Версия контента — растёт при любой мутации lines. */
    private int contentVersion = 0;
    /** Версия ширины — растёт при изменении width. */
    private int widthVersion = 0;
    /** Для какой версии построен wrapCache. */
    private int wrapCacheContentVersion = -1;
    private int wrapCacheWidthVersion = -1;
    private int wrapCacheAvailableWidth = -1;

    /** Кэш «сколько физических строк до строки L». Растёт лениво. */
    private int[] visualRowStarts = null;  // visualRowStarts[i] = визуальный ряд, с которого начинается логическая строка i
    private int cachedTotalVisualRows = -1;
    private int rowStartsContentVersion = -1;
    private int rowStartsWidthVersion = -1;

    private static final class Snapshot {
        final List<String> lines;
        final int cursorLine;
        final int cursorCol;

        Snapshot(List<StringBuilder> src, int cl, int cc) {
            this.lines = new ArrayList<>(src.size());
            for (StringBuilder sb : src) this.lines.add(sb.toString());
            this.cursorLine = cl;
            this.cursorCol = cc;
        }
    }

    public MaredMultiLineEditBox(int x, int y, int width, int height, int accentColor, Runnable onValueChanged) {
        super(x, y, width, height, Component.literal(""));
        this.onValueChanged = onValueChanged;
        this.accentColor = accentColor;
        lines.add(new StringBuilder());
        this.setFocused(false);
    }

    public void setAccentColor(int color) { this.accentColor = color; }

    public void setEditable(boolean editable) {
        this.editable = editable;
        if (!editable) {
            this.setFocused(false);
            this.cursorVisible = false;
            clearSelection();
        }
    }

    public boolean isEditable() { return editable; }

    /** Вызывать при изменении размеров виджета (width). */
    private void invalidateWidth() {
        widthVersion++;
    }

    /** Вызывать при любой мутации lines. */
    private void invalidateContent() {
        contentVersion++;
    }

    // ============================================================
    //  Value
    // ============================================================

    public String getValue() {
        int n = lines.size();
        if (n == 0) return "";
        int totalLen = 0;
        for (int i = 0; i < n; i++) totalLen += lines.get(i).length() + 1;
        StringBuilder sb = new StringBuilder(totalLen);
        for (int i = 0; i < n; i++) {
            if (i > 0) sb.append('\n');
            sb.append(lines.get(i));
        }
        return sb.toString();
    }

    public void setValue(String text) {
        if (text != null) text = text.replace("\r", "");
        suppressUndoSnapshot = true;
        lines.clear();
        String[] parts = text.split("\n", -1);
        for (String p : parts) lines.add(new StringBuilder(p));
        if (lines.isEmpty()) lines.add(new StringBuilder());
        cursorLine = 0; cursorCol = 0; scrollLine = 0;
        clearSelection();
        undoStack.clear();
        redoStack.clear();
        suppressUndoSnapshot = false;
        invalidateContent();
        notifyChanged();
    }

    private void notifyChanged() {
        if (onValueChanged != null) onValueChanged.run();
    }

    // ============================================================
    //  Undo / Redo
    // ============================================================

    private void pushUndo() {
        if (suppressUndoSnapshot) return;
        undoStack.push(new Snapshot(lines, cursorLine, cursorCol));
        if (undoStack.size() > MAX_UNDO) {
            List<Snapshot> tmp = new ArrayList<>(undoStack);
            undoStack.clear();
            for (int i = 0; i < MAX_UNDO; i++) undoStack.push(tmp.get(i));
        }
        redoStack.clear();
    }

    private void undo() {
        if (undoStack.isEmpty()) return;
        Snapshot snap = undoStack.pop();
        redoStack.push(new Snapshot(lines, cursorLine, cursorCol));

        suppressUndoSnapshot = true;
        lines.clear();
        for (String s : snap.lines) lines.add(new StringBuilder(s));
        if (lines.isEmpty()) lines.add(new StringBuilder());
        cursorLine = Math.max(0, Math.min(snap.cursorLine, lines.size() - 1));
        cursorCol = Math.max(0, Math.min(snap.cursorCol, lines.get(cursorLine).length()));
        clearSelection();
        ensureCursorVisible();
        suppressUndoSnapshot = false;
        invalidateContent();
        notifyChanged();
    }

    private void redo() {
        if (redoStack.isEmpty()) return;
        Snapshot snap = redoStack.pop();
        undoStack.push(new Snapshot(lines, cursorLine, cursorCol));

        suppressUndoSnapshot = true;
        lines.clear();
        for (String s : snap.lines) lines.add(new StringBuilder(s));
        if (lines.isEmpty()) lines.add(new StringBuilder());
        cursorLine = Math.max(0, Math.min(snap.cursorLine, lines.size() - 1));
        cursorCol = Math.max(0, Math.min(snap.cursorCol, lines.get(cursorLine).length()));
        clearSelection();
        ensureCursorVisible();
        suppressUndoSnapshot = false;
        invalidateContent();
        notifyChanged();
    }

    // ---- Selection ----

    public boolean hasSelection() {
        return selStartLine >= 0 && selEndLine >= 0
            && (selStartLine != selEndLine || selStartCol != selEndCol);
    }

    private void clearSelection() {
        selStartLine = -1; selStartCol = -1;
        selEndLine = -1; selEndCol = -1;
    }

    private void startSelection(int line, int col) {
        selStartLine = line; selStartCol = col;
        selEndLine = line; selEndCol = col;
    }

    private void extendSelection(int line, int col) {
        selEndLine = line; selEndCol = col;
    }

    private String getSelectedText() {
        if (!hasSelection()) return "";
        int[] s = orderSelectionStart();
        int[] e = orderSelectionEnd();
        int sl = s[0], sc = s[1], el = e[0], ec = e[1];
        if (sl == el) return lines.get(sl).substring(sc, ec);
        StringBuilder sb = new StringBuilder();
        sb.append(lines.get(sl).substring(sc));
        for (int i = sl + 1; i < el; i++) sb.append('\n').append(lines.get(i));
        sb.append('\n').append(lines.get(el).substring(0, ec));
        return sb.toString();
    }

    private int[] orderSelectionStart() {
        if (selStartLine < selEndLine || (selStartLine == selEndLine && selStartCol <= selEndCol)) {
            return new int[]{selStartLine, selStartCol};
        }
        return new int[]{selEndLine, selEndCol};
    }

    private int[] orderSelectionEnd() {
        if (selStartLine < selEndLine || (selStartLine == selEndLine && selStartCol <= selEndCol)) {
            return new int[]{selEndLine, selEndCol};
        }
        return new int[]{selStartLine, selStartCol};
    }

    private void deleteSelection() {
        if (!hasSelection()) return;
        int[] s = orderSelectionStart();
        int[] e = orderSelectionEnd();
        int sl = s[0], sc = s[1], el = e[0], ec = e[1];

        StringBuilder first = lines.get(sl);
        String tail = lines.get(el).substring(ec);

        first.delete(sc, first.length());
        first.append(tail);

        for (int i = el; i > sl; i--) {
            lines.remove(i);
        }

        cursorLine = sl;
        cursorCol = sc;
        clearSelection();
    }

    // ---- Input ----

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (!isFocused() || !editable) return false;
        if (codePoint == '\r') return true;
        if (codePoint == '\t') return false;
        if (codePoint < 32) return false;

        pushUndo();
        if (hasSelection()) deleteSelection();

        if (codePoint == '{' && shouldAutoIndentOnBrace()) {
            insertBraceBlock();
            invalidateContent();
            notifyChanged();
            return true;
        }

        lines.get(cursorLine).insert(cursorCol, codePoint);
        cursorCol++;
        invalidateContent();
        notifyChanged();
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!isFocused() || !editable) return false;

        boolean ctrl = (modifiers & 2) != 0;

        if (ctrl) {
            switch (keyCode) {
                case 67: copySelection(); return true;
                case 86: pasteFromClipboard(); return true;
                case 88: cutSelection(); return true;
                case 65: selectAll(); return true;
                case 90: undo(); return true;
                case 89: redo(); return true;
                default: return false;
            }
        }

        switch (keyCode) {
            case 257: case 335: insertNewLine(); return true;
            case 259: backspace(); return true;
            case 261: delete(); return true;
            case 262: moveRight(); return true;
            case 263: moveLeft(); return true;
            case 264: moveDown(); return true;
            case 265: moveUp(); return true;
            case 268: cursorCol = 0; return true;
            case 269: cursorCol = lines.get(cursorLine).length(); return true;
            default: return false;
        }
    }

    private void copySelection() {
        if (!hasSelection()) return;
        Minecraft.getInstance().keyboardHandler.setClipboard(getSelectedText());
    }

    private void cutSelection() {
        if (!hasSelection()) return;
        pushUndo();
        Minecraft.getInstance().keyboardHandler.setClipboard(getSelectedText());
        deleteSelection();
        invalidateContent();
        notifyChanged();
    }

    private void pasteFromClipboard() {
        String clip = Minecraft.getInstance().keyboardHandler.getClipboard();
        if (clip == null || clip.isEmpty()) return;
        clip = clip.replace("\r", "");
        pushUndo();
        if (hasSelection()) deleteSelection();

        String[] pasted = clip.split("\n", -1);
        StringBuilder current = lines.get(cursorLine);
        String tail = current.substring(cursorCol);
        current.delete(cursorCol, current.length());

        if (pasted.length == 1) {
            current.append(pasted[0]).append(tail);
            cursorCol += pasted[0].length();
        } else {
            current.append(pasted[0]);
            int insertLine = cursorLine + 1;
            for (int i = 1; i < pasted.length - 1; i++) {
                lines.add(insertLine++, new StringBuilder(pasted[i]));
            }
            lines.add(insertLine, new StringBuilder(pasted[pasted.length - 1] + tail));
            cursorLine = insertLine;
            cursorCol = pasted[pasted.length - 1].length();
        }
        ensureCursorVisible();
        invalidateContent();
        notifyChanged();
    }

    private void selectAll() {
        selStartLine = 0; selStartCol = 0;
        selEndLine = lines.size() - 1;
        selEndCol = lines.get(selEndLine).length();
    }

    private void insertNewLine() {
        pushUndo();
        if (hasSelection()) deleteSelection();
        StringBuilder line = lines.get(cursorLine);
        String rest = line.substring(cursorCol);

        String indent = "";
        if (shouldAutoIndentOnEnter()) {
            indent = computeIndentAtCursor();
        }

        line.delete(cursorCol, line.length());
        lines.add(cursorLine + 1, new StringBuilder(indent + rest));
        cursorLine++;
        cursorCol = indent.length();
        ensureCursorVisible();
        invalidateContent();
        notifyChanged();
    }

    private void backspace() {
        if (hasSelection()) { pushUndo(); deleteSelection(); invalidateContent(); notifyChanged(); return; }

        if (cursorCol > 0 && MaredSettings.isBackspaceRemovesIndent()) {
            String line = lines.get(cursorLine).toString();
            String upToCursor = line.substring(0, cursorCol);
            if (isIndentOnly(upToCursor)) {
                String unit = MaredSettings.indentUnit();
                int removeLen = 0;
                if (upToCursor.endsWith(unit)) removeLen = unit.length();
                else if (upToCursor.endsWith("\t")) removeLen = 1;
                else if (upToCursor.length() > 0 && upToCursor.charAt(upToCursor.length() - 1) == ' ') {
                    int end = upToCursor.length();
                    int start = end;
                    while (start > 0 && upToCursor.charAt(start - 1) == ' ') start--;
                    int maxLen = Math.min(unit.length(), end - start);
                    removeLen = maxLen > 0 ? maxLen : 1;
                } else {
                    removeLen = 1;
                }
                if (removeLen > 0) {
                    pushUndo();
                    lines.get(cursorLine).delete(cursorCol - removeLen, cursorCol);
                    cursorCol -= removeLen;
                    invalidateContent();
                    notifyChanged();
                    return;
                }
            }
        }

        if (cursorCol > 0) {
            pushUndo();
            lines.get(cursorLine).deleteCharAt(cursorCol - 1);
            cursorCol--;
            invalidateContent();
            notifyChanged();
        } else if (cursorLine > 0) {
            pushUndo();
            StringBuilder prev = lines.get(cursorLine - 1);
            StringBuilder cur = lines.get(cursorLine);
            cursorCol = prev.length();
            prev.append(cur);
            lines.remove(cursorLine);
            cursorLine--;
            ensureCursorVisible();
            invalidateContent();
            notifyChanged();
        }
    }

    private void delete() {
        if (hasSelection()) { pushUndo(); deleteSelection(); invalidateContent(); notifyChanged(); return; }
        StringBuilder line = lines.get(cursorLine);
        if (cursorCol < line.length()) {
            pushUndo();
            line.deleteCharAt(cursorCol);
            invalidateContent();
            notifyChanged();
        } else if (cursorLine < lines.size() - 1) {
            pushUndo();
            line.append(lines.get(cursorLine + 1));
            lines.remove(cursorLine + 1);
            invalidateContent();
            notifyChanged();
        }
    }

    private void moveLeft() {
        if (hasSelection() && !selecting) { clearSelection(); return; }
        if (cursorCol > 0) cursorCol--;
        else if (cursorLine > 0) {
            cursorLine--;
            cursorCol = lines.get(cursorLine).length();
            ensureCursorVisible();
        }
    }

    private void moveRight() {
        if (hasSelection() && !selecting) { clearSelection(); return; }
        StringBuilder line = lines.get(cursorLine);
        if (cursorCol < line.length()) cursorCol++;
        else if (cursorLine < lines.size() - 1) {
            cursorLine++;
            cursorCol = 0;
            ensureCursorVisible();
        }
    }

    private void moveUp() {
        if (cursorLine > 0) {
            cursorLine--;
            cursorCol = Math.min(cursorCol, lines.get(cursorLine).length());
            ensureCursorVisible();
        }
    }

    private void moveDown() {
        if (cursorLine < lines.size() - 1) {
            cursorLine++;
            cursorCol = Math.min(cursorCol, lines.get(cursorLine).length());
            ensureCursorVisible();
        }
    }

    private void ensureCursorVisible() {
        int visibleLines = visibleLogicalRows();
        if (cursorLine < scrollLine) scrollLine = cursorLine;
        if (cursorLine >= scrollLine + visibleLines) scrollLine = cursorLine - visibleLines + 1;
        if (scrollLine < 0) scrollLine = 0;
    }

    /** Сколько логических строк влезает. Используется для скролла. */
    private int visibleLogicalRows() {
        return Math.max(1, (height - PADDING * 2) / LINE_HEIGHT);
    }

    // ============================================================
    //  АВТО-ФОРМАТ
    // ============================================================

    private boolean shouldAutoIndentOnBrace() {
        MaredSettings.AutoIndent m = MaredSettings.getAutoIndent();
        return m == MaredSettings.AutoIndent.SIMPLE || m == MaredSettings.AutoIndent.FULL;
    }

    private boolean shouldAutoIndentOnEnter() {
        MaredSettings.AutoIndent m = MaredSettings.getAutoIndent();
        return m == MaredSettings.AutoIndent.SMART || m == MaredSettings.AutoIndent.FULL;
    }

    private void insertBraceBlock() {
        String unit = MaredSettings.indentUnit();
        StringBuilder line = lines.get(cursorLine);
        String tail = line.substring(cursorCol);
        line.delete(cursorCol, line.length());
        line.append('{').append(tail);

        String currentIndent = extractIndent(line.toString());
        String bodyIndent = currentIndent + unit;

        cursorLine++;
        lines.add(cursorLine, new StringBuilder(bodyIndent));
        cursorCol = bodyIndent.length();

        cursorLine++;
        lines.add(cursorLine, new StringBuilder(currentIndent + "}"));
        cursorLine--;
        cursorCol = bodyIndent.length();

        ensureCursorVisible();
    }

    private String computeIndentAtCursor() {
        if (cursorLine < 0 || cursorLine >= lines.size()) return "";
        String currentLine = lines.get(cursorLine).toString();
        String currentIndent = extractIndent(currentLine);
        String unit = MaredSettings.indentUnit();

        String restAfterCursor = currentLine.substring(cursorCol).trim();
        String beforeCursor = currentLine.substring(0, cursorCol);

        if (beforeCursor.trim().endsWith("{")) {
            return currentIndent + unit;
        }
        if (restAfterCursor.startsWith("}")) {
            return currentIndent;
        }
        return currentIndent;
    }

    private String extractIndent(String line) {
        int i = 0;
        while (i < line.length() && (line.charAt(i) == ' ' || line.charAt(i) == '\t')) i++;
        return line.substring(0, i);
    }

    private boolean isIndentOnly(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c != ' ' && c != '\t') return false;
        }
        return true;
    }

    // ---- Mouse ----

    @Override
    public void onClick(double mx, double my) {
        if (!editable) return;

        if (isOverScrollbar(mx, my)) {
            beginScrollbarDrag(my);
            return;
        }

        this.setFocused(true);
        int[] pos = pixelToPos(mx, my);
        cursorLine = pos[0];
        cursorCol = pos[1];
        startSelection(cursorLine, cursorCol);
        selecting = true;
        ensureCursorVisible();
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (draggingScrollbar) {
            dragScrollbar(my);
            return true;
        }
        if (!editable || !selecting) return false;
        int[] pos = pixelToPos(mx, my);
        cursorLine = pos[0];
        cursorCol = pos[1];
        extendSelection(cursorLine, cursorCol);
        ensureCursorVisible();
        return true;
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        selecting = false;
        draggingScrollbar = false;
        return super.mouseReleased(mx, my, button);
    }

    // ============================================================
    //  pixelToPos — быстрая версия (накопление ширины)
    // ============================================================

    private int[] pixelToPos(double mx, double my) {
        int relX = (int) mx - getX() - PADDING - LINE_NUM_WIDTH;
        int relY = (int) my - getY() - PADDING;
        int clickedVisualRow = relY / LINE_HEIGHT + scrollLine;
        int maxVisual = Math.max(0, totalVisualRows() - 1);
        clickedVisualRow = Mth.clamp(clickedVisualRow, 0, maxVisual);

        // Найти логическую строку и позицию внутри неё
        int logLine = findLogicalLineByVisualRow(clickedVisualRow);
        LineWrap wrap = getWrapFor(logLine);
        if (wrap == null) return new int[]{0, 0};

        int physicalStart = visualRowStarts[logLine];
        int physIdx = clickedVisualRow - physicalStart;
        if (physIdx < 0) physIdx = 0;
        if (physIdx >= wrap.physical.size()) physIdx = wrap.physical.size() - 1;

        String physicalText = wrap.physical.get(physIdx);
        int baseChar = wrap.charStart[physIdx];

        // Накопление ширины
        Font font = Minecraft.getInstance().font;
        int accW = 0;
        int col = physicalText.length();
        for (int i = 0; i < physicalText.length(); i++) {
            int chW = font.width(String.valueOf(physicalText.charAt(i)));
            if (accW + chW >= relX) {
                col = i;
                break;
            }
            accW += chW;
        }

        int logicalCol = baseChar + col;
        String fullLine = lines.get(logLine).toString();
        logicalCol = Mth.clamp(logicalCol, 0, fullLine.length());
        return new int[]{logLine, logicalCol};
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double deltaX, double deltaY) {
        if (!this.isMouseOver(mx, my)) return false;
        int visibleLines = visibleLogicalRows();
        int maxScroll = Math.max(0, lines.size() - visibleLines);
        if (deltaY < 0) scrollLine = Math.min(maxScroll, scrollLine + 1);
        else if (deltaY > 0) scrollLine = Math.max(0, scrollLine - 1);
        return true;
    }

    // ---- Скроллбар ----

    private boolean isScrollable() {
        return lines.size() > visibleLogicalRows();
    }

    private boolean isOverScrollbar(double mx, double my) {
        if (!isScrollable()) return false;
        int sbX = getX() + width - SCROLLBAR_W - 1;
        return mx >= sbX - 2 && mx < getX() + width
            && my >= getY() && my < getY() + height;
    }

    private int scrollbarTrackH() {
        return height - 2;
    }

    private int scrollbarThumbH() {
        int visibleLines = visibleLogicalRows();
        int totalLines = lines.size();
        return Math.max(10, scrollbarTrackH() * visibleLines / Math.max(1, totalLines));
    }

    private int scrollbarThumbY() {
        int visibleLines = visibleLogicalRows();
        int totalLines = lines.size();
        int maxScroll = Math.max(0, totalLines - visibleLines);
        if (maxScroll == 0) return getY() + 1;
        int travel = scrollbarTrackH() - scrollbarThumbH();
        return getY() + 1 + travel * scrollLine / maxScroll;
    }

    private void beginScrollbarDrag(double my) {
        draggingScrollbar = true;
        dragStartY = my;
        dragStartScroll = scrollLine;
    }

    private void dragScrollbar(double my) {
        int visibleLines = visibleLogicalRows();
        int totalLines = lines.size();
        int maxScroll = Math.max(0, totalLines - visibleLines);
        int travel = Math.max(1, scrollbarTrackH() - scrollbarThumbH());
        int delta = (int) Math.round((my - dragStartY) * maxScroll / travel);
        scrollLine = Mth.clamp(dragStartScroll + delta, 0, maxScroll);
    }

    // ============================================================
    //  Wrap cache
    // ============================================================

    /** Доступная ширина для текста (без номеров строк и скроллбара). */
    private int availableTextWidth() {
        return width - PADDING * 2 - LINE_NUM_WIDTH - SCROLLBAR_W;
    }

    /** Построить (или вернуть из кэша) wrap-данные для одной строки. */
    private LineWrap getWrapFor(int logLine) {
        ensureWrapCache();

        if (logLine < 0 || logLine >= lines.size()) return null;
        LineWrap cached = wrapCache.get(logLine);
        if (cached != null) return cached;

        Font font = Minecraft.getInstance().font;
        String text = lines.get(logLine).toString();
        int availW = availableTextWidth();

        List<String> physical = MaredUi.wrapLinesCached(font, text, availW);

        int[] charStart = new int[physical.size()];
        charStart[0] = 0;
        int searchFrom = 0;
        for (int i = 1; i < physical.size(); i++) {
            int found = text.indexOf(physical.get(i), searchFrom);
            if (found < 0) found = searchFrom;
            // пропускаем пробелы
            while (found < text.length() && text.charAt(found) == ' ') found++;
            charStart[i] = found;
            searchFrom = found + physical.get(i).length();
        }

        LineWrap lw = new LineWrap(physical, charStart);
        wrapCache.set(logLine, lw);
        return lw;
    }

    /** Перестроить wrapCache, если версии не совпадают. */
    private void ensureWrapCache() {
        if (wrapCacheContentVersion == contentVersion
            && wrapCacheWidthVersion == widthVersion
            && wrapCacheAvailableWidth == availableTextWidth()
            && wrapCache.size() == lines.size()) {
            return;
        }

        int n = lines.size();
        wrapCache.clear();
        for (int i = 0; i < n; i++) wrapCache.add(null);  // ленивая инициализация

        wrapCacheContentVersion = contentVersion;
        wrapCacheWidthVersion = widthVersion;
        wrapCacheAvailableWidth = availableTextWidth();

        // Инвалидируем rowStarts — они тоже зависят от wrap
        rowStartsContentVersion = -1;
    }

    // ============================================================
    //  Visual row starts (для быстрого поиска логической строки по visual row)
    // ============================================================

    /**
     * visualRowStarts[i] = визуальный ряд, с которого начинается логическая строка i.
     * cachedTotalVisualRows = общее количество визуальных рядов.
     */
    private void ensureVisualRowStarts() {
        if (rowStartsContentVersion == contentVersion
            && rowStartsWidthVersion == widthVersion
            && visualRowStarts != null
            && visualRowStarts.length == lines.size() + 1) {
            return;
        }

        ensureWrapCache();

        int n = lines.size();
        int[] starts = new int[n + 1];
        int total = 0;
        for (int i = 0; i < n; i++) {
            starts[i] = total;
            LineWrap lw = getWrapFor(i);
            total += (lw != null ? lw.physical.size() : 1);
        }
        starts[n] = total;

        visualRowStarts = starts;
        cachedTotalVisualRows = total;
        rowStartsContentVersion = contentVersion;
        rowStartsWidthVersion = widthVersion;
    }

    private int totalVisualRows() {
        ensureVisualRowStarts();
        return cachedTotalVisualRows;
    }

    /** Бинарный поиск логической строки по visual row. */
    private int findLogicalLineByVisualRow(int visualRow) {
        ensureVisualRowStarts();
        int n = lines.size();
        if (n == 0) return 0;
        // visualRowStarts — отсортирован
        int lo = 0, hi = n - 1;
        while (lo < hi) {
            int mid = (lo + hi + 1) >>> 1;
            if (visualRowStarts[mid] <= visualRow) lo = mid;
            else hi = mid - 1;
        }
        return lo;
    }

    // ============================================================
    //  Render
    // ============================================================

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        Font font = Minecraft.getInstance().font;

        graphics.fill(getX(), getY(), getX() + width, getY() + height, COLOR_BG);

        graphics.enableScissor(getX() + 1, getY() + 1, getX() + width - 1, getY() + height - 1);

        int visibleVisualRows = (height - PADDING * 2) / LINE_HEIGHT;
        int textX0 = getX() + PADDING + LINE_NUM_WIDTH;
        int lineNumX = getX() + PADDING;

        ensureVisualRowStarts();
        int totalVisual = cachedTotalVisualRows;

        // Диапазон видимых visual rows: [scrollLine, scrollLine + visibleVisualRows)
        int visualStart = scrollLine;
        int visualEnd = Math.min(totalVisual, scrollLine + visibleVisualRows);

        // Selection bounds (pre-compute)
        int selStartL = -1, selStartC = -1, selEndL = -1, selEndC = -1;
        if (hasSelection()) {
            int[] s = orderSelectionStart();
            int[] e = orderSelectionEnd();
            selStartL = s[0]; selStartC = s[1];
            selEndL = e[0]; selEndC = e[1];
        }

        // Итерация по логическим строкам, начиная с той, что содержит visualStart
        int startLog = findLogicalLineByVisualRow(visualStart);

        // Найти конец: логическая строка, которая содержит visualEnd (или последняя)
        int endLog = (visualEnd > 0) ? findLogicalLineByVisualRow(visualEnd - 1) : startLog;
        if (endLog >= lines.size()) endLog = lines.size() - 1;

        int cursorVisualRow = -1;
        int cursorX = textX0;

        for (int li = startLog; li <= endLog; li++) {
            LineWrap wrap = getWrapFor(li);
            if (wrap == null) continue;

            int physicalStart = visualRowStarts[li];
            List<String> physical = wrap.physical;
            int physCount = physical.size();

            for (int pIdx = 0; pIdx < physCount; pIdx++) {
                int currentVisual = physicalStart + pIdx;
                if (currentVisual < scrollLine) continue;
                if (currentVisual >= scrollLine + visibleVisualRows) break;

                int drawRow = currentVisual - scrollLine;
                int lineY = getY() + PADDING + drawRow * LINE_HEIGHT;
                if (lineY >= getY() + height - PADDING) continue;

                String physicalText = physical.get(pIdx);
                int baseChar = wrap.charStart[pIdx];

                // Номер строки — только для первой физической строки
                if (pIdx == 0) {
                    String num = String.valueOf(li + 1);
                    graphics.drawString(font, num, lineNumX, lineY, COLOR_NUMBERS, false);
                }

                int curX = textX0;
                int physLen = physicalText.length();
                for (int c = 0; c < physLen; c++) {
                    char rawChar = physicalText.charAt(c);
                    String ch = rawChar == '\t' ? "    " : String.valueOf(rawChar);
                    int chWidth = font.width(ch);

                    int logicalCol = baseChar + c;

                    // Selection
                    if (selStartL >= 0) {
                        boolean sel = isCharSelectedFast(li, logicalCol,
                            selStartL, selStartC, selEndL, selEndC);
                        if (sel) {
                            graphics.fill(curX, lineY, curX + chWidth, lineY + LINE_HEIGHT - 1, COLOR_SELECT);
                        }
                    }

                    graphics.drawString(font, ch, curX, lineY, COLOR_TEXT, false);
                    curX += chWidth;

                    if (cursorLine == li && cursorCol == logicalCol) {
                        cursorVisualRow = currentVisual;
                        cursorX = curX - chWidth;
                    }
                }

                // Курсор в конце физической строки
                if (cursorLine == li && cursorCol == baseChar + physLen) {
                    cursorVisualRow = currentVisual;
                    cursorX = curX;
                }
            }
        }

        // Курсор в самом конце логической строки (после последней физической)
        if (cursorLine >= 0 && cursorLine < lines.size()
            && cursorCol == lines.get(cursorLine).length()) {
            int physStart = visualRowStarts[cursorLine];
            LineWrap wrap = getWrapFor(cursorLine);
            if (wrap != null) {
                int lastPhys = physStart + wrap.physical.size() - 1;
                cursorVisualRow = lastPhys;
            }
        }

        if (editable && isFocused() && cursorVisualRow >= 0) {
            long now = System.currentTimeMillis();
            if (now - lastBlink > CURSOR_BLINK) {
                cursorVisible = !cursorVisible;
                lastBlink = now;
            }
            if (cursorVisible) {
                int drawRow = cursorVisualRow - scrollLine;
                if (drawRow >= 0 && drawRow < visibleVisualRows) {
                    int cy = getY() + PADDING + drawRow * LINE_HEIGHT;
                    if (cy >= getY() && cy < getY() + height) {
                        graphics.fill(cursorX, cy + 1, cursorX + 1, cy + LINE_HEIGHT - 1, accentColor);
                    }
                }
            }
        }

        graphics.disableScissor();

        // === Скроллбар ===
        if (isScrollable()) {
            int sbX = getX() + width - SCROLLBAR_W - 1;
            int sbY = getY() + 1;
            int sbH = scrollbarTrackH();

            graphics.fill(sbX, sbY, sbX + SCROLLBAR_W, sbY + sbH, COLOR_SB_TRACK);

            int thumbH = scrollbarThumbH();
            int thumbY = scrollbarThumbY();
            graphics.fill(sbX, thumbY, sbX + SCROLLBAR_W, thumbY + thumbH, accentColor);
        }
    }

    /** Fast selection check — inline, без массивов. */
    private static boolean isCharSelectedFast(int line, int col,
                                              int sl, int sc, int el, int ec) {
        if (line < sl || line > el) return false;
        if (line == sl && line == el) return col >= sc && col < ec;
        if (line == sl) return col >= sc;
        if (line == el) return col < ec;
        return true;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput narration) { /* Empty. */ }

    @Override
    public boolean isMouseOver(double mx, double my) {
        return mx >= getX() && mx < getX() + width && my >= getY() && my < getY() + height;
    }
}