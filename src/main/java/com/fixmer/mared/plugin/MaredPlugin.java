package com.fixmer.mared.plugin;

/**
 * Точка входа плагина MaRed.
 *
 * 0.3.2 (plugin API):
 *   Плагин — отдельный мод, объявляющий свою реализацию через
 *   META-INF/services/com.fixmer.mared.plugin.MaredPlugin.
 *   ServiceLoader находит и инстанцирует её при старте клиента.
 *
 *   onLoad() вызывается один раз при старте, до открытия Studio.
 *   Внутри плагин может регистрировать:
 *     - ModuleDescriptor       → ModuleRegistry
 *     - PanelDescriptor        → PanelRegistry
 *     - SettingsPageDescriptor → SettingsPageRegistry
 *     - EditorAction           → откладывается до старта сессии Studio
 *
 *   onUnload() вызывается на GameShuttingDownEvent.
 *
 *   Пример:
 *     public final class MyPlugin implements MaredPlugin {
 *         @Override public String id() { return "myplugin"; }
 *         @Override public String version() { return "1.0.0"; }
 *         @Override public void onLoad(PluginContext ctx) {
 *             ctx.registerModule(new ModuleDescriptor(
 *                 "myplugin:dialogue", "myplugin.module.dialogue.name",
 *                 "myplugin.module.dialogue.desc", "D",
 *                 ModuleType.CONTENT, ModuleAvailability.AVAILABLE, 100));
 *         }
 *     }
 */
public interface MaredPlugin {

    /** Уникальный id. Формат: [a-z0-9_\-:]+. */
    String id();

    /** Отображаемое имя. По умолчанию совпадает с id. */
    default String displayName() { return id(); }

    /** Semver-версия. Для логов. */
    default String version() { return "0.0.0"; }

    /**
     * Вызывается один раз при старте клиента.
     * Исключения логируются — плагин помечается как failed, не
     * участвует в системе.
     */
    default void onLoad(PluginContext ctx) throws Exception {}

    /** Вызывается при выключении клиента. Идемпотентно. */
    default void onUnload() {}
}