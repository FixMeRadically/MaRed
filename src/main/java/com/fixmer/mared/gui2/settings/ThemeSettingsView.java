package com.fixmer.mared.gui2.settings;

import java.util.List;

import com.fixmer.mared.MaredSettings;
import com.fixmer.mared.gui2.framework.theme.MaredTheme;
import com.fixmer.mared.services.theme.ThemeService;

/**
 * Draft-view настроек темы.
 *
 * 0.3.2: вынесено из SettingsContext.
 * 0.3.2 (audit #76 / #104): reducedMotion.
 */
public final class ThemeSettingsView {

    private final MaredSettings.Snapshot s;

    ThemeSettingsView(MaredSettings.Snapshot s) {
        this.s = s;
    }

    // ---- Theme id ----

    public String themeId()              { return s.themeId; }
    public void setThemeId(String v)     { s.themeId = v; }

    // ---- Options ----

    public boolean monotoneTabs()        { return s.monotoneTabs; }
    public void setMonotoneTabs(boolean v) { s.monotoneTabs = v; }

    public boolean patternsEnabled()     { return s.patternsEnabled; }
    public void setPatternsEnabled(boolean v) { s.patternsEnabled = v; }

    public boolean tornEdgesEnabled()    { return s.tornEdgesEnabled; }
    public void setTornEdgesEnabled(boolean v) { s.tornEdgesEnabled = v; }

    public boolean reducedMotion()       { return s.reducedMotion; }
    public void setReducedMotion(boolean v) { s.reducedMotion = v; }

    // ---- Registry lookups ----

    public List<MaredTheme> availableThemes() {
        return ThemeService.get().all();
    }

    public MaredTheme themeById(String id) {
        return ThemeService.get().byId(id);
    }

    public int themeCount() {
        return ThemeService.get().count();
    }

    // ---- Reset ----

    void resetToDefaults() {
        s.themeId          = "mared";
        s.monotoneTabs     = false;
        s.patternsEnabled  = true;
        s.tornEdgesEnabled = false;
        s.reducedMotion    = false;
    }
}