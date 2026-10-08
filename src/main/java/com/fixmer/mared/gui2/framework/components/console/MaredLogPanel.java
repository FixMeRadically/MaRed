package com.fixmer.mared.gui2.framework.components.console;

import java.util.ArrayList;
import java.util.List;

import com.fixmer.mared.services.logging.LogEntry;
import com.fixmer.mared.services.logging.LogService;
import com.fixmer.mared.services.logging.LogSettings;
import com.fixmer.mared.MaredLang;
import com.fixmer.mared.gui2.framework.render.interaction.DragKind;
import com.fixmer.mared.gui2.framework.render.interaction.DragState;
import com.fixmer.mared.gui2.framework.render.legacy.MaredUi;
import com.fixmer.mared.gui2.framework.render.layout.PixelScroll;
import com.fixmer.mared.gui2.framework.render.layout.ScrollArea;
import com.fixmer.mared.gui2.studio.events.StudioEvents;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

/**
 * Панель лога (Console).
 *
 * 0.3.1: add(LogEvent) — принимает structured LogEvent напрямую.
 */
public class MaredLogPanel {

    private static int BG(){return com.fixmer.mared.technology.editor.GenesisEditorVisuals.panel();}
    private static int TEXT(){return com.fixmer.mared.technology.editor.GenesisEditorVisuals.text();}
    private static int TEXT_DIM(){return com.fixmer.mared.technology.editor.GenesisEditorVisuals.dim();}
    private static int BTN_BG(){return com.fixmer.mared.technology.editor.GenesisEditorVisuals.raised();}
    private static int BTN_HOVER(){return com.fixmer.mared.technology.editor.GenesisEditorVisuals.hover();}
    private static int BTN_ACTIVE(){return com.fixmer.mared.technology.editor.GenesisEditorVisuals.selected();}
    private static int ACCENT(){return com.fixmer.mared.technology.editor.GenesisEditorVisuals.accent();}
    private static int SELECT_BG(){return com.fixmer.mared.technology.editor.GenesisEditorVisuals.selected();}
    private static int PANEL_SETTINGS(){return com.fixmer.mared.technology.editor.GenesisEditorVisuals.panel();}
    private static final int SCROLLBAR_W    = 6;
    private static int CHECK_ON(){return com.fixmer.mared.technology.editor.GenesisEditorVisuals.accent();}
    private static int CHECK_BRD(){return com.fixmer.mared.technology.editor.GenesisEditorVisuals.edge();}
    private static int SEARCH_BG(){return com.fixmer.mared.technology.editor.GenesisEditorVisuals.sunken();}

    private static int BORDER_TOP(){return com.fixmer.mared.technology.editor.GenesisEditorVisuals.edge();}
    private static int BORDER_BOTTOM(){return com.fixmer.mared.technology.editor.GenesisEditorVisuals.edge();}

    public static final int LOG_HEADER = 20;
    private boolean compact;
    public void setCompact(boolean value){compact=value;}
    private int headerHeight(){return compact?(searchOpen?34:18):LOG_HEADER;}
    public static final int LOG_LINE   = 10;
    public static final int PANEL_H    = 140;

    private static final int PAD = 4;

    private static final int SEARCH_BOX_OFFSET_X  = 40;
    private static final int SEARCH_BOX_RIGHT_PAD = 220;
    private static final int SEARCH_BOX_Y_OFFSET  = 3;
    private static final int SEARCH_BOX_HEIGHT    = 14;

    private final ScrollArea scroll = new ScrollArea();
    private final PixelScroll filterScroll = new PixelScroll();
    private final DragState drag = new DragState();

    private static final class Pos {
        final int line, col;
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

    private boolean searchOpen = false;
    private String searchQuery = "";
    private EditBox searchBox;

    private int cachedX, cachedY, cachedW, cachedH;
    private Font cachedFont;

    private List<LogEntry> visibleCache = null;
    private int visibleCacheKey = 0;
    private List<LogEntry> localEntries;
    private int localRevision;
    private Runnable localClear;

    /** Panel-local run view. null restores the shared Studio log. */
    public void setLocalEntries(List<LogEntry> entries) {
        setLocalEntries(entries, null);
    }
    public void setLocalEntries(List<LogEntry> entries, Runnable clear) {
        boolean sameAnchor = localEntries != null && entries != null
            && !localEntries.isEmpty() && !entries.isEmpty()
            && java.util.Objects.equals(localEntries.get(0).source, entries.get(0).source)
            && localEntries.get(0).epochMillis == entries.get(0).epochMillis
            && localEntries.get(0).message.equals(entries.get(0).message);
        localClear = clear;
        localEntries = entries == null ? null : List.copyOf(entries);
        localRevision++;
        if (!sameAnchor) {
            selStart = new Pos(-1, -1); selEnd = new Pos(-1, -1);
            scroll.offset = 0;
        }
        invalidateVisibleCache();
    }

    public MaredLogPanel() {
        LogSettings.load();
        LogService.get().ensureLoaded();
    }

    // ============================================================
    //  Add
    // ============================================================

    public void add(String line) {
        LogService.get().add(line);
        scroll.offset = 0;
        invalidateVisibleCache();
    }

    public void addStructured(LogSettings.Level level, String category,
                              String message) {
        LogService.get().addStructured(level, category, message);
        scroll.offset = 0;
        invalidateVisibleCache();
    }

    /**
     * 0.3.1: приём structured LogEvent из StudioEventBus.
     * Сохраняет структуру, не парсит заново.
     */
    public void add(StudioEvents.LogEvent event) {
        if (event == null) return;
        LogService.get().addStructured(event.level(), event.category(), event.message(), event.source());
        invalidateVisibleCache();
    }

    public void clear() {
        if (localEntries != null) {
            if (localClear != null) localClear.run();
            setLocalEntries(List.of()); scroll.offset = 0; return;
        }
        LogService.get().clear();
        selStart = new Pos(-1, -1);
        selEnd   = new Pos(-1, -1);
        scroll.offset = 0;
        invalidateVisibleCache();
    }

    public boolean isSettingsOpen() { return settingsOpen; }
    public boolean isSearchOpen()   { return searchOpen; }

    public void closeSearch() {
        searchOpen = false;
        searchQuery = "";
        if (searchBox != null) searchBox.setFocused(false);
        searchBox = null;
        invalidateVisibleCache();
    }

    public void closeSettings() { settingsOpen = false; }

    public void setSearchOpen(boolean open) {
        this.searchOpen = open;
        if (!open) {
            this.searchQuery = "";
            if (searchBox != null) searchBox.setFocused(false);
            this.searchBox = null;
            invalidateVisibleCache();
        }
    }

    public List<LogEntry> snapshot() {
        return localEntries == null ? LogService.get().snapshot() : localEntries;
    }

    public static List<LogEntry> snapshotForStats() {
        return LogService.get().snapshot();
    }

    public static int countVisible() {
        return LogService.get().countVisible();
    }

    public static void saveToDisk() {
        LogService.get().save();
    }

    private void invalidateVisibleCache() { visibleCache = null; }

    private int currentCacheKey() {
        int h = 17;
        h = h * 31 + LogService.get().version();
        h = h * 31 + localRevision;
        h = h * 31 + LogSettings.getVersion();
        h = h * 31 + (searchQuery == null ? 0 : searchQuery.hashCode());
        return h;
    }

    public List<LogEntry> getVisibleEntries() {
        int key = currentCacheKey();
        if (visibleCache != null && visibleCacheKey == key) return visibleCache;

        List<LogEntry> all = snapshot();
        int n = all.size();
        List<LogEntry> result = new ArrayList<>(n);

        String q = searchQuery == null ? "" : searchQuery;
        String qLower = q.isEmpty() ? "" : q.toLowerCase();

        var settings = com.fixmer.mared.services.logging
            .LogSettingsService.get();

        for (int i = 0; i < n; i++) {
            LogEntry e = all.get(i);
            if (!settings.shouldShow(e.level, e.category)) continue;
            if (!qLower.isEmpty()
                && !containsIgnoreCase(e.text, qLower)
                && !containsIgnoreCase(e.time, q)) continue;
            result.add(e);
        }

        visibleCache = result;
        visibleCacheKey = key;
        return result;
    }

    private static boolean containsIgnoreCase(String haystack, String needleLower) {
        if (needleLower.isEmpty()) return true;
        int hLen = haystack.length();
        int nLen = needleLower.length();
        if (nLen > hLen) return false;
        for (int i = 0; i <= hLen - nLen; i++) {
            if (haystack.regionMatches(true, i, needleLower, 0, nLen)) return true;
        }
        return false;
    }

    public EditBox searchBox() { return searchBox; }

    private int searchBoxX(int x) { return x + (compact?4:SEARCH_BOX_OFFSET_X); }
    private int searchBoxW(int w) {
        return compact?Math.max(20,w-8):Math.max(60, w - SEARCH_BOX_OFFSET_X - SEARCH_BOX_RIGHT_PAD);
    }
    private int searchBoxY(int y) { return y + (compact?19:SEARCH_BOX_Y_OFFSET); }

    public EditBox ensureSearchBox(Font font, int x, int y, int w) {
        if (!searchOpen) { searchBox = null; return null; }

        int boxX = searchBoxX(x);
        int boxW = searchBoxW(w);
        int boxY = searchBoxY(y);
        int boxH = SEARCH_BOX_HEIGHT;

        if (searchBox == null) {
            searchBox = new EditBox(font, boxX, boxY, boxW, boxH,
                Component.literal(MaredLang.get("mared.log.search_hint")));
            searchBox.setBordered(false);
            searchBox.setMaxLength(128);
            searchBox.setValue(searchQuery);
            searchBox.setTextColor(com.fixmer.mared.technology.editor.GenesisEditorVisuals.text());
            searchBox.setTextColorUneditable(com.fixmer.mared.technology.editor.GenesisEditorVisuals.dim());
            searchBox.setResponder(s -> {
                searchQuery = s == null ? "" : s;
                scroll.offset = 0;
                invalidateVisibleCache();
            });
        } else {
            searchBox.setX(boxX);
            searchBox.setY(boxY);
            searchBox.setWidth(boxW);
            searchBox.setHeight(boxH);
        }
        searchBox.setTextColor(com.fixmer.mared.technology.editor.GenesisEditorVisuals.text());
        searchBox.setTextColorUneditable(com.fixmer.mared.technology.editor.GenesisEditorVisuals.dim());
        return searchBox;
    }

    private void drawHeader(GuiGraphics g, Font font, int x, int y, int w,
                            int mouseX, int mouseY) {
        if(compact){drawCompactHeader(g,font,x,y,w,mouseX,mouseY);return;}
        MaredUi.text(g, font, MaredLang.get("mared.log.title"),
            x + 6, y + 6, ACCENT());

        if (searchOpen) {
            int boxX = searchBoxX(x);
            int boxW = searchBoxW(w);
            int boxY = searchBoxY(y);
            int boxH = SEARCH_BOX_HEIGHT;
            MaredUi.rect(g, boxX - 1, boxY - 1,
                boxX + boxW + 1, boxY + boxH + 1, SEARCH_BG());
            MaredUi.outlineGradient(g, boxX - 1, boxY - 1,
                boxW + 2, boxH + 2, ACCENT(), ACCENT());
        }

        int btnY = y + 2;
        int btnH = 14;
        int right = x + w - 6;

        right = drawBtn(g, font, right, btnY, btnH, 50,
            MaredLang.get("mared.log.button.export"),
            mouseX, mouseY, BTN_BG(), ACCENT(), false);
        right = drawBtn(g, font, right, btnY, btnH, 44,
            MaredLang.get("mared.log.button.clear"),
            mouseX, mouseY, BTN_BG(), com.fixmer.mared.technology.editor.GenesisEditorVisuals.danger(), false);
        right = drawBtn(g, font, right, btnY, btnH, 44,
            MaredLang.get("mared.log.button.copy"),
            mouseX, mouseY, BTN_BG(), ACCENT(), false);
        right = drawBtn(g, font, right, btnY, btnH, 18,
            MaredLang.get("mared.log.button.search"),
            mouseX, mouseY, BTN_BG(), ACCENT(), searchOpen);
        right = drawBtn(g, font, right, btnY, btnH, 18,
            MaredLang.get("mared.log.button.settings"),
            mouseX, mouseY, BTN_BG(), ACCENT(), settingsOpen);
        right = drawBtn(g, font, right, btnY, btnH, 18,
            MaredLang.get("mared.log.button.toggle"),
            mouseX, mouseY, BTN_BG(), ACCENT(), false);
    }

    private void drawCompactHeader(GuiGraphics g,Font font,int x,int y,int w,int mx,int my){
        if(w>150)MaredUi.text(g,font,MaredLang.get("mared.log.title"),x+4,y+5,TEXT_DIM());
        String[] labels={"E","X","C","/","F"};
        int right=x+w-4;
        for(int i=0;i<labels.length;i++){right=drawBtn(g,font,right,y+2,14,16,labels[i],mx,my,BTN_BG(),ACCENT(),i==3&&searchOpen||i==4&&settingsOpen);}
        if(searchOpen)MaredUi.rect(g,searchBoxX(x)-1,searchBoxY(y)-1,searchBoxX(x)+searchBoxW(w)+1,searchBoxY(y)+SEARCH_BOX_HEIGHT+1,SEARCH_BG());
    }
    private boolean compactHeaderClick(double mx,double my,int x,int y,int w){
        int right=x+w-4;
        for(int i=0;i<5;i++){
            right-=16;
            if(MaredUi.hovered(mx,my,right,y+2,16,14)){
                switch(i){case 0->exportLog();case 1->clear();case 2->copySelectedOrAll();case 3->{
                    searchOpen=!searchOpen;
                    if(!searchOpen){searchQuery="";if(searchBox!=null)searchBox.setFocused(false);searchBox=null;invalidateVisibleCache();}
                }case 4->settingsOpen=!settingsOpen;}
                return true;
            }
            right-=4;
        }
        return false;
    }

    private int drawBtn(GuiGraphics g, Font font, int right, int btnY, int btnH,
                        int width, String label, int mouseX, int mouseY,
                        int bgNormal, int accent, boolean active) {
        int bx = right - width;
        boolean hover = MaredUi.hovered(mouseX, mouseY, bx, btnY, width, btnH);
        int bg = hover ? BTN_HOVER() : (active ? BTN_ACTIVE() : bgNormal);
        MaredUi.button3D(g, font, bx, btnY, width, btnH, label, bg, accent,
            TEXT(), hover);
        return bx - 4;
    }

    private boolean headerClick(double mx, double my, int x, int y, int w) {
        if (my < y || my >= y + headerHeight()) return false;
        if(compact)return compactHeaderClick(mx,my,x,y,w);
        int btnY = y + 2;
        int btnH = 14;
        int right = x + w - 6;

        right -= 50;
        if (MaredUi.hovered(mx, my, right, btnY, 50, btnH)) { exportLog(); return true; }
        right -= 4; right -= 44;
        if (MaredUi.hovered(mx, my, right, btnY, 44, btnH)) { clear(); return true; }
        right -= 4; right -= 44;
        if (MaredUi.hovered(mx, my, right, btnY, 44, btnH)) {
            copySelectedOrAll(); return true;
        }
        right -= 4; right -= 18;
        if (MaredUi.hovered(mx, my, right, btnY, 18, btnH)) {
            searchOpen = !searchOpen;
            if (!searchOpen) {
                searchQuery = "";
                if (searchBox != null) searchBox.setFocused(false);
                searchBox = null;
                invalidateVisibleCache();
            }
            return true;
        }
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

    public void render(GuiGraphics g, Font font, int x, int y, int w, int h,
                       int mouseX, int mouseY) {
        cachedX = x; cachedY = y; cachedW = w; cachedH = h; cachedFont = font;

        MaredUi.rect(g, x, y, x + w, y + h, BG());
        MaredUi.rect(g, x, y, x + w, y + 1, BORDER_TOP());

        drawHeader(g, font, x, y, w, mouseX, mouseY);

        MaredUi.gradientV(g, x, y + headerHeight() - 1, x + w, y + headerHeight(),
            BORDER_TOP(), BORDER_BOTTOM());

        if (collapsed) return;

        if (settingsOpen) {
            renderSettings(g, font, x, y + headerHeight(), w, h - headerHeight(),
                mouseX, mouseY);
            return;
        }

        List<LogEntry> visible = getVisibleEntries();
        int innerY = y + headerHeight();
        int innerH = h - headerHeight();

        scroll.set(x, innerY, w, innerH).inverted(true)
              .items(LOG_LINE, visible.size());

        int visibleItems = scroll.visibleItems();
        int end = visible.size() - scroll.offset;
        int start = Math.max(0, end - visibleItems);

        MaredUi.scissorOn(g, x, innerY, x + w, y + h);

        int selMinL = -1, selMinC = -1, selMaxL = -1, selMaxC = -1;
        if (selStart.valid() && selEnd.valid()) {
            selMinL = selMinLine(); selMinC = selMinCol();
            selMaxL = selMaxLine(); selMaxC = selMaxCol();
        }

        for (int i = start; i < end && i < visible.size(); i++) {
            LogEntry e = visible.get(i);
            int row = i - start;
            int lineY = innerY + row * LOG_LINE;
            String fullLine = "[" + e.time + "] " + e.text;

            if (selMinL >= 0 && i >= selMinL && i <= selMaxL) {
                int selFrom = (i == selMinL) ? selMinC : 0;
                int selTo = (i == selMaxL) ? selMaxC : fullLine.length();
                selFrom = Math.max(0, Math.min(fullLine.length(), selFrom));
                selTo = Math.max(0, Math.min(fullLine.length(), selTo));
                int xFrom = x + PAD + font.width(fullLine.substring(0, selFrom));
                int xTo   = x + PAD + font.width(fullLine.substring(0, selTo));
                if (xTo > xFrom) {
                    MaredUi.rect(g, xFrom, lineY, xTo, lineY + LOG_LINE, SELECT_BG());
                }
            }

            MaredUi.text(g, font, fullLine, x + PAD, lineY, logColor(e));
        }

        MaredUi.scissorOff(g);

        scroll.drawScrollbar(g, ACCENT(), SCROLLBAR_W);
    }

    private int settingsContentHeight(Font font, int w) {
        int innerW = w - 12 - SCROLLBAR_W - 2;
        int lvlItemW = 70;
        int lvlCols = Math.max(1, innerW / (lvlItemW + 4));
        int lvlRows = (LogSettings.Level.values().length + lvlCols - 1) / lvlCols;

        int catItemW = 90;
        int catCols = Math.max(1, innerW / (catItemW + 4));
        int catRows = (LogSettings.CATEGORIES.size() + catCols - 1) / catCols;

        return 20 + 16 + lvlRows * 14 + 8 + 14 + catRows * 14 + 10;
    }

    private void renderSettings(GuiGraphics g, Font font, int x, int y, int w, int h,
                                int mouseX, int mouseY) {
        MaredUi.rect(g, x, y, x + w, y + h, PANEL_SETTINGS());

        int contentH = settingsContentHeight(font, w);
        filterScroll.set(x, y, w, h).content(contentH);

        MaredUi.scissorOn(g, x, y, x + w, y + h);

        int startX = x + 6;
        int startY = y + 6 - filterScroll.offset;
        int innerW = w - 12 - SCROLLBAR_W - 2;

        MaredUi.text(g, font, MaredLang.get("mared.log.filter.title"),
            startX, startY, ACCENT());
        MaredUi.text(g, font, MaredLang.get("mared.log.filter.levels"),
            startX, startY + 16, MaredUi.WARN);

        int lvlRowY = startY + 30;
        int lvlItemW = 70;
        int lvlCols = Math.max(1, innerW / (lvlItemW + 4));
        int col = 0, row = 0;

        for (LogSettings.Level lvl : LogSettings.Level.values()) {
            boolean enabled = LogSettings.isLevelEnabled(lvl);
            int cx = startX + col * (lvlItemW + 4);
            int cy = lvlRowY + row * 14;
            boolean hov = MaredUi.hovered(mouseX, mouseY, cx, cy, lvlItemW, 12);
            int bg = enabled ? BTN_ACTIVE() : (hov ? BTN_HOVER() : BTN_BG());
            MaredUi.rect(g, cx, cy, cx + lvlItemW, cy + 12, bg);
            MaredUi.outline(g, cx, cy, lvlItemW, 12,
                enabled ? CHECK_ON() : CHECK_BRD());
            MaredUi.drawCheckbox(g, cx + 3, cy + 1, 10, enabled, CHECK_BRD(), CHECK_ON());
            MaredUi.text(g, font, lvl.name(), cx + 18, cy + 2,
                enabled ? CHECK_ON() : TEXT_DIM());
            col++;
            if (col >= lvlCols) { col = 0; row++; }
        }
        int lvlRows = row + 1;
        lvlRowY += lvlRows * 14 + 8;

        MaredUi.text(g, font, MaredLang.get("mared.log.filter.categories"),
            startX, lvlRowY, MaredUi.WARN);
        lvlRowY += 14;

        int catItemW = 90;
        int catCols = Math.max(1, innerW / (catItemW + 4));
        col = 0; row = 0;

        for (String cat : LogSettings.CATEGORIES) {
            boolean enabled = LogSettings.isCategoryEnabled(cat);
            int cx = startX + col * (catItemW + 4);
            int cy = lvlRowY + row * 14;
            boolean hov = MaredUi.hovered(mouseX, mouseY, cx, cy, catItemW, 12);
            int bg = enabled ? BTN_ACTIVE() : (hov ? BTN_HOVER() : BTN_BG());
            MaredUi.rect(g, cx, cy, cx + catItemW, cy + 12, bg);
            MaredUi.outline(g, cx, cy, catItemW, 12,
                enabled ? CHECK_ON() : CHECK_BRD());
            MaredUi.drawCheckbox(g, cx + 3, cy + 1, 10, enabled, CHECK_BRD(), CHECK_ON());
            MaredUi.text(g, font, "[" + cat + "]", cx + 18, cy + 2,
                enabled ? CHECK_ON() : TEXT_DIM());
            col++;
            if (col >= catCols) { col = 0; row++; }
        }

        MaredUi.scissorOff(g);
        filterScroll.drawScrollbarGradient(g, ACCENT(), ACCENT(), SCROLLBAR_W);
    }

    private int selMinLine() { return Math.min(selStart.line, selEnd.line); }
    private int selMaxLine() { return Math.max(selStart.line, selEnd.line); }

    private int selMinCol() {
        return (selStart.line < selEnd.line
            || (selStart.line == selEnd.line && selStart.col <= selEnd.col))
            ? selStart.col : selEnd.col;
    }

    private int selMaxCol() {
        return (selStart.line < selEnd.line
            || (selStart.line == selEnd.line && selStart.col <= selEnd.col))
            ? selEnd.col : selStart.col;
    }

    private Pos posAt(double mx, double my, Font font,
                      int x, int innerY, int w, int innerH,
                      List<LogEntry> visible) {
        if (visible.isEmpty()) return new Pos(-1, -1);

        scroll.set(x, innerY, w, innerH).inverted(true)
              .items(LOG_LINE, visible.size());
        int visibleItems = scroll.visibleItems();
        int end = visible.size() - scroll.offset;
        int start = Math.max(0, end - visibleItems);

        int row = ((int) my - innerY) / LOG_LINE;
        if (row < 0) row = 0;
        if (row >= visibleItems) row = visibleItems - 1;

        int idx = start + row;
        if (idx < 0) idx = 0;
        if (idx >= visible.size()) idx = visible.size() - 1;

        String line = "[" + visible.get(idx).time + "] "
            + visible.get(idx).text;

        int relX = (int) mx - (x + PAD);
        if (relX < 0) return new Pos(idx, 0);
        int col = line.length();
        for (int i = 0; i <= line.length(); i++) {
            int ww = font.width(line.substring(0, i));
            if (ww >= relX) { col = i; break; }
        }
        return new Pos(idx, col);
    }

    public boolean mouseClicked(double mx, double my, int button, int x, int y,
                                int w, int h, Font font) {
        if (my >= y && my < y + headerHeight()) {
            if (searchOpen && searchBox != null && searchBox.isMouseOver(mx, my))
                return false;
            return headerClick(mx, my, x, y, w);
        }

        if (collapsed) return false;

        if (settingsOpen) {
            if (mx < x || mx > x + w || my < y || my > y + h) return false;

            filterScroll.set(x, y + headerHeight(), w, h - headerHeight());
            if (filterScroll.clickScrollbar(mx, my, SCROLLBAR_W, drag,
                DragKind.FILTER_SCROLL)) return true;

            return settingsClick(mx, my, x, y + headerHeight(), w, h - headerHeight());
        }

        if (my >= y + headerHeight()) {
            List<LogEntry> visible = getVisibleEntries();
            int innerY = y + headerHeight();
            int innerH = h - headerHeight();

            scroll.set(x, innerY, w, innerH).inverted(true)
                  .items(LOG_LINE, visible.size());
            if (scroll.clickScrollbar(mx, my, SCROLLBAR_W, drag,
                DragKind.LOG_SCROLL)) return true;

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

    public boolean mouseClicked(double mx, double my, int button, int x, int y,
                                int w, int h) {
        return mouseClicked(mx, my, button, x, y, w, h, null);
    }

    public boolean mouseDragged(double mx, double my, int button,
                                double dx, double dy, int x, int y, int w, int h,
                                Font font) {
        if (drag.active()) {
            switch (drag.kind) {
                case LOG_SCROLL    -> scroll.dragScrollbar(my, drag);
                case FILTER_SCROLL -> filterScroll.dragScrollbar(my, drag);
                default -> {}
            }
            return true;
        }
        if (collapsed) return false;
        if (selecting) {
            List<LogEntry> visible = getVisibleEntries();
            if (visible.isEmpty()) return true;

            int innerY = y + headerHeight();
            int innerH = h - headerHeight();

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

    public boolean mouseDragged(double mx, double my, int button,
                                double dx, double dy, int x, int y, int w, int h) {
        return mouseDragged(mx, my, button, dx, dy, x, y, w, h, null);
    }

    public boolean mouseReleased(double mx, double my, int button) {
        drag.clear();
        selecting = false;
        autoScrollDir = 0;
        return false;
    }

    public void tickAutoScroll() {
        if (!selecting || autoScrollDir == 0) return;
        long now = System.currentTimeMillis();
        if (now - lastAutoScrollMs < 40) return;
        lastAutoScrollMs = now;

        List<LogEntry> visible = getVisibleEntries();
        if (visible.isEmpty()) return;

        int innerH = cachedH - headerHeight();
        scroll.set(cachedX, cachedY + headerHeight(), cachedW, innerH)
              .inverted(true).items(LOG_LINE, visible.size());

        if (autoScrollDir < 0) {
            scroll.offset = Math.min(scroll.maxScroll(), scroll.offset + 1);
        } else {
            scroll.offset = Math.max(0, scroll.offset - 1);
        }
    }

    public boolean mouseScrolled(double mx, double my, double deltaY,
                                 int x, int y, int w, int h) {
        if (my < y || my >= y + h || mx < x || mx > x + w) return false;
        if (collapsed) return false;

        if (settingsOpen) {
            filterScroll.set(x, y + headerHeight(), w, h - headerHeight());
            filterScroll.wheel(deltaY, 15);
            return true;
        }
        scroll.set(x, y + headerHeight(), w, h - headerHeight());
        scroll.wheelLog(deltaY, 3);
        return true;
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

        for (LogSettings.Level lvl : LogSettings.Level.values()) {
            int cx = startX + col * (lvlItemW + 4);
            int cy = lvlRowY + row * 14;
            if (MaredUi.hovered(mx, my, cx, cy, lvlItemW, 12)) {
                LogSettings.toggleLevel(lvl);
                invalidateVisibleCache();
                return true;
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

        for (String cat : LogSettings.CATEGORIES) {
            int cx = startX + col * (catItemW + 4);
            int cy = lvlRowY + row * 14;
            if (MaredUi.hovered(mx, my, cx, cy, catItemW, 12)) {
                LogSettings.toggleCategory(cat);
                invalidateVisibleCache();
                return true;
            }
            col++;
            if (col >= catCols) { col = 0; row++; }
        }
        return true;
    }

    public void copySelectedOrAll() {
        StringBuilder sb = new StringBuilder(1024);
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
                int from = (i == loL)
                    ? Math.max(0, Math.min(full.length(), selMinCol())) : 0;
                int to = (i == hiL)
                    ? Math.max(0, Math.min(full.length(), selMaxCol())) : full.length();
                if (from > to) { int t = from; from = to; to = t; }
                sb.append(full, from, to);
                if (i < hiL) sb.append('\n');
            }
        } else {
            selStart = new Pos(-1, -1);
            selEnd = new Pos(-1, -1);
            int n = visible.size();
            for (int i = 0; i < n; i++) {
                LogEntry e = visible.get(i);
                sb.append('[').append(e.time).append("] ")
                  .append(e.text).append('\n');
            }
        }

        String toCopy = sb.toString();
        try {
            Minecraft.getInstance().keyboardHandler.setClipboard(toCopy);
            addStructured(LogSettings.Level.INFO, "info",
                "copied " + toCopy.length()
                    + " chars (visible=" + visible.size()
                    + ", sel=" + (hasRealSelection ? 1 : 0) + ")");
        } catch (Exception ex) {
            addStructured(LogSettings.Level.ERROR, "error",
                "copy failed: " + ex.getMessage());
        }
    }

    public void selectAll() {
        List<LogEntry> visible = getVisibleEntries();
        if (visible.isEmpty()) {
            selStart = new Pos(-1, -1);
            selEnd = new Pos(-1, -1);
        } else {
            selStart = new Pos(0, 0);
            selEnd = new Pos(visible.size() - 1, Integer.MAX_VALUE);
        }
        addStructured(LogSettings.Level.INFO, "info",
            "all lines selected (" + visible.size() + ")");
    }

    public void exportLog() {
        LogService.get().export(snapshot());
        invalidateVisibleCache();
    }

    private int logColor(LogEntry e) {
        if (e == null) return TEXT();

        if (e.level == LogSettings.Level.ERROR) return com.fixmer.mared.technology.editor.GenesisEditorVisuals.danger();
        if (e.level == LogSettings.Level.WARN)  return com.fixmer.mared.technology.editor.GenesisEditorVisuals.warn();

        String cat = e.category;
        if (cat == null) return TEXT();
        return com.fixmer.mared.technology.editor.GenesisEditorVisuals.text();
    }
}
