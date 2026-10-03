package com.fixmer.mared.gui2.framework.core;

/**
 * Базовый компонент GUI2 MaRed.
 *
 * 0.3.1 (FocusManager):
 *   - focused больше не локальное boolean-поле. Делегирует в
 *     FocusManager.
 *   - focusable(), onFocusGained()/onFocusLost().
 *
 * 0.3.1 (PointerCaptureManager):
 *   - Компонент может захватить pointer на время drag через
 *     capturePointer(button). До mouseReleased все события идут
 *     только ему.
 *   - Освобождение — автоматическое в MaredContainer/Screen после
 *     mouseReleased, или явное через releasePointer().
 */
public abstract class MaredComponent {

    protected MaredBounds bounds = new MaredBounds(0, 0, 0, 0);

    protected boolean visible = true;
    protected boolean enabled = true;

    private FocusManager focusManager;
    private PointerCaptureManager pointerManager;

    // ============================================================
    //  Attach (см. FocusManager.attachTo / PointerCaptureManager.attachTo)
    // ============================================================

    public void attachFocusManager(FocusManager fm) {
        this.focusManager = fm;
    }

    public void attachPointerManager(PointerCaptureManager pm) {
        this.pointerManager = pm;
    }

    // ============================================================
    //  Focus
    // ============================================================

    public boolean isFocused() {
        return focusManager != null && focusManager.isFocused(this);
    }

    public void requestFocus() {
        if (focusManager != null) focusManager.request(this);
    }

    public void clearFocus() {
        if (focusManager != null) focusManager.clear(this);
    }

    /** @deprecated используйте requestFocus()/clearFocus(). */
    @Deprecated
    public void setFocused(boolean focused) {
        if (focused) requestFocus();
        else clearFocus();
    }

    public boolean focusable() { return false; }

    public void onFocusGained() {}
    public void onFocusLost() {}

    // ============================================================
    //  Pointer capture
    // ============================================================

    /**
     * Захватить pointer для кнопки button.
     * Обычно вызывается в mouseClicked/mousePressed при старте drag.
     */
    public void capturePointer(int button) {
        if (pointerManager != null) pointerManager.capture(this, button);
    }

    /** Освободить pointer, если он удерживается этим компонентом. */
    public void releasePointer() {
        if (pointerManager != null) pointerManager.release(this);
    }

    /** Удерживает ли этот компонент pointer. */
    public boolean hasPointerCapture() {
        return pointerManager != null && pointerManager.isCapturedBy(this);
    }

    public boolean hasPointerCapture(int button) {
        return pointerManager != null
            && pointerManager.isCapturedBy(this)
            && pointerManager.button() == button;
    }

    /** Кто сейчас удерживает pointer для указанной кнопки, или null. */
    protected MaredComponent pointerOwner(int button) {
        if (pointerManager == null) return null;
        if (!pointerManager.isCapturedForButton(button)) return null;
        return pointerManager.owner();
    }

    /** Кто сейчас удерживает pointer (для любой кнопки), или null. */
    protected MaredComponent pointerOwner() {
        return pointerManager != null ? pointerManager.owner() : null;
    }

    // ============================================================
    //  Рендер
    // ============================================================

    public final void render(MaredRenderContext context) {
        if (!visible) return;
        safeRender(context);
    }

    protected abstract void safeRender(MaredRenderContext context);

    // ============================================================
    //  Layout / состояние
    // ============================================================

    public void layout(MaredBounds bounds) { this.bounds = bounds; }
    public MaredBounds bounds() { return bounds; }

    public boolean isVisible() { return visible; }
    public void setVisible(boolean visible) { this.visible = visible; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public boolean contains(double mouseX, double mouseY) {
        return bounds.contains(mouseX, mouseY);
    }

    // ============================================================
    //  Input
    // ============================================================

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return false;
    }
    public boolean mousePressed(double mouseX, double mouseY, int button) {
        return false;
    }
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        return false;
    }
    public boolean mouseDragged(double mouseX, double mouseY, int button,
                                double dragX, double dragY) {
        return false;
    }
    public boolean mouseScrolled(double mouseX, double mouseY,
                                 double scrollX, double scrollY) {
        return false;
    }
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return false;
    }
    public boolean charTyped(char codePoint, int modifiers) {
        return false;
    }
    public void mouseMoved(double mouseX, double mouseY) {}
}