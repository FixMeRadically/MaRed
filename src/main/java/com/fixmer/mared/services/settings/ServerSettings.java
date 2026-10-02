package com.fixmer.mared.services.settings;

import com.fixmer.mared.MaredSettings;

/**
 * Настройки режима сервера.
 *
 * 0.3.0 (Phase F1): вынесено из MaredSettings.
 */
public final class ServerSettings {

    private MaredSettings.ServerMode serverMode = MaredSettings.ServerMode.AUTO;

    public MaredSettings.ServerMode serverMode() { return serverMode; }
    public void setServerMode(MaredSettings.ServerMode value) {
        serverMode = value == null ? MaredSettings.ServerMode.AUTO : value;
    }

    void resetToDefaults() {
        serverMode = MaredSettings.ServerMode.AUTO;
    }
}