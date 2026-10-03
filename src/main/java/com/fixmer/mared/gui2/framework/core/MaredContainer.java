package com.fixmer.mared.gui2.framework.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Контейнер компонентов.
 *
 * 0.3.1 (FocusManager): mouseClicked → requestFocus, если focusable.
 * 0.3.1 (PointerCaptureManager):
 *   - mouseDragged сначала смотрит pointer capture. Если кто-то держит
 *     pointer — направляет drag только ему, независимо от позиции.
 *   - mouseReleased — то же самое, после чего capture освобождается.
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
                if (c.focusable()) c.requestFocus();
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
        // 0.3.1: если pointer захвачен для этой кнопки — идём только owner'у.
        MaredComponent captured = pointerOwner(button);
        if (captured != null) {
            boolean handled = captured.mouseReleased(mouseX, mouseY, button);
            captured.releasePointer();
            return handled;
        }

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
        // 0.3.1: capture — только owner.
        MaredComponent captured = pointerOwner(button);
        if (captured != null) {
            return captured.mouseDragged(mouseX, mouseY, button, dragX, dragY);
        }

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
        for (int i = children.size() - 1; i >= 0; i--) {
            MaredComponent c = children.get(i);
            if (!c.isVisible() || !c.isEnabled() || !c.isFocused()) continue;
            if (c.keyPressed(keyCode, scanCode, modifiers)) return true;
        }
        for (int i = children.size() - 1; i >= 0; i--) {
            MaredComponent c = children.get(i);
            if (!c.isVisible() || !c.isEnabled() || c.isFocused()) continue;
            if (c.keyPressed(keyCode, scanCode, modifiers)) return true;
        }
        return false;
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        for (int i = children.size() - 1; i >= 0; i--) {
            MaredComponent c = children.get(i);
            if (!c.isVisible() || !c.isEnabled() || !c.isFocused()) continue;
            if (c.charTyped(codePoint, modifiers)) return true;
        }
        for (int i = children.size() - 1; i >= 0; i--) {
            MaredComponent c = children.get(i);
            if (!c.isVisible() || !c.isEnabled() || c.isFocused()) continue;
            if (c.charTyped(codePoint, modifiers)) return true;
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