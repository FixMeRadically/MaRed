package com.fixmer.mared.services.settings;

/**
 * Настройки логирования поведения скриптов.
 *
 * 0.3.0 (Phase F1): вынесено из MaredSettings.
 * ВНИМАНИЕ: не путать с services.logging.LogSettings — тот про фильтр
 * сообщений в консоли, этот — про то, что писать в лог.
 */
public final class LogBehaviorSettings {

    private boolean logChatToEditor = true;
    private boolean verboseScriptLog = false;

    public boolean logChatToEditor() { return logChatToEditor; }
    public void setLogChatToEditor(boolean value) { logChatToEditor = value; }

    public boolean verboseScriptLog() { return verboseScriptLog; }
    public void setVerboseScriptLog(boolean value) { verboseScriptLog = value; }

    void resetToDefaults() {
        logChatToEditor = true;
        verboseScriptLog = false;
    }
}