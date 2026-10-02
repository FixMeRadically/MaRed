package com.fixmer.mared.gui2.framework.theme;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Реестр тем gui2.
 *
 * 0.3.0: перенесён из gui.common.MaredThemeRegistry. Встроенные темы.
 * Кастомные (config/mared/themes.json) грузятся при первом active().
 * 0.3.0 (fix): active() при первом вызове читает MaredSettings.getThemeId() —
 * раньше всегда возвращалось "mared" (хардкод из static), изменения в
 * Theme tab не влияли на рендер.
 */
public final class MaredThemeRegistry {

    private MaredThemeRegistry() {}

    private static final Map<String, MaredTheme> THEMES = new LinkedHashMap<>(8);
    private static volatile MaredTheme active = null;
    private static volatile boolean customLoaded = false;

    static {
        registerDefaults();
    }

    private static void registerDefaults() {
        register(MaredTheme.builder("mared", "Mared")
            .bgScreen(0xFF0A0A10)
            .bgPanel(0xFF14141C)
            .bgPanelRaised(0xFF1A1A24)
            .bgSunken(0xFF0A0A10)
            .bgHover(0xFF2A2A38)
            .bgSelected(0xFF3A3A4A)
            .text(0xFFEEDDFF)
            .textDim(0xFF9988AA)
            .textFaint(0xFF665577)
            .accent(0xFFFF55FF)
            .accentAlt(0xFFDD33DD)
            .accentDim(0xFF8833AA)
            .border(0xFF3D2A4A)
            .borderAccent(0xFFFF55FF)
            .tabScripts(0xFFFF55FF)
            .tabCommands(0xFFFFAA00)
            .tabNpc(0xFF55FF55)
            .tabEvents(0xFF55AAFF)
            .tabQuests(0xFFFF5555)
            .build());

        register(MaredTheme.builder("dark", "Dark")
            .bgScreen(0xFF1E1E1E).bgPanel(0xFF252526)
            .bgPanelRaised(0xFF2D2D30).bgSunken(0xFF1E1E1E)
            .bgHover(0xFF2A2D2E).bgSelected(0xFF37373D)
            .text(0xFFD4D4D4).textDim(0xFF858585).textFaint(0xFF5A5A5A)
            .accent(0xFF0E7AC7).accentAlt(0xFF007ACC).accentDim(0xFF095270)
            .border(0xFF3E3E42).borderAccent(0xFF007ACC)
            .tabScripts(0xFFC586C0).tabCommands(0xFFCE9178)
            .tabNpc(0xFF6A9955).tabEvents(0xFF569CD6).tabQuests(0xFFD16969)
            .build());

        register(MaredTheme.builder("light", "Light")
            .light(true)
            .bgScreen(0xFFF5F5F5).bgPanel(0xFFFFFFFF)
            .bgPanelRaised(0xFFFAFAFA).bgSunken(0xFFEEEEEE)
            .bgHover(0xFFE8E8E8).bgSelected(0xFFD8D8D8)
            .text(0xFF222222).textDim(0xFF555555).textFaint(0xFF888888)
            .textInverse(0xFFFFFFFF)
            .accent(0xFF3366CC).accentAlt(0xFF2255AA).accentDim(0xFF99BBEE)
            .border(0xFFCCCCCC).borderAccent(0xFF3366CC).divider(0xFFDDDDDD)
            .scrollTrack(0xFFE0E0E0).scrollThumb(0xFFBBBBBB).scrollThumbHover(0xFF999999)
            .overlayBg(0x80000000).tooltipBg(0xFFFFFFFF).tooltipBorder(0xFF3366CC)
            .tabScripts(0xFFAA33AA).tabCommands(0xFFCC6600)
            .tabNpc(0xFF339933).tabEvents(0xFF3366CC).tabQuests(0xFFCC3333)
            .build());

        // 0.3.0 (fix): НЕ устанавливаем active жёстко — active() сам решит
        // при первом вызове, читая MaredSettings.getThemeId().
    }

    public static void register(MaredTheme theme) {
        if (theme == null || theme.id == null) return;
        THEMES.put(theme.id, theme);
    }

    public static MaredTheme get(String id) {
        ensureCustomLoaded();
        return THEMES.get(id);
    }

    /**
     * Активная тема. Никогда не null.
     *
     * 0.3.0 (fix): при первом вызове читаем MaredSettings.getThemeId() —
     * раньше всегда возвращалось "mared" (хардкод из static), изменения
     * в Theme tab не влияли на рендер.
     */
    public static MaredTheme active() {
        ensureCustomLoaded();
        MaredTheme t = active;
        if (t == null) {
            String id = "mared";
            try {
                id = com.fixmer.mared.MaredSettings.getThemeId();
            } catch (Throwable ignored) {}
            t = THEMES.get(id);
            if (t == null) t = THEMES.get("mared");
            if (t == null && !THEMES.isEmpty()) {
                t = THEMES.values().iterator().next();
            }
            active = t;
        }
        return t;
    }

    public static void setActive(String id) {
        ensureCustomLoaded();
        MaredTheme t = THEMES.get(id);
        if (t != null) active = t;
    }

    public static void setActive(MaredTheme theme) {
        if (theme != null) active = theme;
    }

    public static List<MaredTheme> all() {
        ensureCustomLoaded();
        return new ArrayList<>(THEMES.values());
    }

    public static List<String> ids() {
        ensureCustomLoaded();
        return new ArrayList<>(THEMES.keySet());
    }

    public static int count() {
        ensureCustomLoaded();
        return THEMES.size();
    }

    public static Map<String, MaredTheme> raw() {
        ensureCustomLoaded();
        return Collections.unmodifiableMap(THEMES);
    }

    private static void ensureCustomLoaded() {
        if (customLoaded) return;
        customLoaded = true;
        try {
            MaredCustomThemes.load();
        } catch (Throwable t) {
            // silent — кастомные темы опциональны
        }
    }
}