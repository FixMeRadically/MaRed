package com.fixmer.mared.gui2.studio.panels.explorer;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.fixmer.mared.Mared;
import com.fixmer.mared.MaredLang;
import com.fixmer.mared.commands.registry.MaredCommandRegistry;
import com.fixmer.mared.commands.storage.MaredCommandStorage;
import com.fixmer.mared.commands.storage.MaredPersistentStorage;
import com.fixmer.mared.gui2.framework.components.MaredPanel;
import com.fixmer.mared.gui2.framework.core.MaredRenderContext;
import com.fixmer.mared.gui2.framework.core.NarratableComponent;
import com.fixmer.mared.gui2.framework.theme.ThemeColors;
import com.fixmer.mared.gui2.studio.events.StudioEventBus;
import com.fixmer.mared.gui2.studio.events.StudioEvents;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * Explorer панель MaRed Studio.
 *
 * 0.3.2:
 *   - FileEntry { name, persistent } как view model. Раньше render
 *     на каждой строке звал MaredPersistentStorage.isPersistent(name).
 *     Теперь флаг вычисляется при reload() один раз.
 *   - Правый клик публикует RequestContextMenuForFileEvent.
 *     Hardcoded строки меню ("Open", "Rename...") удалены —
 *     меню строится Screen'ом из EditorActions.
 *   - Реализует NarratableComponent — FocusTraversal озвучивает
 *     число файлов и команд, выбранный файл.
 */
public final class ExplorerComponent extends MaredPanel
        implements NarratableComponent {

    private static final int ROW_H = 14;
    private static final int PAD = 4;
    private static final int HEADER_H = 14;
    private static final int SPLIT_GAP = 8;
    private static final int PLUS_SIZE = 12;

    /** 0.3.2: view model — immutable entry. */
    public record FileEntry(String name, boolean persistent) {}

    private final StudioEventBus bus;

    private final List<FileEntry> files = new ArrayList<>(16);
    private final List<MaredCommandRegistry.CommandInfo> commands =
        new ArrayList<>(80);

    private int hoveredFileIndex = -1;
    private String selectedFileName = null;
    private int filesScroll = 0;
    private boolean plusHover = false;

    private int hoveredCommandIndex = -1;
    private int selectedCommandIndex = -1;
    private int commandsScroll = 0;

    public ExplorerComponent(StudioEventBus bus) {
        this.bus = bus;
        reload();
    }

    @Override
    public boolean focusable() { return true; }

    public void reload() {
        files.clear();
        commands.clear();

        // 0.3.2: один snapshot persistent-файлов — потом lookup в Set.
        Set<String> persistentNames;
        try {
            persistentNames = new HashSet<>(MaredPersistentStorage.load());
        } catch (Throwable t) {
            persistentNames = new HashSet<>();
            Mared.LOGGER.warn(
                "[studio] failed to load persistent list", t);
        }

        try {
            for (String name : MaredCommandStorage.listCommands()) {
                files.add(new FileEntry(name, persistentNames.contains(name)));
            }
        } catch (Throwable t) {
            Mared.LOGGER.warn(
                "[studio] failed to list commands", t);
        }

        try {
            commands.addAll(MaredCommandRegistry.all());
        } catch (Throwable t) {
            Mared.LOGGER.warn(
                "[studio] failed to load registry", t);
        }

        // Если выбранного файла больше нет — сбрасываем selection.
        if (selectedFileName != null && !containsFile(selectedFileName)) {
            selectedFileName = null;
        }
        if (selectedCommandIndex >= commands.size()) selectedCommandIndex = -1;
        clampScroll();
    }

    private boolean containsFile(String name) {
        for (FileEntry e : files) {
            if (e.name().equals(name)) return true;
        }
        return false;
    }

    public List<FileEntry> files() { return List.copyOf(files); }

    public List<MaredCommandRegistry.CommandInfo> commands() {
        return List.copyOf(commands);
    }

    public String selectedFile() { return selectedFileName; }

    public void setSelectedFile(String name) { this.selectedFileName = name; }

    private int filesListY() { return bounds.y() + HEADER_H; }
    private int filesListH() {
        int half = (bounds.height() - SPLIT_GAP) / 2;
        return Math.max(20, half - HEADER_H);
    }
    private int commandsHeaderY() {
        return filesListY() + filesListH() + SPLIT_GAP;
    }
    private int commandsListY()   { return commandsHeaderY() + HEADER_H; }
    private int commandsListH()   {
        return Math.max(20, bounds.bottom() - commandsListY());
    }
    private int filesVisibleRows()    { return Math.max(1, filesListH() / ROW_H); }
    private int commandsVisibleRows() { return Math.max(1, commandsListH() / ROW_H); }
    private int plusX() { return bounds.right() - PLUS_SIZE - PAD; }
    private int plusY() { return bounds.y() + 1; }

    @Override
    protected void safeRender(MaredRenderContext ctx) {
        super.safeRender(ctx);
        GuiGraphics g = ctx.graphics();
        Font font = ctx.font();

        g.drawString(font, "Files", bounds.x() + PAD, bounds.y() + 3,
            ThemeColors.muted(), false);

        int px = plusX();
        int py = plusY();
        int bg = plusHover ? ThemeColors.hover() : 0xFF252530;
        int border = plusHover ? ThemeColors.accent() : 0xFF3A3A4A;
        g.fill(px, py, px + PLUS_SIZE, py + PLUS_SIZE, bg);
        g.renderOutline(px, py, PLUS_SIZE, PLUS_SIZE, border);
        g.drawString(font, "+",
            px + PLUS_SIZE / 2 - font.width("+") / 2,
            py + PLUS_SIZE / 2 - 4,
            ThemeColors.accent(), false);

        if (files.isEmpty()) {
            g.drawString(font, "(empty)",
                bounds.x() + PAD, filesListY() + 2, ThemeColors.muted(), false);
        } else {
            int visible = filesVisibleRows();
            int end = Math.min(files.size(), filesScroll + visible);
            for (int i = filesScroll; i < end; i++) {
                int y = filesListY() + (i - filesScroll) * ROW_H;
                drawFileRow(g, font, i, y);
            }
        }

        int sepY = commandsHeaderY() - SPLIT_GAP / 2;
        g.fill(bounds.x(), sepY, bounds.right(), sepY + 1, 0x33FFFFFF);

        g.drawString(font, "Commands (" + commands.size() + ")",
            bounds.x() + PAD, commandsHeaderY() + 3,
            ThemeColors.muted(), false);

        if (commands.isEmpty()) {
            g.drawString(font, "(none loaded)",
                bounds.x() + PAD, commandsListY() + 2,
                ThemeColors.muted(), false);
            return;
        }
        int cVisible = commandsVisibleRows();
        int cEnd = Math.min(commands.size(), commandsScroll + cVisible);
        for (int i = commandsScroll; i < cEnd; i++) {
            int y = commandsListY() + (i - commandsScroll) * ROW_H;
            drawCommandRow(g, font, i, y);
        }
    }

    private void drawFileRow(GuiGraphics g, Font font, int i, int y) {
        FileEntry entry = files.get(i);
        boolean selected = entry.name().equals(selectedFileName);
        boolean hovered = (i == hoveredFileIndex);

        if (selected) {
            g.fill(bounds.x() + 1, y, bounds.right() - 1, y + ROW_H - 1,
                ThemeColors.hover());
        } else if (hovered) {
            g.fill(bounds.x() + 1, y, bounds.right() - 1, y + ROW_H - 1,
                0x33FFFFFF);
        }

        // 0.3.2: persistent — из модели, не из Storage.
        if (entry.persistent()) {
            g.fill(bounds.x() + 1, y, bounds.x() + 3, y + ROW_H - 1, 0xFF55FF88);
        }

        int color = selected ? ThemeColors.accent() : ThemeColors.text();
        g.drawString(font, entry.name(),
            bounds.x() + PAD + 4, y + 3, color, false);
    }

    private void drawCommandRow(GuiGraphics g, Font font, int i, int y) {
        boolean selected = (i == selectedCommandIndex);
        boolean hovered = (i == hoveredCommandIndex);
        if (selected) {
            g.fill(bounds.x() + 1, y, bounds.right() - 1, y + ROW_H - 1,
                ThemeColors.hover());
        } else if (hovered) {
            g.fill(bounds.x() + 1, y, bounds.right() - 1, y + ROW_H - 1,
                0x33FFFFFF);
        }
        MaredCommandRegistry.CommandInfo info = commands.get(i);
        int color = selected ? ThemeColors.accent() : ThemeColors.text();
        int maxW = bounds.width() - PAD * 2 - 6;
        g.drawString(font, ellipsize(font, info.name, maxW),
            bounds.x() + PAD, y + 3, color, false);
    }

    private static String ellipsize(Font font, String s, int maxW) {
        if (font.width(s) <= maxW) return s;
        int ell = font.width("...");
        if (maxW <= ell) return "";
        return font.plainSubstrByWidth(s, maxW - ell) + "...";
    }

    @Override
    public void mouseMoved(double mx, double my) {
        if (!bounds.contains(mx, my)) {
            hoveredFileIndex = -1;
            hoveredCommandIndex = -1;
            plusHover = false;
            return;
        }
        plusHover = mx >= plusX() && mx < plusX() + PLUS_SIZE
                 && my >= plusY() && my < plusY() + PLUS_SIZE;

        int fListY = filesListY(), fListH = filesListH();
        if (my >= fListY && my < fListY + fListH) {
            int idx = filesScroll + (int) ((my - fListY) / ROW_H);
            hoveredFileIndex = (idx >= 0 && idx < files.size()) ? idx : -1;
            hoveredCommandIndex = -1;
            return;
        }
        int cListY = commandsListY(), cListH = commandsListH();
        if (my >= cListY && my < cListY + cListH) {
            int idx = commandsScroll + (int) ((my - cListY) / ROW_H);
            hoveredCommandIndex = (idx >= 0 && idx < commands.size()) ? idx : -1;
            hoveredFileIndex = -1;
            return;
        }
        hoveredFileIndex = -1;
        hoveredCommandIndex = -1;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (!bounds.contains(mx, my)) return false;

        requestFocus();

        if (button == 0 && plusHover) {
            bus.publish(new StudioEvents.RequestNewFileEvent());
            return true;
        }

        int fListY = filesListY(), fListH = filesListH();
        if (my >= fListY && my < fListY + fListH) {
            int idx = filesScroll + (int) ((my - fListY) / ROW_H);
            if (idx < 0 || idx >= files.size()) return true;
            String name = files.get(idx).name();
            if (button == 0) {
                if (name.equals(selectedFileName)) return true;
                selectedFileName = name;
                bus.publish(new StudioEvents.FileSelectedEvent(name));
                bus.publish(StudioEvents.LogEvent.legacy(
                    "[studio] selected file: " + name));
                return true;
            }
            if (button == 1) {
                selectedFileName = name;
                // 0.3.2: Explorer не знает содержимого меню —
                // Screen строит его из actions.
                bus.publish(new StudioEvents.RequestContextMenuForFileEvent(
                    (int) mx, (int) my, name));
                return true;
            }
            return true;
        }

        int cListY = commandsListY(), cListH = commandsListH();
        if (my >= cListY && my < cListY + cListH && button == 0) {
            int idx = commandsScroll + (int) ((my - cListY) / ROW_H);
            if (idx < 0 || idx >= commands.size()) return true;
            if (idx == selectedCommandIndex) return true;
            selectedCommandIndex = idx;
            MaredCommandRegistry.CommandInfo info = commands.get(idx);
            bus.publish(new StudioEvents.CommandSelectedEvent(info));
            bus.publish(StudioEvents.LogEvent.legacy(
                "[studio] selected command: " + info.name));
            return true;
        }
        return true;
    }

    @Override
    public boolean mouseScrolled(double mx, double my,
                                 double scrollX, double scrollY) {
        if (!bounds.contains(mx, my)) return false;
        int fListY = filesListY(), fListH = filesListH();
        if (my >= fListY && my < fListY + fListH) {
            int visible = filesVisibleRows();
            int maxOff = Math.max(0, files.size() - visible);
            if (scrollY < 0) filesScroll = Math.min(maxOff, filesScroll + 1);
            else if (scrollY > 0) filesScroll = Math.max(0, filesScroll - 1);
            return true;
        }
        int cListY = commandsListY(), cListH = commandsListH();
        if (my >= cListY && my < cListY + cListH) {
            int visible = commandsVisibleRows();
            int maxOff = Math.max(0, commands.size() - visible);
            if (scrollY < 0) commandsScroll = Math.min(maxOff, commandsScroll + 1);
            else if (scrollY > 0) commandsScroll = Math.max(0, commandsScroll - 1);
            return true;
        }
        return true;
    }

    private void clampScroll() {
        int visibleF = filesVisibleRows();
        int maxOffF = Math.max(0, files.size() - visibleF);
        if (filesScroll > maxOffF) filesScroll = maxOffF;
        if (filesScroll < 0) filesScroll = 0;
        int visibleC = commandsVisibleRows();
        int maxOffC = Math.max(0, commands.size() - visibleC);
        if (commandsScroll > maxOffC) commandsScroll = maxOffC;
        if (commandsScroll < 0) commandsScroll = 0;
    }

    // ============================================================
    //  Narration (0.3.2 accessibility)
    // ============================================================

    @Override
    public Component narrationText() {
        StringBuilder sb = new StringBuilder();
        sb.append(MaredLang.format("mared.narration.explorer.summary",
            files.size(), commands.size()));
        if (selectedFileName != null) {
            sb.append(' ').append(MaredLang.format(
                "mared.narration.explorer.selected", selectedFileName));
        }
        return Component.literal(sb.toString());
    }
}