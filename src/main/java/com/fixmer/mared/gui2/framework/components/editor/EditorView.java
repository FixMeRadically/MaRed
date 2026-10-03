package com.fixmer.mared.gui2.framework.components.editor;

import java.util.ArrayList;
import java.util.List;

import com.fixmer.mared.gui2.framework.render.MaredUi;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;

/**
 * View-слой редактора.
 *
 * 0.3.1: добавлен resetBlink() — редактор дёргает его при вводе,
 * чтобы курсор не пропадал в невидимой половине цикла.
 */
public final class EditorView {

    private static final int LINE_HEIGHT    = 10;
    private static final int PADDING        = 4;
    private static final int LINE_NUM_WIDTH = 20;
    private static final int SCROLLBAR_W    = 5;
    private static final int CURSOR_BLINK   = 500;

    private static final int COLOR_TEXT     = 0xFFDDDDDD;
    private static final int COLOR_NUMBERS  = 0xFF666680;
    private static final int COLOR_SELECT   = 0x804466CC;
    private static final int COLOR_BG       = 0xFF0E0E16;
    private static final int COLOR_SB_TRACK = 0xFF15151E;

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
    private int widthVersion = 0;
    private int wrapCacheContentVersion = -1;
    private int wrapCacheWidthVersion = -1;
    private int wrapCacheAvailableWidth = -1;

    private int[] visualRowStarts = null;
    private int cachedTotalVisualRows = -1;
    private int rowStartsContentVersion = -1;
    private int rowStartsWidthVersion = -1;

    // ============================================================
    //  Scroll / cursor-blink state
    // ============================================================

    private int scrollLine = 0;
    private long lastBlink = 0;
    private boolean cursorVisible = true;

    private boolean draggingScrollbar = false;
    private double dragStartY = 0;
    private int dragStartScroll = 0;

    public EditorView() {}

    public void invalidateWidth() { widthVersion++; }

    public void resetScroll() {
        scrollLine = 0;
        lastBlink = 0;
        cursorVisible = true;
        draggingScrollbar = false;
    }

    /**
     * 0.3.1: сбросить blink в видимое состояние.
     * Вызывается при вводе, клике, перемещении курсора — чтобы
     * пользователь сразу видел каретку.
     */
    public void resetBlink() {
        lastBlink = 0;
        cursorVisible = true;
    }

    public int scrollLine() { return scrollLine; }
    public boolean isDraggingScrollbar() { return draggingScrollbar; }

    // ============================================================
    //  Wrap cache
    // ============================================================

    private int availableTextWidth(int w) {
        return w - PADDING * 2 - LINE_NUM_WIDTH - SCROLLBAR_W;
    }

    private LineWrap getWrapFor(int logLine, int w, EditorDocument doc, Font font) {
        ensureWrapCache(w, doc);
        if (logLine < 0 || logLine >= doc.lineCount()) return null;
        LineWrap cached = wrapCache.get(logLine);
        if (cached != null) return cached;

        String text = doc.line(logLine);
        int availW = availableTextWidth(w);

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

    private void ensureWrapCache(int w, EditorDocument doc) {
        int docVersion = doc.contentVersion();
        if (wrapCacheContentVersion == docVersion
            && wrapCacheWidthVersion == widthVersion
            && wrapCacheAvailableWidth == availableTextWidth(w)
            && wrapCache.size() == doc.lineCount()) return;

        int n = doc.lineCount();
        wrapCache.clear();
        for (int i = 0; i < n; i++) wrapCache.add(null);

        wrapCacheContentVersion = docVersion;
        wrapCacheWidthVersion = widthVersion;
        wrapCacheAvailableWidth = availableTextWidth(w);
        rowStartsContentVersion = -1;
    }

    private void ensureVisualRowStarts(int w, EditorDocument doc, Font font) {
        int docVersion = doc.contentVersion();
        if (rowStartsContentVersion == docVersion
            && rowStartsWidthVersion == widthVersion
            && visualRowStarts != null
            && visualRowStarts.length == doc.lineCount() + 1) return;

        ensureWrapCache(w, doc);

        int n = doc.lineCount();
        int[] starts = new int[n + 1];
        int total = 0;
        for (int i = 0; i < n; i++) {
            starts[i] = total;
            LineWrap lw = getWrapFor(i, w, doc, font);
            total += (lw != null ? lw.physical.size() : 1);
        }
        starts[n] = total;

        visualRowStarts = starts;
        cachedTotalVisualRows = total;
        rowStartsContentVersion = docVersion;
        rowStartsWidthVersion = widthVersion;
    }

    private int totalVisualRows(int w, EditorDocument doc, Font font) {
        ensureVisualRowStarts(w, doc, font);
        return cachedTotalVisualRows;
    }

    private int findLogicalLineByVisualRow(int visualRow, int w,
                                           EditorDocument doc, Font font) {
        ensureVisualRowStarts(w, doc, font);
        int n = doc.lineCount();
        if (n == 0) return 0;
        int lo = 0, hi = n - 1;
        while (lo < hi) {
            int mid = (lo + hi + 1) >>> 1;
            if (visualRowStarts[mid] <= visualRow) lo = mid;
            else hi = mid - 1;
        }
        return lo;
    }

    private int visibleVisualRows(int h) {
        return Math.max(1, (h - PADDING * 2) / LINE_HEIGHT);
    }

    // ============================================================
    //  Cursor visibility
    // ============================================================

    public void ensureCursorVisible(int w, int h, EditorDocument doc, Font font) {
        ensureVisualRowStarts(w, doc, font);
        int cl = Math.min(doc.cursorLine(), doc.lineCount() - 1);
        int cursorVisual = visualRowStarts[cl];
        int visibleRows = visibleVisualRows(h);
        if (cursorVisual < scrollLine) scrollLine = cursorVisual;
        else if (cursorVisual >= scrollLine + visibleRows)
            scrollLine = cursorVisual - visibleRows + 1;
        if (scrollLine < 0) scrollLine = 0;
    }

    // ============================================================
    //  Pixel hit
    // ============================================================

    public int[] pixelToPos(double mx, double my, int x, int y, int w, int h,
                            EditorDocument doc, Font font) {
        int relX = (int) mx - x - PADDING - LINE_NUM_WIDTH;
        int relY = (int) my - y - PADDING;
        int clickedVisual = relY / LINE_HEIGHT + scrollLine;
        int maxVisual = Math.max(0, totalVisualRows(w, doc, font) - 1);
        clickedVisual = Mth.clamp(clickedVisual, 0, maxVisual);

        int logLine = findLogicalLineByVisualRow(clickedVisual, w, doc, font);
        LineWrap wrap = getWrapFor(logLine, w, doc, font);
        if (wrap == null) return new int[]{0, 0};

        int physicalStart = visualRowStarts[logLine];
        int physIdx = clickedVisual - physicalStart;
        if (physIdx < 0) physIdx = 0;
        if (physIdx >= wrap.physical.size()) physIdx = wrap.physical.size() - 1;

        String physicalText = wrap.physical.get(physIdx);
        int baseChar = wrap.charStart[physIdx];

        int accW = 0;
        int col = physicalText.length();
        for (int i = 0; i < physicalText.length(); i++) {
            int chW = font.width(String.valueOf(physicalText.charAt(i)));
            if (accW + chW >= relX) { col = i; break; }
            accW += chW;
        }

        int logicalCol = baseChar + col;
        String fullLine = doc.line(logLine);
        logicalCol = Mth.clamp(logicalCol, 0, fullLine.length());
        return new int[]{logLine, logicalCol};
    }

    // ============================================================
    //  Scroll
    // ============================================================

    public boolean mouseScrolled(double deltaY, int w, int h,
                                 EditorDocument doc, Font font) {
        int maxScroll = Math.max(0,
            totalVisualRows(w, doc, font) - visibleVisualRows(h));
        if (deltaY < 0) scrollLine = Math.min(maxScroll, scrollLine + 1);
        else if (deltaY > 0) scrollLine = Math.max(0, scrollLine - 1);
        return true;
    }

    public boolean isScrollable(int w, int h, EditorDocument doc, Font font) {
        return totalVisualRows(w, doc, font) > visibleVisualRows(h);
    }

    public boolean isOverScrollbar(double mx, double my,
                                   int x, int y, int w, int h,
                                   EditorDocument doc, Font font) {
        if (!isScrollable(w, h, doc, font)) return false;
        int sbX = x + w - SCROLLBAR_W - 1;
        return mx >= sbX - 2 && mx < x + w
            && my >= y && my < y + h;
    }

    private int scrollbarTrackH(int h) { return h - 2; }

    private int scrollbarThumbH(int h, int w, EditorDocument doc, Font font) {
        int total = Math.max(1, totalVisualRows(w, doc, font));
        return Math.max(10, scrollbarTrackH(h) * visibleVisualRows(h) / total);
    }

    private int scrollbarThumbY(int y, int h, int w, EditorDocument doc, Font font) {
        int maxScroll = Math.max(0,
            totalVisualRows(w, doc, font) - visibleVisualRows(h));
        if (maxScroll == 0) return y + 1;
        int travel = scrollbarTrackH(h) - scrollbarThumbH(h, w, doc, font);
        return y + 1 + travel * scrollLine / maxScroll;
    }

    public void beginScrollbarDrag(double my) {
        draggingScrollbar = true;
        dragStartY = my;
        dragStartScroll = scrollLine;
    }

    public void dragScrollbar(double my, int w, int h,
                              EditorDocument doc, Font font) {
        int maxScroll = Math.max(0,
            totalVisualRows(w, doc, font) - visibleVisualRows(h));
        int travel = Math.max(1,
            scrollbarTrackH(h) - scrollbarThumbH(h, w, doc, font));
        int delta = (int) Math.round((my - dragStartY) * maxScroll / travel);
        scrollLine = Mth.clamp(dragStartScroll + delta, 0, maxScroll);
    }

    public void endScrollbarDrag() {
        draggingScrollbar = false;
    }

    // ============================================================
    //  Render
    // ============================================================

    public void render(GuiGraphics g, Font font,
                       int x, int y, int w, int h,
                       EditorDocument doc,
                       boolean focused, boolean editable, int accentColor) {

        g.fill(x, y, x + w, y + h, COLOR_BG);

        g.enableScissor(x + 1, y + 1, x + w - 1, y + h - 1);

        int visibleRows = visibleVisualRows(h);
        int textX0 = x + PADDING + LINE_NUM_WIDTH;
        int lineNumX = x + PADDING;

        ensureVisualRowStarts(w, doc, font);
        int totalVisual = cachedTotalVisualRows;

        int visualStart = scrollLine;
        int visualEnd = Math.min(totalVisual, scrollLine + visibleRows);

        int cursorLine = doc.cursorLine();
        int cursorCol  = doc.cursorCol();

        int selStartL = -1, selStartC = -1, selEndL = -1, selEndC = -1;
        int[] sel = doc.orderedSelection();
        if (sel != null) {
            selStartL = sel[0]; selStartC = sel[1];
            selEndL   = sel[2]; selEndC   = sel[3];
        }

        int startLog = findLogicalLineByVisualRow(visualStart, w, doc, font);
        int endLog = (visualEnd > 0)
            ? findLogicalLineByVisualRow(visualEnd - 1, w, doc, font)
            : startLog;
        if (endLog >= doc.lineCount()) endLog = doc.lineCount() - 1;

        int cursorVisualRow = -1;
        int cursorX = textX0;

        for (int li = startLog; li <= endLog; li++) {
            LineWrap wrap = getWrapFor(li, w, doc, font);
            if (wrap == null) continue;

            int physicalStart = visualRowStarts[li];
            List<String> physical = wrap.physical;
            int physCount = physical.size();

            for (int pIdx = 0; pIdx < physCount; pIdx++) {
                int currentVisual = physicalStart + pIdx;
                if (currentVisual < scrollLine) continue;
                if (currentVisual >= scrollLine + visibleRows) break;

                int drawRow = currentVisual - scrollLine;
                int lineY = y + PADDING + drawRow * LINE_HEIGHT;
                if (lineY >= y + h - PADDING) continue;

                String physicalText = physical.get(pIdx);
                int baseChar = wrap.charStart[pIdx];

                if (pIdx == 0) {
                    g.drawString(font, String.valueOf(li + 1),
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
                        g.fill(curX, lineY, curX + chWidth,
                            lineY + LINE_HEIGHT - 1, COLOR_SELECT);
                    }

                    g.drawString(font, ch, curX, lineY, COLOR_TEXT, false);
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

        if (cursorLine >= 0 && cursorLine < doc.lineCount()
            && cursorCol == doc.lineLength(cursorLine)) {
            int physStart = visualRowStarts[cursorLine];
            LineWrap wrap = getWrapFor(cursorLine, w, doc, font);
            if (wrap != null)
                cursorVisualRow = physStart + wrap.physical.size() - 1;
        }

        if (editable && focused && cursorVisualRow >= 0) {
            long now = System.currentTimeMillis();
            if (now - lastBlink > CURSOR_BLINK) {
                cursorVisible = !cursorVisible;
                lastBlink = now;
            }
            if (cursorVisible) {
                int drawRow = cursorVisualRow - scrollLine;
                if (drawRow >= 0 && drawRow < visibleRows) {
                    int cy = y + PADDING + drawRow * LINE_HEIGHT;
                    if (cy >= y && cy < y + h) {
                        g.fill(cursorX, cy + 1, cursorX + 1,
                            cy + LINE_HEIGHT - 1, accentColor);
                    }
                }
            }
        }

        g.disableScissor();

        if (isScrollable(w, h, doc, font)) {
            int sbX = x + w - SCROLLBAR_W - 1;
            int sbY = y + 1;
            int sbH = scrollbarTrackH(h);

            g.fill(sbX, sbY, sbX + SCROLLBAR_W, sbY + sbH, COLOR_SB_TRACK);
            int thumbH = scrollbarThumbH(h, w, doc, font);
            int thumbY = scrollbarThumbY(y, h, w, doc, font);
            g.fill(sbX, thumbY, sbX + SCROLLBAR_W, thumbY + thumbH, accentColor);
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
}