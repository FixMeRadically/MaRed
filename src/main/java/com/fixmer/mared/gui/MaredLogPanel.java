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

    private static final int BG          = 0xFF1A1A24;
    private static final int TEXT        = 0xFFFFFFFF;
    private static final int TEXT_DIM    = 0xFFAAAAAA;
    private static final int BTN_BG      = 0xFF2D2D2D;
    private static final int BTN_HOVER   = 0xFF3E3E42;
    private static final int BTN_ACTIVE  = 0xFF3A6A3A;
    private static final int ACCENT      = 0xFF55AAFF;
    private static final int SELECT_BG   = 0x804466CC;

    public static final int LOG_HEADER = 20;
    public static final int LOG_LINE   = 10;
    public static final int PANEL_H    = 140;

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final List<LogEntry> entries = Collections.synchronizedList(new ArrayList<>());

    private final MaredUi.ScrollArea scroll = new MaredUi.ScrollArea();
    private final MaredUi.DragState drag = new MaredUi.DragState();

    private int selStart = -1;
    private int selEnd   = -1;
    private boolean selecting = false;

    private boolean settingsOpen = false;

    /** Колбэк для сворачивания лога. Вызывается, когда пользователь кликает по ▼/▲. */
    private Runnable onToggle = null;
    private boolean collapsed = false;

    public MaredLogPanel() {
        MaredLogSettings.load();
    }

    public void setOnToggle(Runnable r) { this.onToggle = r; }
    public void setCollapsed(boolean c) { this.collapsed = c; }

    // ============================================================
    //  Public API
    // ============================================================

    public void add(String line) {
        entries.add(new LogEntry(LocalTime.now().format(TIME_FMT), line));
        if (entries.size() > 2000) entries.remove(0);
        scroll.offset = 0;
    }

    public void clear() {
        synchronized (entries) { entries.clear(); }
        selStart = -1; selEnd = -1;
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
        for (LogEntry e : copy) {
            if (MaredLogSettings.shouldShow(e.text)) result.add(e);
        }
        return result;
    }

    // ============================================================
    //  Render
    // ============================================================

    public void render(GuiGraphics g, Font font, int x, int y, int w, int h, int mouseX, int mouseY) {
        MaredUi.rect(g, x, y, x + w, y + h, BG);

        MaredUi.text(g, font, "Log", x + 6, y + 6, ACCENT);

        int btnY = y + 2;
        int btnH = 14;
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

        // Шестерёнка
        right -= 4; right -= 18;
        boolean gearHover = MaredUi.hovered(mouseX, mouseY, right, btnY, 18, btnH);
        MaredUi.button(g, font, right, btnY, 18, btnH, "⚙",
            gearHover ? BTN_HOVER : (settingsOpen ? BTN_ACTIVE : BTN_BG),
            ACCENT, gearHover, TEXT);

        // Toggle ▼/▲
        right -= 4; right -= 18;
        boolean toggleHover = MaredUi.hovered(mouseX, mouseY, right, btnY, 18, btnH);
        MaredUi.button(g, font, right, btnY, 18, btnH, collapsed ? "▲" : "▼",
            toggleHover ? BTN_HOVER : BTN_BG, ACCENT, toggleHover, TEXT);

        MaredUi.rect(g, x, y + LOG_HEADER - 1, x + w, y + LOG_HEADER, 0xFF333344);

        if (collapsed) return;

        if (settingsOpen) {
            renderSettings(g, font, x, y + LOG_HEADER, w, h - LOG_HEADER, mouseX, mouseY);
            return;
        }

        List<LogEntry> visible = getVisibleEntries();
        int innerX = x;
        int innerY = y + LOG_HEADER;
        int innerH = h - LOG_HEADER;
        scroll.set(innerX, innerY, w, innerH).inverted(true).items(LOG_LINE, visible.size());

        int visibleItems = scroll.visibleItems();
        int end = visible.size() - scroll.offset;
        int start = Math.max(0, end - visibleItems);

        MaredUi.scissorOn(g, x, innerY, x + w, y + h);

        for (int i = start; i < end && i < visible.size(); i++) {
            LogEntry e = visible.get(i);
            int row = i - start;
            int lineY = innerY + row * LOG_LINE;

            if (isLineSelected(i)) {
                MaredUi.rect(g, innerX, lineY, innerX + w - 4, lineY + LOG_LINE, SELECT_BG);
            }

            String fullLine = "[" + e.time + "] " + e.text;
            MaredUi.text(g, font, fullLine, innerX + 4, lineY, logColor(e.text));
        }

        MaredUi.scissorOff(g);
        scroll.drawScrollbar(g, ACCENT, 6);
    }

    private void renderSettings(GuiGraphics g, Font font, int x, int y, int w, int h, int mouseX, int mouseY) {
        MaredUi.rect(g, x, y, x + w, y + h, 0xFF0E0E14);

        // Компактнее: 2 колонки уровней, 3 колонки категорий
        int startX = x + 6;
        int startY = y + 6;

        MaredUi.text(g, font, "Log filters", startX, startY, ACCENT);
        MaredUi.text(g, font, "Levels:", startX, startY + 16, 0xFFFFAA00);

        int rowH = 14;
        int lvlRowY = startY + 30;
        int col = startX;
        int row = 0;
        int perRow = Math.max(1, (w - 12) / 74);

        for (MaredLogSettings.Level lvl : MaredLogSettings.Level.values()) {
            boolean enabled = MaredLogSettings.isLevelEnabled(lvl);
            int itemW = 70;
            boolean hov = MaredUi.hovered(mouseX, mouseY, col, lvlRowY, itemW, 12);
            int bg = enabled ? BTN_ACTIVE : (hov ? BTN_HOVER : BTN_BG);
            MaredUi.rect(g, col, lvlRowY, col + itemW, lvlRowY + 12, bg);
            MaredUi.outline(g, col, lvlRowY, itemW, 12, enabled ? 0xFF55FF88 : 0xFF4A4A4A);
            MaredUi.text(g, font, (enabled ? "+ " : "  ") + lvl.name(), col + 4, lvlRowY + 2,
                enabled ? 0xFF55FF88 : TEXT_DIM);
            col += itemW + 4;
            row++;
            if (row >= perRow) { row = 0; col = startX; lvlRowY += rowH; }
        }
        if (row > 0) lvlRowY += rowH;

        int catStartY = lvlRowY + 4;
        MaredUi.text(g, font, "Categories:", startX, catStartY, 0xFFFFAA00);

        int catRowY = catStartY + 14;
        col = startX; row = 0;
        int catPerRow = Math.max(1, (w - 12) / 94);

        for (String cat : MaredLogSettings.CATEGORIES) {
            boolean enabled = MaredLogSettings.isCategoryEnabled(cat);
            int itemW = 90;
            boolean hov = MaredUi.hovered(mouseX, mouseY, col, catRowY, itemW, 12);
            int bg = enabled ? BTN_ACTIVE : (hov ? BTN_HOVER : BTN_BG);
            MaredUi.rect(g, col, catRowY, col + itemW, catRowY + 12, bg);
            MaredUi.outline(g, col, catRowY, itemW, 12, enabled ? 0xFF55FF88 : 0xFF4A4A4A);
            MaredUi.text(g, font, (enabled ? "+ " : "  ") + "[" + cat + "]", col + 4, catRowY + 2,
                enabled ? 0xFF55FF88 : TEXT_DIM);
            col += itemW + 4;
            row++;
            if (row >= catPerRow) { row = 0; col = startX; catRowY += rowH; }
        }
    }

    // ============================================================
    //  Клик / drag / scroll
    // ============================================================

    private int indexAt(double my, int innerY, int visibleCount) {
        int row = ((int) my - innerY) / LOG_LINE;
        int end = visibleCount - scroll.offset;
        int start = Math.max(0, end - scroll.visibleItems());
        int idx = start + row;
        if (idx < start || idx >= end || idx >= visibleCount) return -1;
        return idx;
    }

    /** Обработка клика. @return true — клик поглощён. */
    public boolean mouseClicked(double mx, double my, int button, int x, int y, int w, int h) {
        // Кнопки в шапке
        if (my >= y && my < y + LOG_HEADER) {
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
            if (MaredUi.hovered(mx, my, right, btnY, 18, btnH)) { settingsOpen = !settingsOpen; return true; }
            right -= 4; right -= 18;
            if (MaredUi.hovered(mx, my, right, btnY, 18, btnH)) {
                if (onToggle != null) onToggle.run();
                return true;
            }
            return true;
        }

        if (collapsed) return false;

        if (settingsOpen) {
            return settingsClick(mx, my, x, y + LOG_HEADER, w, h - LOG_HEADER);
        }

        if (my >= y + LOG_HEADER) {
            scroll.set(x, y + LOG_HEADER, w, h - LOG_HEADER);
            if (scroll.clickScrollbar(mx, my, 6, drag, MaredUi.DragKind.LOG_SCROLL)) return true;

            List<LogEntry> visible = getVisibleEntries();
            int idx = indexAt(my, y + LOG_HEADER, visible.size());
            if (idx >= 0) {
                selStart = idx; selEnd = idx; selecting = true;
                return true;
            }
            // Клик по пустой области лога — не поглощаем, но выделение сбрасываем
            selStart = -1; selEnd = -1;
            return false;
        }
        return false;
    }

    public boolean mouseDragged(double mx, double my, int button, double dx, double dy, int x, int y, int w, int h) {
        if (drag.active()) { scroll.dragScrollbar(my, drag); return true; }
        if (collapsed) return false;
        if (selecting && my >= y + LOG_HEADER) {
            List<LogEntry> visible = getVisibleEntries();
            int idx = indexAt(my, y + LOG_HEADER, visible.size());
            if (idx >= 0) selEnd = idx;
            return true;
        }
        return false;
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
        int startX = x + 6;
        int startY = y + 6;
        int rowH = 14;
        int lvlRowY = startY + 30;
        int col = startX;
        int row = 0;
        int perRow = Math.max(1, (w - 12) / 74);

        for (MaredLogSettings.Level lvl : MaredLogSettings.Level.values()) {
            int itemW = 70;
            if (MaredUi.hovered(mx, my, col, lvlRowY, itemW, 12)) {
                MaredLogSettings.toggleLevel(lvl); return true;
            }
            col += itemW + 4; row++;
            if (row >= perRow) { row = 0; col = startX; lvlRowY += rowH; }
        }
        if (row > 0) lvlRowY += rowH;

        int catRowY = lvlRowY + 4 + 14;
        col = startX; row = 0;
        int catPerRow = Math.max(1, (w - 12) / 94);

        for (String cat : MaredLogSettings.CATEGORIES) {
            int itemW = 90;
            if (MaredUi.hovered(mx, my, col, catRowY, itemW, 12)) {
                MaredLogSettings.toggleCategory(cat); return true;
            }
            col += itemW + 4; row++;
            if (row >= catPerRow) { row = 0; col = startX; catRowY += rowH; }
        }
        return true;
    }

    // ============================================================
    //  Копирование / выделение / экспорт
    // ============================================================

    private boolean isLineSelected(int idx) {
        if (selStart < 0 || selEnd < 0) return false;
        int lo = Math.min(selStart, selEnd), hi = Math.max(selStart, selEnd);
        return idx >= lo && idx <= hi;
    }

    public void copySelectedOrAll() {
        StringBuilder sb = new StringBuilder();
        List<LogEntry> visible = getVisibleEntries();

        if (selStart >= 0 && selEnd >= 0) {
            int lo = Math.min(selStart, selEnd), hi = Math.max(selStart, selEnd);
            for (int i = lo; i <= hi && i < visible.size(); i++) {
                LogEntry e = visible.get(i);
                sb.append('[').append(e.time).append("] ").append(e.text).append('\n');
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
        if (visible.isEmpty()) { selStart = -1; selEnd = -1; }
        else { selStart = 0; selEnd = visible.size() - 1; }
        add("[info] all lines selected (" + visible.size() + ")");
    }

    public void exportLog() {
        try {
            Path dir = FMLPaths.CONFIGDIR.get().resolve("mared").resolve("logs");
            Files.createDirectories(dir);
            String name = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss")) + ".log";
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
        if (line.contains("[cmd]"))        return 0xFF88DDFF;
        if (line.contains("[cmd error]"))  return 0xFFFF5555;
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
        public LogEntry(String time, String text) {
            this.time = time;
            this.text = text;
        }
    }
}