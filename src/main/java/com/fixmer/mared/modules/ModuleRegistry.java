package com.fixmer.mared.modules;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fixmer.mared.gui2.theme.ModuleType;

/**
 * Единый реестр модулей MaRed Studio.
 *
 * 0.3.1: закрывает аудитные #26 (Theme tabScripts/tabNpc...),
 * #103 (Welcome-карточки), #93 (Settings tabs), #105 (Welcome data
 * caches translated strings).
 *
 * Built-in модули регистрируются в bootstrap(). Позже plugin API
 * сможет добавлять свои модули через register() — ids стабильны и
 * зарезервированы под префикс "mared:", plugin — под своим namespace.
 *
 * Не потокобезопасная: bootstrap/register вызываются с main thread
 * при старте клиента.
 */
public final class ModuleRegistry {

    private ModuleRegistry() {}

    private static final Map<String, ModuleDescriptor> MODULES =
        new LinkedHashMap<>(16);

    private static boolean bootstrapped = false;

    private static final Comparator<ModuleDescriptor> BY_ORDER =
        Comparator.comparingInt(ModuleDescriptor::order);

    // ============================================================
    //  Bootstrap
    // ============================================================

    public static synchronized void bootstrap() {
        if (bootstrapped) return;
        bootstrapped = true;

        register(new ModuleDescriptor(
            "scripts",
            "mared.module.scripts.name",
            "mared.module.scripts.desc",
            "S",
            ModuleType.LOGIC,
            ModuleAvailability.AVAILABLE,
            0));

        register(new ModuleDescriptor(
            "commands",
            "mared.module.commands.name",
            "mared.module.commands.desc",
            "C",
            ModuleType.LOGIC,
            ModuleAvailability.AVAILABLE,
            10));

        register(new ModuleDescriptor(
            "npc",
            "mared.module.npc.name",
            "mared.module.npc.desc",
            "N",
            ModuleType.CONTENT,
            ModuleAvailability.SOON,
            20));

        register(new ModuleDescriptor(
            "events",
            "mared.module.events.name",
            "mared.module.events.desc",
            "E",
            ModuleType.LOGIC,
            ModuleAvailability.SOON,
            30));

        register(new ModuleDescriptor(
            "quests",
            "mared.module.quests.name",
            "mared.module.quests.desc",
            "Q",
            ModuleType.GAMEPLAY,
            ModuleAvailability.SOON,
            40));
    }

    public static synchronized void reset() {
        MODULES.clear();
        bootstrapped = false;
    }

    // ============================================================
    //  Регистрация
    // ============================================================

    public static synchronized void register(ModuleDescriptor d) {
        if (d == null) return;
        MODULES.put(d.id(), d);
    }

    // ============================================================
    //  Чтение
    // ============================================================

    public static synchronized ModuleDescriptor byId(String id) {
        bootstrap();
        if (id == null) return null;
        return MODULES.get(id);
    }

    public static synchronized List<ModuleDescriptor> all() {
        bootstrap();
        List<ModuleDescriptor> list = new ArrayList<>(MODULES.values());
        list.sort(BY_ORDER);
        return list;
    }

    public static synchronized List<ModuleDescriptor> available() {
        bootstrap();
        List<ModuleDescriptor> list = new ArrayList<>();
        for (ModuleDescriptor d : MODULES.values()) {
            if (d.availability() == ModuleAvailability.AVAILABLE) {
                list.add(d);
            }
        }
        list.sort(BY_ORDER);
        return list;
    }

    public static synchronized int count() {
        bootstrap();
        return MODULES.size();
    }

    public static synchronized Map<String, ModuleDescriptor> raw() {
        bootstrap();
        return Collections.unmodifiableMap(MODULES);
    }
}