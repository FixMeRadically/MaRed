package com.fixmer.mared.gui2.welcome.hero;

public final class WelcomeThemeController {

    private int currentColor = 0xFF101018;
    private int targetColor  = 0xFF101018;

    public void setColor(int color) {
        targetColor = 0xFF000000 | (color & 0x00FFFFFF);
    }

    public void tick() {
        currentColor = interpolate(currentColor, targetColor, 0.08f);
    }

    private int interpolate(int a, int b, float t) {
        int aa = (a >>> 24) & 0xFF, ar = (a >> 16) & 0xFF,
            ag = (a >>  8) & 0xFF, ab = a & 0xFF;
        int ba = (b >>> 24) & 0xFF, br = (b >> 16) & 0xFF,
            bg = (b >>  8) & 0xFF, bb = b & 0xFF;
        int ra = (int) (aa + (ba - aa) * t);
        int rr = (int) (ar + (br - ar) * t);
        int rg = (int) (ag + (bg - ag) * t);
        int rb = (int) (ab + (bb - ab) * t);
        return (ra << 24) | (rr << 16) | (rg << 8) | rb;
    }

    public int color() {
        return currentColor;
    }
}