package com.fixmer.mared;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.client.Minecraft;
import net.neoforged.fml.ModList;

/**
 * Р›РѕРєР°Р»РёР·Р°С†РёСЏ MaRed.
 *
 * v9: hardcoded HARDCODED/HARDCODED_RU РїРµСЂРµРїРёСЃР°РЅС‹ РІ UTF-8 (Р±С‹Р»Рё mojibake).
 * РўРѕС‡РєР° U+00B7 "В·" Р·Р°РјРµРЅРµРЅР° РЅР° "|" вЂ” РЅРµ РІСЃРµ С€СЂРёС„С‚С‹ РµС‘ РґРµСЂР¶Р°С‚.
 */
public final class MaredLang {

    private MaredLang() {}

    private static final long MAX_LANG_FILE_SIZE = 8L * 1024L * 1024L;

    private static String currentCode = "en_us";
    private static final Map<String, Map<String, String>> DICTS = new HashMap<>();
    private static boolean loaded = false;
    private static final String FALLBACK = "en_us";

    private static final Map<String, String> HARDCODED = new HashMap<>();
    static {
        HARDCODED.put("mared.genesis.title", "Genesis");
        HARDCODED.put("mared.genesis.hint", "Choose a category | Esc to return");
        HARDCODED.put("mared.genesis.panel_hint", "Click again to enter | Esc to return");
        HARDCODED.put("mared.genesis.core", "MaRed Core");
        HARDCODED.put("mared.genesis.content", "Content");
        HARDCODED.put("mared.genesis.world", "World");
        HARDCODED.put("mared.genesis.logic", "Logic");
        HARDCODED.put("mared.genesis.resources", "Resources");
        HARDCODED.put("mared.genesis.tools", "Tools");
        HARDCODED.put("mared.genesis.scenarios", "Scenarios");
        HARDCODED.put("mared.genesis.desc.core", "Genesis core of MaRed. Source of every creation.");
        HARDCODED.put("mared.genesis.desc.content", "Items, entities, models, animations, and gameplay content.");
        HARDCODED.put("mared.genesis.desc.world", "Terrain, biomes, chunks, dimensions, and structures.");
        HARDCODED.put("mared.genesis.desc.logic", "Scripts, events, conditions, actions, and behaviors.");
        HARDCODED.put("mared.genesis.desc.resources", "Textures, sounds, models, and data files.");
        HARDCODED.put("mared.genesis.desc.tools", "Debugging, profiling, and testing utilities.");
        HARDCODED.put("mared.genesis.desc.scenarios", "Quests, campaigns, cutscenes, and gameplay flow.");
        HARDCODED.put("mared.onboarding.back", "Back");
        HARDCODED.put("mared.onboarding.next", "Next");
        HARDCODED.put("mared.onboarding.skip", "Skip");
        HARDCODED.put("mared.onboarding.enter", "Enter");
        HARDCODED.put("mared.dialog.cancel", "Cancel");
        HARDCODED.put("mared.dialog.confirm", "Confirm");
        HARDCODED.put("mared.dialog.create", "Create");
        HARDCODED.put("mared.dialog.name_label", "Name");
    }

    private static final Map<String, String> HARDCODED_RU = new HashMap<>();
    static {
        HARDCODED_RU.put("mared.genesis.title", "Genesis");
        HARDCODED_RU.put("mared.genesis.hint", "Р’С‹Р±РµСЂРё РєР°С‚РµРіРѕСЂРёСЋ | Esc вЂ” РЅР°Р·Р°Рґ");
        HARDCODED_RU.put("mared.genesis.panel_hint", "РљР»РёРєРЅРё РµС‰С‘ СЂР°Р· РґР»СЏ РІС…РѕРґР° | Esc вЂ” РЅР°Р·Р°Рґ");
        HARDCODED_RU.put("mared.genesis.core", "РЇРґСЂРѕ MaRed");
        HARDCODED_RU.put("mared.genesis.content", "РљРѕРЅС‚РµРЅС‚");
        HARDCODED_RU.put("mared.genesis.world", "РњРёСЂ");
        HARDCODED_RU.put("mared.genesis.logic", "Р›РѕРіРёРєР°");
        HARDCODED_RU.put("mared.genesis.resources", "Р РµСЃСѓСЂСЃС‹");
        HARDCODED_RU.put("mared.genesis.tools", "РРЅСЃС‚СЂСѓРјРµРЅС‚С‹");
        HARDCODED_RU.put("mared.genesis.scenarios", "РЎС†РµРЅР°СЂРёРё");
        HARDCODED_RU.put("mared.genesis.desc.core", "РЇРґСЂРѕ MaRed. РСЃС‚РѕС‡РЅРёРє РІСЃРµРіРѕ СЃРѕР·РґР°РЅРёСЏ.");
        HARDCODED_RU.put("mared.genesis.desc.content", "РџСЂРµРґРјРµС‚С‹, СЃСѓС‰РЅРѕСЃС‚Рё, РјРѕРґРµР»Рё, Р°РЅРёРјР°С†РёРё Рё РєРѕРЅС‚РµРЅС‚.");
        HARDCODED_RU.put("mared.genesis.desc.world", "РњРёСЂ, Р±РёРѕРјС‹, С‡Р°РЅРєРё, РёР·РјРµСЂРµРЅРёСЏ Рё СЃС‚СЂСѓРєС‚СѓСЂС‹.");
        HARDCODED_RU.put("mared.genesis.desc.logic", "РЎРєСЂРёРїС‚С‹, СЃРѕР±С‹С‚РёСЏ, СѓСЃР»РѕРІРёСЏ, РґРµР№СЃС‚РІРёСЏ Рё РїРѕРІРµРґРµРЅРёРµ.");
        HARDCODED_RU.put("mared.genesis.desc.resources", "РўРµРєСЃС‚СѓСЂС‹, Р·РІСѓРєРё, РјРѕРґРµР»Рё Рё С„Р°Р№Р»С‹ РґР°РЅРЅС‹С….");
        HARDCODED_RU.put("mared.genesis.desc.tools", "РћС‚Р»Р°РґРєР°, РїСЂРѕС„РёР»РёСЂРѕРІР°РЅРёРµ Рё С‚РµСЃС‚С‹.");
        HARDCODED_RU.put("mared.genesis.desc.scenarios", "РљРІРµСЃС‚С‹, РєР°РјРїР°РЅРёРё, РєР°С‚СЃС†РµРЅС‹ Рё СЃСЋР¶РµС‚.");
        HARDCODED_RU.put("mared.onboarding.back", "РќР°Р·Р°Рґ");
        HARDCODED_RU.put("mared.onboarding.next", "Р”Р°Р»РµРµ");
        HARDCODED_RU.put("mared.onboarding.skip", "РџСЂРѕРїСѓСЃС‚РёС‚СЊ");
        HARDCODED_RU.put("mared.onboarding.enter", "Р’РѕР№С‚Рё");
        HARDCODED_RU.put("mared.dialog.cancel", "РћС‚РјРµРЅР°");
        HARDCODED_RU.put("mared.dialog.confirm", "РџРѕРґС‚РІРµСЂРґРёС‚СЊ");
        HARDCODED_RU.put("mared.dialog.create", "РЎРѕР·РґР°С‚СЊ");
        HARDCODED_RU.put("mared.dialog.name_label", "РРјСЏ");
    }

    public static String get(String key) {
        if (key == null || key.isEmpty()) return "";
        try { ensureLoaded(); }
        catch (Throwable t) {
            Mared.LOGGER.warn("[Mared] lang load failed: {}", t.toString());
        }

        Map<String, String> dict = DICTS.get(currentCode);
        if (dict != null && dict.containsKey(key)) return dict.get(key);
        Map<String, String> fallback = DICTS.get(FALLBACK);
        if (fallback != null && fallback.containsKey(key)) return fallback.get(key);

        if (currentCode != null && currentCode.startsWith("ru")) {
            String v = HARDCODED_RU.get(key);
            if (v != null) return v;
        }
        String v = HARDCODED.get(key);
        if (v != null) return v;
        return key;
    }

    public static String format(String key, Object... args) {
        String s = get(key);
        try { return String.format(s, args); }
        catch (Throwable t) { return s; }
    }

    public static void reload() {
        loaded = false;
        DICTS.clear();
        try { ensureLoaded(); } catch (Throwable ignored) { }
    }

    public static void setLanguage(String code) {
        currentCode = (code == null || code.isEmpty()) ? FALLBACK : code;
    }

    public static String getCurrentCode() { return currentCode; }

    private static void ensureLoaded() {
        if (loaded) return;
        loaded = true;

        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc != null && mc.getLanguageManager() != null) {
                currentCode = mc.getLanguageManager().getSelected();
            }
        } catch (Throwable t) { currentCode = FALLBACK; }
        if (currentCode == null || currentCode.isEmpty()) currentCode = FALLBACK;

        loadLanguage(currentCode);
        if (!FALLBACK.equals(currentCode)) loadLanguage(FALLBACK);
    }

    private static void loadLanguage(String code) {
        String relative = "assets/mared/lang/" + code + ".json";
        try (InputStream in = openModResource(relative)) {
            if (in == null) {
                Mared.LOGGER.warn("[Mared] Lang file not found: {}", relative);
                return;
            }
            byte[] bytes = in.readAllBytes();
            if (bytes.length > MAX_LANG_FILE_SIZE) {
                Mared.LOGGER.error("[Mared] Lang {} too large: {} bytes", relative, bytes.length);
                return;
            }
            String json = new String(bytes, StandardCharsets.UTF_8);
            JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
            Map<String, String> dict = new HashMap<>(Math.max(64, obj.size() * 2));
            for (Map.Entry<String, JsonElement> e : obj.entrySet()) {
                JsonElement v = e.getValue();
                if (v != null && v.isJsonPrimitive()) {
                    try { dict.put(e.getKey(), v.getAsString()); } catch (Throwable ignored) { }
                }
            }
            DICTS.put(code, dict);
            Mared.LOGGER.info("[Mared] Loaded lang {} ({} keys)", code, dict.size());
        } catch (Throwable t) {
            Mared.LOGGER.error("[Mared] Failed to load lang {}: {}", code, t.toString());
        }
    }

    private static InputStream openModResource(String relative) throws IOException {
        try {
            var modFile = ModList.get().getModFileById(Mared.MOD_ID).getFile();
            Path found = modFile.findResource(relative);
            if (found != null && Files.exists(found) && Files.isRegularFile(found)) {
                long size = Files.size(found);
                if (size > MAX_LANG_FILE_SIZE) {
                    Mared.LOGGER.error("[Mared] Oversized lang: {} ({} bytes)", relative, size);
                    return null;
                }
                return Files.newInputStream(found);
            }
        } catch (Throwable ignored) { }
        return MaredLang.class.getResourceAsStream("/" + relative);
    }
}