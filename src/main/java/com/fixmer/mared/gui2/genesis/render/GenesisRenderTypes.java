package com.fixmer.mared.gui2.genesis.render;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;

import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;

/**
 * 1.5.38.0e: РµРґРёРЅС‹Р№ RenderType РґР»СЏ glow-СЌР»РµРјРµРЅС‚РѕРІ Genesis.
 * POSITION_COLOR, TRIANGLES. Р’СЃРµ glow-РІРµСЂС€РёРЅС‹ РєР°РґСЂР° - РѕРґРёРЅ batch.
 */
public final class GenesisRenderTypes {

    private GenesisRenderTypes() {}

    public static final RenderType GLOW = RenderType.create(
        "genesis_glow",
        DefaultVertexFormat.POSITION_COLOR,
        VertexFormat.Mode.TRIANGLES,
        8192,
        false,
        false,
        RenderType.CompositeState.builder()
            .setShaderState(new RenderStateShard.ShaderStateShard(
                GameRenderer::getPositionColorShader))
            .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
            .setCullState(RenderStateShard.NO_CULL)
            .setWriteMaskState(RenderStateShard.COLOR_WRITE)
            .createCompositeState(false)
    );
}