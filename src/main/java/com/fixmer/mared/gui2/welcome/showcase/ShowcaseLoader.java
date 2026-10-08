package com.fixmer.mared.gui2.welcome.showcase;


import com.google.gson.Gson;

import java.io.InputStream;
import java.io.InputStreamReader;



public final class ShowcaseLoader {



    private static final Gson GSON =
            new Gson();





    private ShowcaseLoader(){

    }





    public static ModuleShowcaseProfile load(

            String path

    ){



        InputStream stream =
                ShowcaseLoader.class
                        .getResourceAsStream(path);





        if(stream == null){

            throw new IllegalStateException(

                    "Missing showcase profile "
                    + path

            );

        }





        ShowcaseJson json =
                GSON.fromJson(

                        new InputStreamReader(stream),

                        ShowcaseJson.class

                );





        return new ModuleShowcaseProfile(

                json.module,

                json.color,

                new ModuleTransitionProfile(

                        json.intro,

                        json.transition,

                        json.finalState

                )

        );


    }





    private static final class ShowcaseJson {


        String module;


        int color;


        String intro;


        String transition;


        String finalState;


    }


}