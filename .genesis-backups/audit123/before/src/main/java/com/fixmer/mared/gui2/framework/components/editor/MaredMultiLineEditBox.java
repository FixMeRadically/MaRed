package com.fixmer.mared.gui2.framework.components.editor;

import com.fixmer.mared.MaredLang;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

/**
 * Многострочный редактор кода.
 *
 * 0.3.1: Document/History/View/Controller разделены.
 * 0.3.2:
 *   - span-based rendering (EditorView.setSpanResolver).
 *   - updateWidgetNarration.
 *   - attachDocument() — переключение активного DocumentSession без
 *     пересоздания виджета (multi-document workspace).
 *
 * Терминология:
 *   "document bundle" = {EditorDocument, EditorHistory, EditorView}.
 *   Один MaredMultiLineEditBox может держать разные bundle'ы
 *   последовательно — при переключении таба.
 */
public class MaredMultiLineEditBox extends AbstractWidget {

    private EditorDocument document;
    private EditorHistory  history;
    private EditorView     view;
    private EditorController controller;

    private final Runnable onValueChanged;
    private final HostAdapter hostAdapter;

    private int accentColor;
    private boolean editable = true;

    public MaredMultiLineEditBox(int x, int y, int width, int height,
                                 int accentColor, Runnable onValueChanged) {
        super(x, y, width, height, Component.literal(""));
        this.accentColor = accentColor;
        this.onValueChanged = onValueChanged;
        this.setFocused(false);

        this.hostAdapter = new HostAdapter();

        // Пустой дефолтный bundle — виджет не падает, если ничего
        // не прикреплено, но и не рендерит текст.
        EditorDocument doc = new EditorDocument();
        EditorHistory hist = new EditorHistory();
        EditorView v = new EditorView();
        attachBundle(doc, hist, v);
    }

    // ============================================================
    //  Attach / detach
    // ============================================================

    /**
     * 0.3.2: прикрепить другой document bundle.
     * Работает как переключение активного документа: держит
     * undo/redo и scroll каждого документа.
     */
    public void attachDocument(EditorDocument doc,
                               EditorHistory hist,
                               EditorView view) {
        if (doc == null || hist == null || view == null) {
            // Отсоединиться — редактор пуст.
            EditorDocument emptyDoc = new EditorDocument();
            EditorHistory emptyHist = new EditorHistory();
            EditorView emptyView = new EditorView();
            attachBundle(emptyDoc, emptyHist, emptyView);
            return;
        }
        attachBundle(doc, hist, view);
    }

    private void attachBundle(EditorDocument doc,
                              EditorHistory hist,
                              EditorView view) {
        this.document = doc;
        this.history = hist;
        this.view = view;
        this.controller = new EditorController(
            hostAdapter, doc, hist, view, this::notifyChanged);
        view.resetBlink();
        notifyChanged();
    }

    // ============================================================
    //  Public API
    // ============================================================

    public void setAccentColor(int color) { this.accentColor = color; }

    public void setSpanResolver(SpanResolver r) {
        if (view != null) view.setSpanResolver(r);
    }

    public SpanResolver spanResolver() {
        return view != null ? view.spanResolver() : SpanResolver.plain();
    }

    public void setEditable(boolean editable) {
        this.editable = editable;
        if (!editable) {
            this.setFocused(false);
            if (document != null) document.clearSelection();
        }
    }

    public boolean isEditable() { return editable; }

    public String getValue() {
        return document != null ? document.getValue() : "";
    }

    /** 0.3.2: доступ к текущему EditorDocument — для Workspace. */
    public EditorDocument document() { return document; }

    public void setValue(String text) {
        if (document == null) return;
        history.clear();
        document.setValue(text);
        if (view != null) view.resetScroll();
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
            } catch (Throwable t) { return ""; }
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
        if (controller == null) return false;
        return controller.charTyped(codePoint, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (controller == null) return false;
        return controller.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void onClick(double mx, double my) {
        if (controller == null) return;
        controller.onMouseClicked(mx, my);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button,
                                double dx, double dy) {
        if (controller == null) return false;
        return controller.onMouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (controller != null) controller.onMouseReleased(mx, my, button);
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my,
                                 double deltaX, double deltaY) {
        if (controller == null) return false;
        if (!this.isMouseOver(mx, my)) return false;
        return controller.onMouseScrolled(deltaY);
    }

    // ============================================================
    //  Render
    // ============================================================

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY,
                                float partialTick) {
        if (document == null || view == null) return;
        Font font = Minecraft.getInstance().font;
        view.render(graphics, font,
            getX(), getY(), width, height,
            document, isFocused(), editable, accentColor);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput narration) {
        narration.add(NarratedElementType.TITLE,
            Component.literal(MaredLang.get("mared.narration.editor.title")));

        if (document == null) {
            narration.add(NarratedElementType.HINT,
                Component.literal(MaredLang.get("mared.narration.editor.empty")));
            return;
        }

        int lineCount = document.lineCount();
        boolean empty = lineCount == 1 && document.lineLength(0) == 0;

        if (empty) {
            narration.add(NarratedElementType.HINT,
                Component.literal(MaredLang.get("mared.narration.editor.empty")));
            return;
        }

        int line = document.cursorLine() + 1;
        int col  = document.cursorCol() + 1;

        if (document.hasSelection()) {
            int selLen = document.getSelectedText().length();
            narration.add(NarratedElementType.HINT,
                Component.literal(MaredLang.format(
                    "mared.narration.editor.position_selection",
                    lineCount, line, col, selLen)));
        } else {
            narration.add(NarratedElementType.HINT,
                Component.literal(MaredLang.format(
                    "mared.narration.editor.position",
                    lineCount, line, col)));
        }
    }

    @Override
    public boolean isMouseOver(double mx, double my) {
        return mx >= getX() && mx < getX() + width
            && my >= getY() && my < getY() + height;
    }
}