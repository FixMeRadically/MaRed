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

    private static final int BG          = 0xFF101018;
    private static final int TEXT        = MaredUi.TEXT;
    private static final int TEXT_DIM    = MaredUi.TEXT_DIM;
    private static final int BTN_BG      = 0xFF2D2D2D;
    private static final int BTN_HOVER   = 0xFF3E3E42;
    private static final int BTN_ACTIVE  = 0xFF3A6A3A;
    private static final int ACCENT      = 0xFF55AAFF;
    private static final int SELECT_BG   = 0x804466CC;
    private static final int PANEL_SETTINGS = 0xFF0E0E14;
    private static final int SCROLLBAR_W = 6;
    private static final int CHECK_ON    = MaredUi.SUCCESS;
    private static final int CHECK_BRD   = 0xFF4A4A4A;

    public static final int LOG_HEADER = 20;
    public static final int LOG_LINE   = 10;
    public static final int PANEL_H    = 140;

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final int PAD = 4;

    private final List<LogEntry> entries = Collections.synchronizedList(new ArrayList<>());
    private final MaredUi.ScrollArea scroll = new MaredUi.ScrollArea();
    private final MaredUi.PixelScroll filterScroll = new MaredUi.PixelScroll();
    private final MaredUi.DragState drag = new MaredUi.DragState();

    private static final class Pos {
        final int line; final int col;
        Pos(int line, int col) { this.line = line; this.col = col; }
        boolean valid() { return line >= 0 && col >= 0; }
    }

    private Pos selStart = new Pos(-1, -1);
    private Pos selEnd   = new Pos(-1, -1);
    private boolean selecting = false;

    private int autoScrollDir = 0;
    private long lastAutoScrollMs = 0;

    private boolean settingsOpen = false;
    private Runnable onToggle = null;
    private boolean collapsed = false;

    private int cachedX, cachedY, cachedW, cachedH;
    private Font cachedFont;

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

    // ============================================================
    //  Шапка
    // ============================================================

    private void drawHeader(GuiGraphics g, Font font, int x, int y, int w,
                            int mouseX, int mouseY) {
        MaredUi.text(g, font, "Log", x + 6, y + 6, ACCENT);

        int btnY = y + 2;
        int btnH = 14;
        int right = x + w - 6;

        right -= 50;
        boolean exportHover = MaredUi.hovered(mouseX, mouseY, right, btnY, 50, btnH);
        MaredUi.button3D(g, font, right, btnY, 50, btnH, "Export",
            exportHover ? BTN_HOVER : BTN_BG, ACCENT, TEXT, exportHover);

        right -= 4; right -= 44;
        boolean clearHover = MaredUi.hovered(mouseX, mouseY, right, btnY, 44, btnH);
        MaredUi.button3D(g, font, right, btnY, 44, btnH, "Clear",
            clearHover ? BTN_HOVER : BTN_BG, 0xFFFF5555, TEXT, clearHover);

        right -= 4; right -= 44;
        boolean copyHover = MaredUi.hovered(mouseX, mouseY, right, btnY, 44, btnH);
        MaredUi.button3D(g, font, right, btnY, 44, btnH, "Copy",
            copyHover ? BTN_HOVER : BTN_BG, ACCENT, TEXT, copyHover);

        right -= 4; right -= 18;
        boolean gearHover = MaredUi.hovered(mouseX, mouseY, right, btnY, 18, btnH);
        MaredUi.button3D(g, font, right, btnY, 18, btnH, "⚙",
            gearHover ? BTN_HOVER : (settingsOpen ? BTN_ACTIVE : BTN_BG),
            ACCENT, TEXT, gearHover);

        right -= 4; right -= 18;
        boolean toggleHover = MaredUi.hovered(mouseX, mouseY, right, btnY, 18, btnH);
        MaredUi.button3D(g, font, right, btnY, 18, btnH, collapsed ? "▲" : "▼",
            toggleHover ? BTN_HOVER : BTN_BG, ACCENT, TEXT, toggleHover);
    }

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
        return false;
    }

    // ============================================================
    //  Render
    // ============================================================

    public void render(GuiGraphics g, Font font, int x, int y, int w, int h,
                       int mouseX, int mouseY) {
        cachedX = x; cachedY = y; cachedW = w; cachedH = h; cachedFont = font;

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

        scroll.drawScrollbar(g, ACCENT, SCROLLBAR_W);
    }

    // ============================================================
    //  Настройки фильтра
    // ============================================================

    private int settingsContentHeight(Font font, int w) {
        int innerW = w - 12 - SCROLLBAR_W - 2;
        int lvlItemW = 70;
        int lvlCols = Math.max(1, innerW / (lvlItemW + 4));
        int lvlRows = (MaredLogSettings.Level.values().length + lvlCols - 1) / lvlCols;

        int catItemW = 90;
        int catCols = Math.max(1, innerW / (catItemW + 4));
        int catRows = (MaredLogSettings.CATEGORIES.length + catCols - 1) / catCols;

        return 20 + 16 + lvlRows * 14 + 8 + 14 + catRows * 14 + 10;
    }

    private void renderSettings(GuiGraphics g, Font font, int x, int y, int w, int h,
                                int mouseX, int mouseY) {
        MaredUi.rect(g, x, y, x + w, y + h, PANEL_SETTINGS);

        int contentH = settingsContentHeight(font, w);
        filterScroll.set(x, y, w, h).content(contentH);

        MaredUi.scissorOn(g, x, y, x + w, y + h);

        int startX = x + 6;
        int startY = y + 6 - filterScroll.offset;
        int innerW = w - 12 - SCROLLBAR_W - 2;

        MaredUi.text(g, font, "Log filters", startX, startY, ACCENT);
        MaredUi.text(g, font, "Levels:", startX, startY + 16, MaredUi.WARN);

        int lvlRowY = startY + 30;
        int lvlItemW = 70;
        int lvlCols = Math.max(1, innerW / (lvlItemW + 4));
        int col = 0, row = 0;

        for (MaredLogSettings.Level lvl : MaredLogSettings.Level.values()) {
            boolean enabled = MaredLogSettings.isLevelEnabled(lvl);
            int cx = startX + col * (lvlItemW + 4);
            int cy = lvlRowY + row * 14;
            boolean hov = MaredUi.hovered(mouseX, mouseY, cx, cy, lvlItemW, 12);
            int bg = enabled ? BTN_ACTIVE : (hov ? BTN_HOVER : BTN_BG);
            MaredUi.rect(g, cx, cy, cx + lvlItemW, cy + 12, bg);
            MaredUi.outline(g, cx, cy, lvlItemW, 12, enabled ? CHECK_ON : CHECK_BRD);
            MaredUi.drawCheckbox(g, cx + 3, cy + 1, 10, enabled, CHECK_BRD, CHECK_ON);
            MaredUi.text(g, font, lvl.name(), cx + 18, cy + 2,
                enabled ? CHECK_ON : TEXT_DIM);
            col++;
            if (col >= lvlCols) { col = 0; row++; }
        }
        int lvlRows = row + 1;
        lvlRowY += lvlRows * 14 + 8;

        MaredUi.text(g, font, "Categories:", startX, lvlRowY, MaredUi.WARN);
        lvlRowY += 14;

        int catItemW = 90;
        int catCols = Math.max(1, innerW / (catItemW + 4));
        col = 0; row = 0;

        for (String cat : MaredLogSettings.CATEGORIES) {
            boolean enabled = MaredLogSettings.isCategoryEnabled(cat);
            int cx = startX + col * (catItemW + 4);
            int cy = lvlRowY + row * 14;
            boolean hov = MaredUi.hovered(mouseX, mouseY, cx, cy, catItemW, 12);
            int bg = enabled ? BTN_ACTIVE : (hov ? BTN_HOVER : BTN_BG);
            MaredUi.rect(g, cx, cy, cx + catItemW, cy + 12, bg);
            MaredUi.outline(g, cx, cy, catItemW, 12, enabled ? CHECK_ON : CHECK_BRD);
            MaredUi.drawCheckbox(g, cx + 3, cy + 1, 10, enabled, CHECK_BRD, CHECK_ON);
            MaredUi.text(g, font, "[" + cat + "]", cx + 18, cy + 2,
                enabled ? CHECK_ON : TEXT_DIM);
            col++;
            if (col >= catCols) { col = 0; row++; }
        }

        MaredUi.scissorOff(g);
        filterScroll.drawScrollbarGradient(g, ACCENT, ACCENT, SCROLLBAR_W);
    }

    // ============================================================
    //  Selection helpers
    // ============================================================

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

    private Pos posAt(double mx, double my, Font font,
                      int x, int innerY, int w, int innerH, List<LogEntry> visible) {
        if (visible.isEmpty()) return new Pos(-1, -1);

        scroll.set(x, innerY, w, innerH).inverted(true).items(LOG_LINE, visible.size());
        int visibleItems = scroll.visibleItems();
        int end = visible.size() - scroll.offset;
        int start = Math.max(0, end - visibleItems);

        int row = ((int) my - innerY) / LOG_LINE;
        if (row < 0) row = 0;
        if (row >= visibleItems) row = visibleItems - 1;

        int idx = start + row;
        if (idx < 0) idx = 0;
        if (idx >= visible.size()) idx = visible.size() - 1;

        String line = "[" + visible.get(idx).time + "] " + visible.get(idx).text;

        int relX = (int) mx - (x + PAD);
        if (relX < 0) return new Pos(idx, 0);
        int col = line.length();
        for (int i = 0; i <= line.length(); i++) {
            int ww = font.width(line.substring(0, i));
            if (ww >= relX) { col = i; break; }
        }
        return new Pos(idx, col);
    }

    // ============================================================
    //  Клик / drag / scroll
    // ============================================================

    public boolean mouseClicked(double mx, double my, int button, int x, int y, int w, int h,
                                Font font) {
        if (my >= y && my < y + LOG_HEADER) {
            return headerClick(mx, my, x, y, w);
        }

        if (collapsed) return false;

        if (settingsOpen) {
            if (mx < x || mx > x + w || my < y || my > y + h) return false;

            filterScroll.set(x, y + LOG_HEADER, w, h - LOG_HEADER);
            if (filterScroll.clickScrollbar(mx, my, SCROLLBAR_W, drag,
                MaredUi.DragKind.FILTER_SCROLL)) return true;

            return settingsClick(mx, my, x, y + LOG_HEADER, w, h - LOG_HEADER);
        }

        if (my >= y + LOG_HEADER) {
            List<LogEntry> visible = getVisibleEntries();
            int innerY = y + LOG_HEADER;
            int innerH = h - LOG_HEADER;

            scroll.set(x, innerY, w, innerH).inverted(true).items(LOG_LINE, visible.size());
            if (scroll.clickScrollbar(mx, my, SCROLLBAR_W, drag, MaredUi.DragKind.LOG_SCROLL)) return true;

            if (font != null) {
                Pos p = posAt(mx, my, font, x, innerY, w, innerH, visible);
                if (p.valid()) {
                    selStart = p;
                    selEnd = p;
                    selecting = true;
                    return true;
                }
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
        if (drag.active()) {
            switch (drag.kind) {
                case LOG_SCROLL -> scroll.dragScrollbar(my, drag);
                case FILTER_SCROLL -> filterScroll.dragScrollbar(my, drag);
                default -> {}
            }
            return true;
        }
        if (collapsed) return false;
        if (selecting) {
            List<LogEntry> visible = getVisibleEntries();
            if (visible.isEmpty()) return true;

            int innerY = y + LOG_HEADER;
            int innerH = h - LOG_HEADER;

            autoScrollDir = 0;
            if (my < innerY + 2) autoScrollDir = -1;
            else if (my > y + h - 2) autoScrollDir = 1;

            double clampY = Math.max(innerY + 1, Math.min(y + h - 1, my));

            if (font != null) {
                Pos p = posAt(mx, clampY, font, x, innerY, w, innerH, visible);
                if (p.valid()) selEnd = p;
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
        drag.clear();
        selecting = false;
        autoScrollDir = 0;
        return false;
    }

    /**
     * FIX: правильные знаки.
     * inverted(true): offset++ = смотрим ВВЕРХ (старые строки).
     *   - мышь вверху панели (autoScrollDir=-1) → offset++
     *   - мышь внизу панели (autoScrollDir=+1) → offset--
     */
    public void tickAutoScroll() {
        if (!selecting || autoScrollDir == 0) return;
        long now = System.currentTimeMillis();
        if (now - lastAutoScrollMs < 40) return;
        lastAutoScrollMs = now;

        List<LogEntry> visible = getVisibleEntries();
        if (visible.isEmpty()) return;

        int innerH = cachedH - LOG_HEADER;
        scroll.set(cachedX, cachedY + LOG_HEADER, cachedW, innerH)
              .inverted(true).items(LOG_LINE, visible.size());

        if (autoScrollDir < 0) {
            // мышь вверху → к старым → offset++
            scroll.offset = Math.min(scroll.maxScroll(), scroll.offset + 1);
        } else {
            // мышь внизу → к новым → offset--
            scroll.offset = Math.max(0, scroll.offset - 1);
        }
    }

    public boolean mouseScrolled(double mx, double my, double deltaY, int x, int y, int w, int h) {
        if (my >= y && my < y + h && mx >= x && mx <= x + w) {
            if (collapsed) return false;
            if (settingsOpen) {
                filterScroll.set(x, y + LOG_HEADER, w, h - LOG_HEADER);
                filterScroll.wheel(deltaY, 15);
                return true;
            }
            scroll.set(x, y + LOG_HEADER, w, h - LOG_HEADER);
            scroll.wheelLog(deltaY, 3);
            return true;
        }
        return false;
    }

    private boolean settingsClick(double mx, double my, int x, int y, int w, int h) {
        if (mx < x || mx > x + w || my < y || my > y + h) return false;

        int startX = x + 6;
        int startY = y + 6 - filterScroll.offset;
        int innerW = w - 12 - SCROLLBAR_W - 2;

        int lvlRowY = startY + 30;
        int lvlItemW = 70;
        int lvlCols = Math.max(1, innerW / (lvlItemW + 4));
        int col = 0, row = 0;

        for (MaredLogSettings.Level lvl : MaredLogSettings.Level.values()) {
            int cx = startX + col * (lvlItemW + 4);
            int cy = lvlRowY + row * 14;
            if (MaredUi.hovered(mx, my, cx, cy, lvlItemW, 12)) {
                MaredLogSettings.toggleLevel(lvl); return true;
            }
            col++;
            if (col >= lvlCols) { col = 0; row++; }
        }
        int lvlRows = row + 1;
        lvlRowY += lvlRows * 14 + 8;
        lvlRowY += 14;

        int catItemW = 90;
        int catCols = Math.max(1, innerW / (catItemW + 4));
        col = 0; row = 0;

        for (String cat : MaredLogSettings.CATEGORIES) {
            int cx = startX + col * (catItemW + 4);
            int cy = lvlRowY + row * 14;
            if (MaredUi.hovered(mx, my, cx, cy, catItemW, 12)) {
                MaredLogSettings.toggleCategory(cat); return true;
            }
            col++;
            if (col >= catCols) { col = 0; row++; }
        }
        return true;
    }

    // ============================================================
    //  Копирование / экспорт
    // ============================================================

    /**
     * FIX: hasRealSelection — true только если выделено НЕСКОЛЬКО символов
     * и в пределах visible. Иначе — копируем весь лог.
     */
    public void copySelectedOrAll() {
        StringBuilder sb = new StringBuilder();
        List<LogEntry> visible = getVisibleEntries();

        boolean hasRealSelection = selStart.valid() && selEnd.valid()
            && (selStart.line != selEnd.line || selStart.col != selEnd.col)
            && selMinLine() < visible.size()
            && selMaxLine() < visible.size();

        if (hasRealSelection) {
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
            // Нет выделения → весь видимый лог
            selStart = new Pos(-1, -1);
            selEnd = new Pos(-1, -1);
            for (LogEntry e : visible) {
                sb.append('[').append(e.time).append("] ").append(e.text).append('\n');
            }
        }

        String toCopy = sb.toString();
        try {
            Minecraft.getInstance().keyboardHandler.setClipboard(toCopy);
            add("[info] copied " + toCopy.length() + " chars (visible=" + visible.size()
                + ", sel=" + (hasRealSelection ? 1 : 0) + ")");
        } catch (Exception ex) {
            add("[error] copy failed: " + ex.getMessage());
        }
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