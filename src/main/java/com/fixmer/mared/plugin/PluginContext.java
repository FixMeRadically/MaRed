package com.fixmer.mared.plugin;

import com.fixmer.mared.Mared;
import com.fixmer.mared.gui2.settings.SettingsPageDescriptor;
import com.fixmer.mared.gui2.settings.SettingsPageRegistry;
import com.fixmer.mared.gui2.studio.action.EditorAction;
import com.fixmer.mared.gui2.studio.panel.PanelDescriptor;
import com.fixmer.mared.gui2.studio.panel.PanelRegistry;
import com.fixmer.mared.modules.ModuleDescriptor;
import com.fixmer.mared.modules.ModuleRegistry;

/**
 * Контекст, передаваемый плагину в onLoad().
 *
 * Все методы регистрации делегируют в соответствующие реестры.
 *
 * EditorAction — особый случай: EditorActionRegistry создаётся
 * per-session в StudioSession, поэтому плагин регистрирует action
 * один раз, а StudioSession применяет их к своему реестру при
 * каждом старте сессии. См. PluginRegistry.applyPendingActions.
 */
public final class PluginContext {

    private final String pluginId;

    PluginContext(String pluginId) {
        this.pluginId = pluginId;
    }

    public String pluginId() { return pluginId; }

    // ============================================================
    //  Регистрация
    // ============================================================

    public void registerModule(ModuleDescriptor d) {
        if (d == null) return;
        ModuleRegistry.register(d);
        Mared.LOGGER.debug("[plugin:{}] module '{}' registered",
            pluginId, d.id());
    }

    public void registerPanel(PanelDescriptor d) {
        if (d == null) return;
        PanelRegistry.register(d);
        Mared.LOGGER.debug("[plugin:{}] panel '{}' registered",
            pluginId, d.id());
    }

    public void registerSettingsPage(SettingsPageDescriptor d) {
        if (d == null) return;
        SettingsPageRegistry.register(d);
        Mared.LOGGER.debug("[plugin:{}] settings page '{}' registered",
            pluginId, d.id());
    }

    /**
     * 0.3.2: EditorAction — откладывается в PluginRegistry.
     * StudioSession применит их после StudioActions.registerAll().
     * Идемпотентно: один и тот же action не может быть зарегистрирован
     * дважды в одном реестре (id-uniqueness).
     */
    public void registerAction(EditorAction a) {
        if (a == null) return;
        PluginRegistry.addPendingAction(a);
        Mared.LOGGER.debug("[plugin:{}] action '{}' queued",
            pluginId, a.id());
    }

    // ============================================================
    //  Logging
    // ============================================================

    public void log(String msg) {
        Mared.LOGGER.info("[plugin:{}] {}", pluginId, msg);
    }

    public void warn(String msg) {
        Mared.LOGGER.warn("[plugin:{}] {}", pluginId, msg);
    }
}