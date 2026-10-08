package com.fixmer.mared.gui2.genesis.node;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public final class GenesisNode {

    public final GenesisNodeData data;

    private float worldX, worldY, worldZ;
    private float hover, focus;
    private float morph = 0.25f;
    private boolean mouseOver, focused;

    private float spinYaw, spinPitch;
    private final float pulsePhase;

    private static final String[] SUB_GLYPHS = {
        "{", "}", "if", "==", "->", "<-", "()", "[]", ":",
        "entity", "model", "value", "true", "0.3", "+", "world",
        "spawn", "tick", "block", "data", "json", "npc", "quest"
    };

    private static final int SUB_COUNT = 12;
    private static final float BOB_AMP = 12f;
    private static final float BOB_SPEED = 0.85f;

    private final List<GenesisSubParticle> subParticles;

    public GenesisNode(GenesisNodeData data) {
        this.data = data;
        this.subParticles = buildSubParticles(data.type());
        Random rng = new Random(data.type().ordinal() * 0x1234ABCDL);
        this.pulsePhase = rng.nextFloat() * (float)(Math.PI * 2);
    }

    private static List<GenesisSubParticle> buildSubParticles(
            GenesisNodeData.NodeType type) {
        Random rng = new Random(type.ordinal() * 0x9E3779B9L + 0xDEADBEEF);

        List<GenesisSubParticle> out = new ArrayList<>(SUB_COUNT);
        for (int i = 0; i < SUB_COUNT; i++) {
            out.add(new GenesisSubParticle(
                1.4f + rng.nextFloat() * 1.4f,
                (rng.nextFloat() - 0.5f) * 1.8f,
                rng.nextFloat() * (float)(Math.PI * 2),
                rng.nextFloat() * (float)Math.PI,
                rng.nextFloat() * (float)(Math.PI * 2),
                1.0f + rng.nextFloat() * 1.5f,
                0.10f + rng.nextFloat() * 0.10f,
                SUB_GLYPHS[rng.nextInt(SUB_GLYPHS.length)],
                rng.nextFloat()
            ));
        }
        return Collections.unmodifiableList(out);
    }

    public void tick(float orbitTimeSec, float dt, float reveal) {
        float angle = data.orbitPhase() + orbitTimeSec * data.orbitSpeed();
        float R = data.orbitRadius();

        float x = (float)(Math.cos(angle) * R);
        float z = (float)(Math.sin(angle) * R);
        float y = 0f;

        float cX = (float)Math.cos(data.orbitTiltX());
        float sX = (float)Math.sin(data.orbitTiltX());
        float y2 = y * cX - z * sX;
        float z2 = y * sX + z * cX;
        y = y2; z = z2;

        float cZ = (float)Math.cos(data.orbitTiltZ());
        float sZ = (float)Math.sin(data.orbitTiltZ());
        float x2 = x * cZ - y * sZ;
        float y3 = x * sZ + y * cZ;
        x = x2; y = y3;

        float bob = (float)Math.sin(orbitTimeSec * BOB_SPEED + pulsePhase) * BOB_AMP;

        worldX = x * reveal;
        worldY = y * reveal + bob;
        worldZ = z * reveal;

        // --- Framerate-independent lerps ---
        // Р Р°РЅСЊС€Рµ: hover += (t - hover) * 0.18f РїСЂРё 20 TPS.
        // РЎРµР№С‡Р°СЃ: k = dt * rate, РіРґРµ rate СЌРєРІРёРІР°Р»РµРЅС‚РЅР° СЃС‚Р°СЂРѕРјСѓ
        // Р·РЅР°С‡РµРЅРёСЋ, РЅРѕ СЂР°Р±РѕС‚Р°РµС‚ РїСЂРё Р»СЋР±РѕРј FPS.
        float kHover = Math.min(1f, dt * 3.6f);
        float kFocus = Math.min(1f, dt * 2.4f);
        float kMorph = Math.min(1f, dt * 0.8f);

        float hoverTarget = (mouseOver || focused) ? 1f : 0f;
        hover += (hoverTarget - hover) * kHover;

        float focusTarget = focused ? 1f : 0f;
        focus += (focusTarget - focus) * kFocus;

        float morphTarget = focused ? 1f : 0.25f;
        morph += (morphTarget - morph) * kMorph;

        for (GenesisSubParticle p : subParticles) p.tick(dt);
    }

    public void addSpin(float dYaw, float dPitch) {
        spinYaw += dYaw;
        spinPitch += dPitch;
        if (spinPitch > 1.3f) spinPitch = 1.3f;
        if (spinPitch < -1.3f) spinPitch = -1.3f;
    }

    public void resetSpin() { spinYaw = 0f; spinPitch = 0f; }

    public void setMouseOver(boolean v) { mouseOver = v; }
    public void setFocused(boolean v)   { focused = v; }

    public GenesisNodeData data() { return data; }
    public float worldX() { return worldX; }
    public float worldY() { return worldY; }
    public float worldZ() { return worldZ; }
    public float hover() { return hover; }
    public float focus() { return focus; }
    public float morph() { return morph; }
    public float spinYaw()   { return spinYaw; }
    public float spinPitch() { return spinPitch; }
    public float pulsePhase() { return pulsePhase; }
    public boolean isFocused()   { return focused; }
    public boolean isMouseOver() { return mouseOver; }
    public List<GenesisSubParticle> subParticles() { return subParticles; }

    public float currentGlow() {
        return data.glow() + hover * 0.30f + focus * 0.50f;
    }

    public float currentPulse(float timeSec) {
        return 1f + 0.05f * (float)Math.sin(timeSec * 1.4f + pulsePhase);
    }

    public float currentScale() {
        return 1f + hover * 0.10f + focus * 0.25f;
    }
}