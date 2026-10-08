package com.fixmer.mared.gui2.welcome.screen;

import com.fixmer.mared.gui2.genesis.GenesisController;
import com.fixmer.mared.gui2.genesis.GenesisWorld;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import org.lwjgl.glfw.GLFW;

/**
 * Genesis: РґРѕР±Р°РІР»РµРЅ mouseScrolled в†’ zoom.
 */
public final class MaredWelcomeScreen extends Screen {

    private final GenesisWorld genesis;

    public MaredWelcomeScreen() {
        super(Component.literal("MaRed Welcome"));
        genesis = new GenesisWorld();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY,
                       float partialTick) {
        genesis.render(graphics, width, height, mouseX, mouseY);
    }

    @Override
    public void tick() {
        genesis.tick();
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        genesis.onMouseMoved(mouseX, mouseY, width, height);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0) genesis.onMousePressed(mx, my, width, height);
        return true;
    }

    @Override
    public boolean mouseDragged(double mx, double my,
                                int button, double dx, double dy) {
        if (button == 0) genesis.onMouseDragged(mx, my, width, height);
        return true;
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (button == 0) genesis.onMouseReleased(mx, my, width, height);
        return true;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double dx, double scrollY) {
        genesis.onMouseScrolled(scrollY);
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            if (genesis.controller().state() != GenesisController.State.IDLE) {
                genesis.onEscape();
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void removed() {
        try {
            genesis.controller().cancelFlight(genesis);
        } catch (Throwable ignored) {}
    }

    @Override
    public boolean isPauseScreen() { return false; }

    public GenesisWorld genesis() { return genesis; }
}