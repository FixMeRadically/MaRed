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
    private static java.util.Map<String,Integer> palette152=java.util.Map.of();
    public static void palette152(java.util.Map<String,Integer> colors){palette152=java.util.Map.copyOf(colors);}
    public static int color152(GenesisNodeData.NodeType type,int fallback){var rgb=palette152.get(type.name().toLowerCase(java.util.Locale.ROOT));return rgb==null?fallback:0xff000000|rgb;}

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

    private static final Spec[] SPECS = buildSpecs();
    private static Spec[] buildSpecs(){
        var directions=com.fixmer.genesis.technology.creation.CreationCatalog145.Direction.values();
        Shape3D[] shapes={Shape3D.OCTA,Shape3D.CUBE,Shape3D.BIPYRAMID,Shape3D.TETRA,Shape3D.PYRAMID,Shape3D.HEXPRISM,Shape3D.CUBE};
        Spec[] specs=new Spec[directions.length];
        for(int i=0;i<directions.length;i++){
            float radius=230f+i*65f;
            specs[i]=new Spec(GenesisNodeData.NodeType.valueOf(directions[i].name()),radius,
                .082f*(float)Math.pow(230f/radius,1.5),i*(float)(Math.PI*2/directions.length),
                .06f+(i%3)*.04f,i%2==0?.06f:-.06f,26f+(i%3)*3, .60f,directions[i].color,shapes[i]);
        }
        return specs;
    }

    public static List<GenesisNodeData> buildAll() {
        List<GenesisNodeData> nodes = new ArrayList<>(SPECS.length+1);
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