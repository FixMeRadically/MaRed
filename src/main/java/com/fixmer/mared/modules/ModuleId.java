package com.fixmer.mared.gui2.modules;


public enum ModuleId {

    CONTENT(
        "Контент",
        0xFFFFAA33
    ),

    WORLD(
        "Мир",
        0xFF55CC66
    ),

    LOGIC(
        "Логика",
        0xFF9966FF
    ),

    RESOURCES(
        "Ресурсы",
        0xFF55CCFF
    ),

    TOOLS(
        "Инструменты",
        0xFFAAAAAA
    ),

    SCENARIOS(
        "Сценарии",
        0xFFFF6688
    );


    private final String title;
    private final int accent;


    ModuleId(
        String title,
        int accent
    ) {
        this.title = title;
        this.accent = accent;
    }


    public String title() {
        return title;
    }


    public int accent() {
        return accent;
    }
}