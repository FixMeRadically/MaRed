package com.fixmer.mared.gui2.settings;

import com.fixmer.mared.gui2.settings.SettingsContext;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Один таб в MaredSettingsScreen.
 *
 * 0.3.0 (Stage B5): перенос legacy gui.settings.tabs.MaredSettingsTab в gui2.
 */
public interface MaredSettingsTab {

    String id();

    String displayName();

    int accentColor();

    default void onOpen(SettingsContext ctx) {}

    default void onClose(SettingsContext ctx) {}

    void render(GuiGraphics g, Font font, int x, int y, int w, int h,
                SettingsContext ctx, int mouseX, int mouseY);

    default boolean mouseClicked(double mx, double my, int button,
                                 int x, int y, int w, int h,
                                 SettingsContext ctx) {
        return false;
    }

    default boolean mouseScrolled(double mx, double my, double delta,
                                  int x, int y, int w, int h,
                                  SettingsContext ctx) {
        return false;
    }

    default boolean keyPressed(int keyCode, int scanCode, int modifiers,
                               SettingsContext ctx) {
        return false;
    }

    default boolean charTyped(char c, int modifiers, SettingsContext ctx) {
        return false;
    }
}