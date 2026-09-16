package com.fixmer.mared.gui;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.fixmer.mared.Mared;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.neoforged.fml.loading.FMLPaths;

public class MaredLogPanel {

    private static final int BG         = 0xFF101018;
    private static final int TEXT       = 0xFFFFFFFF;
    private static final int TEXT_DIM   = 0xFFAAAAAA;
    private static final int BTN_BG     = 0xFF2D2D2D;
    private static final int BTN_HOVER  = 0xFF3E3E42;
    private static final int BTN_ACTIVE = 0xFF3A6A3A;
    private static final int ACCENT     = 0xFF55AAFF;
    private static final int SELECT_BG  = 0x804466CC;
    private static final int PANEL_SETTINGS = 0xFF0E0E14;

    public static final int LOG_HEADER = 20;
    public static final int LOG_LINE   = 10;
    public static final int PANEL_H    = 140;

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final int PAD = 4;

    private final List<LogEntry> entries = Collections.synchronizedList(new ArrayList<>());
    private final MaredUi.ScrollArea scroll = new MaredUi.ScrollArea();
    private final MaredUi.DragState drag = new MaredUi.DragState();

    private static final class Pos {
        final int line; final int col;
        Pos(int line, int col) { this.line = line; this.col = col; }
        boolean valid() { return line >= 0 && col >= 0; }
    }

    private Pos selStart = new Pos(-1, -1);
    private Pos selEnd   = new Pos(-1, -1);
    private boolean selecting = false;

    private boolean settingsOpen = false;
    private Runnable onToggle = null;
    private boolean collapsed = false;

    public MaredLogPanel() { MaredLogSettings.load(); }

    public void setOnToggle(Runnable r) { this.onToggle = r; }
    public void setCollapsed(boolean c) {
        this.collapsed = c;
        if (c) settingsOpen = false;
    }

    public void add(String line) {
        entries.add(new LogEntry(LocalTime.now().format(TIME_FMT), line));
        if (entries.size() > 2000) entries.remove(0);
        scroll.offset = 0;
    }

    public void clear() {
        synchronized (entries) { entries.clear(); }
        selStart = new Pos(-1, -1); selEnd = new Pos(-1, -1);
        scroll.offset = 0;
    }

    public boolean isSettingsOpen() { return settingsOpen; }
    public void closeSettings() { settingsOpen = false; }

    public List<LogEntry> snapshot() {
        synchronized (entries) { return new ArrayList<>(entries); }
    }

    public List<LogEntry> getVisibleEntries() {
        List<LogEntry> copy = snapshot();
        List<LogEntry> result = new ArrayList<>();
        for (LogEntry e : copy) if (MaredLogSettings.shouldShow(e.text)) result.add(e);
        return result;
    }

    /** FIX: шапка рисуется в фиксированной точке — не зависит от collapsed. */
    private void drawHeader(GuiGraphics g, Font font, int x, int y, int w,
                            int mouseX, int mouseY) {
        MaredUi.text(g, font, "Log", x + 6, y + 6, ACCENT);

        int btnY = y + 2;
        int btnH = 14;
        // FIX: правый край всегда = x + w - 6 (не зависит от того, растянут лог или нет)
        int right = x + w - 6;

        // Export
        right -= 50;
        boolean exportHover = MaredUi.hovered(mouseX, mouseY, right, btnY, 50, btnH);
        MaredUi.button(g, font, right, btnY, 50, btnH, "Export",
            exportHover ? BTN_HOVER : BTN_BG, ACCENT, exportHover, TEXT);

        // Clear
        right -= 4; right -= 44;
        boolean clearHover = MaredUi.hovered(mouseX, mouseY, right, btnY, 44, btnH);
        MaredUi.button(g, font, right, btnY, 44, btnH, "Clear",
            clearHover ? BTN_HOVER : BTN_BG, 0xFFFF5555, clearHover, TEXT);

        // Copy
        right -= 4; right -= 44;
        boolean copyHover = MaredUi.hovered(mouseX, mouseY, right, btnY, 44, btnH);
        MaredUi.button(g, font, right, btnY, 44, btnH, "Copy",
            copyHover ? BTN_HOVER : BTN_BG, ACCENT, copyHover, TEXT);

        // Gear
        right -= 4; right -= 18;
        boolean gearHover = MaredUi.hovered(mouseX, mouseY, right, btnY, 18, btnH);
        MaredUi.button(g, font, right, btnY, 18, btnH, "⚙",
            gearHover ? BTN_HOVER : (settingsOpen ? BTN_ACTIVE : BTN_BG),
            ACCENT, gearHover, TEXT);

        // Toggle
        right -= 4; right -= 18;
        boolean toggleHover = MaredUi.hovered(mouseX, mouseY, right, btnY, 18, btnH);
        MaredUi.button(g, font, right, btnY, 18, btnH, collapsed ? "▲" : "▼",
            toggleHover ? BTN_HOVER : BTN_BG, ACCENT, toggleHover, TEXT);
    }

    /** FIX: клик в шапке — независимо от collapsed. */
    private boolean headerClick(double mx, double my, int x, int y, int w) {
        if (my < y || my >= y + LOG_HEADER) return false;
        int btnY = y + 2;
        int btnH = 14;
        int right = x + w - 6;

        right -= 50;
        if (MaredUi.hovered(mx, my, right, btnY, 50, btnH)) { exportLog(); return true; }
        right -= 4; right -= 44;
        if (MaredUi.hovered(mx, my, right, btnY, 44, btnH)) { clear(); return true; }
        right -= 4; right -= 44;
        if (MaredUi.hovered(mx, my, right, btnY, 44, btnH)) { copySelectedOrAll(); return true; }
        right -= 4; right -= 18;
        if (MaredUi.hovered(mx, my, right, btnY, 18, btnH)) {
            settingsOpen = !settingsOpen;
            return true;
        }
        right -= 4; right -= 18;
        if (MaredUi.hovered(mx, my, right, btnY, 18, btnH)) {
            if (onToggle != null) onToggle.run();
            return true;
        }
        // клик по пустому месту шапки — не поглощаем
        return false;
    }

    public void render(GuiGraphics g, Font font, int x, int y, int w, int h,
                       int mouseX, int mouseY) {
        MaredUi.rect(g, x, y, x + w, y + h, BG);
        MaredUi.rect(g, x, y, x + w, y + 1, MaredEditorLayout.BORDER_TOP);

        drawHeader(g, font, x, y, w, mouseX, mouseY);

        MaredUi.gradientV(g, x, y + LOG_HEADER - 1, x + w, y + LOG_HEADER,
            MaredEditorLayout.BORDER_TOP, MaredEditorLayout.BORDER_BOTTOM);

        if (collapsed) return;

        if (settingsOpen) {
            renderSettings(g, font, x, y + LOG_HEADER, w, h - LOG_HEADER, mouseX, mouseY);
            return;
        }

        List<LogEntry> visible = getVisibleEntries();
        int innerY = y + LOG_HEADER;
        int innerH = h - LOG_HEADER;
        scroll.set(x, innerY, w, innerH).inverted(true).items(LOG_LINE, visible.size());

        int visibleItems = scroll.visibleItems();
        int end = visible.size() - scroll.offset;
        int start = Math.max(0, end - visibleItems);

        MaredUi.scissorOn(g, x, innerY, x + w, y + h);

        for (int i = start; i < end && i < visible.size(); i++) {
            LogEntry e = visible.get(i);
            int row = i - start;
            int lineY = innerY + row * LOG_LINE;

            String fullLine = "[" + e.time + "] " + e.text;

            if (isLineSelected(i)) {
                int selFrom = (i == selMinLine()) ? selMinCol() : 0;
                int selTo = (i == selMaxLine()) ? selMaxCol() : fullLine.length();
                selFrom = Math.max(0, Math.min(fullLine.length(), selFrom));
                selTo = Math.max(0, Math.min(fullLine.length(), selTo));
                int xFrom = x + PAD + font.width(fullLine.substring(0, selFrom));
                int xTo   = x + PAD + font.width(fullLine.substring(0, selTo));
                if (xTo > xFrom) {
                    MaredUi.rect(g, xFrom, lineY, xTo, lineY + LOG_LINE, SELECT_BG);
                }
            }

            MaredUi.text(g, font, fullLine, x + PAD, lineY, logColor(e.text));
        }

        MaredUi.scissorOff(g);
        scroll.drawScrollbar(g, ACCENT, 6);
    }

    /** FIX: категории wrap'аются по ширине, ничего не выходит за экран. */
    private void renderSettings(GuiGraphics g, Font font, int x, int y, int w, int h,
                                int mouseX, int mouseY) {
        MaredUi.rect(g, x, y, x + w, y + h, PANEL_SETTINGS);
        MaredUi.scissorOn(g, x, y, x + w, y + h);

        int startX = x + 6;
        int startY = y + 6;
        int innerW = w - 12;

        MaredUi.text(g, font, "Log filters", startX, startY, ACCENT);

        // ---- Levels ----
        MaredUi.text(g, font, "Levels:", startX, startY + 16, 0xFFFFAA00);

        int rowH = 14;
        int lvlRowY = startY + 30;
        int lvlItemW = 70;
        int lvlCols = Math.max(1, innerW / (lvlItemW + 4));
        int col = 0, row = 0;

        for (MaredLogSettings.Level lvl : MaredLogSettings.Level.values()) {
            boolean enabled = MaredLogSettings.isLevelEnabled(lvl);
            int cx = startX + col * (lvlItemW + 4);
            int cy = lvlRowY + row * rowH;
            boolean hov = MaredUi.hovered(mouseX, mouseY, cx, cy, lvlItemW, 12);
            int bg = enabled ? BTN_ACTIVE : (hov ? BTN_HOVER : BTN_BG);
            MaredUi.rect(g, cx, cy, cx + lvlItemW, cy + 12, bg);
            MaredUi.outline(g, cx, cy, lvlItemW, 12, enabled ? 0xFF55FF88 : 0xFF4A4A4A);
            MaredUi.text(g, font, (enabled ? "+ " : "  ") + lvl.name(),
                cx + 4, cy + 2, enabled ? 0xFF55FF88 : TEXT_DIM);
            col++;
            if (col >= lvlCols) { col = 0; row++; }
        }
        int lvlRows = (row + 1);
        lvlRowY += lvlRows * rowH + 8;

        // ---- Categories ----
        MaredUi.text(g, font, "Categories:", startX, lvlRowY, 0xFFFFAA00);
        lvlRowY += 14;

        int catItemW = 90;
        int catCols = Math.max(1, innerW / (catItemW + 4));
        col = 0; row = 0;

        for (String cat : MaredLogSettings.CATEGORIES) {
            boolean enabled = MaredLogSettings.isCategoryEnabled(cat);
            int cx = startX + col * (catItemW + 4);
            int cy = lvlRowY + row * rowH;
            boolean hov = MaredUi.hovered(mouseX, mouseY, cx, cy, catItemW, 12);
            int bg = enabled ? BTN_ACTIVE : (hov ? BTN_HOVER : BTN_BG);
            MaredUi.rect(g, cx, cy, cx + catItemW, cy + 12, bg);
            MaredUi.outline(g, cx, cy, catItemW, 12, enabled ? 0xFF55FF88 : 0xFF4A4A4A);
            MaredUi.text(g, font, (enabled ? "+ " : "  ") + "[" + cat + "]",
                cx + 4, cy + 2, enabled ? 0xFF55FF88 : TEXT_DIM);
            col++;
            if (col >= catCols) { col = 0; row++; }
        }

        MaredUi.scissorOff(g);
    }

    private boolean isLineSelected(int lineIdx) {
        if (!selStart.valid() || !selEnd.valid()) return false;
        int lo = selMinLine(), hi = selMaxLine();
        return lineIdx >= lo && lineIdx <= hi;
    }

    private int selMinLine() { return Math.min(selStart.line, selEnd.line); }
    private int selMaxLine() { return Math.max(selStart.line, selEnd.line); }
    private int selMinCol()  {
        return (selStart.line < selEnd.line
            || (selStart.line == selEnd.line && selStart.col <= selEnd.col))
            ? selStart.col : selEnd.col;
    }
    private int selMaxCol()  {
        return (selStart.line < selEnd.line
            || (selStart.line == selEnd.line && selStart.col <= selEnd.col))
            ? selEnd.col : selStart.col;
    }

    private Pos posAt(double mx, double my, Font font, int x, int innerY, int visibleCount) {
        int row = ((int) my - innerY) / LOG_LINE;
        int end = visibleCount - scroll.offset;
        int start = Math.max(0, end - scroll.visibleItems());
        int idx = start + row;
        if (idx < start || idx >= end || idx >= visibleCount) return new Pos(-1, -1);

        List<LogEntry> visible = getVisibleEntries();
        if (idx < 0 || idx >= visible.size()) return new Pos(-1, -1);
        String line = "[" + visible.get(idx).time + "] " + visible.get(idx).text;

        int relX = (int) mx - (x + PAD);
        if (relX < 0) return new Pos(idx, 0);
        int col = line.length();
        for (int i = 0; i <= line.length(); i++) {
            int w = font.width(line.substring(0, i));
            if (w >= relX) { col = i; break; }
        }
        return new Pos(idx, col);
    }

    private int indexAt(double my, int innerY, int visibleCount) {
        int row = ((int) my - innerY) / LOG_LINE;
        int end = visibleCount - scroll.offset;
        int start = Math.max(0, end - scroll.visibleItems());
        int idx = start + row;
        if (idx < start || idx >= end || idx >= visibleCount) return -1;
        return idx;
    }

    public boolean mouseClicked(double mx, double my, int button, int x, int y, int w, int h,
                                Font font) {
        // клик в шапке — фиксированные координаты
        if (my >= y && my < y + LOG_HEADER) {
            return headerClick(mx, my, x, y, w);
        }

        if (collapsed) return false;

        if (settingsOpen) {
            // если клик вне панели лога — не поглощаем
            if (mx < x || mx > x + w || my < y || my > y + h) return false;
            return settingsClick(mx, my, x, y + LOG_HEADER, w, h - LOG_HEADER);
        }

        if (my >= y + LOG_HEADER) {
            scroll.set(x, y + LOG_HEADER, w, h - LOG_HEADER);
            if (scroll.clickScrollbar(mx, my, 6, drag, MaredUi.DragKind.LOG_SCROLL)) return true;

            List<LogEntry> visible = getVisibleEntries();
            int idx = indexAt(my, y + LOG_HEADER, visible.size());
            if (idx >= 0) {
                if (font != null) {
                    Pos p = posAt(mx, my, font, x, y + LOG_HEADER, visible.size());
                    selStart = p; selEnd = p;
                } else {
                    selStart = new Pos(idx, 0); selEnd = new Pos(idx, Integer.MAX_VALUE);
                }
                selecting = true;
                return true;
            }
            selStart = new Pos(-1, -1); selEnd = new Pos(-1, -1);
            return false;
        }
        return false;
    }

    public boolean mouseClicked(double mx, double my, int button, int x, int y, int w, int h) {
        return mouseClicked(mx, my, button, x, y, w, h, null);
    }

    public boolean mouseDragged(double mx, double my, int button, double dx, double dy,
                                int x, int y, int w, int h, Font font) {
        if (drag.active()) { scroll.dragScrollbar(my, drag); return true; }
        if (collapsed) return false;
        if (selecting && my >= y + LOG_HEADER) {
            List<LogEntry> visible = getVisibleEntries();
            if (font != null) {
                Pos p = posAt(mx, my, font, x, y + LOG_HEADER, visible.size());
                if (p.valid()) selEnd = p;
            } else {
                int idx = indexAt(my, y + LOG_HEADER, visible.size());
                if (idx >= 0) selEnd = new Pos(idx, Integer.MAX_VALUE);
            }
            return true;
        }
        return false;
    }

    public boolean mouseDragged(double mx, double my, int button, double dx, double dy,
                                int x, int y, int w, int h) {
        return mouseDragged(mx, my, button, dx, dy, x, y, w, h, null);
    }

    public boolean mouseReleased(double mx, double my, int button) {
        drag.clear(); selecting = false; return false;
    }

    public boolean mouseScrolled(double mx, double my, double deltaY, int x, int y, int w, int h) {
        if (my >= y && my < y + h && mx >= x && mx <= x + w) {
            if (settingsOpen || collapsed) return false;
            scroll.set(x, y + LOG_HEADER, w, h - LOG_HEADER);
            scroll.wheelLog(deltaY, 3);
            return true;
        }
        return false;
    }

    private boolean settingsClick(double mx, double my, int x, int y, int w, int h) {
        if (mx < x || mx > x + w || my < y || my > y + h) return false;

        int startX = x + 6;
        int startY = y + 6;
        int innerW = w - 12;
        int rowH = 14;

        // Levels
        int lvlRowY = startY + 30;
        int lvlItemW = 70;
        int lvlCols = Math.max(1, innerW / (lvlItemW + 4));
        int col = 0, row = 0;

        for (MaredLogSettings.Level lvl : MaredLogSettings.Level.values()) {
            int cx = startX + col * (lvlItemW + 4);
            int cy = lvlRowY + row * rowH;
            if (MaredUi.hovered(mx, my, cx, cy, lvlItemW, 12)) {
                MaredLogSettings.toggleLevel(lvl); return true;
            }
            col++;
            if (col >= lvlCols) { col = 0; row++; }
        }
        int lvlRows = (row + 1);
        lvlRowY += lvlRows * rowH + 8;

        // Categories
        lvlRowY += 14;
        int catItemW = 90;
        int catCols = Math.max(1, innerW / (catItemW + 4));
        col = 0; row = 0;

        for (String cat : MaredLogSettings.CATEGORIES) {
            int cx = startX + col * (catItemW + 4);
            int cy = lvlRowY + row * rowH;
            if (MaredUi.hovered(mx, my, cx, cy, catItemW, 12)) {
                MaredLogSettings.toggleCategory(cat); return true;
            }
            col++;
            if (col >= catCols) { col = 0; row++; }
        }
        return true;
    }

    public void copySelectedOrAll() {
        StringBuilder sb = new StringBuilder();
        List<LogEntry> visible = getVisibleEntries();

        if (selStart.valid() && selEnd.valid()) {
            int loL = selMinLine(), hiL = selMaxLine();
            for (int i = loL; i <= hiL && i < visible.size(); i++) {
                LogEntry e = visible.get(i);
                String full = "[" + e.time + "] " + e.text;
                int from = (i == loL) ? Math.max(0, Math.min(full.length(), selMinCol())) : 0;
                int to   = (i == hiL) ? Math.max(0, Math.min(full.length(), selMaxCol())) : full.length();
                if (from > to) { int t = from; from = to; to = t; }
                sb.append(full, from, to);
                if (i < hiL) sb.append('\n');
            }
        } else {
            for (LogEntry e : visible) {
                sb.append('[').append(e.time).append("] ").append(e.text).append('\n');
            }
        }
        Minecraft.getInstance().keyboardHandler.setClipboard(sb.toString());
        add("[info] log copied to clipboard");
    }

    public void selectAll() {
        List<LogEntry> visible = getVisibleEntries();
        if (visible.isEmpty()) { selStart = new Pos(-1, -1); selEnd = new Pos(-1, -1); }
        else { selStart = new Pos(0, 0); selEnd = new Pos(visible.size() - 1, Integer.MAX_VALUE); }
        add("[info] all lines selected (" + visible.size() + ")");
    }

    public void exportLog() {
        try {
            Path dir = FMLPaths.CONFIGDIR.get().resolve("mared").resolve("logs");
            Files.createDirectories(dir);
            String name = LocalDateTime.now().format(
                DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss")) + ".log";
            Path file = dir.resolve(name);

            StringBuilder sb = new StringBuilder();
            for (LogEntry e : snapshot()) {
                sb.append('[').append(e.time).append("] ").append(e.text).append('\n');
            }
            Files.writeString(file, sb.toString(), StandardCharsets.UTF_8);
            add("[info] log exported: " + file.getFileName());
        } catch (IOException e) {
            Mared.LOGGER.error("[Mared] Failed to export log", e);
            add("[error] export failed: " + e.getMessage());
        }
    }

    private int logColor(String line) {
        if (line.contains("[error]") || line.contains("[ошибка]")) return 0xFFFF5555;
        if (line.contains("[warn]") || line.contains("[предупр]")) return 0xFFFFAA00;
        if (line.contains("[give error]")) return 0xFFFF5555;
        if (line.contains("[give]"))       return 0xFF55FF88;
        if (line.contains("[mc error]"))   return 0xFFFF5555;
        if (line.contains("[mc]"))         return 0xFF88DDFF;
        if (line.contains("[cmd error]"))  return 0xFFFF5555;
        if (line.contains("[cmd]"))        return 0xFF88DDFF;
        if (line.contains("[mared parse]"))return 0xFFFF5555;
        if (line.contains("[log]"))        return 0xFFCCCCFF;
        if (line.contains("[mared]"))      return 0xFF55FF88;
        if (line.contains("[bind fire]"))  return 0xFFAA55FF;
        if (line.contains("[bind]"))       return 0xFFFF55FF;
        if (line.contains("[unblock]"))    return 0xFF55FF55;
        if (line.contains("[block]"))      return 0xFFFFAA00;
        if (line.contains("[toggle]"))     return 0xFF55AAFF;
        if (line.contains("[assert fail]"))return 0xFFFF5555;
        if (line.contains("[assert ok]"))  return 0xFF55FF88;
        if (line.contains("[run]") || line.contains("[запуск]")) return 0xFFFFD700;
        if (line.contains("[info]") || line.contains("[инфо]"))  return 0xFFAAAAAA;
        if (line.contains("[auto-save]") || line.contains("[автосейв]")) return 0xFF88DDFF;
        if (line.contains("[say]"))        return 0xFF88DDFF;
        return TEXT;
    }

    public static class LogEntry {
        public final String time;
        public final String text;
        public LogEntry(String time, String text) { this.time = time; this.text = text; }
    }
}