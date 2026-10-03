package com.fixmer.mared.gui2.settings;

import java.util.List;

import org.lwjgl.glfw.GLFW;

import com.fixmer.mared.gui2.studio.MaredStudioScreen;
import com.fixmer.mared.MaredLang;
import com.fixmer.mared.MaredSettings;
import com.fixmer.mared.gui2.framework.core.UiContext;
import com.fixmer.mared.gui2.framework.render.MaredScale;
import com.fixmer.mared.gui2.framework.render.MaredUi;
import com.fixmer.mared.gui2.framework.render.MaredWidgets;
import com.fixmer.mared.gui2.framework.theme.MaredThemeRegistry;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Экран настроек Mared.
 *
 * 0.3.0 (Stage B5): перенос legacy gui.settings.MaredSettingsScreen в gui2.
 * 0.3.0 (Phase F3a): load() до capture(), onSave → ctx.apply().
 * 0.3.1:
 *   - Screen сам делает MaredScale.bind в init и unbind в removed.
 *     Раньше этого не было — Settings открывался поверх Studio и
 *     падал на MaredUi.px() → MaredScale.context().
 *   - Если layout изменился — Studio пересоздаётся.
 */
public class MaredSettingsScreen extends Screen {

    private static final int BG            = 0xFF0A0A10;
    private static final int PANEL_BG      = 0xFF14141C;
    private static final int PANEL_RAISED  = 0xFF1A1A24;
    private static final int TEXT          = 0xFFFFFFFF;
    private static final int TEXT_DIM      = 0xFFAAAAAA;

    private static final int TAB_STRIP_H   = 28;
    private static final int TAB_MIN_W     = 90;
    private static final int TAB_MAX_W     = 140;
    private static final int BUTTON_H      = 20;

    private final Screen parent;
    private final List<MaredSettingsTab> tabs = MaredSettingsTabs.createAll();
    private int activeIndex = 0;

    private final MaredSettings.Snapshot snapshot;
    private final SettingsContext ctx;

    private final boolean initialShowSidebar;
    private final boolean initialShowRightPanel;
    private final boolean initialShowConsole;
    private final boolean initialShowStatusBar;
    private final boolean initialShowToolbar;

    private int[] tabX;
    private int[] tabW;

    private int saveX, saveY, saveW, saveH;
    private int cancelX, cancelY, cancelW, cancelH;

    public MaredSettingsScreen(Screen parent) {
        super(Component.literal("Mared Settings"));
        this.parent = parent;

        MaredSettings.load();

        this.initialShowSidebar    = MaredSettings.isLayoutShowSidebar();
        this.initialShowRightPanel = MaredSettings.isLayoutShowRightPanel();
        this.initialShowConsole    = MaredSettings.isLayoutShowConsole();
        this.initialShowStatusBar  = MaredSettings.isLayoutShowStatusBar();
        this.initialShowToolbar    = MaredSettings.isLayoutShowToolbar();

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

        // 0.3.1: свой UiContext для этого Screen.
        Minecraft mc = Minecraft.getInstance();
        int physW = mc.getWindow().getWidth();
        int physH = mc.getWindow().getHeight();
        int mcGuiScale = (int) mc.getWindow().getGuiScale();

        UiContext uiCtx = new UiContext(this.width, this.height,
            physW, physH, mcGuiScale, MaredThemeRegistry.active());
        MaredScale.bind(uiCtx);

        computeTabLayout();
        computeButtonLayout();

        if (activeIndex >= 0 && activeIndex < tabs.size()) {
            tabs.get(activeIndex).onOpen(ctx);
        }
    }

    @Override
    public void removed() {
        MaredScale.unbind();
    }

    private void computeTabLayout() {
        int n = tabs.size();
        tabX = new int[n];
        tabW = new int[n];

        int totalAvailable = this.width - MaredUi.px(40);
        int gap = MaredUi.px(4);

        int tw = Math.min(MaredUi.px(TAB_MAX_W),
            Math.max(MaredUi.px(TAB_MIN_W),
                (totalAvailable - gap * (n - 1)) / n));

        int totalW = n * tw + gap * (n - 1);
        int startX = (this.width - totalW) / 2;

        for (int i = 0; i < n; i++) {
            tabX[i] = startX + i * (tw + gap);
            tabW[i] = tw;
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
        saveW = MaredUi.px(100);
        saveH = saveH;
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

        boolean layoutChanged =
            initialShowSidebar    != MaredSettings.isLayoutShowSidebar()    ||
            initialShowRightPanel != MaredSettings.isLayoutShowRightPanel() ||
            initialShowConsole    != MaredSettings.isLayoutShowConsole()    ||
            initialShowStatusBar  != MaredSettings.isLayoutShowStatusBar()  ||
            initialShowToolbar    != MaredSettings.isLayoutShowToolbar();

        if (layoutChanged) {
            // Studio пересоздаётся — старый Session будет уничтожен при
            // removed() (isClosing остаётся false, но Studio закрывается
            // через setScreen и больше не вернётся).
            Minecraft.getInstance().setScreen(new MaredStudioScreen());
            return;
        }

        onClose();
    }

    private void onCancel() {
        onClose();
    }

    @Override
    public void onClose() {
        if (activeIndex >= 0 && activeIndex < tabs.size()) {
            tabs.get(activeIndex).onClose(ctx);
        }
        if (this.minecraft != null) this.minecraft.setScreen(parent);
    }

    private int contentY() {
        return MaredUi.px(TAB_STRIP_H) + MaredUi.px(4);
    }

    private int contentH() {
        return this.height - contentY() - MaredUi.px(BUTTON_H + 26);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, this.width, this.height, BG);

        renderTabStrip(g, mouseX, mouseY);

        int cy = contentY();
        int ch = contentH();
        if (cy < this.height && ch > 0) {
            MaredUi.rect(g, 0, cy - 1, this.width, this.height, PANEL_BG);
            tabs.get(activeIndex).render(g, this.font,
                MaredUi.px(20), cy, this.width - MaredUi.px(40), ch,
                ctx, mouseX, mouseY);
        }

        renderButtons(g, mouseX, mouseY);
    }

    private void renderTabStrip(GuiGraphics g, int mouseX, int mouseY) {
        int stripY = MaredUi.px(6);
        int stripH = MaredUi.px(TAB_STRIP_H);

        MaredUi.rect(g, 0, 0, this.width, stripY + stripH, PANEL_RAISED);
        MaredUi.rect(g, 0, stripY + stripH, this.width, stripY + stripH + 1,
            0x40FFFFFF);

        for (int i = 0; i < tabs.size(); i++) {
            MaredSettingsTab tab = tabs.get(i);
            int x = tabX[i];
            int w = tabW[i];
            int y = stripY;

            boolean active = i == activeIndex;
            boolean hover = MaredUi.hovered(mouseX, mouseY, x, y, w, stripH);

            int accent = tab.accentColor();

            if (active) {
                MaredUi.gradientV(g, x, y, x + w, y + stripH,
                    accent, MaredUi.darken(accent, 0.4f));
                MaredUi.rect(g, x, y + stripH - 2, x + w, y + stripH, accent);
            } else if (hover) {
                MaredUi.rect(g, x, y, x + w, y + stripH, 0xFF23232E);
            } else {
                MaredUi.rect(g, x, y, x + w, y + stripH, PANEL_BG);
            }

            MaredUi.outline(g, x, y, w, stripH,
                active ? accent : (hover ? MaredUi.lighten(accent, 0.1f)
                    : 0xFF333344));

            String label = tab.displayName();
            int textColor = active ? 0xFF000000 : (hover ? TEXT : TEXT_DIM);
            MaredUi.centered(g, this.font, label, x + w / 2,
                y + (stripH - 8) / 2, textColor);
        }

        MaredUi.text(g, this.font, MaredLang.get("mared.settings.title"),
            MaredUi.px(8), MaredUi.px(4) + (stripH - 8) / 2, TEXT_DIM);
    }

    private void renderButtons(GuiGraphics g, int mouseX, int mouseY) {
        boolean cancelHover = MaredUi.hovered(mouseX, mouseY,
            cancelX, cancelY, cancelW, cancelH);
        MaredWidgets.button3D(g, this.font, cancelX, cancelY,
            cancelW, cancelH,
            MaredLang.get("mared.settings.cancel"),
            cancelHover ? 0xFF663333 : 0xFF3A2020,
            0xFFFF5555, TEXT, cancelHover);

        boolean saveHover = MaredUi.hovered(mouseX, mouseY,
            saveX, saveY, saveW, saveH);
        MaredWidgets.button3D(g, this.font, saveX, saveY, saveW, saveH,
            MaredLang.get("mared.settings.save"),
            saveHover ? 0xFF336633 : 0xFF203A20,
            0xFF55FF88, TEXT, saveHover);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0) return super.mouseClicked(mx, my, button);

        int stripY = MaredUi.px(6);
        int stripH = MaredUi.px(TAB_STRIP_H);

        for (int i = 0; i < tabs.size(); i++) {
            if (MaredUi.hovered(mx, my, tabX[i], stripY, tabW[i], stripH)) {
                switchTab(i);
                return true;
            }
        }

        if (MaredUi.hovered(mx, my, cancelX, cancelY, cancelW, cancelH)) {
            onCancel();
            return true;
        }
        if (MaredUi.hovered(mx, my, saveX, saveY, saveW, saveH)) {
            onSave();
            return true;
        }

        if (my >= contentY() && my < contentY() + contentH()) {
            int cx = MaredUi.px(20);
            int cy = contentY();
            int cw = this.width - MaredUi.px(40);
            int ch = contentH();
            if (tabs.get(activeIndex).mouseClicked(mx, my, button,
                cx, cy, cw, ch, ctx)) {
                return true;
            }
        }

        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        if (my >= contentY() && my < contentY() + contentH()) {
            int cx = MaredUi.px(20);
            int cy = contentY();
            int cw = this.width - MaredUi.px(40);
            int ch = contentH();
            if (tabs.get(activeIndex).mouseScrolled(mx, my, dy,
                cx, cy, cw, ch, ctx)) {
                return true;
            }
        }
        return super.mouseScrolled(mx, my, dx, dy);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            onCancel();
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_LEFT) {
            switchTab(Math.max(0, activeIndex - 1));
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_RIGHT) {
            switchTab(Math.min(tabs.size() - 1, activeIndex + 1));
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_TAB && (modifiers & 2) != 0) {
            switchTab((activeIndex + 1) % tabs.size());
            return true;
        }

        if (tabs.get(activeIndex).keyPressed(keyCode, scanCode, modifiers, ctx)) {
            return true;
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char c, int modifiers) {
        if (tabs.get(activeIndex).charTyped(c, modifiers, ctx)) return true;
        return super.charTyped(c, modifiers);
    }

    @Override public boolean isPauseScreen() { return false; }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY,
                                 float partialTick) {}
}