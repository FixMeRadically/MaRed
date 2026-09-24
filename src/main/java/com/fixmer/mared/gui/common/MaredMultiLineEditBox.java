package com.fixmer.mared.gui.common;

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

    private static final int COLOR_TEXT     = 0xFFDDDDDD;
    private static final int COLOR_NUMBERS  = 0xFF666680;
    private static final int COLOR_SELECT   = 0x804466CC;
    private static final int COLOR_BG       = 0xFF0E0E16;
    private static final int COLOR_SB_TRACK = 0xFF15151E;

    // ============================================================
    //  Модель
    // ============================================================

    private final List<StringBuilder> lines = new ArrayList<>(64);
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

    // ---- Скроллбар ----
    private boolean draggingScrollbar = false;
    private double dragStartY = 0;
    private int dragStartScroll = 0;

    // ---- Undo / Redo (компактно: String[] + курсор) ----
    private final Deque<UndoEntry> undoStack = new ArrayDeque<>(MAX_UNDO);
    private final Deque<UndoEntry> redoStack = new ArrayDeque<>(MAX_UNDO);
    private boolean undoPaused = false;

    private static final class UndoEntry {
        final String[] lines;
        final int cursorLine;
        final int cursorCol;
        UndoEntry(String[] lines, int cl, int cc) {
            this.lines = lines;
            this.cursorLine = cl;
            this.cursorCol = cc;
        }
    }

    // ============================================================
    //  Wrap-кэш
    // ============================================================

    private static final class LineWrap {
        final List<String> physical;
        final int[] charStart;
        LineWrap(List<String> physical, int[] charStart) {
            this.physical = physical;
            this.charStart = charStart;
        }
    }

    private final List<LineWrap> wrapCache = new ArrayList<>(64);
    private int contentVersion = 0;
    private int widthVersion = 0;
    private int wrapCacheContentVersion = -1;
    private int wrapCacheWidthVersion = -1;
    private int wrapCacheAvailableWidth = -1;

    private int[] visualRowStarts = null;
    private int cachedTotalVisualRows = -1;
    private int rowStartsContentVersion = -1;
    private int rowStartsWidthVersion = -1;

    // ============================================================
    //  Constructor
    // ============================================================

    public MaredMultiLineEditBox(int x, int y, int width, int height,
                                 int accentColor, Runnable onValueChanged) {
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

    private void invalidateWidth()   { widthVersion++; }
    private void invalidateContent() { contentVersion++; }

    // ============================================================
    //  Value
    // ============================================================

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

    public void setValue(String text) {
        if (text != null) text = text.replace("\r", "");
        undoPaused = true;
        lines.clear();
        String[] parts = text.split("\n", -1);
        for (String p : parts) lines.add(new StringBuilder(p));
        if (lines.isEmpty()) lines.add(new StringBuilder());
        cursorLine = 0;
        cursorCol = 0;
        scrollLine = 0;
        clearSelection();
        undoStack.clear();
        redoStack.clear();
        undoPaused = false;
        invalidateContent();
        notifyChanged();
    }

    private void notifyChanged() { if (onValueChanged != null) onValueChanged.run(); }

    // ============================================================
    //  Undo / Redo
    // ============================================================

    private void pushUndo() {
        if (undoPaused) return;
        undoStack.push(new UndoEntry(snapshotLines(), cursorLine, cursorCol));
        if (undoStack.size() > MAX_UNDO) undoStack.removeLast();
        redoStack.clear();
    }

    private String[] snapshotLines() {
        int n = lines.size();
        String[] arr = new String[n];
        for (int i = 0; i < n; i++) arr[i] = lines.get(i).toString();
        return arr;
    }

    private void applySnapshot(String[] arr, int cl, int cc) {
        lines.clear();
        for (String s : arr) lines.add(new StringBuilder(s));
        if (lines.isEmpty()) lines.add(new StringBuilder());
        cursorLine = Mth.clamp(cl, 0, lines.size() - 1);
        cursorCol = Mth.clamp(cc, 0, lines.get(cursorLine).length());
        clearSelection();
        ensureCursorVisible();
    }

    private void undo() {
        if (undoStack.isEmpty()) return;
        UndoEntry snap = undoStack.pop();
        redoStack.push(new UndoEntry(snapshotLines(), cursorLine, cursorCol));
        undoPaused = true;
        applySnapshot(snap.lines, snap.cursorLine, snap.cursorCol);
        undoPaused = false;
        invalidateContent();
        notifyChanged();
    }

    private void redo() {
        if (redoStack.isEmpty()) return;
        UndoEntry snap = redoStack.pop();
        undoStack.push(new UndoEntry(snapshotLines(), cursorLine, cursorCol));
        undoPaused = true;
        applySnapshot(snap.lines, snap.cursorLine, snap.cursorCol);
        undoPaused = false;
        invalidateContent();
        notifyChanged();
    }

    // ============================================================
    //  Selection
    // ============================================================

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
        int[] s = orderStart();
        int[] e = orderEnd();
        int sl = s[0], sc = s[1], el = e[0], ec = e[1];
        if (sl == el) return lines.get(sl).substring(sc, ec);
        StringBuilder sb = new StringBuilder(64);
        sb.append(lines.get(sl).substring(sc));
        for (int i = sl + 1; i < el; i++) sb.append('\n').append(lines.get(i));
        sb.append('\n').append(lines.get(el).substring(0, ec));
        return sb.toString();
    }

    private int[] orderStart() {
        if (selStartLine < selEndLine
            || (selStartLine == selEndLine && selStartCol <= selEndCol))
            return new int[]{selStartLine, selStartCol};
        return new int[]{selEndLine, selEndCol};
    }

    private int[] orderEnd() {
        if (selStartLine < selEndLine
            || (selStartLine == selEndLine && selStartCol <= selEndCol))
            return new int[]{selEndLine, selEndCol};
        return new int[]{selStartLine, selStartCol};
    }

    private void deleteSelection() {
        if (!hasSelection()) return;
        int[] s = orderStart();
        int[] e = orderEnd();
        int sl = s[0], sc = s[1], el = e[0], ec = e[1];

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
    //  Input
    // ============================================================

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
        StringBuilder cur = lines.get(cursorLine);
        String tail = cur.substring(cursorCol);
        cur.delete(cursorCol, cur.length());

        if (pasted.length == 1) {
            cur.append(pasted[0]).append(tail);
            cursorCol += pasted[0].length();
        } else {
            cur.append(pasted[0]);
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

        String indent = shouldAutoIndentOnEnter() ? computeIndentAtCursor() : "";
        line.delete(cursorCol, line.length());
        lines.add(cursorLine + 1, new StringBuilder(indent + rest));
        cursorLine++;
        cursorCol = indent.length();
        ensureCursorVisible();
        invalidateContent();
        notifyChanged();
    }

    private void backspace() {
        if (hasSelection()) {
            pushUndo();
            deleteSelection();
            invalidateContent();
            notifyChanged();
            return;
        }

        if (cursorCol > 0 && MaredSettings.isBackspaceRemovesIndent()) {
            String line = lines.get(cursorLine).toString();
            String upToCursor = line.substring(0, cursorCol);
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
                pushUndo();
                lines.get(cursorLine).delete(cursorCol - removeLen, cursorCol);
                cursorCol -= removeLen;
                invalidateContent();
                notifyChanged();
                return;
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
        if (hasSelection()) {
            pushUndo();
            deleteSelection();
            invalidateContent();
            notifyChanged();
            return;
        }
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

    /**
     * Курсор виден: работает по ВИЗУАЛЬНЫМ рядам (учитывает wrap).
     */
    private void ensureCursorVisible() {
        ensureVisualRowStarts();
        int cursorVisual = visualRowStarts[Math.min(cursorLine, lines.size() - 1)];
        int visibleRows = visibleVisualRows();
        if (cursorVisual < scrollLine) scrollLine = cursorVisual;
        else if (cursorVisual >= scrollLine + visibleRows) scrollLine = cursorVisual - visibleRows + 1;
        if (scrollLine < 0) scrollLine = 0;
    }

    private int visibleVisualRows() {
        return Math.max(1, (height - PADDING * 2) / LINE_HEIGHT);
    }

    // ============================================================
    //  Авто-формат
    // ============================================================

    private boolean shouldAutoIndentOnBrace() {
        var m = MaredSettings.getAutoIndent();
        return m == MaredSettings.AutoIndent.SIMPLE || m == MaredSettings.AutoIndent.FULL;
    }

    private boolean shouldAutoIndentOnEnter() {
        var m = MaredSettings.getAutoIndent();
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

        String beforeCursor = currentLine.substring(0, cursorCol);
        String restAfterCursor = currentLine.substring(cursorCol).trim();

        if (beforeCursor.trim().endsWith("{")) return currentIndent + unit;
        if (restAfterCursor.startsWith("}")) return currentIndent;
        return currentIndent;
    }

    private String extractIndent(String line) {
        int i = 0;
        int n = line.length();
        while (i < n && (line.charAt(i) == ' ' || line.charAt(i) == '\t')) i++;
        return line.substring(0, i);
    }

    private boolean isIndentOnly(String s) {
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

    @Override
    public void onClick(double mx, double my) {
        if (!editable) return;

        if (isOverScrollbar(mx, my)) { beginScrollbarDrag(my); return; }

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
        if (draggingScrollbar) { dragScrollbar(my); return true; }
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

    private int[] pixelToPos(double mx, double my) {
        int relX = (int) mx - getX() - PADDING - LINE_NUM_WIDTH;
        int relY = (int) my - getY() - PADDING;
        int clickedVisual = relY / LINE_HEIGHT + scrollLine;
        int maxVisual = Math.max(0, totalVisualRows() - 1);
        clickedVisual = Mth.clamp(clickedVisual, 0, maxVisual);

        int logLine = findLogicalLineByVisualRow(clickedVisual);
        LineWrap wrap = getWrapFor(logLine);
        if (wrap == null) return new int[]{0, 0};

        int physicalStart = visualRowStarts[logLine];
        int physIdx = clickedVisual - physicalStart;
        if (physIdx < 0) physIdx = 0;
        if (physIdx >= wrap.physical.size()) physIdx = wrap.physical.size() - 1;

        String physicalText = wrap.physical.get(physIdx);
        int baseChar = wrap.charStart[physIdx];

        Font font = Minecraft.getInstance().font;
        int accW = 0;
        int col = physicalText.length();
        for (int i = 0; i < physicalText.length(); i++) {
            int chW = font.width(String.valueOf(physicalText.charAt(i)));
            if (accW + chW >= relX) { col = i; break; }
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
        int maxScroll = Math.max(0, totalVisualRows() - visibleVisualRows());
        if (deltaY < 0) scrollLine = Math.min(maxScroll, scrollLine + 1);
        else if (deltaY > 0) scrollLine = Math.max(0, scrollLine - 1);
        return true;
    }

    private boolean isScrollable() {
        return totalVisualRows() > visibleVisualRows();
    }

    private boolean isOverScrollbar(double mx, double my) {
        if (!isScrollable()) return false;
        int sbX = getX() + width - SCROLLBAR_W - 1;
        return mx >= sbX - 2 && mx < getX() + width
            && my >= getY() && my < getY() + height;
    }

    private int scrollbarTrackH() { return height - 2; }

    private int scrollbarThumbH() {
        int total = Math.max(1, totalVisualRows());
        return Math.max(10, scrollbarTrackH() * visibleVisualRows() / total);
    }

    private int scrollbarThumbY() {
        int maxScroll = Math.max(0, totalVisualRows() - visibleVisualRows());
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
        int maxScroll = Math.max(0, totalVisualRows() - visibleVisualRows());
        int travel = Math.max(1, scrollbarTrackH() - scrollbarThumbH());
        int delta = (int) Math.round((my - dragStartY) * maxScroll / travel);
        scrollLine = Mth.clamp(dragStartScroll + delta, 0, maxScroll);
    }

    // ============================================================
    //  Wrap cache
    // ============================================================

    private int availableTextWidth() {
        return width - PADDING * 2 - LINE_NUM_WIDTH - SCROLLBAR_W;
    }

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
            while (found < text.length() && text.charAt(found) == ' ') found++;
            charStart[i] = found;
            searchFrom = found + physical.get(i).length();
        }

        LineWrap lw = new LineWrap(physical, charStart);
        wrapCache.set(logLine, lw);
        return lw;
    }

    private void ensureWrapCache() {
        if (wrapCacheContentVersion == contentVersion
            && wrapCacheWidthVersion == widthVersion
            && wrapCacheAvailableWidth == availableTextWidth()
            && wrapCache.size() == lines.size()) return;

        int n = lines.size();
        wrapCache.clear();
        for (int i = 0; i < n; i++) wrapCache.add(null);

        wrapCacheContentVersion = contentVersion;
        wrapCacheWidthVersion = widthVersion;
        wrapCacheAvailableWidth = availableTextWidth();
        rowStartsContentVersion = -1;
    }

    private void ensureVisualRowStarts() {
        if (rowStartsContentVersion == contentVersion
            && rowStartsWidthVersion == widthVersion
            && visualRowStarts != null
            && visualRowStarts.length == lines.size() + 1) return;

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

    private int findLogicalLineByVisualRow(int visualRow) {
        ensureVisualRowStarts();
        int n = lines.size();
        if (n == 0) return 0;
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

        int visibleRows = visibleVisualRows();
        int textX0 = getX() + PADDING + LINE_NUM_WIDTH;
        int lineNumX = getX() + PADDING;

        ensureVisualRowStarts();
        int totalVisual = cachedTotalVisualRows;

        int visualStart = scrollLine;
        int visualEnd = Math.min(totalVisual, scrollLine + visibleRows);

        int selStartL = -1, selStartC = -1, selEndL = -1, selEndC = -1;
        if (hasSelection()) {
            int[] s = orderStart();
            int[] e = orderEnd();
            selStartL = s[0]; selStartC = s[1];
            selEndL = e[0]; selEndC = e[1];
        }

        int startLog = findLogicalLineByVisualRow(visualStart);
        int endLog = (visualEnd > 0)
            ? findLogicalLineByVisualRow(visualEnd - 1)
            : startLog;
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
                if (currentVisual >= scrollLine + visibleRows) break;

                int drawRow = currentVisual - scrollLine;
                int lineY = getY() + PADDING + drawRow * LINE_HEIGHT;
                if (lineY >= getY() + height - PADDING) continue;

                String physicalText = physical.get(pIdx);
                int baseChar = wrap.charStart[pIdx];

                if (pIdx == 0) {
                    graphics.drawString(font, String.valueOf(li + 1),
                        lineNumX, lineY, COLOR_NUMBERS, false);
                }

                int curX = textX0;
                int physLen = physicalText.length();
                for (int c = 0; c < physLen; c++) {
                    char rawChar = physicalText.charAt(c);
                    String ch = rawChar == '\t' ? "    " : String.valueOf(rawChar);
                    int chWidth = font.width(ch);

                    int logicalCol = baseChar + c;

                    if (selStartL >= 0 && isCharSelectedFast(li, logicalCol,
                        selStartL, selStartC, selEndL, selEndC)) {
                        graphics.fill(curX, lineY, curX + chWidth,
                            lineY + LINE_HEIGHT - 1, COLOR_SELECT);
                    }

                    graphics.drawString(font, ch, curX, lineY, COLOR_TEXT, false);
                    curX += chWidth;

                    if (cursorLine == li && cursorCol == logicalCol) {
                        cursorVisualRow = currentVisual;
                        cursorX = curX - chWidth;
                    }
                }

                if (cursorLine == li && cursorCol == baseChar + physLen) {
                    cursorVisualRow = currentVisual;
                    cursorX = curX;
                }
            }
        }

        if (cursorLine >= 0 && cursorLine < lines.size()
            && cursorCol == lines.get(cursorLine).length()) {
            int physStart = visualRowStarts[cursorLine];
            LineWrap wrap = getWrapFor(cursorLine);
            if (wrap != null) cursorVisualRow = physStart + wrap.physical.size() - 1;
        }

        if (editable && isFocused() && cursorVisualRow >= 0) {
            long now = System.currentTimeMillis();
            if (now - lastBlink > CURSOR_BLINK) {
                cursorVisible = !cursorVisible;
                lastBlink = now;
            }
            if (cursorVisible) {
                int drawRow = cursorVisualRow - scrollLine;
                if (drawRow >= 0 && drawRow < visibleRows) {
                    int cy = getY() + PADDING + drawRow * LINE_HEIGHT;
                    if (cy >= getY() && cy < getY() + height) {
                        graphics.fill(cursorX, cy + 1, cursorX + 1,
                            cy + LINE_HEIGHT - 1, accentColor);
                    }
                }
            }
        }

        graphics.disableScissor();

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

    private static boolean isCharSelectedFast(int line, int col,
                                              int sl, int sc, int el, int ec) {
        if (line < sl || line > el) return false;
        if (line == sl && line == el) return col >= sc && col < ec;
        if (line == sl) return col >= sc;
        if (line == el) return col < ec;
        return true;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput narration) {}

    @Override
    public boolean isMouseOver(double mx, double my) {
        return mx >= getX() && mx < getX() + width
            && my >= getY() && my < getY() + height;
    }
}