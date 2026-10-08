package com.fixmer.mared.gui2.welcome.data;


import java.util.List;



public final class WelcomeSceneData {



    private final int background;



    private final List<WelcomeObjectData> objects;





    public WelcomeSceneData(

            int background,

            List<WelcomeObjectData> objects

    ){

        this.background = background;

        this.objects = objects;

    }





    public int background(){

        return background;

    }





    public List<WelcomeObjectData> objects(){

        return objects;

    }


}