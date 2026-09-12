package com.fixmer.mared;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
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
    private static final int COLOR_ITEM_HOVER   = 0xFF2E2E3E;
    private static final int COLOR_ITEM_SEL     = 0xFF55336B;
    private static final int COLOR_ITEM_NORMAL  = 0xFF252530;

    private static final int COLOR_SCRIPTS = 0xFFFF55FF;
    private static final int COLOR_NPC     = 0xFFFFAA00;
    private static final int COLOR_EVENTS  = 0xFF55AAFF;
    private static final int COLOR_QUESTS  = 0xFF55FF55;
    private static final int COLOR_STATES  = 0xFF55FFFF;
    private static final int COLOR_DANGER  = 0xFFFF4444;

    // ---- Размеры ----
    private static final int TAB_WIDTH       = 30;
    private static final int TAB_HEIGHT      = 30;
    private static final int TOOLBAR_HEIGHT  = 22;
    private static final int PADDING         = 6;
    private static final int LOG_HEIGHT      = 110;
    private static final int STRIP_WIDTH     = 36;
    private static final int FULL_WIDTH      = 200;
    private static final int BTN_SIZE        = 18;
    private static final int ITEM_HEIGHT     = 14;
    private static final int SCROLLBAR_WIDTH = 6;
    private static final int LOG_LINE_HEIGHT = 10;
    private static final int LOG_HEADER      = 16;
    /** Отступ между редактором и логом. Для панели Scripts не применяется. */
    private static final int GAP_EDITOR_LOG  = 6;

    private enum SidebarState { CLOSED, STRIP, FULL }
    private enum ScrollTarget { NONE, SCRIPTS, LOG }

    private SidebarState sidebarState = SidebarState.CLOSED;
    private ScrollTarget scrollTarget = ScrollTarget.NONE;
    private String openTab = "scripts";

    private final List<String> scriptNames = new ArrayList<>();
    private final List<String> logLines = new ArrayList<>();
    private String selectedScript = null;

    private int scrollOffset = 0;
    private int logScrollOffset = 0;

    private final List<AbstractWidget> toolButtons = new ArrayList<>();

    public MaredScriptsScreen() {
        super(Component.literal("Mared Editor"));
    }

    @Override
    protected void init() {
        super.init();
        toolButtons.clear();
        reloadScripts();

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
            rightEdge, y, delW, 18, Component.literal("Delete"), COLOR_DANGER, this::onDelete)));
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

        updateToolButtonsVisibility();
    }

    private void updateToolButtonsVisibility() {
        boolean scripts = "scripts".equals(openTab);
        for (int i = 0; i < toolButtons.size(); i++) {
            AbstractWidget w = toolButtons.get(i);
            boolean isClose = i == 0;
            w.visible = isClose || scripts;
            w.active = isClose || scripts;
        }
    }

    private void reloadScripts() {
        scriptNames.clear();
        scriptNames.addAll(MaredScriptStorage.listScripts());
        if (selectedScript != null && !scriptNames.contains(selectedScript)) {
            selectedScript = null;
        }
        scrollOffset = 0;
    }

    // ---- Действия ----

    private void onNew() {
        Minecraft.getInstance().setScreen(new MaredNameDialog(this, "Новый скрипт", name -> {
            if (MaredScriptStorage.createScript(name)) {
                addLog("Создан скрипт: " + name);
                selectedScript = name;
                reloadScripts();
            } else {
                addLog("Не удалось создать " + name);
            }
        }));
    }

    private void onImport() { addLog("Import: пока не реализовано"); }

    private void onDelete() {
        if (selectedScript == null) {
            addLog("Не выбран скрипт для удаления");
            return;
        }
        String name = selectedScript;
        Minecraft.getInstance().setScreen(new MaredConfirmDialog(
            this,
            "Удалить скрипт?",
            "Скрипт \"" + name + "\" будет удалён.",
            () -> {
                if (MaredScriptStorage.deleteScript(name)) {
                    addLog("Удалён скрипт: " + name);
                    selectedScript = null;
                    reloadScripts();
                } else {
                    addLog("Не удалось удалить: " + name);
                }
            }
        ));
    }

    private void onSave() {
        if (selectedScript == null) {
            addLog("Не выбран скрипт для сохранения");
            return;
        }
        MaredScriptStorage.writeScript(selectedScript, "// TODO\n");
        addLog("Сохранён: " + selectedScript);
    }

    private void onRun() {
        if (selectedScript == null) {
            addLog("Не выбран скрипт для запуска");
            return;
        }
        addLog("Запуск: " + selectedScript + " (движок в разработке)");
    }

    private void addLog(String line) {
        logLines.add(line);
        if (logLines.size() > 500) logLines.remove(0);
        logScrollOffset = 0;
    }

    // ---- Геометрия ----

    private int logTop() {
        return this.height - LOG_HEIGHT;
    }

    /** Границы панели Scripts: от верха до лога без зазора. */
    private int sidebarBottom() {
        return logTop();
    }

    /** Границы редактора: чуть выше лога. */
    private int editorBottom() {
        return logTop() - GAP_EDITOR_LOG;
    }

    // ---- Мышь ----

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (mx < TAB_WIDTH) {
            int idx = (int) (my / TAB_HEIGHT);
            String[] tabs = {"scripts", "npc", "events", "quests", "states"};
            if (idx >= 0 && idx < tabs.length) {
                String clicked = tabs[idx];
                if (clicked.equals(openTab) && sidebarWidth() > 0) {
                    sidebarState = SidebarState.CLOSED;
                } else {
                    openTab = clicked;
                    sidebarState = "scripts".equals(clicked) ? SidebarState.STRIP : SidebarState.CLOSED;
                }
                updateToolButtonsVisibility();
                scrollTarget = ScrollTarget.NONE;
                return true;
            }
        }

        if (sidebarState == SidebarState.STRIP && "scripts".equals(openTab)) {
            int ax = TAB_WIDTH + (STRIP_WIDTH - BTN_SIZE) / 2;
            int ay = arrowY();
            if (mx >= ax && mx < ax + BTN_SIZE && my >= ay && my < ay + BTN_SIZE) {
                sidebarState = SidebarState.FULL;
                return true;
            }
        }

        if (sidebarState == SidebarState.FULL && "scripts".equals(openTab)) {
            int sx = TAB_WIDTH;
            int ay = arrowY();
            int arrowX = sx + FULL_WIDTH - BTN_SIZE - 4;
            int plusX = arrowX - BTN_SIZE - 4;

            if (mx >= arrowX && mx < arrowX + BTN_SIZE && my >= ay && my < ay + BTN_SIZE) {
                sidebarState = SidebarState.STRIP;
                return true;
            }
            if (mx >= plusX && mx < plusX + BTN_SIZE && my >= ay && my < ay + BTN_SIZE) {
                onNew();
                return true;
            }

            int listX = sx + 6;
            int listW = FULL_WIDTH - 12 - SCROLLBAR_WIDTH;
            int listY = ay + BTN_SIZE + 8;
            int listH = sidebarBottom() - listY - 6;

            if (mx >= listX && mx < listX + listW && my >= listY && my < listY + listH) {
                scrollTarget = ScrollTarget.SCRIPTS;
                int idx = ((int) my - listY) / ITEM_HEIGHT + scrollOffset;
                if (idx >= 0 && idx < scriptNames.size()) {
                    selectedScript = scriptNames.get(idx);
                    addLog("Выбран: " + selectedScript);
                }
                return true;
            }
        }

        if (my >= logTop()) {
            scrollTarget = ScrollTarget.LOG;
            return true;
        }

        scrollTarget = ScrollTarget.NONE;
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double deltaX, double deltaY) {
        if (my >= logTop()) {
            scrollTarget = ScrollTarget.LOG;
            int visibleLines = Math.max(1, (LOG_HEIGHT - LOG_HEADER - PADDING) / LOG_LINE_HEIGHT);
            int maxScroll = Math.max(0, logLines.size() - visibleLines);
            if (deltaY < 0) logScrollOffset = Math.max(0, logScrollOffset - 1);
            else if (deltaY > 0) logScrollOffset = Math.min(maxScroll, logScrollOffset + 1);
            return true;
        }

        if (sidebarState == SidebarState.FULL && "scripts".equals(openTab)) {
            int listX = TAB_WIDTH + 6;
            int listW = FULL_WIDTH - 12 - SCROLLBAR_WIDTH;
            int listY = arrowY() + BTN_SIZE + 8;
            int listH = sidebarBottom() - listY - 6;

            if (mx >= listX && mx < listX + listW && my >= listY && my < listY + listH) {
                scrollTarget = ScrollTarget.SCRIPTS;
                int maxVisible = listH / ITEM_HEIGHT;
                int maxScroll = Math.max(0, scriptNames.size() - maxVisible);
                if (deltaY < 0) scrollOffset = Math.min(maxScroll, scrollOffset + 1);
                else if (deltaY > 0) scrollOffset = Math.max(0, scrollOffset - 1);
                return true;
            }
        }

        if (scrollTarget == ScrollTarget.LOG) {
            int visibleLines = Math.max(1, (LOG_HEIGHT - LOG_HEADER - PADDING) / LOG_LINE_HEIGHT);
            int maxScroll = Math.max(0, logLines.size() - visibleLines);
            if (deltaY < 0) logScrollOffset = Math.max(0, logScrollOffset - 1);
            else if (deltaY > 0) logScrollOffset = Math.min(maxScroll, logScrollOffset + 1);
            return true;
        }
        if (scrollTarget == ScrollTarget.SCRIPTS && sidebarState == SidebarState.FULL && "scripts".equals(openTab)) {
            int listY = arrowY() + BTN_SIZE + 8;
            int listH = sidebarBottom() - listY - 6;
            int maxVisible = listH / ITEM_HEIGHT;
            int maxScroll = Math.max(0, scriptNames.size() - maxVisible);
            if (deltaY < 0) scrollOffset = Math.min(maxScroll, scrollOffset + 1);
            else if (deltaY > 0) scrollOffset = Math.max(0, scrollOffset - 1);
            return true;
        }

        return super.mouseScrolled(mx, my, deltaX, deltaY);
    }

    // ---- Рендер ----

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, this.width, this.height, COLOR_BACKGROUND);

        int sbW = sidebarWidth();
        int logTop = logTop();

        // 1. Лог — внизу, во всю ширину.
        drawLog(graphics, mouseX, mouseY, logTop);

        // 2. Панель Scripts — от верха до лога (без зазора).
        if (sbW > 0) {
            int bgColor = (sidebarState == SidebarState.STRIP) ? COLOR_PANEL_STRIP : COLOR_PANEL;
            graphics.fill(TAB_WIDTH, 0, TAB_WIDTH + sbW, sidebarBottom(), bgColor);
        }

        // 3. Тулбар — фон.
        int toolbarX = TAB_WIDTH + sbW;
        graphics.fill(toolbarX, 0, this.width, TOOLBAR_HEIGHT + PADDING, COLOR_TOOLBAR_BG);

        // 4. Полоса вкладок.
        graphics.fill(0, 0, TAB_WIDTH, this.height, COLOR_TAB_STRIP);
        drawTabs(graphics, mouseX, mouseY);

        // 5. Содержимое панели Scripts.
        if (sidebarState == SidebarState.STRIP && "scripts".equals(openTab)) {
            drawArrow(graphics, TAB_WIDTH + (STRIP_WIDTH - BTN_SIZE) / 2, arrowY(), "►", mouseX, mouseY);
        }
        if (sidebarState == SidebarState.FULL && "scripts".equals(openTab)) {
            drawFullSidebar(graphics, mouseX, mouseY);
        }

        // 6. Редактор — с зазором до лога.
        int editorX = TAB_WIDTH + sbW;
        int editorY = TOOLBAR_HEIGHT + PADDING + 1;
        int editorW = this.width - editorX;
        int editorH = editorBottom() - editorY;

        if ("scripts".equals(openTab)) {
            graphics.fill(editorX, editorY, editorX + editorW, editorY + editorH, COLOR_EDITOR_BG);
            graphics.renderOutline(editorX, editorY, editorW, editorH, COLOR_SCRIPTS);

            String title = (selectedScript == null) ? "Редактор (скрипт не выбран)" : "Редактор: " + selectedScript;
            graphics.drawString(this.font, title, editorX + 6, editorY + 6, COLOR_TEXT, true);

            if (selectedScript == null) {
                graphics.drawString(this.font, "Выберите скрипт слева или создайте новый",
                    editorX + 6, editorY + 24, COLOR_TEXT_DIM, true);
            } else {
                String content = MaredScriptStorage.readScript(selectedScript);
                String[] lines = content.split("\n");
                int maxLines = (editorH - 30) / 10;
                for (int i = 0; i < Math.min(lines.length, maxLines); i++) {
                    graphics.drawString(this.font, lines[i],
                        editorX + 6, editorY + 24 + i * 10, COLOR_TEXT_DIM, true);
                }
            }
        } else {
            graphics.fill(editorX, editorY, editorX + editorW, editorY + editorH, COLOR_EDITOR_BG);
            graphics.renderOutline(editorX, editorY, editorW, editorH, COLOR_TEXT_DIM);
            graphics.drawString(this.font, "Раздел «" + tabTitle(openTab) + "» в разработке",
                editorX + 8, editorY + 8, COLOR_TEXT_DIM, true);
        }

        // 7. Тонкий разделитель над логом — во всю ширину.
        int dividerY = logTop - 1;
        graphics.fill(0, dividerY, this.width, dividerY + 1, COLOR_EVENTS);

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void drawLog(GuiGraphics graphics, int mouseX, int mouseY, int logTop) {
        graphics.fill(0, logTop, this.width, this.height, COLOR_LOG_BG);
        graphics.drawString(this.font, "Лог:", PADDING, logTop + 4, COLOR_EVENTS, true);

        int visibleLines = Math.max(1, (LOG_HEIGHT - LOG_HEADER - PADDING) / LOG_LINE_HEIGHT);
        int maxScroll = Math.max(0, logLines.size() - visibleLines);
        if (logScrollOffset > maxScroll) logScrollOffset = maxScroll;

        int end = logLines.size() - logScrollOffset;
        int start = Math.max(0, end - visibleLines);

        for (int i = start; i < end && i < logLines.size(); i++) {
            int lineY = logTop + LOG_HEADER + (i - start) * LOG_LINE_HEIGHT;
            graphics.drawString(this.font, logLines.get(i), PADDING + 4, lineY, COLOR_TEXT, true);
        }

        if (logLines.size() > visibleLines) {
            int trackX = this.width - SCROLLBAR_WIDTH - 2;
            int trackY = logTop + LOG_HEADER;
            int trackH = LOG_HEIGHT - LOG_HEADER - PADDING;
            graphics.fill(trackX, trackY, trackX + SCROLLBAR_WIDTH, trackY + trackH, 0xFF15151E);

            int thumbH = Math.max(8, trackH * visibleLines / logLines.size());
            int thumbY = trackY + (trackH - thumbH) * (maxScroll - logScrollOffset) / Math.max(1, maxScroll);
            graphics.fill(trackX, thumbY, trackX + SCROLLBAR_WIDTH, thumbY + thumbH, COLOR_EVENTS);
        }
    }

    private int arrowY() { return PADDING + 1; }

    private int sidebarWidth() {
        if (!"scripts".equals(openTab)) return 0;
        switch (sidebarState) {
            case STRIP: return STRIP_WIDTH;
            case FULL:  return FULL_WIDTH;
            default:    return 0;
        }
    }

    private void drawArrow(GuiGraphics graphics, int ax, int ay, String symbol, int mouseX, int mouseY) {
        boolean hover = mouseX >= ax && mouseX < ax + BTN_SIZE
            && mouseY >= ay && mouseY < ay + BTN_SIZE;
        graphics.fill(ax, ay, ax + BTN_SIZE, ay + BTN_SIZE, hover ? 0xFF3E3E42 : 0xFF2D2D2D);
        graphics.renderOutline(ax, ay, BTN_SIZE, BTN_SIZE, COLOR_SCRIPTS);
        graphics.drawString(this.font, symbol, ax + 5, ay + 5, COLOR_SCRIPTS, true);
    }

    private void drawFullSidebar(GuiGraphics graphics, int mouseX, int mouseY) {
        int sx = TAB_WIDTH;
        int sw = FULL_WIDTH;
        int ay = arrowY();

        graphics.drawString(this.font, tabTitle(openTab), sx + 8, ay + 5, COLOR_TEXT, true);

        int arrowX = sx + sw - BTN_SIZE - 4;
        int plusX = arrowX - BTN_SIZE - 4;

        boolean plusHover = mouseX >= plusX && mouseX < plusX + BTN_SIZE
            && mouseY >= ay && mouseY < ay + BTN_SIZE;
        graphics.fill(plusX, ay, plusX + BTN_SIZE, ay + BTN_SIZE, plusHover ? 0xFF3E3E42 : 0xFF2D2D2D);
        graphics.renderOutline(plusX, ay, BTN_SIZE, BTN_SIZE, COLOR_SCRIPTS);
        graphics.drawString(this.font, "+", plusX + 6, ay + 5, COLOR_SCRIPTS, true);

        boolean arrowHover = mouseX >= arrowX && mouseX < arrowX + BTN_SIZE
            && mouseY >= ay && mouseY < ay + BTN_SIZE;
        graphics.fill(arrowX, ay, arrowX + BTN_SIZE, ay + BTN_SIZE, arrowHover ? 0xFF3E3E42 : 0xFF2D2D2D);
        graphics.renderOutline(arrowX, ay, BTN_SIZE, BTN_SIZE, COLOR_SCRIPTS);
        graphics.drawString(this.font, "◄", arrowX + 5, ay + 5, COLOR_SCRIPTS, true);

        int listX = sx + 6;
        int listW = sw - 12 - SCROLLBAR_WIDTH;
        int listY = ay + BTN_SIZE + 8;
        int listH = sidebarBottom() - listY - 6;
        int maxVisible = listH / ITEM_HEIGHT;

        if (scriptNames.isEmpty()) {
            graphics.drawString(this.font, "Нет скриптов. Нажмите +", listX + 4, listY + 4, COLOR_TEXT_DIM, true);
            return;
        }

        int maxScroll = Math.max(0, scriptNames.size() - maxVisible);
        if (scrollOffset > maxScroll) scrollOffset = maxScroll;

        for (int i = 0; i < maxVisible; i++) {
            int realIdx = i + scrollOffset;
            if (realIdx >= scriptNames.size()) break;

            String name = scriptNames.get(realIdx);
            int itemY = listY + i * ITEM_HEIGHT;
            boolean hovered = mouseX >= listX && mouseX < listX + listW
                && mouseY >= itemY && mouseY < itemY + ITEM_HEIGHT - 2;
            boolean selected = name.equals(selectedScript);

            int bg = selected ? COLOR_ITEM_SEL : (hovered ? COLOR_ITEM_HOVER : COLOR_ITEM_NORMAL);
            graphics.fill(listX, itemY, listX + listW, itemY + ITEM_HEIGHT - 2, bg);

            if (selected) {
                graphics.fill(listX, itemY, listX + 2, itemY + ITEM_HEIGHT - 2, COLOR_SCRIPTS);
            }

            graphics.drawString(this.font, name, listX + 4, itemY + 2,
                selected ? COLOR_SCRIPTS : COLOR_TEXT, true);
        }

        if (scriptNames.size() > maxVisible) {
            int trackX = sx + sw - SCROLLBAR_WIDTH - 2;
            int trackY = listY;
            int trackH = listH;
            graphics.fill(trackX, trackY, trackX + SCROLLBAR_WIDTH, trackY + trackH, 0xFF15151E);

            int thumbH = Math.max(10, trackH * maxVisible / scriptNames.size());
            int thumbY = trackY + (trackH - thumbH) * scrollOffset / Math.max(1, maxScroll);
            graphics.fill(trackX, thumbY, trackX + SCROLLBAR_WIDTH, thumbY + thumbH, COLOR_SCRIPTS);
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