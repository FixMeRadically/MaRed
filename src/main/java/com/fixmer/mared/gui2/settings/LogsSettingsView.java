package com.fixmer.mared.gui2.settings;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.fixmer.mared.MaredSettings;
import com.fixmer.mared.services.logging.LogSettings;

/**
 * Draft-view настроек логов.
 *
 * 0.3.2: вынесено из SettingsContext.
 * Draft для level/category: изменения применяются при commit().
 */
public final class LogsSettingsView {

    private final MaredSettings.Snapshot s;

    private final Set<LogSettings.Level> draftLevels;
    private final Set<String>            draftCategories;

    LogsSettingsView(MaredSettings.Snapshot s) {
        this.s = s;
        this.draftLevels = new LinkedHashSet<>(LogSettings.getEnabledLevels());
        this.draftCategories = new LinkedHashSet<>(LogSettings.getEnabledCategories());
    }

    // ---- Behavior flags ----

    public boolean logChatToEditor()         { return s.logChatToEditor; }
    public void setLogChatToEditor(boolean v) { s.logChatToEditor = v; }

    public boolean verboseScriptLog()        { return s.verboseScriptLog; }
    public void setVerboseScriptLog(boolean v) { s.verboseScriptLog = v; }

    // ---- Levels ----

    public LogSettings.Level[] allLevels() {
        return LogSettings.Level.values();
    }

    public boolean levelEnabled(LogSettings.Level l) {
        return draftLevels.contains(l);
    }

    public void toggleLevel(LogSettings.Level l) {
        if (draftLevels.contains(l)) {
            if (draftLevels.size() <= 1) return;
            draftLevels.remove(l);
        } else {
            draftLevels.add(l);
        }
    }

    // ---- Categories ----

    public List<String> allCategories() {
        return List.copyOf(LogSettings.CATEGORIES);
    }

    public boolean categoryEnabled(String cat) {
        return draftCategories.contains(cat);
    }

    public void toggleCategory(String cat) {
        if (draftCategories.contains(cat)) {
            if (draftCategories.size() <= 1) return;
            draftCategories.remove(cat);
        } else {
            draftCategories.add(cat);
        }
    }

    // ---- Commit / Reset ----

    void commit() {
        LogSettings.applyDraft(draftLevels, draftCategories);
    }

    void resetToDefaults() {
        s.logChatToEditor  = true;
        s.verboseScriptLog = false;
        draftLevels.clear();
        for (LogSettings.Level l : LogSettings.Level.values()) draftLevels.add(l);
        draftCategories.clear();
        draftCategories.addAll(LogSettings.CATEGORIES);
    }
}