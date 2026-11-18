package com.fixmer.mared.mixin;
import com.fixmer.mared.technology.server.setup.ServerReturnClient108;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
/** Only visual presentation of this reconnect's transport/loading screens is replaced. */
@Mixin(Screen.class)
public abstract class ServerReturnScreenMixin110 {
    @Inject(method="renderWithTooltip",at=@At("HEAD"),cancellable=true)
    private void genesis$returnPresentation(GuiGraphics g,int x,int y,float dt,CallbackInfo ci){
        if(ServerReturnClient108.renderReplacement((Screen)(Object)this,g,x,y,dt))ci.cancel();
    }
}
