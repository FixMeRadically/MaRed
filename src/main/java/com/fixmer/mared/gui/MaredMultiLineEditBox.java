package com.fixmer.mared.gui;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import com.fixmer.mared.MaredSettings;

import net.minecraft.client.Minecraft;
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

    public String getValue() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) sb.append('\n');
            sb.append(lines.get(i));
        }
        return sb.toString();
    }

    public void setValue(String text) {
        if (text != null) text = text.replace("\r", "");
        // При программной установке значения НЕ создаём undo-снапшот
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
    }

    private void notifyChanged() {
        if (onValueChanged != null) onValueChanged.run();
    }

    // ============================================================
    //  Undo / Redo
    // ============================================================

    /** Снимок ДО изменения. Вызывать перед мутацией lines. */
    private void pushUndo() {
        if (suppressUndoSnapshot) return;
        undoStack.push(new Snapshot(lines, cursorLine, cursorCol));
        if (undoStack.size() > MAX_UNDO) {
            // ArrayDeque не поддерживает removeLast — но у нас push/pop с головы,
            // поэтому чтобы отрезать хвост, снимем весь стек в список.
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
        notifyChanged();
    }

    // ---- Selection (публично) ----

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
            return true;
        }

        lines.get(cursorLine).insert(cursorCol, codePoint);
        cursorCol++;
        notifyChanged();
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!isFocused() || !editable) return false;

        boolean ctrl = (modifiers & 2) != 0;

        if (ctrl) {
            switch (keyCode) {
                case 67: copySelection(); return true;   // C
                case 86: pasteFromClipboard(); return true; // V
                case 88: cutSelection(); return true;    // X
                case 65: selectAll(); return true;       // A
                case 90: undo(); return true;            // Z
                case 89: redo(); return true;            // Y
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
        notifyChanged();
    }

    private void backspace() {
        if (hasSelection()) { pushUndo(); deleteSelection(); notifyChanged(); return; }

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
                    notifyChanged();
                    return;
                }
            }
        }

        if (cursorCol > 0) {
            pushUndo();
            lines.get(cursorLine).deleteCharAt(cursorCol - 1);
            cursorCol--;
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
            notifyChanged();
        }
    }

    private void delete() {
        if (hasSelection()) { pushUndo(); deleteSelection(); notifyChanged(); return; }
        StringBuilder line = lines.get(cursorLine);
        if (cursorCol < line.length()) {
            pushUndo();
            line.deleteCharAt(cursorCol);
            notifyChanged();
        } else if (cursorLine < lines.size() - 1) {
            pushUndo();
            line.append(lines.get(cursorLine + 1));
            lines.remove(cursorLine + 1);
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
        int visibleLines = (height - PADDING * 2) / LINE_HEIGHT;
        if (cursorLine < scrollLine) scrollLine = cursorLine;
        if (cursorLine >= scrollLine + visibleLines) scrollLine = cursorLine - visibleLines + 1;
        if (scrollLine < 0) scrollLine = 0;
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
        notifyChanged();
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

        // Клик по скроллбару — начало drag
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

    private int[] pixelToPos(double mx, double my) {
        int relX = (int) mx - getX() - PADDING - LINE_NUM_WIDTH;
        int relY = (int) my - getY() - PADDING;
        int clickedVisualRow = relY / LINE_HEIGHT + scrollLine;
        clickedVisualRow = Mth.clamp(clickedVisualRow, 0, Math.max(0, lines.size() - 1));
        String lineText = lines.get(clickedVisualRow).toString();
        Minecraft mc = Minecraft.getInstance();
        int col = lineText.length();
        for (int i = 0; i <= lineText.length(); i++) {
            int w = mc.font.width(lineText.substring(0, i));
            if (w >= relX) { col = i; break; }
        }
        col = Mth.clamp(col, 0, lineText.length());
        return new int[]{clickedVisualRow, col};
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double deltaX, double deltaY) {
        if (!this.isMouseOver(mx, my)) return false;
        int visibleLines = (height - PADDING * 2) / LINE_HEIGHT;
        int maxScroll = Math.max(0, lines.size() - visibleLines);
        if (deltaY < 0) scrollLine = Math.min(maxScroll, scrollLine + 1);
        else if (deltaY > 0) scrollLine = Math.max(0, scrollLine - 1);
        return true;
    }

    // ---- Скроллбар ----

    private boolean isScrollable() {
        int visibleLines = (height - PADDING * 2) / LINE_HEIGHT;
        return lines.size() > visibleLines;
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
        int visibleLines = (height - PADDING * 2) / LINE_HEIGHT;
        int totalLines = lines.size();
        return Math.max(10, scrollbarTrackH() * visibleLines / Math.max(1, totalLines));
    }

    private int scrollbarThumbY() {
        int visibleLines = (height - PADDING * 2) / LINE_HEIGHT;
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
        int visibleLines = (height - PADDING * 2) / LINE_HEIGHT;
        int totalLines = lines.size();
        int maxScroll = Math.max(0, totalLines - visibleLines);
        int travel = Math.max(1, scrollbarTrackH() - scrollbarThumbH());
        int delta = (int) Math.round((my - dragStartY) * maxScroll / travel);
        scrollLine = Mth.clamp(dragStartScroll + delta, 0, maxScroll);
    }

    // ---- Render ----

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        Minecraft mc = Minecraft.getInstance();

        graphics.fill(getX(), getY(), getX() + width, getY() + height, COLOR_BG);

        graphics.enableScissor(getX() + 1, getY() + 1, getX() + width - 1, getY() + height - 1);

        int visibleVisualRows = (height - PADDING * 2) / LINE_HEIGHT;
        int availableWidth = width - PADDING * 2 - LINE_NUM_WIDTH - SCROLLBAR_W;
        int textX0 = getX() + PADDING + LINE_NUM_WIDTH;
        int lineNumX = getX() + PADDING;

        int selStartL = -1, selStartC = -1, selEndL = -1, selEndC = -1;
        if (hasSelection()) {
            int[] s = orderSelectionStart();
            int[] e = orderSelectionEnd();
            selStartL = s[0]; selStartC = s[1];
            selEndL = e[0]; selEndC = e[1];
        }

        int visualRow = 0;

        Integer cursorVisualRow = null;
        int cursorX = textX0;
        int cursorBaseVisualRow = 0;

        for (int li = 0; li < lines.size(); li++) {
            String lineText = lines.get(li).toString();
            List<String> physical = MaredUi.wrapLines(mc.font, lineText, availableWidth);
            if (physical.isEmpty()) physical.add("");

            if (visualRow + physical.size() <= scrollLine) {
                visualRow += physical.size();
                continue;
            }

            for (int pIdx = 0; pIdx < physical.size(); pIdx++) {
                int currentVisual = visualRow;
                visualRow++;

                if (currentVisual < scrollLine) continue;
                int drawRow = currentVisual - scrollLine;
                if (drawRow >= visibleVisualRows) continue;

                int lineY = getY() + PADDING + drawRow * LINE_HEIGHT;
                if (lineY >= getY() + height - PADDING) continue;

                String physicalText = physical.get(pIdx);

                if (pIdx == 0) {
                    String num = String.valueOf(li + 1);
                    graphics.drawString(mc.font, num, lineNumX, lineY, COLOR_NUMBERS, false);
                }

                int curX = textX0;
                int physCharStart = 0;
                if (pIdx > 0) {
                    int searchFrom = 0;
                    for (int q = 0; q < pIdx; q++) {
                        int found = lineText.indexOf(physical.get(q), searchFrom);
                        if (found >= 0) searchFrom = found + physical.get(q).length();
                    }
                    while (searchFrom < lineText.length() && lineText.charAt(searchFrom) == ' ') {
                        searchFrom++;
                    }
                    physCharStart = searchFrom;
                }

                for (int c = 0; c < physicalText.length(); c++) {
                    char rawChar = physicalText.charAt(c);
                    String ch = rawChar == '\t' ? "    " : String.valueOf(rawChar);
                    int chWidth = mc.font.width(ch);

                    int logicalCol = physCharStart + c;

                    if (hasSelection() && isCharSelected(li, logicalCol, selStartL, selStartC, selEndL, selEndC)) {
                        graphics.fill(curX, lineY, curX + chWidth, lineY + LINE_HEIGHT - 1, COLOR_SELECT);
                    }

                    graphics.drawString(mc.font, ch, curX, lineY, COLOR_TEXT, false);
                    curX += chWidth;

                    if (cursorLine == li && cursorCol == logicalCol) {
                        cursorVisualRow = currentVisual;
                        cursorX = curX - chWidth;
                        cursorBaseVisualRow = currentVisual;
                    }
                }

                if (cursorLine == li && cursorCol == physCharStart + physicalText.length()) {
                    cursorVisualRow = currentVisual;
                    cursorX = curX;
                    cursorBaseVisualRow = currentVisual;
                }
            }

            if (cursorLine == li && cursorCol == lineText.length()) {
                int lastPhysRow = visualRow - 1;
                cursorVisualRow = lastPhysRow;
            }
        }

        if (editable && isFocused() && cursorVisualRow != null) {
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

            // трек
            graphics.fill(sbX, sbY, sbX + SCROLLBAR_W, sbY + sbH, COLOR_SB_TRACK);

            // ползунок
            int thumbH = scrollbarThumbH();
            int thumbY = scrollbarThumbY();
            graphics.fill(sbX, thumbY, sbX + SCROLLBAR_W, thumbY + thumbH, accentColor);
        }
    }

    private boolean isCharSelected(int line, int col, int sl, int sc, int el, int ec) {
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