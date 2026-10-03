package com.fixmer.mared.gui2.modules.theme;



/**
 * Основные акцентные цвета MaRed Studio.
 *
 * Используются:
 *
 * - ModuleTheme
 * - MaredThemeManager
 * - Dock панели
 */
public enum AccentColor {


    /**
     * Контент:
     * предметы, блоки,
     * модели, NPC, UI
     */
    CONTENT(
            0xFFFF8800
    ),



    /**
     * Мир:
     * генерация,
     * измерения,
     * структуры
     */
    WORLD(
            0xFF45C46B
    ),



    /**
     * Логика:
     * скрипты,
     * события,
     * AI
     */
    LOGIC(
            0xFF3B82F6
    ),



    /**
     * Ресурсы:
     * ассеты,
     * текстуры,
     * локализация
     */
    RESOURCES(
            0xFFA855F7
    ),



    /**
     * Инструменты:
     * debug,
     * profiler,
     * API
     */
    TOOLS(
            0xFFEAB308
    ),



    /**
     * Сценарии и геймплей
     */
    GAMEPLAY(
            0xFFEF4444
    );





    private final int color;





    AccentColor(
            int color
    ){

        this.color = color;

    }






    public int value(){

        return color;

    }



}