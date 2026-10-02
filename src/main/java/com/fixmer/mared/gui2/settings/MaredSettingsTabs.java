package com.fixmer.mared.gui2.settings;

import java.util.ArrayList;
import java.util.List;

import com.fixmer.mared.gui2.settings.tabs.MaredAboutTab;
import com.fixmer.mared.gui2.settings.tabs.MaredEditorTab;
import com.fixmer.mared.gui2.settings.tabs.MaredKeybindsTab;
import com.fixmer.mared.gui2.settings.tabs.MaredLayoutTab;
import com.fixmer.mared.gui2.settings.tabs.MaredLogsTab;
import com.fixmer.mared.gui2.settings.tabs.MaredThemeTab;

/**
 * Фабрика табов настроек.
 *
 * 0.3.0 (Stage B5): перенос legacy gui.settings.MaredSettingsTabs в gui2.
 */
public final class MaredSettingsTabs {

    private MaredSettingsTabs() {}

    public static List<MaredSettingsTab> createAll() {
        List<MaredSettingsTab> list = new ArrayList<>(6);
        list.add(new MaredLayoutTab());
        list.add(new MaredThemeTab());
        list.add(new MaredEditorTab());
        list.add(new MaredLogsTab());
        list.add(new MaredKeybindsTab());
        list.add(new MaredAboutTab());
        return list;
    }
}