package com.fixmer.mared.gui2.framework.core;

/**
 * Базовый компонент GUI2 MaRed.
 *
 * 0.3.0: расширенный контракт.
 *
 * Ключевые изменения по сравнению с 0.2.5c:
 *   - mouseClicked / mousePressed / mouseReleased / mouseDragged
 *     возвращают boolean — поглотил ли компонент событие.
 *   - Добавлены mouseScrolled / keyPressed / charTyped.
 *   - Добавлено состояние: visible / enabled / focused.
 *
 * Это позволяет MaredContainer останавливать propagation: если ребёнок
 * поглотил клик, родитель больше никого не опрашивает. Без этого
 * один клик обрабатывался сразу всеми детьми в области.
 */
public abstract class MaredComponent {

    /** Геометрия компонента. */
    protected MaredBounds bounds = new MaredBounds(0, 0, 0, 0);

    /** Флаг видимости — рендер и input игнорируются, если false. */
    protected boolean visible = true;

    /** Флаг активности — рендер идёт, input игнорируется, если false. */
    protected boolean enabled = true;

    /** Флаг фокуса — для полей ввода, редакторов. */
    protected boolean focused = false;

    // ============================================================
    //  Рендер
    // ============================================================

    /** Точка входа рендера. Не переопределять — только safeRender. */
    public final void render(MaredRenderContext context) {
        if (!visible) return;
        safeRender(context);
    }

    /** Реализация рендера в наследниках. */
    protected abstract void safeRender(MaredRenderContext context);

    // ============================================================
    //  Layout
    // ============================================================

    public void layout(MaredBounds bounds) {
        this.bounds = bounds;
    }

    public MaredBounds bounds() {
        return bounds;
    }

    // ============================================================
    //  Состояние
    // ============================================================

    public boolean isVisible() { return visible; }

    public void setVisible(boolean visible) { this.visible = visible; }

    public boolean isEnabled() { return enabled; }

    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public boolean isFocused() { return focused; }

    public void setFocused(boolean focused) { this.focused = focused; }

    /** Удобный шорткат. */
    public boolean contains(double mouseX, double mouseY) {
        return bounds.contains(mouseX, mouseY);
    }

    // ============================================================
    //  Input
    //
    //  Все методы возвращают boolean:
    //    true  — компонент обработал событие, propagation останавливается
    //    false — событие не обработано, родитель может опросить других
    //
    //  По умолчанию ничего не делает и возвращает false.
    //  Это безопасно: MaredContainer не пропустит клик дальше, если
    //  ребёнок не поглотил его — родитель попробует следующих.
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

    /**
     * Уведомление о движении мыши. Не поглощается — void.
     * Используется для hover-состояний.
     */
    public void mouseMoved(double mouseX, double mouseY) {
        // по умолчанию ничего
    }
}