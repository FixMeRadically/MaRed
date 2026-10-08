package com.fixmer.mared.gui2.settings;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.lwjgl.glfw.GLFW;

import com.fixmer.mared.Mared;
import com.fixmer.mared.MaredLang;
import com.fixmer.mared.MaredSettings;
import com.fixmer.mared.gui2.framework.core.UiContext;
import com.fixmer.mared.gui2.framework.overlay.OverlayManager;
import com.fixmer.mared.gui2.framework.overlay.ToastOverlay;
import com.fixmer.mared.gui2.framework.render.MaredScale;
import com.fixmer.mared.gui2.framework.render.legacy.MaredUi;
import com.fixmer.mared.gui2.framework.render.legacy.MaredWidgets;
import com.fixmer.mared.gui2.framework.theme.MaredThemeRegistry;
import com.fixmer.mared.gui2.framework.theme.MaredTheme;
import com.fixmer.mared.gui2.studio.MaredStudioScreen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Экран настроек Mared.
 *
 * 0.3.2 (audit #96):
 *   Sidebar вместо top-tabs. Категории слева, вкладки — вертикально.
 *   Раньше 6+ табов в горизонтальной полосе рисковали переполнением
 *   на узких экранах; sidebar масштабируется до 15+ страниц.
 *
 * 0.3.2 (audit #97):
 *   Key events идут СНАЧАЛА в активный таб (может перехватить,
 *   например, при rebind или search), потом — screen-level tab
 *   switching. Раньше Left/Right всегда переключали таб, что
 *   конфликтовало с будущими слайдерами/полями ввода.
 *
 * 0.3.2 (audit #92):
 *   Вкладки берутся из SettingsPageRegistry (уже было).
 */
public class MaredSettingsScreen extends Screen {

    private static int BG(){return com.fixmer.mared.gui2.framework.theme.MaredThemeRegistry.active().bgScreen;}
    private static int PANEL_BG(){return com.fixmer.mared.gui2.framework.theme.MaredThemeRegistry.active().bgPanel;}
    private static int PANEL_RAISED(){return com.fixmer.mared.gui2.framework.theme.MaredThemeRegistry.active().bgPanelRaised;}
    private static int SIDEBAR_BG(){return com.fixmer.mared.gui2.framework.theme.MaredThemeRegistry.active().bgSunken;}
    private static int TEXT(){return com.fixmer.mared.gui2.framework.theme.MaredThemeRegistry.active().text;}
    private static int TEXT_DIM(){return com.fixmer.mared.gui2.framework.theme.MaredThemeRegistry.active().textDim;}
    private static int TEXT_HEADER(){return com.fixmer.mared.gui2.framework.theme.MaredThemeRegistry.active().textFaint;}

    private static final int SIDEBAR_W     = 180;
    private static final int SIDEBAR_PAD   = 8;
    private static final int CATEGORY_H    = 22;
    private static final int TAB_ROW_H     = 24;
    private static final int BUTTON_H      = 20;
    private static final int CONTENT_PAD   = 20;

    private final Screen parent;
    private final List<MaredSettingsTab> tabs;
    private int activeIndex = 0;

    private final MaredSettings.Snapshot snapshot;
    private final SettingsContext ctx;

    private final boolean initialShowSidebar;
    private final boolean initialShowRightPanel;
    private final boolean initialShowConsole;
    private final boolean initialShowStatusBar;
    private final boolean initialShowToolbar;

    // Sidebar layout cache.
    private int[] sidebarY = new int[0];
    private int[] sidebarHeaderY = new int[0]; // -1 if no header before tab

    private int saveX, saveY, saveW, saveH;
    private int cancelX, cancelY, cancelW, cancelH;

    private OverlayManager overlayManager;
    private ToastOverlay toastOverlay;
    private MaredTheme initialTheme;
    private boolean logicContext;
    private boolean settingsApplied;

    public MaredSettingsScreen(Screen parent) {
        super(Component.literal("Mared Settings"));
        this.parent = parent;
        this.logicContext=parent instanceof MaredStudioScreen;
        if(parent instanceof com.fixmer.mared.gui2.shell.MaredShellScreen && com.fixmer.mared.gui2.runtime.RuntimeProvider.isInstalled()){
            var current=com.fixmer.mared.gui2.runtime.RuntimeProvider.get().navigation().current();
            this.logicContext=current!=null&&(current.id()==com.fixmer.mared.gui2.navigation.SpaceId.LOGIC||current.id()==com.fixmer.mared.gui2.navigation.SpaceId.STUDIO);
        }

        MaredSettings.load();
        this.initialTheme=MaredThemeRegistry.active();

        this.initialShowSidebar    = MaredSettings.isLayoutShowSidebar();
        this.initialShowRightPanel = MaredSettings.isLayoutShowRightPanel();
        this.initialShowConsole    = MaredSettings.isLayoutShowConsole();
        this.initialShowStatusBar  = MaredSettings.isLayoutShowStatusBar();
        this.initialShowToolbar    = MaredSettings.isLayoutShowToolbar();

        this.tabs = SettingsPageRegistry.instantiateAll();
        if (this.tabs.isEmpty()) {
            Mared.LOGGER.error(
                "[settings] no pages registered — screen may be empty");
        }

        this.snapshot = MaredSettings.Snapshot.capture();
        this.ctx = new SettingsContext(snapshot);

        String lastId = MaredSettings.getSettingsActiveTab();
        if (lastId != null && !lastId.isEmpty()) {
            for (int i = 0; i < tabs.size(); i++) {
                if (tabs.get(i).id().equals(lastId)) { activeIndex = i; break; }
            }
        }
    }

    @Override
    protected void init() {
        super.init();
        MaredLang.reload();
        MaredSettings.load();

        Minecraft mc = Minecraft.getInstance();
        int physW = mc.getWindow().getWidth();
        int physH = mc.getWindow().getHeight();
        int mcGuiScale = (int) mc.getWindow().getGuiScale();

        UiContext uiCtx = new UiContext(this.width, this.height,
            physW, physH, mcGuiScale, MaredThemeRegistry.active());
        MaredScale.bind(uiCtx);

        overlayManager = new OverlayManager();
        toastOverlay = new ToastOverlay();
        overlayManager.push(toastOverlay);

        computeSidebarLayout();
        computeButtonLayout();

        if (activeIndex >= 0 && activeIndex < tabs.size()) {
            tabs.get(activeIndex).onOpen(ctx);
        }
    }

    @Override
    public void removed() {
        restoreThemePreview();
        if (overlayManager != null) overlayManager.clear();
        MaredScale.unbind();
    }

    private void computeSidebarLayout() {
        int n = tabs.size();
        sidebarY = new int[n];
        sidebarHeaderY = new int[n];

        int y = SIDEBAR_PAD;
        String prevCat = null;
        for (int i = 0; i < n; i++) {
            SettingsPageDescriptor d = SettingsPageRegistry.byId(tabs.get(i).id());
            String cat = d != null ? d.category()
                : SettingsPageDescriptor.CATEGORY_GENERAL;
            if (!cat.equals(prevCat)) {
                sidebarHeaderY[i] = y;
                y += CATEGORY_H;
                prevCat = cat;
            } else {
                sidebarHeaderY[i] = -1;
            }
            sidebarY[i] = y;
            y += TAB_ROW_H;
        }
    }

    private void computeButtonLayout() {
        saveW = MaredUi.px(100);
        saveH = MaredUi.px(BUTTON_H);
        int gap = MaredUi.px(12);
        int totalW = saveW * 2 + gap;
        int startX = this.width / 2 - totalW / 2;
        int y = this.height - MaredUi.px(14) - saveH;

        cancelX = startX;
        cancelY = y;
        cancelW = saveW;
        cancelH = saveH;

        saveX = startX + saveW + gap;
        saveY = y;
    }

    private void switchTab(int idx) {
        if (idx < 0 || idx >= tabs.size()) return;
        if (idx == activeIndex) return;

        tabs.get(activeIndex).onClose(ctx);
        activeIndex = idx;
        MaredSettings.setSettingsActiveTab(tabs.get(idx).id());
        tabs.get(activeIndex).onOpen(ctx);
    }

    private void onSave() {
        ctx.apply();
        settingsApplied=true;

        boolean layoutChanged =
            initialShowSidebar    != MaredSettings.isLayoutShowSidebar()    ||
            initialShowRightPanel != MaredSettings.isLayoutShowRightPanel() ||
            initialShowConsole    != MaredSettings.isLayoutShowConsole()    ||
            initialShowStatusBar  != MaredSettings.isLayoutShowStatusBar()  ||
            initialShowToolbar    != MaredSettings.isLayoutShowToolbar();

        if(parent instanceof com.fixmer.mared.gui2.shell.MaredShellScreen){onClose();return;}
        if (layoutChanged) {
            com.fixmer.mared.gui2.launcher.MaredScreenManager.openStudio();
            return;
        }
        onClose();
    }

    private void onCancel() { onClose(); }

    @Override
    public void onClose() {
        restoreThemePreview();
        if (activeIndex >= 0 && activeIndex < tabs.size()) {
            tabs.get(activeIndex).onClose(ctx);
        }
        if (overlayManager != null) overlayManager.clear();
        if (this.minecraft != null) this.minecraft.setScreen(parent);
    }

    private void restoreThemePreview(){
        if(!settingsApplied&&initialTheme!=null&&MaredThemeRegistry.active()!=initialTheme)MaredThemeRegistry.setActive(initialTheme);
    }

    private int contentX()      { return SIDEBAR_W + CONTENT_PAD; }
    private int contentY()      { return CONTENT_PAD; }
    private int contentW()      { return this.width - contentX() - CONTENT_PAD; }
    private int contentH()      {
        return this.height - contentY() - MaredUi.px(BUTTON_H + 40);
    }

    // ============================================================
    //  Render
    // ============================================================

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        MaredTheme preview=MaredThemeRegistry.get(ctx.theme().themeId());
        if(preview!=null&&MaredThemeRegistry.active()!=preview)MaredThemeRegistry.setActive(preview);
        g.fill(0, 0, this.width, this.height, BG());

        drawSidebar(g, mouseX, mouseY);
        drawContent(g, mouseX, mouseY);
        drawButtons(g, mouseX, mouseY);

        if (overlayManager != null) {
            overlayManager.render(g, this.font,
                this.width, this.height, mouseX, mouseY);
        }
    }

    private void drawSidebar(GuiGraphics g, int mouseX, int mouseY) {
        MaredUi.rect(g, 0, 0, SIDEBAR_W, this.height, SIDEBAR_BG());
        MaredUi.rect(g, SIDEBAR_W - 1, 0, SIDEBAR_W, this.height, com.fixmer.mared.gui2.framework.theme.MaredThemeRegistry.active().border);

        String prevCat = null;
        for (int i = 0; i < tabs.size(); i++) {
            MaredSettingsTab tab = tabs.get(i);
            SettingsPageDescriptor d = SettingsPageRegistry.byId(tab.id());
            String cat = d != null ? d.category()
                : SettingsPageDescriptor.CATEGORY_GENERAL;

            if (!cat.equals(prevCat)) {
                String label = MaredLang.get("mared.settings.category." + cat);
                MaredUi.text(g, this.font, label,
                    SIDEBAR_PAD, sidebarY[i] - CATEGORY_H + 7,
                    TEXT_HEADER());
                prevCat = cat;
            }

            int rowX = SIDEBAR_PAD;
            int rowW = SIDEBAR_W - SIDEBAR_PAD * 2;
            int rowY = sidebarY[i];
            int rowH = TAB_ROW_H - 2;

            boolean active = i == activeIndex;
            boolean hover = MaredUi.hovered(mouseX, mouseY, rowX, rowY, rowW, rowH);

            if (active) {
                MaredUi.rect(g, rowX, rowY, rowX + rowW, rowY + rowH, PANEL_BG());
                MaredUi.rect(g, rowX, rowY, rowX + 3, rowY + rowH,
                    logicContext?com.fixmer.mared.technology.editor.GenesisEditorVisuals.accent():tab.accentColor());
            } else if (hover) {
                MaredUi.rect(g, rowX, rowY, rowX + rowW, rowY + rowH, com.fixmer.mared.gui2.framework.theme.MaredThemeRegistry.active().bgHover);
            }

            MaredUi.text(g, this.font, tab.displayName(),
                rowX + 12, rowY + 6,
                active ? TEXT() : TEXT_DIM());
        }
    }

    private void drawContent(GuiGraphics g, int mouseX, int mouseY) {
        int cx = contentX();
        int cy = contentY();
        int cw = contentW();
        int ch = contentH();
        if (ch <= 0) return;

        MaredUi.text(g, this.font,
            tabs.isEmpty() ? "" : tabs.get(activeIndex).displayName(),
            cx, cy, TEXT());

        int innerY = cy + 18;
        int innerH = ch - 18;
        if (innerH <= 0) return;

        MaredUi.rect(g, cx - 4, innerY - 4,
            cx + cw + 4, innerY + innerH + 4, PANEL_BG());

        if (!tabs.isEmpty()) {
            tabs.get(activeIndex).render(g, this.font,
                cx, innerY, cw, innerH,
                ctx, mouseX, mouseY);
        }
    }

    private void drawButtons(GuiGraphics g, int mouseX, int mouseY) {
        boolean cancelHover = MaredUi.hovered(mouseX, mouseY,
            cancelX, cancelY, cancelW, cancelH);
        MaredWidgets.button3D(g, this.font, cancelX, cancelY,
            cancelW, cancelH,
            MaredLang.get("mared.settings.cancel"),
            cancelHover ? com.fixmer.mared.gui2.framework.theme.MaredThemeRegistry.active().bgHover : com.fixmer.mared.gui2.framework.theme.MaredThemeRegistry.active().bgPanelRaised,
            com.fixmer.mared.gui2.framework.theme.MaredThemeRegistry.active().danger, TEXT(), cancelHover);

        boolean saveHover = MaredUi.hovered(mouseX, mouseY,
            saveX, saveY, saveW, saveH);
        MaredWidgets.button3D(g, this.font, saveX, saveY, saveW, saveH,
            MaredLang.get("mared.settings.save"),
            saveHover ? com.fixmer.mared.gui2.framework.theme.MaredThemeRegistry.active().bgHover : com.fixmer.mared.gui2.framework.theme.MaredThemeRegistry.active().bgPanelRaised,
            com.fixmer.mared.gui2.framework.theme.MaredThemeRegistry.active().success, TEXT(), saveHover);
    }

    // ============================================================
    //  Input
    // ============================================================

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (overlayManager != null
            && overlayManager.mouseClicked(mx, my, button)) return true;

        if (button != 0) return super.mouseClicked(mx, my, button);

        // Sidebar hit
        for (int i = 0; i < tabs.size(); i++) {
            int rowX = SIDEBAR_PAD;
            int rowW = SIDEBAR_W - SIDEBAR_PAD * 2;
            int rowY = sidebarY[i];
            int rowH = TAB_ROW_H - 2;
            if (MaredUi.hovered(mx, my, rowX, rowY, rowW, rowH)) {
                switchTab(i);
                return true;
            }
        }

        // Buttons
        if (MaredUi.hovered(mx, my, cancelX, cancelY, cancelW, cancelH)) {
            onCancel();
            return true;
        }
        if (MaredUi.hovered(mx, my, saveX, saveY, saveW, saveH)) {
            onSave();
            return true;
        }

        // Content
        if (my >= contentY() + 18 && my < contentY() + contentH()) {
            int cx = contentX();
            int cy = contentY() + 18;
            int cw = contentW();
            int ch = contentH() - 18;
            if (!tabs.isEmpty() && tabs.get(activeIndex).mouseClicked(
                    mx, my, button, cx, cy, cw, ch, ctx)) {
                return true;
            }
        }

        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        if (overlayManager != null
            && overlayManager.mouseScrolled(mx, my, dx, dy)) return true;

        if (!tabs.isEmpty()
            && my >= contentY() + 18 && my < contentY() + contentH()) {
            int cx = contentX();
            int cy = contentY() + 18;
            int cw = contentW();
            int ch = contentH() - 18;
            if (tabs.get(activeIndex).mouseScrolled(mx, my, dy,
                cx, cy, cw, ch, ctx)) {
                return true;
            }
        }
        return super.mouseScrolled(mx, my, dx, dy);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (overlayManager != null
            && overlayManager.keyPressed(keyCode, scanCode, modifiers))
            return true;

        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            onCancel();
            return true;
        }

        // 0.3.2 (audit #97): tab handles key FIRST — может перехватить
        // (rebind, search).
        if (!tabs.isEmpty()) {
            if (tabs.get(activeIndex).keyPressed(keyCode, scanCode,
                    modifiers, ctx)) {
                return true;
            }
        }

        // Screen-level tab switching (после tab).
        if (keyCode == GLFW.GLFW_KEY_LEFT) {
            switchTab(Math.max(0, activeIndex - 1));
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_RIGHT) {
            switchTab(Math.min(tabs.size() - 1, activeIndex + 1));
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_TAB && (modifiers & 2) != 0) {
            boolean shift = (modifiers & 1) != 0;
            int next = shift
                ? (activeIndex - 1 + tabs.size()) % tabs.size()
                : (activeIndex + 1) % tabs.size();
            switchTab(next);
            return true;
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char c, int modifiers) {
        if (overlayManager != null
            && overlayManager.charTyped(c, modifiers)) return true;

        if (!tabs.isEmpty()) {
            if (tabs.get(activeIndex).charTyped(c, modifiers, ctx)) return true;
        }
        return super.charTyped(c, modifiers);
    }

    @Override public boolean isPauseScreen() { return false; }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY,
                                 float partialTick) {}
}