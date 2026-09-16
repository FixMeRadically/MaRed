package com.fixmer.mared.script;

import java.util.ArrayList;
import java.util.List;

import com.fixmer.mared.Mared;
import com.fixmer.mared.script.commands.MaredOnCommand;
import com.fixmer.mared.script.commands.MaredScriptCommand;
import com.fixmer.mared.storage.MaredCommandStorage;

/**
 * Загрузчик persistent-скриптов.
 *
 * FIX 3: помечает контекст persistent=true, чтобы on-события
 * переживали выход из мира и не требовали перезапуска.
 */
public final class MaredPersistentLoader {

    private MaredPersistentLoader() {}

    private static boolean loaded = false;

    public static void loadAll() {
        if (loaded) {
            Mared.LOGGER.info("[Mared] loadAll: already loaded, skipping.");
            return;
        }
        loaded = true;

        List<String> names = MaredPersistentStorage.load();
        if (names.isEmpty()) {
            Mared.LOGGER.info("[Mared] No persistent scripts found.");
            return;
        }

        Mared.LOGGER.info("[Mared] Loading {} persistent script(s)...", names.size());

        List<String> validNames = new ArrayList<>();
        boolean needsRewrite = false;

        for (String name : names) {
            try {
                boolean ok = loadScript(name);
                if (ok) {
                    validNames.add(name);
                } else {
                    needsRewrite = true;
                }
            } catch (Exception e) {
                Mared.LOGGER.error("[Mared] Failed to load persistent '{}': {}",
                    name, e.getMessage());
                needsRewrite = true;
            }
        }

        if (needsRewrite) {
            MaredPersistentStorage.save(validNames);
            Mared.LOGGER.info("[Mared] Cleaned persistent.txt: {} valid, {} removed.",
                validNames.size(), names.size() - validNames.size());
        }

        Mared.LOGGER.info("[Mared] Persistent scripts loaded. Events total: {}",
            MaredEventRegistry.totalCount());
    }

    private static boolean loadScript(String name) {
        Mared.LOGGER.info("[Mared] loadScript: '{}'", name);

        String text = MaredCommandStorage.readCommand(name);
        if (text == null || text.trim().isEmpty()) {
            Mared.LOGGER.warn("[Mared] Persistent '{}' is empty or missing — removing.", name);
            return false;
        }

        String firstLine = text.split("\n", 2)[0].trim();
        if (!firstLine.startsWith("#persistent")) {
            Mared.LOGGER.warn("[Mared] '{}' not marked as #persistent — removing.", name);
            return false;
        }

        String body = text;
        int firstNl = text.indexOf('\n');
        if (firstNl >= 0) body = text.substring(firstNl + 1);

        List<MaredScriptCommand> commands;
        try {
            commands = MaredScriptParser.parse(body);
        } catch (MaredScriptParser.ParseException e) {
            Mared.LOGGER.error("[Mared] Parse error in '{}': {}", name, e.getMessage());
            return false;
        }

        if (commands.isEmpty()) {
            Mared.LOGGER.warn("[Mared] '{}' has no commands — removing.", name);
            return false;
        }

        // ← FIX 3: помечаем контекст persistent
        MaredScriptContext ctx = new MaredScriptContext(null, null, msg -> {});
        ctx.setPersistent(true);

        int registered = 0;
        for (MaredScriptCommand cmd : commands) {
            if (cmd instanceof MaredOnCommand onCmd) {
                MaredEventRegistry.register(
                    onCmd.getEventType(),
                    onCmd.getBody(),
                    ctx,
                    onCmd.isReplace(),
                    true
                );
                Mared.LOGGER.info("[Mared] Registered persistent event: {} (from {})",
                    onCmd.getEventType(), name);
                registered++;
            } else {
                Mared.LOGGER.debug("[Mared] Skipping non-event command in persistent: {}",
                    cmd.describe());
            }
        }

        if (registered == 0) {
            Mared.LOGGER.warn("[Mared] '{}' has no on-events — removing.", name);
            return false;
        }

        Mared.LOGGER.info("[Mared] Persistent '{}' loaded: {} events registered", name, registered);
        return true;
    }

    public static void reset() {
        loaded = false;
        Mared.LOGGER.info("[Mared] PersistentLoader reset.");
    }
}