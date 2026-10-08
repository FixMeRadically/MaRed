package com.fixmer.mared.gui2.welcome.hero;



import com.fixmer.mared.gui2.modules.ModuleId;



public final class WelcomeModuleButton {



    private final ModuleId module;



    private final String title;



    private final int color;



    private float hover;



    private boolean selected;





    public WelcomeModuleButton(

            ModuleId module,

            String title,

            int color

    ){

        this.module = module;

        this.title = title;

        this.color = color;

    }





    public void updateHover(

            boolean hovered

    ){


        float target =
                hovered ? 1f : 0f;


        hover +=
                (target - hover)
                        * 0.15f;


    }





    public void select(){

        selected = true;

    }





    public void deselect(){

        selected = false;

    }





    public ModuleId module(){

        return module;

    }





    public String title(){

        return title;

    }





    public int color(){

        return color;

    }





    public float hover(){

        return hover;

    }





    public boolean selected(){

        return selected;

    }


}