package com.fixmer.mared.gui2.welcome.data.validation;


import com.fixmer.mared.gui2.assets.MaredAssetRegistry;
import com.fixmer.mared.gui2.welcome.data.WelcomeObjectData;
import com.fixmer.mared.gui2.welcome.data.WelcomeSceneData;



public final class WelcomeSceneValidator {



    private WelcomeSceneValidator(){

    }





    public static WelcomeValidationResult validate(

            WelcomeSceneData scene

    ){


        WelcomeValidationResult result =
                new WelcomeValidationResult();





        if(scene == null){

            result.add(

                    "scene",

                    "Scene is null"

            );

            return result;

        }





        if(scene.objects() == null){

            result.add(

                    "objects",

                    "Objects list is missing"

            );

            return result;

        }





        int index = 0;



        for(

                WelcomeObjectData object :

                scene.objects()

        ){



            validateObject(

                    object,

                    index,

                    result

            );



            index++;

        }



        return result;

    }





    private static void validateObject(

            WelcomeObjectData object,

            int index,

            WelcomeValidationResult result

    ){



        String prefix =
                "objects[" + index + "]";





        if(object.type() == null){

            result.add(

                    prefix + ".type",

                    "Missing object type"

            );

        }





        if(object.asset() == null){

            result.add(

                    prefix + ".asset",

                    "Missing asset reference"

            );

            return;

        }





        if(!MaredAssetRegistry.contains(

                object.asset()

        )){


            result.add(

                    prefix + ".asset",

                    "Asset not found: "
                    + object.asset()

            );


        }


    }


}