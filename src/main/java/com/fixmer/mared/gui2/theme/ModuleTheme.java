package com.fixmer.mared.gui2.theme;


public final class ModuleTheme {


    private final String id;

    private final String name;

    private final int accent;




    public ModuleTheme(
            String id,
            String name,
            int accent
    ){

        this.id = id;
        this.name = name;
        this.accent = accent;

    }




    /*
     * Совместимость со старой системой
     */
    public ModuleTheme(
            String id,
            AccentColor color
    ){

        this.id = id;

        this.name = id;

        this.accent = color.value();

    }




    public String id(){

        return id;

    }



    public String name(){

        return name;

    }



    public int accent(){

        return accent;

    }


}