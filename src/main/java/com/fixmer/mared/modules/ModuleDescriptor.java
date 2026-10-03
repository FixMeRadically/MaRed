package com.fixmer.mared.modules;

import com.fixmer.mared.gui2.modules.theme.ModuleType;

/**
 * Описание модуля MaRed Studio.
 *
 * Один descriptor наполняет:
 *   - Welcome screen (карточки);
 *   - Module switcher (будущий);
 *   - Command Palette (будущий);
 *   - Theme accent для module-tab'ов (MaredTabStyles);
 *   - Settings-секции (модуль → набор страниц).
 *
 * Никаких UI-строк внутри — только ключи локализации. Тема/иконка —
 * метаданные, они не переводятся.
 */
public record ModuleDescriptor(
    String id,
    String displayNameKey,
    String descriptionKey,
    String icon,
    ModuleType type,
    ModuleAvailability availability,
    int order
) {
    public ModuleDescriptor {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("module id required");
        }
        if (type == null) type = ModuleType.TOOLS;
        if (availability == null) availability = ModuleAvailability.AVAILABLE;
        if (icon == null) icon = "";
    }
}