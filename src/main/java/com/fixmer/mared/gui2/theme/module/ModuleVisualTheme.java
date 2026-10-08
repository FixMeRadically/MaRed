package com.fixmer.mared.gui2.theme.module;


public final class ModuleVisualTheme {


    private final int primary;


    private final int secondary;


    private final int glow;


    private final int particle;


    private final int background;



    public ModuleVisualTheme(

            int primary,

            int secondary,

            int glow,

            int particle,

            int background

    ){

        this.primary = primary;

        this.secondary = secondary;

        this.glow = glow;

        this.particle = particle;

        this.background = background;

    }





    public int primary(){

        return primary;

    }





    public int secondary(){

        return secondary;

    }





    public int glow(){

        return glow;

    }





    public int particle(){

        return particle;

    }





    public int background(){

        return background;

    }


}