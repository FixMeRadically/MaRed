package com.fixmer.mared.gui2.assets.loader;


import com.fixmer.mared.gui2.assets.ModelAsset;
import com.fixmer.mared.gui2.assets.MaredAssetRegistry;
import com.fixmer.mared.gui2.assets.TextureAsset;


import com.google.gson.Gson;


import java.io.InputStream;
import java.io.InputStreamReader;



public final class MaredAssetLoader {



    private static final Gson GSON =
            new Gson();





    private MaredAssetLoader(){

    }





    public static void load(){

        InputStream stream =
                MaredAssetLoader.class
                        .getResourceAsStream(
                                "/assets/mared/manifest.json"
                        );



        if(stream == null){

            return;

        }





        AssetManifest manifest =
                GSON.fromJson(

                        new InputStreamReader(stream),

                        AssetManifest.class

                );





        if(manifest.assets() == null){

            return;

        }





        for(
                AssetManifestEntry entry :
                manifest.assets()

        ){


            switch(entry.type()){


                case "texture" ->

                        MaredAssetRegistry.register(

                                new TextureAsset(

                                        entry.id(),

                                        entry.path()

                                )

                        );



                case "model" ->

                        MaredAssetRegistry.register(

                                new ModelAsset(

                                        entry.id(),

                                        entry.path()

                                )

                        );


            }


        }


    }


}