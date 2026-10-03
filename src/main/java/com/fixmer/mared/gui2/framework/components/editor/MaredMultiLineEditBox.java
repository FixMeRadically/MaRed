package com.fixmer.mared.gui2.framework.components.editor;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

/**
 * Многострочный редактор кода.
 *
 * 0.3.1: Document/History/View/Controller разделены.
 *        History работает на дельтах, не на снапшотах.
 */
public class MaredMultiLineEditBox extends AbstractWidget {

    private final EditorDocument document = new EditorDocument();
    private final EditorHistory  history  = new EditorHistory();
    private final EditorView     view     = new EditorView();
    private final EditorController controller;

    private final Runnable onValueChanged;
    private int accentColor;
    private boolean editable = true;

    public MaredMultiLineEditBox(int x, int y, int width, int height,
                                 int accentColor, Runnable onValueChanged) {
        super(x, y, width, height, Component.literal(""));
        this.accentColor = accentColor;
        this.onValueChanged = onValueChanged;
        this.setFocused(false);

        this.controller = new EditorController(
            new HostAdapter(),
            document, history, view,
            this::notifyChanged
        );
    }

    public void setAccentColor(int color) { this.accentColor = color; }

    public void setEditable(boolean editable) {
        this.editable = editable;
        if (!editable) {
            this.setFocused(false);
            document.clearSelection();
        }
    }

    public boolean isEditable() { return editable; }

    public String getValue() { return document.getValue(); }

    public void setValue(String text) {
        history.clear();
        document.setValue(text);
        view.resetScroll();
        notifyChanged();
    }

    private void notifyChanged() {
        if (onValueChanged != null) onValueChanged.run();
    }

    // ============================================================
    //  Adapter
    // ============================================================

    private final class HostAdapter implements EditorController.EditorHost {
        @Override public int x() { return getX(); }
        @Override public int y() { return getY(); }
        @Override public int w() { return width; }
        @Override public int h() { return height; }
        @Override public boolean focused() { return isFocused(); }
        @Override public void requestFocus() { setFocused(true); }
        @Override public boolean editable() { return editable; }

        @Override
        public String clipboardGet() {
            try {
                return Minecraft.getInstance().keyboardHandler.getClipboard();
            } catch (Throwable t) {
                return "";
            }
        }

        @Override
        public void clipboardSet(String s) {
            try {
                Minecraft.getInstance().keyboardHandler.setClipboard(s);
            } catch (Throwable ignored) {}
        }
    }

    // ============================================================
    //  Input
    // ============================================================

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        return controller.charTyped(codePoint, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return controller.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void onClick(double mx, double my) {
        controller.onMouseClicked(mx, my);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button,
                                double dx, double dy) {
        return controller.onMouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        controller.onMouseReleased(mx, my, button);
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my,
                                 double deltaX, double deltaY) {
        if (!this.isMouseOver(mx, my)) return false;
        return controller.onMouseScrolled(deltaY);
    }

    // ============================================================
    //  Render
    // ============================================================

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY,
                                float partialTick) {
        Font font = Minecraft.getInstance().font;
        view.render(graphics, font,
            getX(), getY(), width, height,
            document, isFocused(), editable, accentColor);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput narration) {}

    @Override
    public boolean isMouseOver(double mx, double my) {
        return mx >= getX() && mx < getX() + width
            && my >= getY() && my < getY() + height;
    }
}