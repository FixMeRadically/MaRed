package com.fixmer.mared.mixin;

import com.fixmer.mared.gui2.development.DevelopmentButton129;
import com.fixmer.mared.gui2.framework.screen.MaredScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Button;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Presentation only: retain original Button instances, callbacks, narration and input handling. */
@Mixin(AbstractButton.class)
public abstract class MaredButtonStyle129 {
    @Inject(method = "renderWidget", at = @At("HEAD"), cancellable = true)
    private void mared$themeButton(GuiGraphics g, int mx, int my, float dt, CallbackInfo ci) {
        if (!(Minecraft.getInstance().screen instanceof MaredScreen)) return;
        if (!((Object)this instanceof Button button) || button instanceof DevelopmentButton129) return;
        DevelopmentButton129.draw(g, button.getX(), button.getY(), button.getWidth(), button.getHeight(),
            button.getMessage(), button.active, button.isHoveredOrFocused(), false);
        ci.cancel();
    }
}
