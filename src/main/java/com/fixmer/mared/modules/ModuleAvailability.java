package com.fixmer.mared.modules;

/**
 * Статус доступности модуля.
 *
 * 0.3.1: введён, чтобы Welcome/ModuleSwitcher/Command Palette могли
 * показывать будущие модули (NPC, Quests) без обмана пользователя:
 * карточка неактивна, а не "кликается в никуда".
 */
public enum ModuleAvailability {
    /** Модуль полностью реализован. */
    AVAILABLE,
    /** Модуль заявлен в roadmap, но ещё не открывается. */
    SOON,
    /** Модуль отключён сборкой/конфигом. */
    DISABLED
}