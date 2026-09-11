package com.fixmer.mared;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class MaredScriptsScreen extends Screen {

    // ---- Палитра ----
    private static final int COLOR_BACKGROUND   = 0xFF0E0E14;
    private static final int COLOR_TAB_STRIP    = 0xFF181822;
    private static final int COLOR_TOOLBAR_BG   = 0xFF20202C;
    private static final int COLOR_PANEL        = 0xFF1E1E2A;
    private static final int COLOR_PANEL_STRIP  = 0xFF252530;
    private static final int COLOR_EDITOR_BG    = 0xFF141420;
    private static final int COLOR_LOG_BG       = 0xFF1A1A24;
    private static final int COLOR_TEXT         = 0xFFFFFFFF;
    private static final int COLOR_TEXT_DIM     = 0xFFAAAAAA;

    private static final int COLOR_SCRIPTS = 0xFFFF55FF;
    private static final int COLOR_NPC     = 0xFFFFAA00;
    private static final int COLOR_EVENTS  = 0xFF55AAFF;
    private static final int COLOR_QUESTS  = 0xFF55FF55;
    private static final int COLOR_STATES  = 0xFF55FFFF;

    // ---- Размеры ----
    private static final int TAB_WIDTH       = 30;
    private static final int TAB_HEIGHT      = 30;
    private static final int TOOLBAR_HEIGHT  = 22;
    private static final int PADDING         = 6;
    private static final int LOG_HEIGHT      = 110;
    private static final int STRIP_WIDTH     = 36;
    private static final int FULL_WIDTH      = 200;
    private static final int BTN_SIZE        = 18;

    private enum SidebarState { CLOSED, STRIP, FULL }

    private SidebarState sidebarState = SidebarState.CLOSED;
    private String openTab = "scripts";

    private final List<String> scriptNames = new ArrayList<>();
    private final List<String> logLines = new ArrayList<>();
    private String selectedScript = null;

    private final List<AbstractWidget> toolButtons = new ArrayList<>();

    public MaredScriptsScreen() {
        super(Component.literal("Mared Editor"));
    }

    private int sidebarWidth() {
        if (!"scripts".equals(openTab)) return 0;
        switch (sidebarState) {
            case STRIP: return STRIP_WIDTH;
            case FULL:  return FULL_WIDTH;
            default:    return 0;
        }
    }

    private boolean sidebarVisible() {
        return sidebarWidth() > 0;
    }

    /** Верхняя Y-координата стрелки. Всегда в шапке панели. */
    private int arrowY() {
        return PADDING + 1;
    }

    @Override
    protected void init() {
        super.init();
        toolButtons.clear();

        int y = PADDING + 1;
        int rightEdge = this.width - PADDING;

        int closeW = 50;
        rightEdge -= closeW;
        toolButtons.add(addRenderableWidget(new MaredCompactButton(
            rightEdge, y, closeW, 18, Component.literal("Close"), 0xFFFF5555, this::onClose)));
        rightEdge -= 4;

        int runW = 40;
        rightEdge -= runW;
        toolButtons.add(addRenderableWidget(new MaredCompactButton(
            rightEdge, y, runW, 18, Component.literal("Run"), 0xFF55FF55, this::onRun)));
        rightEdge -= 4;

        int saveW = 44;
        rightEdge -= saveW;
        toolButtons.add(addRenderableWidget(new MaredCompactButton(
            rightEdge, y, saveW, 18, Component.literal("Save"), 0xFF55AAFF, this::onSave)));
        rightEdge -= 4;

        int delW = 50;
        rightEdge -= delW;
        toolButtons.add(addRenderableWidget(new MaredCompactButton(
            rightEdge, y, delW, 18, Component.literal("Delete"), 0xFFAA4444, this::onDelete)));
        rightEdge -= 4;

        int impW = 52;
        rightEdge -= impW;
        toolButtons.add(addRenderableWidget(new MaredCompactButton(
            rightEdge, y, impW, 18, Component.literal("Import"), 0xFFFFAA00, this::onImport)));
        rightEdge -= 4;

        int newW = 40;
        rightEdge -= newW;
        toolButtons.add(addRenderableWidget(new MaredCompactButton(
            rightEdge, y, newW, 18, Component.literal("New"), 0xFFFF55FF, this::onNew)));
    }

    private void onNew()    { addLog("New"); }
    private void onImport() { addLog("Import"); }
    private void onDelete() { addLog("Delete"); }
    private void onSave()   { addLog("Save"); }
    private void onRun()    { addLog("Run"); }

    private void addLog(String line) {
        logLines.add(line);
        if (logLines.size() > 200) logLines.remove(0);
    }

    // ---- Мышь ----

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        // Клик по вкладкам.
        if (mx < TAB_WIDTH) {
            int idx = (int) (my / TAB_HEIGHT);
            String[] tabs = {"scripts", "npc", "events", "quests", "states"};
            if (idx >= 0 && idx < tabs.length) {
                String clicked = tabs[idx];
                if (clicked.equals(openTab) && sidebarVisible()) {
                    sidebarState = SidebarState.CLOSED;
                } else {
                    openTab = clicked;
                    sidebarState = "scripts".equals(clicked) ? SidebarState.STRIP : SidebarState.CLOSED;
                }
                return true;
            }
        }

        // ---- STRIP: стрелка ----
        if (sidebarState == SidebarState.STRIP && "scripts".equals(openTab)) {
            int ax = TAB_WIDTH + (STRIP_WIDTH - BTN_SIZE) / 2;
            int ay = arrowY();
            if (mx >= ax && mx < ax + BTN_SIZE && my >= ay && my < ay + BTN_SIZE) {
                sidebarState = SidebarState.FULL;
                return true;
            }
        }

        // ---- FULL: стрелка в шапке ----
        if (sidebarState == SidebarState.FULL && "scripts".equals(openTab)) {
            int sx = TAB_WIDTH;
            int arrowX = sx + FULL_WIDTH - BTN_SIZE - 4;
            int ay = arrowY();
            if (mx >= arrowX && mx < arrowX + BTN_SIZE && my >= ay && my < ay + BTN_SIZE) {
                sidebarState = SidebarState.STRIP;
                return true;
            }

            // Кнопка "+"
            int plusX = arrowX - BTN_SIZE - 4;
            if (mx >= plusX && mx < plusX + BTN_SIZE && my >= ay && my < ay + BTN_SIZE) {
                onNew();
                return true;
            }
        }

        return super.mouseClicked(mx, my, button);
    }

    // ---- Рендер ----

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // 1. Фон.
        graphics.fill(0, 0, this.width, this.height, COLOR_BACKGROUND);

        int sbW = sidebarWidth();

        // 2. Фон боковой панели.
        if (sbW > 0) {
            int bgColor = (sidebarState == SidebarState.STRIP) ? COLOR_PANEL_STRIP : COLOR_PANEL;
            graphics.fill(TAB_WIDTH, 0, TAB_WIDTH + sbW, this.height, bgColor);
        }

        // 3. Тулбар.
        int toolbarX = TAB_WIDTH + sbW;
        graphics.fill(toolbarX, 0, this.width, TOOLBAR_HEIGHT + PADDING, COLOR_TOOLBAR_BG);

        // 4. Полоса вкладок.
        graphics.fill(0, 0, TAB_WIDTH, this.height, COLOR_TAB_STRIP);
        drawTabs(graphics, mouseX, mouseY);

        // 5. STRIP — стрелка в шапке панели (не по центру экрана!).
        if (sidebarState == SidebarState.STRIP && "scripts".equals(openTab)) {
            drawArrow(graphics, TAB_WIDTH + (STRIP_WIDTH - BTN_SIZE) / 2, arrowY(), "►", mouseX, mouseY);
        }

        // 6. FULL — полная панель со стрелкой в шапке.
        if (sidebarState == SidebarState.FULL && "scripts".equals(openTab)) {
            drawFullSidebar(graphics, TAB_WIDTH, FULL_WIDTH, mouseX, mouseY);
        }

        // 7. Редактор — без разделителя сверху, до лога.
        int editorX = TAB_WIDTH + sbW;
        int editorY = TOOLBAR_HEIGHT + PADDING + 1;
        int editorW = this.width - editorX;
        int logTop = this.height - LOG_HEIGHT;
        int editorH = logTop - editorY;

        if ("scripts".equals(openTab)) {
            graphics.fill(editorX, editorY, editorX + editorW, editorY + editorH, COLOR_EDITOR_BG);
            graphics.renderOutline(editorX, editorY, editorW, editorH, COLOR_SCRIPTS);

            String title = (selectedScript == null) ? "Редактор (скрипт не выбран)" : "Редактор: " + selectedScript;
            graphics.drawString(this.font, title, editorX + 6, editorY + 6, COLOR_TEXT, true);

            if (selectedScript == null) {
                graphics.drawString(this.font, "Выберите скрипт слева или создайте новый",
                    editorX + 6, editorY + 24, COLOR_TEXT_DIM, true);
            }
        } else {
            graphics.fill(editorX, editorY, editorX + editorW, editorY + editorH, COLOR_EDITOR_BG);
            graphics.renderOutline(editorX, editorY, editorW, editorH, COLOR_TEXT_DIM);
            graphics.drawString(this.font, "Раздел «" + tabTitle(openTab) + "» в разработке",
                editorX + 8, editorY + 8, COLOR_TEXT_DIM, true);
        }

        // 8. Лог — единственная линия-разделитель (верхняя граница).
        graphics.fill(0, logTop, this.width, this.height, COLOR_LOG_BG);
        graphics.fill(0, logTop, this.width, logTop + 1, COLOR_EVENTS);
        graphics.drawString(this.font, "Лог:", PADDING, logTop + 4, COLOR_EVENTS, true);

        int lineHeight = 10;
        int headerHeight = 16;
        int visibleLines = Math.max(1, (LOG_HEIGHT - headerHeight - PADDING) / lineHeight);
        int start = Math.max(0, logLines.size() - visibleLines);
        for (int i = start; i < logLines.size(); i++) {
            graphics.drawString(this.font, logLines.get(i),
                PADDING + 4, logTop + headerHeight + (i - start) * lineHeight, COLOR_TEXT, true);
        }

        // 9. Кнопки — поверх.
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void drawArrow(GuiGraphics graphics, int ax, int ay, String symbol, int mouseX, int mouseY) {
        boolean hover = mouseX >= ax && mouseX < ax + BTN_SIZE
            && mouseY >= ay && mouseY < ay + BTN_SIZE;
        graphics.fill(ax, ay, ax + BTN_SIZE, ay + BTN_SIZE, hover ? 0xFF3E3E42 : 0xFF2D2D2D);
        graphics.renderOutline(ax, ay, BTN_SIZE, BTN_SIZE, COLOR_SCRIPTS);
        graphics.drawString(this.font, symbol, ax + 5, ay + 5, COLOR_SCRIPTS, true);
    }

    private void drawFullSidebar(GuiGraphics graphics, int sx, int sw, int mouseX, int mouseY) {
        // Заголовок.
        graphics.drawString(this.font, tabTitle(openTab), sx + 8, arrowY() + 5, COLOR_TEXT, true);

        // Кнопки "+" и "◄" — в шапке, одинаковые, справа.
        int arrowX = sx + sw - BTN_SIZE - 4;
        int plusX = arrowX - BTN_SIZE - 4;
        int ay = arrowY();

        // "+"
        boolean plusHover = mouseX >= plusX && mouseX < plusX + BTN_SIZE
            && mouseY >= ay && mouseY < ay + BTN_SIZE;
        graphics.fill(plusX, ay, plusX + BTN_SIZE, ay + BTN_SIZE,
            plusHover ? 0xFF3E3E42 : 0xFF2D2D2D);
        graphics.renderOutline(plusX, ay, BTN_SIZE, BTN_SIZE, COLOR_SCRIPTS);
        graphics.drawString(this.font, "+", plusX + 6, ay + 5, COLOR_SCRIPTS, true);

        // "◄"
        boolean arrowHover = mouseX >= arrowX && mouseX < arrowX + BTN_SIZE
            && mouseY >= ay && mouseY < ay + BTN_SIZE;
        graphics.fill(arrowX, ay, arrowX + BTN_SIZE, ay + BTN_SIZE,
            arrowHover ? 0xFF3E3E42 : 0xFF2D2D2D);
        graphics.renderOutline(arrowX, ay, BTN_SIZE, BTN_SIZE, COLOR_SCRIPTS);
        graphics.drawString(this.font, "◄", arrowX + 5, ay + 5, COLOR_SCRIPTS, true);

        // Список скриптов.
        int listY = arrowY() + BTN_SIZE + 8;
        int listX = sx + 6;
        int listW = sw - 12;

        if (scriptNames.isEmpty()) {
            graphics.drawString(this.font, "Нет скриптов", listX + 4, listY + 4, COLOR_TEXT_DIM, true);
        } else {
            int maxVisible = (this.height - LOG_HEIGHT - listY - 6) / 14;
            for (int i = 0; i < Math.min(scriptNames.size(), maxVisible); i++) {
                String name = scriptNames.get(i);
                boolean selected = name.equals(selectedScript);
                int itemY = listY + i * 14;
                int itemColor = selected ? 0xFF3E3E42 : 0xFF252530;
                graphics.fill(listX, itemY, listX + listW, itemY + 12, itemColor);
                graphics.drawString(this.font, name, listX + 4, itemY + 2,
                    selected ? COLOR_SCRIPTS : COLOR_TEXT, true);
            }
        }
    }

    private void drawTabs(GuiGraphics graphics, int mouseX, int mouseY) {
        String[] tabs = {"scripts", "npc", "events", "quests", "states"};
        int[] colors = {COLOR_SCRIPTS, COLOR_NPC, COLOR_EVENTS, COLOR_QUESTS, COLOR_STATES};
        String[] letters = {"S", "N", "E", "Q", "F"};

        for (int i = 0; i < tabs.length; i++) {
            int y = i * TAB_HEIGHT;
            boolean hovered = mouseX < TAB_WIDTH && mouseY >= y && mouseY < y + TAB_HEIGHT;
            boolean active = tabs[i].equals(openTab);

            int bg = active ? colors[i] : (hovered ? 0xFF3A3A4A : 0xFF252530);
            graphics.fill(2, y + 2, TAB_WIDTH - 2, y + TAB_HEIGHT - 2, bg);

            int textColor = active ? 0xFF000000 : colors[i];
            int tw = this.font.width(letters[i]);
            graphics.drawString(this.font, letters[i],
                (TAB_WIDTH - tw) / 2, y + (TAB_HEIGHT - 8) / 2 + 1, textColor, false);
        }
    }

    private String tabTitle(String key) {
        switch (key) {
            case "scripts": return "Scripts";
            case "npc":     return "NPC";
            case "events":  return "Events";
            case "quests":  return "Quests";
            case "states":  return "States";
            default:        return key;
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Пусто.
    }
}