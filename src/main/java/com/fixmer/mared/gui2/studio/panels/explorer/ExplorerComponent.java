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

    private boolean filesOnly;
    public void setFilesOnly(boolean value){filesOnly=value;if(value){commands.clear();if(filter!=null)filter.setFocused(false);}}

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
    private MaredCommandRegistry.Source source=MaredCommandRegistry.Source.MC;
    private long catalogRevision=-1;
    private net.minecraft.client.gui.components.EditBox filter;
    private void reloadCommands() {
        String selected=selectedCommandIndex>=0&&selectedCommandIndex<commands.size()?commands.get(selectedCommandIndex).name:null;
        commands.clear();commands.addAll(MaredCommandRegistry.search(filter==null?"":filter.getValue(),source,null));
        selectedCommandIndex=-1;
        for(int i=0;i<commands.size();i++)if(commands.get(i).name.equals(selected)){selectedCommandIndex=i;break;}
        catalogRevision=MaredCommandRegistry.revision();clampScroll();
    }
    @Override public void onFocusLost(){if(filter!=null)filter.setFocused(false);}
    @Override public boolean keyPressed(int key,int scan,int mods){return filter!=null&&filter.isFocused()&&filter.keyPressed(key,scan,mods);}
    @Override public boolean charTyped(char c,int mods){return filter!=null&&filter.isFocused()&&filter.charTyped(c,mods);}

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
            if(!filesOnly)reloadCommands();
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
        if(filesOnly)return Math.max(0,bounds.height()-HEADER_H);
        int half = (bounds.height() - SPLIT_GAP) / 2;
        return Math.max(20, half - HEADER_H);
    }
    private int commandsHeaderY() {
        return filesListY() + filesListH() + SPLIT_GAP;
    }
    private int commandsListY()   { return commandsHeaderY() + HEADER_H + 16; }
    private int commandsListH()   {
        if(filesOnly)return 0;
        return Math.max(20, bounds.bottom() - commandsListY());
    }
    private int filesVisibleRows()    { return Math.max(1, filesListH() / ROW_H); }
    private int commandsVisibleRows() { return Math.max(1, commandsListH() / ROW_H); }
    private int plusX() { return bounds.right() - PLUS_SIZE - PAD; }
    private int plusY() { return bounds.y() + 1; }

    @Override
    protected void safeRender(MaredRenderContext ctx) {
        if(!filesOnly&&catalogRevision!=MaredCommandRegistry.revision())reloadCommands();
        ctx.graphics().fill(bounds.x(),bounds.y(),bounds.right(),bounds.bottom(),com.fixmer.mared.technology.editor.GenesisEditorVisuals.panel());
        GuiGraphics g = ctx.graphics();
        Font font = ctx.font();

        g.drawString(font, com.fixmer.mared.technology.editor.EditorText.translate("Files"), bounds.x() + PAD, bounds.y() + 3,
            com.fixmer.mared.technology.editor.GenesisEditorVisuals.dim(), false);

        int px = plusX();
        int py = plusY();
        int bg = plusHover ? com.fixmer.mared.technology.editor.GenesisEditorVisuals.raised() : com.fixmer.mared.technology.editor.GenesisEditorVisuals.raised();
        int border = plusHover ? com.fixmer.mared.technology.editor.GenesisEditorVisuals.accent() : com.fixmer.mared.technology.editor.GenesisEditorVisuals.edge();
        g.fill(px, py, px + PLUS_SIZE, py + PLUS_SIZE, bg);
        g.renderOutline(px, py, PLUS_SIZE, PLUS_SIZE, border);
        g.drawString(font, "+",
            px + PLUS_SIZE / 2 - font.width("+") / 2,
            py + PLUS_SIZE / 2 - 4,
            com.fixmer.mared.technology.editor.GenesisEditorVisuals.accent(), false);

        if (files.isEmpty()) {
            g.drawString(font, com.fixmer.mared.technology.editor.EditorText.translate("(empty)"),
                bounds.x() + PAD, filesListY() + 2, com.fixmer.mared.technology.editor.GenesisEditorVisuals.dim(), false);
        } else {
            int visible = filesVisibleRows();
            int end = Math.min(files.size(), filesScroll + visible);
            for (int i = filesScroll; i < end; i++) {
                int y = filesListY() + (i - filesScroll) * ROW_H;
                drawFileRow(g, font, i, y);
            }
        }

        if(filesOnly)return;

        int sepY = commandsHeaderY() - SPLIT_GAP / 2;
        g.fill(bounds.x(), sepY, bounds.right(), sepY + 1, com.fixmer.mared.technology.editor.GenesisEditorVisuals.edge());

        g.drawString(font, source.name()+" ("+commands.size()+")",bounds.x()+PAD,commandsHeaderY()+3,com.fixmer.mared.technology.editor.GenesisEditorVisuals.dim(),false);
        int switchX=bounds.right()-46;
        g.drawString(font,"MC",switchX,commandsHeaderY()+3,source==MaredCommandRegistry.Source.MC?com.fixmer.mared.technology.editor.GenesisEditorVisuals.accent():com.fixmer.mared.technology.editor.GenesisEditorVisuals.dim(),false);
        g.drawString(font,"MR",switchX+24,commandsHeaderY()+3,source==MaredCommandRegistry.Source.MR?com.fixmer.mared.technology.editor.GenesisEditorVisuals.accent():com.fixmer.mared.technology.editor.GenesisEditorVisuals.dim(),false);
        if(filter==null){
            filter=new net.minecraft.client.gui.components.EditBox(font,0,0,80,12,Component.literal("Filter commands"));
            filter.setMaxLength(128);filter.setBordered(false);filter.setHint(Component.literal("Filter…"));filter.setResponder(value->{commandsScroll=0;reloadCommands();});
        }
        filter.setX(bounds.x()+PAD);filter.setY(commandsHeaderY()+HEADER_H+1);filter.setWidth(Math.max(20,bounds.width()-PAD*2));filter.render(g,ctx.mouseX(),ctx.mouseY(),0f);

        if (commands.isEmpty()) {
            g.drawString(font, "(no matches)",
                bounds.x() + PAD, commandsListY() + 2,
                com.fixmer.mared.technology.editor.GenesisEditorVisuals.dim(), false);
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
                com.fixmer.mared.technology.editor.GenesisEditorVisuals.raised());
        } else if (hovered) {
            g.fill(bounds.x() + 1, y, bounds.right() - 1, y + ROW_H - 1,
                com.fixmer.mared.technology.editor.GenesisEditorVisuals.edge());
        }

        // 0.3.2: persistent — из модели, не из Storage.
        if (entry.persistent()) {
            g.fill(bounds.x() + 1, y, bounds.x() + 3, y + ROW_H - 1, com.fixmer.mared.technology.editor.GenesisEditorVisuals.success());
        }

        int color = selected ? com.fixmer.mared.technology.editor.GenesisEditorVisuals.accent() : com.fixmer.mared.technology.editor.GenesisEditorVisuals.text();
        g.drawString(font, ellipsize(font,entry.name(),Math.max(0,bounds.width()-PAD*2-8)),
            bounds.x() + PAD + 4, y + 3, color, false);
    }

    private void drawCommandRow(GuiGraphics g, Font font, int i, int y) {
        boolean selected = (i == selectedCommandIndex);
        boolean hovered = (i == hoveredCommandIndex);
        if (selected) {
            g.fill(bounds.x() + 1, y, bounds.right() - 1, y + ROW_H - 1,
                com.fixmer.mared.technology.editor.GenesisEditorVisuals.raised());
        } else if (hovered) {
            g.fill(bounds.x() + 1, y, bounds.right() - 1, y + ROW_H - 1,
                com.fixmer.mared.technology.editor.GenesisEditorVisuals.edge());
        }
        MaredCommandRegistry.CommandInfo info = commands.get(i);
        int color = selected ? com.fixmer.mared.technology.editor.GenesisEditorVisuals.accent() : com.fixmer.mared.technology.editor.GenesisEditorVisuals.text();
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
        if(bounds.contains(mx,my)&&button==0&&my>=commandsHeaderY()&&my<commandsHeaderY()+HEADER_H&&mx>=bounds.right()-46) {
            source=mx<bounds.right()-22?MaredCommandRegistry.Source.MC:MaredCommandRegistry.Source.MR;
            selectedCommandIndex=-1;commandsScroll=0;reloadCommands();bus.publish(new StudioEvents.CommandSelectedEvent(null));return true;
        }
        if(filter!=null&&filter.isMouseOver(mx,my)&&button==0){requestFocus();filter.setFocused(true);return filter.mouseClicked(mx,my,button);}
        if(filter!=null)filter.setFocused(false);

        if (!bounds.contains(mx, my)) return false;

        requestFocus();

        if (button == 0 && mx>=plusX()&&mx<plusX()+PLUS_SIZE&&my>=plusY()&&my<plusY()+PLUS_SIZE) {
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
            MaredCommandRegistry.CommandInfo info = MaredCommandRegistry.findByName(commands.get(idx).name,source);
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