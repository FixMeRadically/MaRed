package com.fixmer.mared.gui.common;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import com.fixmer.mared.Mared;

public class MaredGuiScreen extends Screen {

    public MaredGuiScreen() {
        super(Component.literal("Mared Editor"));
    }

    @Override
    protected void init() {
        super.init();
        int buttonWidth = 100;
        int buttonHeight = 20;
        this.addRenderableWidget(
            Button.builder(
                Component.literal("Закрыть"),
                b -> this.onClose()
            )
            .bounds(
                (this.width - buttonWidth) / 2,
                this.height / 2 + 40,
                buttonWidth,
                buttonHeight
            )
            .build()
        );
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 40, 0xFFFFFF);
        graphics.drawCenteredString(
            this.font,
            Component.literal("Здесь будет редактор сценариев"),
            this.width / 2,
            this.height / 2 - 20,
            0xAAAAAA
        );
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}