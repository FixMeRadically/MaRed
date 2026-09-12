package com.fixmer.mared.gui;

import java.util.ArrayList;
import java.util.List;

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

    private static final int COLOR_TEXT    = 0xFFDDDDDD;
    private static final int COLOR_NUMBERS = 0xFF666680;
    private static final int COLOR_SELECT  = 0x804466CC;

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
        lines.clear();
        String[] parts = text.split("\n", -1);
        for (String p : parts) lines.add(new StringBuilder(p));
        if (lines.isEmpty()) lines.add(new StringBuilder());
        cursorLine = 0; cursorCol = 0; scrollLine = 0;
        clearSelection();
    }

    private void notifyChanged() {
        if (onValueChanged != null) onValueChanged.run();
    }

    // ---- Selection ----

    private boolean hasSelection() {
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
        // Берём "хвост" последней строки ДО модификации.
        String tail = lines.get(el).substring(ec);

        // Удаляем у первой строки всё после sc и приклеиваем хвост.
        first.delete(sc, first.length());
        first.append(tail);

        // Удаляем строки между sl и el (включительно el).
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
        if (codePoint == '\t') return false;
        if (codePoint < 32) return false;
        if (hasSelection()) deleteSelection();
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
                case 67: copySelection(); return true;
                case 86: pasteFromClipboard(); return true;
                case 88: cutSelection(); return true;
                case 65: selectAll(); return true;
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
        Minecraft.getInstance().keyboardHandler.setClipboard(getSelectedText());
        deleteSelection();
        notifyChanged();
    }

    private void pasteFromClipboard() {
        String clip = Minecraft.getInstance().keyboardHandler.getClipboard();
        if (clip == null || clip.isEmpty()) return;
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
        if (hasSelection()) deleteSelection();
        StringBuilder line = lines.get(cursorLine);
        String rest = line.substring(cursorCol);
        line.delete(cursorCol, line.length());
        lines.add(cursorLine + 1, new StringBuilder(rest));
        cursorLine++;
        cursorCol = 0;
        ensureCursorVisible();
        notifyChanged();
    }

    private void backspace() {
        if (hasSelection()) { deleteSelection(); notifyChanged(); return; }
        if (cursorCol > 0) {
            lines.get(cursorLine).deleteCharAt(cursorCol - 1);
            cursorCol--;
            notifyChanged();
        } else if (cursorLine > 0) {
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
        if (hasSelection()) { deleteSelection(); notifyChanged(); return; }
        StringBuilder line = lines.get(cursorLine);
        if (cursorCol < line.length()) {
            line.deleteCharAt(cursorCol);
            notifyChanged();
        } else if (cursorLine < lines.size() - 1) {
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

    // ---- Mouse ----

    @Override
    public void onClick(double mx, double my) {
        if (!editable) return;
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
        return super.mouseReleased(mx, my, button);
    }

    private int[] pixelToPos(double mx, double my) {
        int relX = (int) mx - getX() - PADDING - LINE_NUM_WIDTH;
        int relY = (int) my - getY() - PADDING;
        int clickedLine = relY / LINE_HEIGHT + scrollLine;
        clickedLine = Mth.clamp(clickedLine, 0, lines.size() - 1);
        String lineText = lines.get(clickedLine).toString();
        Minecraft mc = Minecraft.getInstance();
        int col = lineText.length();
        for (int i = 0; i <= lineText.length(); i++) {
            int w = mc.font.width(lineText.substring(0, i));
            if (w >= relX) { col = i; break; }
        }
        col = Mth.clamp(col, 0, lineText.length());
        return new int[]{clickedLine, col};
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

    // ---- Render ----

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        Minecraft mc = Minecraft.getInstance();

        graphics.fill(getX(), getY(), getX() + width, getY() + height, 0xFF141420);

        graphics.enableScissor(getX() + 1, getY() + 1, getX() + width - 1, getY() + height - 1);

        int visibleLines = (height - PADDING * 2) / LINE_HEIGHT;
        int availableWidth = width - PADDING * 2 - LINE_NUM_WIDTH;

        int selStartL = -1, selStartC = -1, selEndL = -1, selEndC = -1;
        if (hasSelection()) {
            int[] s = orderSelectionStart();
            int[] e = orderSelectionEnd();
            selStartL = s[0]; selStartC = s[1];
            selEndL = e[0]; selEndC = e[1];
        }

        for (int i = 0; i < visibleLines; i++) {
            int lineIdx = i + scrollLine;
            if (lineIdx >= lines.size()) break;
            String lineText = lines.get(lineIdx).toString();
            int lineY = getY() + PADDING + i * LINE_HEIGHT;

            if (!lineText.isEmpty()) {
                String num = String.valueOf(lineIdx + 1);
                graphics.drawString(mc.font, num, getX() + PADDING, lineY, COLOR_NUMBERS, false);
            }

            int textX = getX() + PADDING + LINE_NUM_WIDTH;
            int curX = textX;
            int curY = lineY;

            for (int c = 0; c < lineText.length(); c++) {
                String ch = String.valueOf(lineText.charAt(c));
                int chWidth = mc.font.width(ch);

                if (curX + chWidth - textX > availableWidth) {
                    curX = textX;
                    curY += LINE_HEIGHT;
                }

                if (hasSelection() && isCharSelected(lineIdx, c, selStartL, selStartC, selEndL, selEndC)) {
                    graphics.fill(curX, curY, curX + chWidth, curY + LINE_HEIGHT - 1, COLOR_SELECT);
                }

                graphics.drawString(mc.font, ch, curX, curY, COLOR_TEXT, false);
                curX += chWidth;
            }
        }

        if (editable && isFocused()) {
            long now = System.currentTimeMillis();
            if (now - lastBlink > CURSOR_BLINK) {
                cursorVisible = !cursorVisible;
                lastBlink = now;
            }
            if (cursorVisible) {
                int[] cursorPix = getCursorPixelPos(availableWidth);
                if (cursorPix != null) {
                    int cx = cursorPix[0];
                    int cy = cursorPix[1];
                    if (cy >= getY() && cy < getY() + height) {
                        graphics.fill(cx, cy + 1, cx + 1, cy + LINE_HEIGHT - 1, accentColor);
                    }
                }
            }
        }

        graphics.disableScissor();
    }

    private boolean isCharSelected(int line, int col, int sl, int sc, int el, int ec) {
        if (line < sl || line > el) return false;
        if (line == sl && line == el) return col >= sc && col < ec;
        if (line == sl) return col >= sc;
        if (line == el) return col < ec;
        return true;
    }

    private int[] getCursorPixelPos(int availableWidth) {
        Minecraft mc = Minecraft.getInstance();
        int visibleLines = (height - PADDING * 2) / LINE_HEIGHT;
        if (cursorLine < scrollLine || cursorLine >= scrollLine + visibleLines) {
            return null;
        }
        int visualRow = cursorLine - scrollLine;
        String lineText = lines.get(cursorLine).toString();
        String before = lineText.substring(0, cursorCol);

        int textX = getX() + PADDING + LINE_NUM_WIDTH;
        int curX = textX;
        int curY = getY() + PADDING + visualRow * LINE_HEIGHT;

        for (int c = 0; c < before.length(); c++) {
            String ch = String.valueOf(before.charAt(c));
            int chWidth = mc.font.width(ch);
            if (curX + chWidth - textX > availableWidth) {
                curX = textX;
                curY += LINE_HEIGHT;
            }
            curX += chWidth;
        }
        return new int[]{curX, curY};
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput narration) { /* Empty. */ }

    @Override
    public boolean isMouseOver(double mx, double my) {
        return mx >= getX() && mx < getX() + width && my >= getY() && my < getY() + height;
    }
}