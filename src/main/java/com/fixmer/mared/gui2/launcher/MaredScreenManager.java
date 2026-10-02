package com.fixmer.mared.gui2.launcher;


import net.minecraft.client.Minecraft;

import com.fixmer.mared.gui2.studio.MaredStudioScreen;



/**
 * Управляет открытием экранов MaRed.
 */
public final class MaredScreenManager {



    private MaredScreenManager(){}





    public static void openStudio(){


        Minecraft.getInstance()
                .setScreen(
                        new MaredStudioScreen()
                );


    }


}