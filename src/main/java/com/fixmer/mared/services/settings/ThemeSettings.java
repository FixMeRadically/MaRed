package com.fixmer.mared.services.settings;

/**
 * Настройки темы и визуальных украшений.
 *
 * 0.3.0 (Phase F1): вынесено из MaredSettings.
 */
public final class ThemeSettings {

    private String  themeId = "mared";
    private boolean monotoneTabs = false;
    private boolean patternsEnabled = true;
    private boolean tornEdgesEnabled = false;

    public String themeId() { return themeId; }
    public void setThemeId(String value) {
        themeId = (value == null || value.isBlank()) ? "mared" : value;
    }

    public boolean monotoneTabs() { return monotoneTabs; }
    public void setMonotoneTabs(boolean value) { monotoneTabs = value; }

    public boolean patternsEnabled() { return patternsEnabled; }
    public void setPatternsEnabled(boolean value) { patternsEnabled = value; }

    public boolean tornEdgesEnabled() { return tornEdgesEnabled; }
    public void setTornEdgesEnabled(boolean value) { tornEdgesEnabled = value; }

    void resetToDefaults() {
        themeId = "mared";
        monotoneTabs = false;
        patternsEnabled = true;
        tornEdgesEnabled = false;
    }
}