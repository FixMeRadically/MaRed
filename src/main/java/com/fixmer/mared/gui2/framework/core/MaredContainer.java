package com.fixmer.mared.gui2.framework.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Контейнер компонентов.
 *
 * 0.3.0: правильное propagation input.
 *
 * Ключевое изменение — обратный порядок обхода (top-first, как z-order)
 * и остановка на первом компоненте, который вернул true.
 *
 * Это фиксит старый баг, когда один клик обрабатывался всеми детьми
 * в области, а не только верхним.
 */
public class MaredContainer extends MaredComponent {

    protected final List<MaredComponent> children = new ArrayList<>();

    // ============================================================
    //  Управление детьми
    // ============================================================

    public void add(MaredComponent component) {
        if (component != null && !children.contains(component)) {
            children.add(component);
        }
    }

    public void remove(MaredComponent component) {
        children.remove(component);
    }

    public void clear() {
        children.clear();
    }

    public List<MaredComponent> children() {
        return Collections.unmodifiableList(children);
    }

    public int size() {
        return children.size();
    }

    // ============================================================
    //  Рендер
    // ============================================================

    @Override
    protected void safeRender(MaredRenderContext context) {
        int n = children.size();
        for (int i = 0; i < n; i++) {
            children.get(i).render(context);
        }
    }

    // ============================================================
    //  Input
    // ============================================================

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        for (int i = children.size() - 1; i >= 0; i--) {
            MaredComponent c = children.get(i);
            if (!c.isVisible() || !c.isEnabled()) continue;
            if (!c.bounds().contains(mouseX, mouseY)) continue;
            if (c.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mousePressed(double mouseX, double mouseY, int button) {
        for (int i = children.size() - 1; i >= 0; i--) {
            MaredComponent c = children.get(i);
            if (!c.isVisible() || !c.isEnabled()) continue;
            if (!c.bounds().contains(mouseX, mouseY)) continue;
            if (c.mousePressed(mouseX, mouseY, button)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        for (int i = children.size() - 1; i >= 0; i--) {
            MaredComponent c = children.get(i);
            if (!c.isVisible() || !c.isEnabled()) continue;
            if (c.mouseReleased(mouseX, mouseY, button)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button,
                                double dragX, double dragY) {
        for (int i = children.size() - 1; i >= 0; i--) {
            MaredComponent c = children.get(i);
            if (!c.isVisible() || !c.isEnabled()) continue;
            if (c.mouseDragged(mouseX, mouseY, button, dragX, dragY)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY,
                                 double scrollX, double scrollY) {
        for (int i = children.size() - 1; i >= 0; i--) {
            MaredComponent c = children.get(i);
            if (!c.isVisible() || !c.isEnabled()) continue;
            if (!c.bounds().contains(mouseX, mouseY)) continue;
            if (c.mouseScrolled(mouseX, mouseY, scrollX, scrollY)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Сначала — focused-ребёнок (если есть).
        for (int i = children.size() - 1; i >= 0; i--) {
            MaredComponent c = children.get(i);
            if (!c.isVisible() || !c.isEnabled() || !c.isFocused()) continue;
            if (c.keyPressed(keyCode, scanCode, modifiers)) {
                return true;
            }
        }
        // Затем — все остальные.
        for (int i = children.size() - 1; i >= 0; i--) {
            MaredComponent c = children.get(i);
            if (!c.isVisible() || !c.isEnabled() || c.isFocused()) continue;
            if (c.keyPressed(keyCode, scanCode, modifiers)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        for (int i = children.size() - 1; i >= 0; i--) {
            MaredComponent c = children.get(i);
            if (!c.isVisible() || !c.isEnabled() || !c.isFocused()) continue;
            if (c.charTyped(codePoint, modifiers)) {
                return true;
            }
        }
        for (int i = children.size() - 1; i >= 0; i--) {
            MaredComponent c = children.get(i);
            if (!c.isVisible() || !c.isEnabled() || c.isFocused()) continue;
            if (c.charTyped(codePoint, modifiers)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        int n = children.size();
        for (int i = 0; i < n; i++) {
            MaredComponent c = children.get(i);
            if (!c.isVisible()) continue;
            c.mouseMoved(mouseX, mouseY);
        }
    }
}