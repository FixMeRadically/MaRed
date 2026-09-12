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
    private static final int COLOR_CURSOR  = 0xFFFF55FF;
    private static final int COLOR_NUMBERS = 0xFF666680;

    private final List<StringBuilder> lines = new ArrayList<>();
    private int cursorLine = 0;
    private int cursorCol  = 0;
    private int scrollLine = 0;

    private final Runnable onValueChanged;

    private long lastBlink = 0;
    private boolean cursorVisible = true;
    private boolean editable = true;

    public MaredMultiLineEditBox(int x, int y, int width, int height, Runnable onValueChanged) {
        super(x, y, width, height, Component.literal(""));
        this.onValueChanged = onValueChanged;
        lines.add(new StringBuilder());
        this.setFocused(false);
    }

    public void setEditable(boolean editable) {
        this.editable = editable;
        if (!editable) {
            this.setFocused(false);
            this.cursorVisible = false;
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
        for (String p : parts) {
            lines.add(new StringBuilder(p));
        }
        if (lines.isEmpty()) lines.add(new StringBuilder());
        cursorLine = 0;
        cursorCol = 0;
        scrollLine = 0;
    }

    private void notifyChanged() {
        if (onValueChanged != null) onValueChanged.run();
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (!isFocused() || !editable) return false;
        if (codePoint == '\t') return false;
        if (codePoint < 32) return false;

        StringBuilder line = lines.get(cursorLine);
        line.insert(cursorCol, codePoint);
        cursorCol++;
        notifyChanged();
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!isFocused() || !editable) return false;

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

    private void insertNewLine() {
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
        if (cursorCol > 0) cursorCol--;
        else if (cursorLine > 0) {
            cursorLine--;
            cursorCol = lines.get(cursorLine).length();
            ensureCursorVisible();
        }
    }

    private void moveRight() {
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

    @Override
    public void onClick(double mx, double my) {
        if (!editable) return;
        this.setFocused(true);
        int relX = (int) mx - getX() - PADDING - LINE_NUM_WIDTH;
        int relY = (int) my - getY() - PADDING;

        int clickedLine = relY / LINE_HEIGHT + scrollLine;
        clickedLine = Mth.clamp(clickedLine, 0, lines.size() - 1);
        cursorLine = clickedLine;

        String lineText = lines.get(cursorLine).toString();
        Minecraft mc = Minecraft.getInstance();
        int col = lineText.length();
        for (int i = 0; i <= lineText.length(); i++) {
            int w = mc.font.width(lineText.substring(0, i));
            if (w >= relX) {
                col = i;
                break;
            }
        }
        cursorCol = Mth.clamp(col, 0, lineText.length());
        ensureCursorVisible();
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

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        Minecraft mc = Minecraft.getInstance();

        graphics.fill(getX(), getY(), getX() + width, getY() + height, 0xFF141420);

        int visibleLines = (height - PADDING * 2) / LINE_HEIGHT;

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
            graphics.drawString(mc.font, lineText, textX, lineY, COLOR_TEXT, false);
        }

        if (!editable || !isFocused()) return;

        long now = System.currentTimeMillis();
        if (now - lastBlink > CURSOR_BLINK) {
            cursorVisible = !cursorVisible;
            lastBlink = now;
        }
        if (cursorVisible) {
            int visibleCurLine = cursorLine - scrollLine;
            if (visibleCurLine >= 0 && visibleCurLine < visibleLines) {
                String before = lines.get(cursorLine).substring(0, cursorCol);
                int cursorX = getX() + PADDING + LINE_NUM_WIDTH + mc.font.width(before);
                int cursorY = getY() + PADDING + visibleCurLine * LINE_HEIGHT;
                graphics.fill(cursorX, cursorY + 1, cursorX + 1, cursorY + LINE_HEIGHT - 1, COLOR_CURSOR);
            }
        }
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput narration) {
        // Empty.
    }

    @Override
    public boolean isMouseOver(double mx, double my) {
        return mx >= getX() && mx < getX() + width
            && my >= getY() && my < getY() + height;
    }
}