package com.fixmer.mared.gui2.welcome.data;


import com.fixmer.mared.gui2.assets.MaredAsset;
import com.fixmer.mared.gui2.assets.ModelAsset;
import com.fixmer.mared.gui2.assets.TextureAsset;
import com.fixmer.mared.gui2.assets.MaredAssetRegistry;

import com.fixmer.mared.gui2.visual.object.ModelVisualObject;
import com.fixmer.mared.gui2.visual.object.TextureVisualObject;
import com.fixmer.mared.gui2.visual.object.VisualObject;

import com.fixmer.mared.gui2.visual.camera.ParallaxLayer;



public final class WelcomeObjectFactory {


    private WelcomeObjectFactory(){

    }




    public static VisualObject create(

            WelcomeObjectData data

    ){


        MaredAsset asset =

                MaredAssetRegistry.get(

                        data.asset()

                );



        if(asset == null){

            throw new IllegalStateException(

                    "Missing asset: "
                    + data.asset()

            );

        }




        VisualObject object;



        switch(asset.type()){


            case MODEL -> {

                object =

                        new ModelVisualObject(

                                (ModelAsset) asset

                        );

            }



            case TEXTURE -> {

                object =

                        new TextureVisualObject(

                                (TextureAsset) asset

                        );

            }



            default -> throw new IllegalArgumentException(

                    "Unsupported asset "
                    + asset.type()

            );

        }





        object.setPosition(

                data.x(),

                data.y()

        );





        if(data.properties() != null){


            String layer =

                    data.properties()

                            .get("layer");



            if(layer != null){


                switch(layer){


                    case "BACKGROUND" ->

                            object.setLayer(

                                    ParallaxLayer.BACKGROUND

                            );


                    case "PARTICLES" ->

                            object.setLayer(

                                    ParallaxLayer.PARTICLES

                            );


                    case "SYMBOLS" ->

                            object.setLayer(

                                    ParallaxLayer.SYMBOLS

                            );


                    case "OBJECTS" ->

                            object.setLayer(

                                    ParallaxLayer.OBJECTS

                            );


                }


            }


        }





        return object;


    }


}