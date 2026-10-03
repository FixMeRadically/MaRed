package com.fixmer.mared.gui2.settings;

import java.util.function.Supplier;

/**
 * Описание вкладки настроек.
 *
 * 0.3.2 (audit #92):
 *   Раньше список табов был захардкожен в MaredSettingsTabs.createAll().
 *   Плагин не мог добавить свою вкладку.
 *
 * Теперь: descriptor { id, titleKey, order, category, factory }.
 * Реестр собирает вкладки, сортирует по order, группирует по category.
 *
 * category — на будущее: слева sidebar с категориями, не сверху табами.
 * Пока не используется в UI, но контракт стабилен.
 */
public record SettingsPageDescriptor(
    String id,
    String titleKey,
    int order,
    String category,
    Supplier<MaredSettingsTab> factory
) {

    public static final String CATEGORY_GENERAL = "general";
    public static final String CATEGORY_EDITOR  = "editor";
    public static final String CATEGORY_APPEARANCE = "appearance";
    public static final String CATEGORY_ADVANCED = "advanced";
    public static final String CATEGORY_ABOUT   = "about";

    public SettingsPageDescriptor {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("settings page id required");
        }
        if (titleKey == null || titleKey.isBlank()) titleKey = id;
        if (category == null || category.isBlank()) {
            category = CATEGORY_GENERAL;
        }
        if (factory == null) {
            throw new IllegalArgumentException("settings page factory required for id " + id);
        }
    }
}