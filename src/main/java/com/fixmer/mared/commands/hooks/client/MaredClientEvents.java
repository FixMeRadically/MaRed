package com.fixmer.mared.commands.hooks.client;


import com.fixmer.mared.Mared;
import com.fixmer.mared.MaredSettings;

import com.fixmer.mared.gui2.framework.components.overlay.MaredCompactButton;
import com.fixmer.mared.gui2.launcher.MaredScreenManager;

import com.fixmer.mared.services.settings.LayoutSettings.ButtonCorner;


import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;


import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;



/**
 * Client GUI hooks.
 *
 * Добавляет кнопки MaRed в Minecraft screens.
 *
 * Основной вход:
 *
 * Minecraft Screen
 *        |
 *        v
 * MaredClientEvents
 *        |
 *        +----------------+
 *        |                |
 *        v                v
 * MaredShellLauncher   MaredWelcomeScreen
 *
 */
@EventBusSubscriber(
        modid = Mared.MOD_ID,
        value = Dist.CLIENT
)
public final class MaredClientEvents {



    private static final int BTN_W = 100;

    private static final int BTN_H = 20;




    private MaredClientEvents(){

    }





    @SubscribeEvent
    public static void onScreenInit(

            ScreenEvent.Init.Post event

    ){


        var screen = event.getScreen();




        if(screen instanceof PauseScreen
                || screen instanceof TitleScreen){


            addButtons(

                    event,

                    screen.width,

                    screen.height

            );


        }


    }







    private static void addButtons(

            ScreenEvent.Init.Post event,

            int screenWidth,

            int screenHeight

    ){



        ButtonCorner corner =

                MaredSettings.getMaRedButtonCorner();




        int offset =

                MaredSettings.getMaRedButtonOffset();





        int x;

        int y;





        switch(corner){


            case TOP_LEFT -> {

                x = offset;

                y = offset;

            }



            case BOTTOM_RIGHT -> {

                x =
                        screenWidth
                                - BTN_W
                                - offset;


                y =
                        screenHeight
                                - BTN_H
                                - offset;

            }



            case BOTTOM_LEFT -> {

                x = offset;


                y =
                        screenHeight
                                - BTN_H
                                - offset;

            }



            default -> {

                x =
                        screenWidth
                                - BTN_W
                                - offset;


                y = offset;

            }


        }






        /*
         * Основная кнопка MaRed
         *
         * Открывает новый Shell GUI
         */
        MaredCompactButton shellButton =

                new MaredCompactButton(

                        x,

                        y,

                        BTN_W,

                        BTN_H,

                        Component.literal("MaRed"),

                        0xFFFF55FF,


                        MaredScreenManager::openStudio

                );





        event.addListener(shellButton);








        /*
         * Временная кнопка проверки нового Welcome
         *
         * Используется только во время разработки.
         *
         * После завершения Welcome
         * будет удалена или заменена.
         */
        MaredCompactButton welcomeButton =

                new MaredCompactButton(

                        x,

                        y + BTN_H + 5,

                        BTN_W,

                        BTN_H,

                        Component.literal(
                                "Welcome Preview"
                        ),

                        0xFF55FF55,


                        MaredScreenManager::openWelcome

                );





        event.addListener(welcomeButton);



    }


}