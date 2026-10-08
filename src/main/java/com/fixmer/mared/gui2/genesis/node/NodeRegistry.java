package com.fixmer.mared.gui2.genesis.node;

import java.util.ArrayList;
import java.util.List;

/**
 * Genesis: СЃРѕР»РЅРµС‡РЅР°СЏ СЃРёСЃС‚РµРјР°.
 *
 * Р Р°Р·РЅС‹Рµ СЂР°РґРёСѓСЃС‹ (200..500), СЂР°Р·РЅС‹Рµ СЃРєРѕСЂРѕСЃС‚Рё (Р·Р°РєРѕРЅ РљРµРїР»РµСЂР° вЂ”
 * inner Р±С‹СЃС‚СЂРµРµ), СЂР°Р·РЅС‹Рµ РїР»РѕСЃРєРѕСЃС‚Рё (Р»С‘РіРєРёРµ РЅР°РєР»РѕРЅС‹) вЂ” РІРёР·СѓР°Р»СЊРЅРѕ
 * СѓР·Р»С‹ РЅРµ СЃР»РёРІР°СЋС‚СЃСЏ, Сѓ РєР°Р¶РґРѕРіРѕ СЃРІРѕСЏ В«РґРѕСЂРѕР¶РєР°В».
 *
 * РЎРєРѕСЂРѕСЃС‚СЊ: speed = base * (R_base / R)^1.5, base=0.10 РїСЂРё R=200.
 */
public final class NodeRegistry {

    private NodeRegistry() {}

    private static final float CORE_RADIUS = 60f;

    private static final class Spec {
        final GenesisNodeData.NodeType type;
        final float orbitRadius, orbitSpeed, orbitPhase;
        final float tiltX, tiltZ, radius, glow;
        final int color;
        final Shape3D shape;

        Spec(GenesisNodeData.NodeType type,
             float orbitRadius, float orbitSpeed, float orbitPhase,
             float tiltX, float tiltZ,
             float radius, float glow, int color, Shape3D shape) {
            this.type = type;
            this.orbitRadius = orbitRadius;
            this.orbitSpeed = orbitSpeed;
            this.orbitPhase = orbitPhase;
            this.tiltX = tiltX;
            this.tiltZ = tiltZ;
            this.radius = radius;
            this.glow = glow;
            this.color = color;
            this.shape = shape;
        }
    }

    private static final float PI3 = (float)Math.PI / 3f;

    private static final Spec[] SPECS = new Spec[]{
        // Tools вЂ” РјР°Р»РµРЅСЊРєРёР№, Р±Р»РёР·РєРѕ, Р±С‹СЃС‚СЂРѕ
        new Spec(GenesisNodeData.NodeType.TOOLS,
                 230f,  0.082f, 0.0f * PI3,
                 0.15f,  0.00f, 22f, 0.55f, 0xFFFFFF55, Shape3D.TETRA),

        // Logic вЂ” РЅР°РєР»РѕРЅРЅР°СЏ РїР»РѕСЃРєРѕСЃС‚СЊ
        new Spec(GenesisNodeData.NodeType.LOGIC,
                 300f,  0.056f, 1.0f * PI3,
                 0.08f,  0.10f, 34f, 0.70f, 0xFFAA55FF, Shape3D.OCTA),

        // Content вЂ” РїР»РѕСЃРєР°СЏ
        new Spec(GenesisNodeData.NodeType.CONTENT,
                 380f,  0.041f, 2.0f * PI3,
                 0.05f,  0.00f, 32f, 0.65f, 0xFF55FFAA, Shape3D.CUBE),

        // World вЂ” РїСЂРёРїРѕРґРЅСЏС‚Р°СЏ
        new Spec(GenesisNodeData.NodeType.WORLD,
                 470f,  0.032f, 3.0f * PI3,
                 0.18f,  0.00f, 34f, 0.55f, 0xFF55AAFF, Shape3D.BIPYRAMID),

        // Scenarios вЂ” С€РёСЂРѕРєР°СЏ РЅР°РєР»РѕРЅРЅР°СЏ
        new Spec(GenesisNodeData.NodeType.SCENARIOS,
                 560f,  0.026f, 4.0f * PI3,
                 0.10f, -0.12f, 30f, 0.60f, 0xFFFF5577, Shape3D.PYRAMID),

        // Resources вЂ” СЃР°РјР°СЏ РґР°Р»СЊРЅСЏСЏ
        new Spec(GenesisNodeData.NodeType.RESOURCES,
                 650f,  0.021f, 5.0f * PI3,
                 0.12f,  0.00f, 28f, 0.50f, 0xFFFFAA55, Shape3D.HEXPRISM),
    };

    public static List<GenesisNodeData> buildAll() {
        List<GenesisNodeData> nodes = new ArrayList<>(7);
        nodes.add(new GenesisNodeData(
            GenesisNodeData.NodeType.CORE,
            0f, 0f, 0f, 0f, 0f,
            CORE_RADIUS,
            0xFFFFFFFF,
            Shape3D.CORE_SPECIAL,
            1.00f
        ));
        for (Spec s : SPECS) {
            nodes.add(new GenesisNodeData(
                s.type,
                s.orbitRadius, s.orbitSpeed, s.orbitPhase,
                s.tiltX, s.tiltZ,
                s.radius, s.color, s.shape, s.glow
            ));
        }
        return nodes;
    }
}