package com.fixmer.mared.mixin;

import com.fixmer.genesis.technology.editor.EmbeddedClip155;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Vanilla scissors use screen coordinates and ignore PoseStack translations, including in EditBox. */
@Mixin(GuiGraphics.class)
public abstract class EmbeddedScissor155 {
    @ModifyVariable(method="enableScissor(IIII)V",at=@At("HEAD"),argsOnly=true,ordinal=0,require=1)
    private int mared$left155(int value){return EmbeddedClip155.x(value);}
    @ModifyVariable(method="enableScissor(IIII)V",at=@At("HEAD"),argsOnly=true,ordinal=1,require=1)
    private int mared$top155(int value){return EmbeddedClip155.y(value);}
    @ModifyVariable(method="enableScissor(IIII)V",at=@At("HEAD"),argsOnly=true,ordinal=2,require=1)
    private int mared$right155(int value){return EmbeddedClip155.x(value);}
    @ModifyVariable(method="enableScissor(IIII)V",at=@At("HEAD"),argsOnly=true,ordinal=3,require=1)
    private int mared$bottom155(int value){return EmbeddedClip155.y(value);}
    @ModifyVariable(method="containsPointInScissor(II)Z",at=@At("HEAD"),argsOnly=true,ordinal=0,require=1)
    private int mared$mouseX155(int value){return EmbeddedClip155.x(value);}
    @ModifyVariable(method="containsPointInScissor(II)Z",at=@At("HEAD"),argsOnly=true,ordinal=1,require=1)
    private int mared$mouseY155(int value){return EmbeddedClip155.y(value);}
}
