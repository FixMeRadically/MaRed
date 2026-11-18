package com.fixmer.mared.plugin;

import java.util.HashMap;
import java.util.Map;
import java.util.ServiceLoader;

import com.fixmer.mared.Mared;
import com.fixmer.mared.gui2.settings.SettingsPageRegistry;
import com.fixmer.mared.gui2.studio.panel.PanelRegistry;
import com.fixmer.mared.modules.ModuleRegistry;

/**
 * Загрузчик плагинов.
 *
 * 0.3.2:
 *   1. Discovery — ServiceLoader<MaredPlugin> по classpath.
 *      Внешний мод объявляет реализацию в
 *      META-INF/services/com.fixmer.mared.plugin.MaredPlugin.
 *
 *   2. Config — config/mared/plugins.json:
 *        "disabled": ["id", ...] — пропустить при загрузке.
 *        "enabled":  ["id", ...] — принудительно включить.
 *
 *   3. Bootstrap built-in реестров перед регистрацией плагинных
 *      страниц/панелей/модулей — иначе плагин может занять id,
 *      который позже bootstrap перезапишет.
 *
 *   4. Load — onLoad(ctx) для каждого. Ошибка одного плагина не
 *      рушит остальные: он помечается как failed, пропускается.
 *
 * EditorAction'ы плагинов откладываются в PluginRegistry и
 * применяются при старте каждой StudioSession — EditorActionRegistry
 * создаётся per-session.
 */
public final class PluginLoader {

    private PluginLoader() {}

    private static volatile boolean loaded = false;

    public static synchronized void loadAll() {
        if (loaded) return;
        loaded = true;

        // Discover providers without constructing plugins or loading optional UI registries.
        Map<String, MaredPlugin> discovered = new HashMap<>(4);
        try {
            var providers=ServiceLoader.load(MaredPlugin.class).stream().iterator();
            if(!providers.hasNext()){
                Mared.LOGGER.info("[plugin] no plugins discovered; UI registries stay lazy");
                return;
            }
            // Preserve the original ordering for plugins, including constructor-time registration.
            UiRegistries.bootstrap();
            while(providers.hasNext()) {
                MaredPlugin p;try{p=providers.next().get();}catch(java.util.ServiceConfigurationError|RuntimeException provider){Mared.LOGGER.error("[plugin] provider construction failed; continuing discovery",provider);continue;}
                if (p == null) continue;
                String id;try{id=p.id();}catch(RuntimeException bad){Mared.LOGGER.error("[plugin] invalid provider identity; continuing",bad);continue;}
                if (id == null || id.isBlank()) {
                    Mared.LOGGER.warn(
                        "[plugin] ignoring plugin with blank id: {}",
                        p.getClass().getName());
                    continue;
                }
                if (discovered.containsKey(id)) {
                    Mared.LOGGER.warn(
                        "[plugin] duplicate id '{}', keeping first", id);
                    continue;
                }
                discovered.put(id, p);
            }
        } catch (Throwable t) {
            Mared.LOGGER.error("[plugin] ServiceLoader failed", t);
        }

        if (discovered.isEmpty()) {
            Mared.LOGGER.info("[plugin] no plugins discovered");
            return;
        }

        // 3. Config overrides.
        PluginConfig.reload();

        // 4. Load.
        int ok = 0, skip = 0, fail = 0;
        for (var discoveredEntry : discovered.entrySet()) {
            MaredPlugin p=discoveredEntry.getValue();String stableId=discoveredEntry.getKey();
            if (PluginConfig.isDisabled(stableId)) {
                Mared.LOGGER.info(
                    "[plugin:{}] disabled by config", stableId);
                skip++;
                continue;
            }
            try {
                PluginContext ctx = new PluginContext(stableId);
                p.onLoad(ctx);
                String title=p.displayName(),version=p.version();
                ctx.commit140();
                PluginRegistry.registerLoaded(p);
                Mared.LOGGER.info(
                    "[plugin:{}] loaded '{}' v{}",
                    stableId, title, version);
                ok++;
            } catch (Throwable t) {
                Mared.LOGGER.error(
                    "[plugin:{}] onLoad failed, plugin disabled",
                    stableId, t);
                fail++;
            }
        }

        Mared.LOGGER.info(
            "[plugin] summary: {} loaded, {} skipped, {} failed",
            ok, skip, fail);
    }

    /** Keep optional UI class references out of the startup path with no providers. */
    private static final class UiRegistries {
        static void bootstrap(){
            try { ModuleRegistry.bootstrap(); } catch(Throwable ignored) { }
            try { PanelRegistry.bootstrap(); } catch(Throwable ignored) { }
            try { SettingsPageRegistry.bootstrap(); } catch(Throwable ignored) { }
        }
    }
    public static void reset() {
        loaded = false;
    }
}