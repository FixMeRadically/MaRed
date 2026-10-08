package com.fixmer.mared.gui2.welcome.render.environment.core;

public final class CorePrototypeShape {

    private CorePrototypeShape() {}

    public static final float[][] VERTICES = {
        { 0f,  1f,  0f },
        { 1f,  0f,  0f },
        { 0f,  0f,  1f },
        {-1f,  0f,  0f },
        { 0f,  0f, -1f },
        { 0f, -1f,  0f },
    };

    public static final int[][] EDGES = {
        {0, 1}, {0, 2}, {0, 3}, {0, 4},
        {1, 2}, {2, 3}, {3, 4}, {4, 1},
        {5, 1}, {5, 2}, {5, 3}, {5, 4},
    };
}