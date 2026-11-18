package com.fixmer.mared.gui2.genesis.node;

public record GenesisNodeData(
    NodeType type,
    float orbitRadius,
    float orbitSpeed,
    float orbitPhase,
    float orbitTiltX,
    float orbitTiltZ,
    float radius,
    int defaultColor,
    Shape3D shape,
    float glow
) {

    public int defaultColor(){return NodeRegistry.color152(type,defaultColor);}
    public enum NodeType {
        CORE, MODS, DATAPACKS, MAPS, SERVERS, SHADERS, MODPACKS, RESOURCEPACKS
    }
}