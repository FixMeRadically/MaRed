package com.fixmer.mared.gui2.welcome.screen;

import com.fixmer.mared.MaredLang;
import com.fixmer.mared.welcome.MaredWelcomeStorage;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * Genesis: Р С›Р Р…Р В±Р С•РЎР‚Р Т‘Р С‘Р Р…Р С– MaRed.
 *
 * 8 РЎРѓРЎвЂљРЎР‚Р В°Р Р…Р С‘РЎвЂ :
 *   0. Р С›Р В±РЎвЂ°Р ВµР Вµ РІР‚вЂќ РЎвЂЎРЎвЂљР С• РЎвЂљР В°Р С”Р С•Р Вµ MaRed
 *   1-6. Р СџР С• Р С”Р В°РЎвЂљР ВµР С–Р С•РЎР‚Р С‘РЎРЏР С (Content/World/Logic/Resources/Tools/Scenarios)
 *   7. Genesis РІР‚вЂќ Р С”Р В°Р С” РЎР‚Р В°Р В±Р С•РЎвЂљР В°Р ВµРЎвЂљ Р Р…РЎвЂ№Р Р…Р ВµРЎв‚¬Р Р…РЎРЏРЎРЏ РЎРѓР С‘РЎРѓРЎвЂљР ВµР СР В° Р Р…Р В°Р Р†Р С‘Р С–Р В°РЎвЂ Р С‘Р С‘
 *
 * Р СњР ВµР в„–РЎвЂљРЎР‚Р В°Р В»РЎРЉР Р…Р В°РЎРЏ Р С—Р В°Р В»Р С‘РЎвЂљРЎР‚Р В°. Р СћР С•Р В»РЎРЉР С”Р С• РЎРѓРЎвЂљРЎР‚Р В°Р Р…Р С‘РЎвЂ РЎвЂ№ 1-6 Р С—Р С•Р Т‘Р С”РЎР‚Р В°РЎв‚¬Р ВµР Р…РЎвЂ№ accent'Р С•Р С
 * РЎРѓР С•Р С•РЎвЂљР Р†Р ВµРЎвЂљРЎРѓРЎвЂљР Р†РЎС“РЎР‹РЎвЂ°Р ВµР в„– Р С”Р В°РЎвЂљР ВµР С–Р С•РЎР‚Р С‘Р С‘.
 *
 * Р СџР С•Р С”Р В°Р В·РЎвЂ№Р Р†Р В°Р ВµРЎвЂљРЎРѓРЎРЏ Р С•Р Т‘Р С‘Р Р… РЎР‚Р В°Р В· (РЎвЂћР В»Р В°Р С– MaredWelcomeStorage.hasSeen()).
 */
public final class MaredOnboardingScreen extends Screen {

    private static final int PAGES = 8;

    // Р СњР ВµР в„–РЎвЂљРЎР‚Р В°Р В»РЎРЉР Р…РЎвЂ№Р Вµ РЎвЂ Р Р†Р ВµРЎвЂљР В°
    private static final int BG          = 0xFF0A0A12;
    private static final int BG_PANEL    = 0xFF14141E;
    private static final int BORDER      = 0xFF2A2A38;
    private static final int TEXT        = 0xFFE8E8F0;
    private static final int TEXT_DIM    = 0xFF9090A0;
    private static final int TEXT_FAINT  = 0xFF606070;
    private static final int ACCENT_NEUT = 0xFF7A7A90;

    // Р С™Р В°РЎвЂљР ВµР С–Р С•РЎР‚Р С‘Р в„–Р Р…РЎвЂ№Р Вµ accent'РЎвЂ№
    private static final int ACCENT_CONTENT   = 0xFF55FFAA;
    private static final int ACCENT_WORLD     = 0xFF55AAFF;
    private static final int ACCENT_LOGIC     = 0xFFAA55FF;
    private static final int ACCENT_RESOURCES = 0xFFFFAA55;
    private static final int ACCENT_TOOLS     = 0xFFFFFF55;
    private static final int ACCENT_SCENARIOS = 0xFFFF5577;

    // Layout
    private static final int PANEL_W_MAX = 720;
    private static final int PANEL_W_MIN = 480;
    private static final int PAD         = 44;
    private static final int TITLE_Y     = 40;
    private static final int SUBTITLE_Y  = 88;
    private static final int BULLET_Y    = 130;
    private static final int BULLET_LINE = 26;

    // Р С™Р Р…Р С•Р С—Р С”Р С‘
    private static final int BTN_W       = 140;
    private static final int BTN_H       = 30;
    private static final int BTN_MARGIN  = 40;
    private static final int BTN_GAP     = 12;

    private int index = 0;

    // Р С™РЎРЊРЎв‚¬ layout'Р В°
    private int panelX, panelY, panelW, panelH;
    private int nextX, nextY, prevX, prevY, skipX, skipY;

    public MaredOnboardingScreen() {
        super(Component.literal("MaRed"));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float pt) {
        // Р вЂ”Р В°Р Т‘Р Р…Р С‘Р С”
        g.fill(0, 0, width, height, BG);

        // Р СћР С•Р Р…Р С”Р В°РЎРЏ РЎРѓР ВµРЎвЂљР С”Р В° Р Р…Р ВµР в„–РЎвЂљРЎР‚Р В°Р В»РЎРЉР Р…Р В°РЎРЏ
        int cell = 32;
        int gridC = 0x0CFFFFFF;
        for (int x = 0; x < width; x += cell) g.fill(x, 0, x + 1, height, gridC);
        for (int y = 0; y < height; y += cell) g.fill(0, y, width, y + 1, gridC);

        // Р СџР В°Р Р…Р ВµР В»РЎРЉ
        panelW = Math.min(PANEL_W_MAX, Math.max(PANEL_W_MIN, width - 160));
        panelH = 480;
        panelX = (width - panelW) / 2;
        panelY = (height - panelH) / 2;

        g.fill(panelX + 6, panelY + 6, panelX + panelW + 6, panelY + panelH + 6,
               0x60000000);
        g.fill(panelX, panelY, panelX + panelW, panelY + panelH, BG_PANEL);
        g.renderOutline(panelX, panelY, panelW, panelH, BORDER);

        // Accent-Р С—Р С•Р В»Р С•РЎРѓР С”Р В° РЎРѓР Р†Р ВµРЎР‚РЎвЂ¦РЎС“ РІР‚вЂќ РЎвЂ Р Р†Р ВµРЎвЂљ РЎРѓРЎвЂљРЎР‚Р В°Р Р…Р С‘РЎвЂ РЎвЂ№
        int accent = accentFor(index);
        g.fill(panelX, panelY, panelX + panelW, panelY + 3, accent);

        // Р вЂ”Р В°Р С–Р С•Р В»Р С•Р Р†Р С•Р С” РЎРѓРЎвЂљРЎР‚Р В°Р Р…Р С‘РЎвЂ РЎвЂ№
        Font font = Minecraft.getInstance().font;
        int cx = panelX + panelW / 2;

        String title = titleFor(index);
        var pose = g.pose();
        pose.pushPose();
        pose.translate(cx, panelY + TITLE_Y, 0);
        pose.scale(2f, 2f, 1f);
        int tw = font.width(title);
        g.drawString(font, title, -tw / 2, 0, TEXT, false);
        pose.popPose();

        // Р СџР С•Р Т‘Р В·Р В°Р С–Р С•Р В»Р С•Р Р†Р С•Р С”
        String sub = subtitleFor(index);
        int sw = font.width(sub);
        g.drawString(font, sub, cx - sw / 2, panelY + SUBTITLE_Y,
                     TEXT_DIM, false);

        // Р В Р В°Р В·Р Т‘Р ВµР В»Р С‘РЎвЂљР ВµР В»РЎРЉ Р С—Р С•Р Т‘ Р С—Р С•Р Т‘Р В·Р В°Р С–Р С•Р В»Р С•Р Р†Р С”Р С•Р С
        g.fill(panelX + PAD, panelY + SUBTITLE_Y + 22,
               panelX + panelW - PAD, panelY + SUBTITLE_Y + 23,
               0x40FFFFFF);

        // Bullet points
        List<String> bullets = bulletsFor(index);
        int by = panelY + BULLET_Y;
        for (String bullet : bullets) {
            drawBullet(g, panelX + PAD, by, bullet, accent);
            by += BULLET_LINE;
        }

        // Р С™Р В°РЎвЂљР ВµР С–Р С•РЎР‚Р С‘Р в„–Р Р…РЎвЂ№Р в„– Р СР В°РЎР‚Р С”Р ВµРЎР‚ РІР‚вЂќ Р В±Р С•Р В»РЎРЉРЎв‚¬Р С•Р в„– Р В·Р Р…Р В°РЎвЂЎР С•Р С” Р Р† Р С—РЎР‚Р В°Р Р†Р С•Р С Р Р†Р ВµРЎР‚РЎвЂ¦Р Р…Р ВµР С РЎС“Р С–Р В»РЎС“
        if (index >= 1 && index <= 6) {
            drawCategoryGlyph(g, panelX + panelW - 80, panelY + TITLE_Y + 10,
                              accent);
        }

        // Р ВР Р…Р Т‘Р С‘Р С”Р В°РЎвЂљР С•РЎР‚ РЎРѓРЎвЂљРЎР‚Р В°Р Р…Р С‘РЎвЂ  (РЎвЂљР С•РЎвЂЎР С”Р С‘ РЎРѓР Р…Р С‘Р В·РЎС“)
        int dotsY = panelY + panelH - 60;
        int dotSpacing = 18;
        int totalDotsW = (PAGES - 1) * dotSpacing;
        int dotX0 = cx - totalDotsW / 2;
        for (int i = 0; i < PAGES; i++) {
            int dx = dotX0 + i * dotSpacing;
            boolean active = (i == index);
            int col = active ? accentFor(i) : 0x40FFFFFF;
            int r = active ? 4 : 2;
            g.fill(dx - r, dotsY - r, dx + r + 1, dotsY + r + 1, col);
        }

        // Р С™Р Р…Р С•Р С—Р С”Р С‘
        computeButtons();

        // Prev
        if (index > 0) {
            drawButton(g, font, prevX, prevY, "< " + MaredLang.get("mared.onboarding.back"),
                       0xFF2A2A38, TEXT_DIM, mouseX, mouseY);
        }

        // Skip РІР‚вЂќ Р’В«Р СџРЎР‚Р С•Р С—РЎС“РЎРѓРЎвЂљР С‘РЎвЂљРЎРЉР’В» (Р Р…Р В° Р Р†РЎРѓР ВµРЎвЂ¦ Р С”РЎР‚Р С•Р СР Вµ Р С—Р С•РЎРѓР В»Р ВµР Т‘Р Р…Р ВµР в„–)
        if (index < PAGES - 1) {
            drawButton(g, font, skipX, skipY, MaredLang.get("mared.onboarding.skip"),
                       0xFF1A1A24, TEXT_FAINT, mouseX, mouseY);
        }

        // Next / Enter
        String nextLabel = (index == PAGES - 1)
            ? MaredLang.get("mared.onboarding.enter")
            : MaredLang.get("mared.onboarding.next") + " >";
        int nextAccent = (index == PAGES - 1) ? ACCENT_NEUT : accent;
        drawButton(g, font, nextX, nextY, nextLabel,
                   darken(nextAccent, 0.7f), nextAccent, mouseX, mouseY);
    }

    // ============================================================
    //  Page content
    // ============================================================

    private static boolean ru() {
        String code = MaredLang.getCurrentCode();
        return code != null && code.startsWith("ru");
    }

    private String titleFor(int page) {
        boolean r = ru();
        return switch (page) {
            case 0 -> r ? "MaRed" : "MaRed";
            case 1 -> r ? "Content" : "Content";
            case 2 -> r ? "World" : "World";
            case 3 -> r ? "Logic" : "Logic";
            case 4 -> r ? "Resources" : "Resources";
            case 5 -> r ? "Tools" : "Tools";
            case 6 -> r ? "Scenarios" : "Scenarios";
            case 7 -> r ? "Genesis" : "Genesis";
            default -> "";
        };
    }

    private String subtitleFor(int page) {
        boolean r = ru();
        return switch (page) {
            case 0 -> r ? "Р СљР С•РЎвЂ°Р Р…РЎвЂ№Р в„– Р Т‘Р Р†Р С‘Р В¶Р С•Р С” РЎРѓР С”РЎР‚Р С‘Р С—РЎвЂљР С•Р Р† Р Т‘Р В»РЎРЏ Minecraft" : "Powerful script engine for Minecraft";
            case 1 -> r ? "Р СџРЎР‚Р ВµР Т‘Р СР ВµРЎвЂљРЎвЂ№, Р СР С•Р Т‘Р ВµР В»Р С‘, NPC, Р В°Р Р…Р С‘Р СР В°РЎвЂ Р С‘Р С‘" : "Items, models, NPCs, animations";
            case 2 -> r ? "Р СљР С‘РЎР‚, Р В±Р С‘Р С•Р СРЎвЂ№, РЎРѓРЎвЂљРЎР‚РЎС“Р С”РЎвЂљРЎС“РЎР‚РЎвЂ№, Р С‘Р В·Р СР ВµРЎР‚Р ВµР Р…Р С‘РЎРЏ" : "World, biomes, structures, dimensions";
            case 3 -> r ? "Р РЋР С”РЎР‚Р С‘Р С—РЎвЂљРЎвЂ№, РЎРѓР С•Р В±РЎвЂ№РЎвЂљР С‘РЎРЏ, РЎС“РЎРѓР В»Р С•Р Р†Р С‘РЎРЏ, Р Т‘Р ВµР в„–РЎРѓРЎвЂљР Р†Р С‘РЎРЏ" : "Scripts, events, conditions, actions";
            case 4 -> r ? "Р СћР ВµР С”РЎРѓРЎвЂљРЎС“РЎР‚РЎвЂ№, Р В·Р Р†РЎС“Р С”Р С‘, Р Т‘Р В°РЎвЂљР В°-РЎвЂћР В°Р в„–Р В»РЎвЂ№" : "Textures, sounds, data files";
            case 5 -> r ? "Р С›РЎвЂљР В»Р В°Р Т‘Р С”Р В°, Р С—РЎР‚Р С•РЎвЂћР С‘Р В»Р С‘РЎР‚Р С•Р Р†Р В°Р Р…Р С‘Р Вµ, РЎвЂљР ВµРЎРѓРЎвЂљРЎвЂ№" : "Debugging, profiling, tests";
            case 6 -> r ? "Р С™Р Р†Р ВµРЎРѓРЎвЂљРЎвЂ№, Р С”Р В°Р СР С—Р В°Р Р…Р С‘Р С‘, Р С”Р В°РЎвЂљРЎРѓРЎвЂ Р ВµР Р…РЎвЂ№" : "Quests, campaigns, cutscenes";
            case 7 -> r ? "Р С™Р В°Р С” РЎС“РЎРѓРЎвЂљРЎР‚Р С•Р ВµР Р…Р В° Р Р…Р В°Р Р†Р С‘Р С–Р В°РЎвЂ Р С‘РЎРЏ" : "How navigation works";
            default -> "";
        };
    }

    private List<String> bulletsFor(int page) {
        boolean r = ru();
        List<String> list = new ArrayList<>(5);
        switch (page) {
            case 0 -> {
                if (r) {
                    list.add("РІР‚Сћ Р вЂ™РЎРѓРЎвЂ РЎР‚Р ВµР Т‘Р В°Р С”РЎвЂљР С‘РЎР‚РЎС“Р ВµРЎвЂљРЎРѓРЎРЏ Р С—РЎР‚РЎРЏР СР С• Р Р† Р С‘Р С–РЎР‚Р Вµ РІР‚вЂќ Р В±Р ВµР В· Р Р†Р Р…Р ВµРЎв‚¬Р Р…Р С‘РЎвЂ¦ Р С—РЎР‚Р С•Р С–РЎР‚Р В°Р СР С");
                    list.add("РІР‚Сћ Р РЋР С”РЎР‚Р С‘Р С—РЎвЂљРЎвЂ№, Р С”Р С•Р Р…РЎвЂљР ВµР Р…РЎвЂљ, Р СР С‘РЎР‚, РЎР‚Р ВµРЎРѓРЎС“РЎР‚РЎРѓРЎвЂ№ РІР‚вЂќ Р Р† Р С•Р Т‘Р Р…Р С•Р С Р С•Р С”Р Р…Р Вµ");
                    list.add("РІР‚Сћ Р В Р В°Р В±Р С•РЎвЂљР В°Р ВµРЎвЂљ Р Р…Р В° Р С”Р В»Р С‘Р ВµР Р…РЎвЂљР Вµ: Р Р…Р С‘Р С”Р В°Р С”Р С‘РЎвЂ¦ Р С—Р В»Р В°Р С–Р С‘Р Р…Р С•Р Р† Р Р…Р В° РЎРѓР ВµРЎР‚Р Р†Р ВµРЎР‚Р Вµ");
                    list.add("РІР‚Сћ Р СљРЎС“Р В»РЎРЉРЎвЂљР С‘РЎРЏР В·РЎвЂ№РЎвЂЎР Р…РЎвЂ№Р в„– Р С‘Р Р…РЎвЂљР ВµРЎР‚РЎвЂћР ВµР в„–РЎРѓ (RU / EN)");
                } else {
                    list.add("РІР‚Сћ Everything is edited in-game РІР‚вЂќ no external tools");
                    list.add("РІР‚Сћ Scripts, content, world, resources РІР‚вЂќ in one window");
                    list.add("РІР‚Сћ Client-side: no server plugins required");
                    list.add("РІР‚Сћ Multi-language interface (RU / EN)");
                }
            }
            case 1 -> {
                if (r) {
                    list.add("РІР‚Сћ Р СџРЎР‚Р ВµР Т‘Р СР ВµРЎвЂљРЎвЂ№: РЎРѓР Р†Р С•Р С‘ id, РЎвЂљР ВµР С”РЎРѓРЎвЂљРЎС“РЎР‚РЎвЂ№, Р СР С•Р Т‘Р ВµР В»Р С‘, NBT-Р С”Р С•Р СР С—Р С•Р Р…Р ВµР Р…РЎвЂљРЎвЂ№");
                    list.add("РІР‚Сћ Р РЋРЎС“РЎвЂ°Р Р…Р С•РЎРѓРЎвЂљР С‘: Р СР С•Р В±РЎвЂ№, NPC, Р С—Р С•Р Р†Р ВµР Т‘Р ВµР Р…Р С‘Р Вµ, Р Т‘РЎР‚Р С•Р С—");
                    list.add("РІР‚Сћ Р вЂР В»Р С•Р С”Р С‘: РЎвЂћР С•РЎР‚Р СРЎвЂ№, РЎРѓР С•РЎРѓРЎвЂљР С•РЎРЏР Р…Р С‘РЎРЏ, Р С”Р В°РЎРѓРЎвЂљР С•Р СР Р…РЎвЂ№Р Вµ Р СР С•Р Т‘Р ВµР В»Р С‘");
                    list.add("РІР‚Сћ Р С’Р Р…Р С‘Р СР В°РЎвЂ Р С‘Р С‘: Р С—Р С•Р С”Р В°Р Т‘РЎР‚Р С•Р Р†РЎвЂ№Р Вµ Р С‘ Р С—РЎР‚Р С•РЎвЂ Р ВµР Т‘РЎС“РЎР‚Р Р…РЎвЂ№Р Вµ");
                } else {
                    list.add("РІР‚Сћ Items: custom ids, textures, models, NBT components");
                    list.add("РІР‚Сћ Entities: mobs, NPCs, behavior, drops");
                    list.add("РІР‚Сћ Blocks: shapes, states, custom models");
                    list.add("РІР‚Сћ Animations: frame-based and procedural");
                }
            }
            case 2 -> {
                if (r) {
                    list.add("РІР‚Сћ Р вЂњР ВµР Р…Р ВµРЎР‚Р В°РЎвЂ Р С‘РЎРЏ: РЎРѓР В»Р С•Р С‘, РЎв‚¬РЎС“Р С, Р В±Р С‘Р С•Р СРЎвЂ№, РЎвЂћР С‘РЎвЂЎР С‘");
                    list.add("РІР‚Сћ Р РЋРЎвЂљРЎР‚РЎС“Р С”РЎвЂљРЎС“РЎР‚РЎвЂ№: Р Т‘Р В°Р Р…Р В¶Р С‘, Р Т‘Р ВµРЎР‚Р ВµР Р†Р Р…Р С‘, Р С”РЎР‚Р ВµР С—Р С•РЎРѓРЎвЂљР С‘");
                    list.add("РІР‚Сћ Р ВР В·Р СР ВµРЎР‚Р ВµР Р…Р С‘РЎРЏ: РЎРѓР Р†Р С•Р С‘ dimension id");
                    list.add("РІР‚Сћ Р СџР С•Р С–Р С•Р Т‘Р В° Р С‘ Р Р†РЎР‚Р ВµР СРЎРЏ РЎРѓРЎС“РЎвЂљР С•Р С”");
                } else {
                    list.add("РІР‚Сћ Generation: layers, noise, biomes, features");
                    list.add("РІР‚Сћ Structures: dungeons, villages, strongholds");
                    list.add("РІР‚Сћ Dimensions: custom dimension ids");
                    list.add("РІР‚Сћ Weather and time-of-day control");
                }
            }
            case 3 -> {
                if (r) {
                    list.add("РІР‚Сћ Р РЋР С”РЎР‚Р С‘Р С—РЎвЂљР С•Р Р†РЎвЂ№Р в„– РЎРЏР В·РЎвЂ№Р С”: if / for / while / func / call");
                    list.add("РІР‚Сћ Р РЋР С•Р В±РЎвЂ№РЎвЂљР С‘РЎРЏ: on tick, on chat, on block_break, ...");
                    list.add("РІР‚Сћ Р вЂ™РЎвЂ№РЎР‚Р В°Р В¶Р ВµР Р…Р С‘РЎРЏ, Р С—Р ВµРЎР‚Р ВµР СР ВµР Р…Р Р…РЎвЂ№Р Вµ, Р СР В°РЎРѓРЎРѓР С‘Р Р†РЎвЂ№, Р СР ВµРЎвЂљР С•Р Т‘РЎвЂ№ РЎРѓРЎвЂљРЎР‚Р С•Р С”");
                    list.add("РІР‚Сћ Р С’Р Р†РЎвЂљР С•Р Т‘Р С•Р С—Р С•Р В»Р Р…Р ВµР Р…Р С‘Р Вµ Р С‘ Р С—Р С•Р Т‘РЎРѓР Р†Р ВµРЎвЂљР С”Р В° РЎРѓР С‘Р Р…РЎвЂљР В°Р С”РЎРѓР С‘РЎРѓР В°");
                } else {
                    list.add("РІР‚Сћ Script language: if / for / while / func / call");
                    list.add("РІР‚Сћ Events: on tick, on chat, on block_break, ...");
                    list.add("РІР‚Сћ Expressions, variables, arrays, string methods");
                    list.add("РІР‚Сћ Autocomplete and syntax highlighting");
                }
            }
            case 4 -> {
                if (r) {
                    list.add("РІР‚Сћ Р СћР ВµР С”РЎРѓРЎвЂљРЎС“РЎР‚РЎвЂ№: PNG / resource pack Р СР С•Р Т‘Р ВµР В»Р С‘");
                    list.add("РІР‚Сћ Р вЂ”Р Р†РЎС“Р С”Р С‘: ogg, ambient, step, hit");
                    list.add("РІР‚Сћ Р РЃРЎР‚Р С‘РЎвЂћРЎвЂљРЎвЂ№ Р С‘ Р С‘Р С”Р С•Р Р…Р С”Р С‘ Р С‘Р Р…РЎвЂљР ВµРЎР‚РЎвЂћР ВµР в„–РЎРѓР В°");
                    list.add("РІР‚Сћ Р вЂєР С•Р С”Р В°Р В»Р С‘Р В·Р В°РЎвЂ Р С‘Р С‘: ru_ru, en_us Р С‘ РЎРѓР Р†Р С•Р С‘");
                } else {
                    list.add("РІР‚Сћ Textures: PNG / resource pack models");
                    list.add("РІР‚Сћ Sounds: ogg, ambient, step, hit");
                    list.add("РІР‚Сћ Fonts and interface icons");
                    list.add("РІР‚Сћ Localizations: ru_ru, en_us and custom");
                }
            }
            case 5 -> {
                if (r) {
                    list.add("РІР‚Сћ Debug: РЎвЂљР С•РЎвЂЎР С”Р С‘ Р С•РЎРѓРЎвЂљР В°Р Р…Р С•Р Р†Р В° Р Р† РЎРѓР С”РЎР‚Р С‘Р С—РЎвЂљР В°РЎвЂ¦");
                    list.add("РІР‚Сћ Profiler: FPS, tick time, GC, memory");
                    list.add("РІР‚Сћ Р СћР ВµРЎРѓРЎвЂљРЎвЂ№: Р С—РЎР‚Р С•Р С–Р С•Р Р… РЎРѓРЎвЂ Р ВµР Р…Р В°РЎР‚Р С‘Р ВµР Р†, Р Р†Р В°Р В»Р С‘Р Т‘Р В°РЎвЂ Р С‘РЎРЏ Р Т‘Р В°Р Р…Р Р…РЎвЂ№РЎвЂ¦");
                    list.add("РІР‚Сћ Р вЂєР С•Р С–Р С‘ РЎРѓ РЎвЂћР С‘Р В»РЎРЉРЎвЂљРЎР‚Р В°Р СР С‘ Р С—Р С• РЎС“РЎР‚Р С•Р Р†Р Р…РЎР‹ Р С‘ Р С”Р В°РЎвЂљР ВµР С–Р С•РЎР‚Р С‘Р С‘");
                } else {
                    list.add("РІР‚Сћ Debug: breakpoints in scripts");
                    list.add("РІР‚Сћ Profiler: FPS, tick time, GC, memory");
                    list.add("РІР‚Сћ Tests: scenario runners, data validation");
                    list.add("РІР‚Сћ Logs with level and category filters");
                }
            }
            case 6 -> {
                if (r) {
                    list.add("РІР‚Сћ Р С™Р Р†Р ВµРЎРѓРЎвЂљРЎвЂ№: РЎвЂ Р ВµР В»Р С‘, РЎС“РЎРѓР В»Р С•Р Р†Р С‘РЎРЏ, Р Р…Р В°Р С–РЎР‚Р В°Р Т‘РЎвЂ№, Р Р†Р ВµРЎвЂљР Р†Р В»Р ВµР Р…Р С‘Р Вµ");
                    list.add("РІР‚Сћ Р С™Р В°Р СР С—Р В°Р Р…Р С‘Р С‘: Р С–Р В»Р В°Р Р†РЎвЂ№, Р С—РЎР‚Р С•Р С–РЎР‚Р ВµРЎРѓРЎРѓ, РЎвЂЎР ВµР С”-Р С—Р С•Р С‘Р Р…РЎвЂљРЎвЂ№");
                    list.add("РІР‚Сћ Р С™Р В°РЎвЂљРЎРѓРЎвЂ Р ВµР Р…РЎвЂ№: РЎвЂљР В°Р в„–Р С-Р В»Р В°Р в„–Р Р…, Р С”Р В°Р СР ВµРЎР‚Р В°, Р Т‘Р С‘Р В°Р В»Р С•Р С–Р С‘");
                    list.add("РІР‚Сћ Р ВР Р…РЎвЂљР ВµР С–РЎР‚Р В°РЎвЂ Р С‘РЎРЏ РЎРѓ NPC Р С‘ Content");
                } else {
                    list.add("РІР‚Сћ Quests: goals, conditions, rewards, branches");
                    list.add("РІР‚Сћ Campaigns: chapters, progress, checkpoints");
                    list.add("РІР‚Сћ Cutscenes: timeline, camera, dialogue");
                    list.add("РІР‚Сћ Integration with NPC and Content");
                }
            }
            case 7 -> {
                if (r) {
                    list.add("РІР‚Сћ Р В¦Р ВµР Р…РЎвЂљРЎР‚ РІР‚вЂќ MaRed Core, Р С‘РЎРѓРЎвЂљР С•РЎвЂЎР Р…Р С‘Р С” Р Р†РЎРѓР ВµР С–Р С•");
                    list.add("РІР‚Сћ Р вЂ™Р С•Р С”РЎР‚РЎС“Р С– РІР‚вЂќ 6 Р С”Р В°РЎвЂљР ВµР С–Р С•РЎР‚Р С‘Р в„– Р Р…Р В° РЎРѓР Р†Р С•Р С‘РЎвЂ¦ Р С•РЎР‚Р В±Р С‘РЎвЂљР В°РЎвЂ¦");
                    list.add("РІР‚Сћ Р С™Р В»Р С‘Р С” Р С—Р С• РЎС“Р В·Р В»РЎС“ РІР‚вЂќ РЎвЂћР С•Р С”РЎС“РЎРѓ, Р Р†РЎвЂљР С•РЎР‚Р С•Р в„– Р С”Р В»Р С‘Р С” РІР‚вЂќ Р Р†РЎвЂ¦Р С•Р Т‘");
                    list.add("РІР‚Сћ Р вЂєР С™Р Сљ + drag РІР‚вЂќ Р Р†РЎР‚Р В°РЎвЂ°Р ВµР Р…Р С‘Р Вµ РЎРѓРЎвЂ Р ВµР Р…РЎвЂ№");
                    list.add("РІР‚Сћ Esc РІР‚вЂќ Р Р†Р ВµРЎР‚Р Р…РЎС“РЎвЂљРЎРЉРЎРѓРЎРЏ Р С” Р С•Р В±Р В·Р С•РЎР‚РЎС“");
                } else {
                    list.add("РІР‚Сћ Center РІР‚вЂќ MaRed Core, source of all");
                    list.add("РІР‚Сћ Around it РІР‚вЂќ 6 categories on their orbits");
                    list.add("РІР‚Сћ Click a node РІР‚вЂќ focus, second click РІР‚вЂќ enter");
                    list.add("РІР‚Сћ LMB + drag РІР‚вЂќ rotate the scene");
                    list.add("РІР‚Сћ Esc РІР‚вЂќ return to overview");
                }
            }
        }
        return list;
    }

    private int accentFor(int page) {
        return switch (page) {
            case 1 -> ACCENT_CONTENT;
            case 2 -> ACCENT_WORLD;
            case 3 -> ACCENT_LOGIC;
            case 4 -> ACCENT_RESOURCES;
            case 5 -> ACCENT_TOOLS;
            case 6 -> ACCENT_SCENARIOS;
            default -> ACCENT_NEUT;
        };
    }

    private void drawBullet(GuiGraphics g, int x, int y, String text, int accent) {
        Font font = Minecraft.getInstance().font;
        // Р СљР В°РЎР‚Р С”Р ВµРЎР‚ РІР‚вЂќ Р СР В°Р В»Р ВµР Р…РЎРЉР С”Р В°РЎРЏ РЎвЂљР С•РЎвЂЎР С”Р В°
        g.fill(x, y + 6, x + 4, y + 10, accent);
        g.drawString(font, text, x + 14, y, TEXT, false);
    }

    private void drawCategoryGlyph(GuiGraphics g, int cx, int cy, int accent) {
        // Р СџРЎР‚Р С•РЎРѓРЎвЂљР С•Р в„– РЎР‚Р С•Р СР В±
        int size = 22;
        for (int i = 0; i < size; i++) {
            g.fill(cx - i, cy - (size - i), cx + i + 1, cy - (size - i) + 1, accent);
            g.fill(cx - i, cy + (size - i) - 1, cx + i + 1, cy + (size - i), accent);
        }
        int inner = size - 8;
        for (int i = 0; i < inner; i++) {
            g.fill(cx - i, cy - (inner - i), cx + i + 1, cy - (inner - i) + 1, BG_PANEL);
            g.fill(cx - i, cy + (inner - i) - 1, cx + i + 1, cy + (inner - i), BG_PANEL);
        }
    }

    // ============================================================
    //  Buttons
    // ============================================================

    private void computeButtons() {
        int by = panelY + panelH - BTN_MARGIN - BTN_H + 20;
        nextY = by;
        prevY = by;
        skipY = by;

        nextX = panelX + panelW - BTN_MARGIN - BTN_W;
        prevX = panelX + BTN_MARGIN;
        skipX = panelX + panelW / 2 - BTN_W / 2;
    }

    private void drawButton(GuiGraphics g, Font font,
                            int x, int y, String label,
                            int bg, int accent, int mouseX, int mouseY) {
        boolean hover = mouseX >= x && mouseX < x + BTN_W
                     && mouseY >= y && mouseY < y + BTN_H;
        int actualBg = hover ? lighten(bg, 0.12f) : bg;
        g.fill(x, y, x + BTN_W, y + BTN_H, actualBg);
        g.renderOutline(x, y, BTN_W, BTN_H, hover ? accent : BORDER);
        int tw = font.width(label);
        g.drawString(font, label,
                     x + (BTN_W - tw) / 2, y + (BTN_H - 8) / 2,
                     hover ? 0xFFFFFFFF : accent, false);
    }

    // ============================================================
    //  Input
    // ============================================================

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0) return super.mouseClicked(mx, my, button);

        if (hit(mx, my, prevX, prevY) && index > 0) {
            index--; return true;
        }
        if (hit(mx, my, nextX, nextY)) {
            advance(); return true;
        }
        if (hit(mx, my, skipX, skipY) && index < PAGES - 1) {
            index = PAGES - 1; return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    private boolean hit(double mx, double my, int x, int y) {
        return mx >= x && mx < x + BTN_W && my >= y && my < y + BTN_H;
    }

    private void advance() {
        if (index < PAGES - 1) {
            index++;
            return;
        }
        // Р СџР С•РЎРѓР В»Р ВµР Т‘Р Р…РЎРЏРЎРЏ РЎРѓРЎвЂљРЎР‚Р В°Р Р…Р С‘РЎвЂ Р В° РІР‚вЂќ Р Р† Genesis
        MaredWelcomeStorage.markSeen();
        WelcomeScreenManager.openGenesis();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_RIGHT || keyCode == GLFW.GLFW_KEY_SPACE
            || keyCode == GLFW.GLFW_KEY_ENTER) {
            advance(); return true;
        }
        if (keyCode == GLFW.GLFW_KEY_LEFT && index > 0) {
            index--; return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            MaredWelcomeStorage.markSeen();
            WelcomeScreenManager.openGenesis();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() { return false; }

    private static int lighten(int color, float amt) {
        int a = (color >>> 24), r = (color >> 16) & 0xFF,
            g = (color >> 8) & 0xFF, b = color & 0xFF;
        r = (int)(r + (255 - r) * amt);
        g = (int)(g + (255 - g) * amt);
        b = (int)(b + (255 - b) * amt);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static int darken(int color, float amt) {
        int a = (color >>> 24), r = (color >> 16) & 0xFF,
            g = (color >> 8) & 0xFF, b = color & 0xFF;
        r = (int)(r * (1f - amt));
        g = (int)(g * (1f - amt));
        b = (int)(b * (1f - amt));
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
}