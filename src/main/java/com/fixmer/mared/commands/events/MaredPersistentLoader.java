package com.fixmer.mared.commands.events;

import java.util.ArrayList;
import java.util.List;

import com.fixmer.mared.Mared;
import com.fixmer.mared.commands.engine.MaredScriptCommand;
import com.fixmer.mared.commands.engine.MaredScriptContext;
import com.fixmer.mared.commands.engine.MaredScriptParser;
import com.fixmer.mared.commands.events_cmd.MaredOnCommand;
import com.fixmer.mared.commands.storage.MaredCommandStorage;
import com.fixmer.mared.commands.storage.MaredPersistentStorage;

/**
 * Загрузчик persistent-скриптов.
 *
 * Persistent-скрипты — это файлы в config/mared/commands с первой строкой
 * "#persistent". При старте клиента они читаются, из них берутся только
 * on-команды, которые регистрируются в MaredEventRegistry с флагом persistent.
 */
public final class MaredPersistentLoader {

    private MaredPersistentLoader() {}

    private static volatile boolean loaded = false;

    public static void loadAll() {
        if (loaded) {
            Mared.LOGGER.info("[Mared] loadAll: already loaded, skipping.");
            return;
        }
        loaded = true;

        List<String> names;
        try {names=MaredPersistentStorage.load();}
        catch(Exception error){loaded=false;Mared.LOGGER.error("Cannot read persistent configuration; load can be retried",error);return;}
        if (names.isEmpty()) {
            Mared.LOGGER.info("[Mared] No persistent scripts found.");
            return;
        }

        Mared.LOGGER.info("[Mared] Loading {} persistent script(s)...", names.size());

        for (String name : names) {
            try {
                if(!loadScript(name))Mared.LOGGER.warn("Persistent '{}' skipped; configured name retained",name);
            } catch (Exception e) {
                Mared.LOGGER.error("Failed to load persistent '{}'; configured name retained",name,e);
            }
        }

        Mared.LOGGER.info("[Mared] Persistent scripts loaded. Events total: {}",
            MaredEventRegistry.totalCount());
    }

    private static boolean loadScript(String name) {
        Mared.LOGGER.info("[Mared] loadScript: '{}'", name);

        String text = MaredCommandStorage.readCommand(name);
        if (text == null || text.trim().isEmpty()) {
            Mared.LOGGER.warn("[Mared] Persistent '{}' is empty or missing — skipping.", name);
            return false;
        }

        int firstNl = text.indexOf('\n');
        String firstLine = (firstNl >= 0 ? text.substring(0, firstNl) : text).trim();
        if (!firstLine.startsWith("#persistent")) {
            Mared.LOGGER.warn("[Mared] '{}' not marked as #persistent — skipping.", name);
            return false;
        }

        String body = firstNl >= 0 ? text.substring(firstNl + 1) : "";

        List<MaredScriptCommand> commands;
        try {
            commands = MaredScriptParser.parse(body);
        } catch (MaredScriptParser.ParseException e) {
            Mared.LOGGER.error("[Mared] Parse error in '{}': {}", name, e.getMessage());
            return false;
        }

        if (commands.isEmpty()) {
            Mared.LOGGER.warn("[Mared] '{}' has no commands — skipping.", name);
            return false;
        }

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
            Mared.LOGGER.warn("[Mared] '{}' has no on-events — skipping.", name);
            return false;
        }

        Mared.LOGGER.info("[Mared] Persistent '{}' loaded: {} events registered", name, registered);
        return true;
    }

    public static void reset() { loaded = false; }
}