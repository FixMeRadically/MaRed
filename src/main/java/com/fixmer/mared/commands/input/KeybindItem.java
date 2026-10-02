package com.fixmer.mared.commands.input;

/**
 * 0.3.0 (Phase F8): DTO для UI-отображения бинда.
 * 0.3.0 (Phase F2): упрощён — без списка команд (это внутренность реестра).
 */
public record KeybindItem(
    String key,
    String label,
    boolean blocking
) {}