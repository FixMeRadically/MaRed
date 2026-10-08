package com.fixmer.mared.gui2.welcome.screen;

import com.fixmer.mared.welcome.MaredWelcomeStorage;

import net.minecraft.client.Minecraft;

/**
 * Genesis: РјР°СЂС€СЂСѓС‚РёР·Р°С†РёСЏ РІС…РѕРґР°.
 *
 *   open()        в†’ hasSeen ? Genesis : Onboarding
 *   openGenesis() в†’ Genesis (РІСЃРµРіРґР°)
 *   openOnboarding() в†’ Onboarding (РїСЂРёРЅСѓРґРёС‚РµР»СЊРЅРѕ)
 *
 * Onboarding СЃР°Рј РІС‹СЃС‚Р°РІР»СЏРµС‚ С„Р»Р°Рі Рё РІС‹Р·С‹РІР°РµС‚ openGenesis().
 */
public final class WelcomeScreenManager {

    private WelcomeScreenManager() {}

    public static void open() {
        if (MaredWelcomeStorage.hasSeen()) {
            openGenesis();
        } else {
            openOnboarding();
        }
    }

    public static void openGenesis() {
        Minecraft.getInstance().setScreen(new MaredWelcomeScreen());
    }

    public static void openOnboarding() {
        Minecraft.getInstance().setScreen(new MaredOnboardingScreen());
    }

    public static void resetAndReplay() {
        MaredWelcomeStorage.reset();
        openOnboarding();
    }
}