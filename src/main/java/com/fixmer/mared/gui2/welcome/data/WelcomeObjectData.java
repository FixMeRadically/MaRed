package com.fixmer.mared.gui2.welcome.data;


import java.util.Map;



public final class WelcomeObjectData {



    private final String type;


    private final String asset;


    private final float x;


    private final float y;


    private final Map<String,String> properties;





    public WelcomeObjectData(

            String type,

            String asset,

            float x,

            float y,

            Map<String,String> properties

    ){

        this.type = type;

        this.asset = asset;

        this.x = x;

        this.y = y;

        this.properties = properties;

    }





    public String type(){

        return type;

    }





    public String asset(){

        return asset;

    }





    public float x(){

        return x;

    }





    public float y(){

        return y;

    }





    public Map<String,String> properties(){

        return properties;

    }


}