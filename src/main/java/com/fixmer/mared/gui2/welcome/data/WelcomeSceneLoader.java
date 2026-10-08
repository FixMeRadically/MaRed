package com.fixmer.mared.gui2.welcome.data;


import com.google.gson.Gson;
import com.google.gson.GsonBuilder;


import java.io.InputStream;
import java.io.InputStreamReader;



public final class WelcomeSceneLoader {



    private static final Gson GSON =
            new GsonBuilder()
                    .create();





    private WelcomeSceneLoader(){

    }





    public static WelcomeSceneData load(

            String path

    ){


        InputStream stream =
                WelcomeSceneLoader.class
                        .getResourceAsStream(path);



        if(stream == null){

            throw new IllegalStateException(
                    "Missing welcome scene: "
                    + path
            );

        }



        return GSON.fromJson(

                new InputStreamReader(stream),

                WelcomeSceneData.class

        );


    }


}