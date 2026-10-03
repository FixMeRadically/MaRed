package com.fixmer.mared.gui2.launcher;

import com.fixmer.mared.gui2.studio.ScreenNavigator;

/**
 * Управляет открытием экранов MaRed.
 *
 * 0.3.1: делегирует в ScreenNavigator.
 */
public final class MaredScreenManager {

    private MaredScreenManager() {}

    public static void openStudio() {
        ScreenNavigator.openStudio();
    }
}