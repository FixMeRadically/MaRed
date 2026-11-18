package com.fixmer.mared.gui2.settings;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fixmer.mared.Mared;
import com.fixmer.mared.gui2.settings.tabs.MaredAboutTab;
import com.fixmer.mared.gui2.settings.tabs.MaredEditorTab;
import com.fixmer.mared.gui2.settings.tabs.MaredKeybindsTab;
import com.fixmer.mared.gui2.settings.tabs.MaredLayoutTab;
import com.fixmer.mared.gui2.settings.tabs.MaredLogsTab;
import com.fixmer.mared.gui2.settings.tabs.MaredThemeTab;

/**
 * Реестр вкладок Settings.
 *
 * 0.3.2:
 *   - Bootstrap регистрирует built-in pages.
 *   - Плагин может добавить страницу через register(descriptor).
 *   - listAll() — сортировка по order; при равных order порядок
 *     регистрации сохраняется (стабильная сортировка).
 *
 * Не потокобезопасный — всё с main thread.
 */
public final class SettingsPageRegistry {

    private SettingsPageRegistry() {}

    private static final Map<String, SettingsPageDescriptor> PAGES =
        new LinkedHashMap<>(8);

    private static boolean bootstrapped = false;

    private static final Comparator<SettingsPageDescriptor> BY_ORDER =
        Comparator.comparingInt(SettingsPageDescriptor::order);

    // ============================================================
    //  Bootstrap
    // ============================================================

    public static synchronized void bootstrap() {
        if (bootstrapped) return;
        bootstrapped = true;

        register(new SettingsPageDescriptor(
            "layout",
            "mared.settings.tab.layout",
            10,
            SettingsPageDescriptor.CATEGORY_APPEARANCE,
            MaredLayoutTab::new));

        register(new SettingsPageDescriptor(
            "theme",
            "mared.settings.tab.theme",
            20,
            SettingsPageDescriptor.CATEGORY_APPEARANCE,
            MaredThemeTab::new));

        register(new SettingsPageDescriptor(
            "editor",
            "mared.settings.tab.editor",
            30,
            SettingsPageDescriptor.CATEGORY_EDITOR,
            MaredEditorTab::new));

        register(new SettingsPageDescriptor(
            "logs",
            "mared.settings.tab.logs",
            40,
            SettingsPageDescriptor.CATEGORY_EDITOR,
            MaredLogsTab::new));

        register(new SettingsPageDescriptor(
            "keybinds",
            "mared.settings.tab.keybinds",
            50,
            SettingsPageDescriptor.CATEGORY_EDITOR,
            MaredKeybindsTab::new));

        register(new SettingsPageDescriptor(
            "about",
            "mared.settings.tab.about",
            999,
            SettingsPageDescriptor.CATEGORY_ABOUT,
            MaredAboutTab::new));
    }

    // ============================================================
    //  Регистрация
    // ============================================================

    public static synchronized void register(SettingsPageDescriptor d) {
        if (d == null) return;
        if (PAGES.containsKey(d.id())) {
            Mared.LOGGER.warn(
                "[settings] duplicate page id '{}', overriding", d.id());
        }
        PAGES.put(d.id(), d);
    }

    public static synchronized void unregister(String id) {
        if (id == null) return;
        PAGES.remove(id);
    }

    public static synchronized void reset() {
        PAGES.clear();
        bootstrapped = false;
    }

    // ============================================================
    //  Чтение
    // ============================================================

    public static synchronized SettingsPageDescriptor byId(String id) {
        bootstrap();
        if (id == null) return null;
        return PAGES.get(id);
    }

    public static synchronized List<SettingsPageDescriptor> all() {
        bootstrap();
        List<SettingsPageDescriptor> list = new ArrayList<>(PAGES.values());
        list.sort(BY_ORDER);
        return list;
    }

    public static synchronized List<SettingsPageDescriptor> forCategory(String cat) {
        bootstrap();
        List<SettingsPageDescriptor> list = new ArrayList<>();
        for (SettingsPageDescriptor d : PAGES.values()) {
            if (d.category().equals(cat)) list.add(d);
        }
        list.sort(BY_ORDER);
        return list;
    }

    public static synchronized List<String> categories() {
        bootstrap();
        List<String> out = new ArrayList<>(4);
        for (SettingsPageDescriptor d : all()) {
            if (!out.contains(d.category())) out.add(d.category());
        }
        return Collections.unmodifiableList(out);
    }

    public static synchronized int count() {
        bootstrap();
        return PAGES.size();
    }

    /**
     * Создать экземпляры всех вкладок в порядке order.
     * Каждый вызов создаёт новые инстансы — они не кэшируются в реестре,
     * потому что табы имеют состояние (draft, scroll).
     */
    public static List<MaredSettingsTab> instantiateAll() {
        List<SettingsPageDescriptor> descs = all();
        List<MaredSettingsTab> out = new ArrayList<>(descs.size());
        for (SettingsPageDescriptor d : descs) {
            try {
                MaredSettingsTab tab = d.factory().get();
                if (tab != null) out.add(tab);
            } catch (Throwable t) {
                Mared.LOGGER.error(
                    "[settings] failed to create page '{}'", d.id(), t);
            }
        }
        return out;
    }
}