package com.fixmer.mared.gui2.launcher;

import com.fixmer.mared.gui2.navigation.SpaceId;
import com.fixmer.mared.gui2.shell.MaredShellScreen;
import com.fixmer.mared.gui2.studio.MaredStudioScreen;
import com.fixmer.mared.gui2.welcome.screen.MaredOnboardingScreen;
import com.fixmer.mared.welcome.MaredWelcomeStorage;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/**
 * v13:
 *   openStudio()    → Shell boot(STUDIO)
 *   openGenesis()   → Shell boot(GENESIS)
 *   openWelcome()   → онбординг (кнопка "Welcome Preview" сохраняется
 *                     для отладки онбординга) или openGenesis().
 *
 * MaredStudioScreen больше не используется как основная точка входа —
 * он остаётся как legacy-класс и может быть удалён позже.
 */
public final class MaredScreenManager {

    private MaredScreenManager() {}

    public static void openShell() {
        Minecraft.getInstance().setScreen(new MaredShellScreen());
    }

    public static void openStudio() {
        Minecraft.getInstance().setScreen(
            new MaredShellScreen().setInitialSpace(SpaceId.STUDIO));
    }

    public static void openGenesis() {
        Minecraft.getInstance().setScreen(
            new MaredShellScreen().setInitialSpace(SpaceId.GENESIS));
    }

    /** Legacy: прямой Screen Studio — оставлен для отладки. */
    public static void openLegacyStudio() {
        Minecraft.getInstance().setScreen(new MaredStudioScreen());
    }

    public static void openWelcome() {
        if (MaredWelcomeStorage.hasSeen()) {
            openGenesis();
        } else {
            Minecraft.getInstance().setScreen(new MaredOnboardingScreen());
        }
    }

    public static void open(Screen screen) {
        Minecraft.getInstance().setScreen(screen);
    }
}
