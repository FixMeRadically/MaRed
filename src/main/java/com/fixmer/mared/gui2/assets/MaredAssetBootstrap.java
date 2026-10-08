package com.fixmer.mared.gui2.assets;



import com.fixmer.mared.gui2.assets.loader.MaredAssetLoader;



public final class MaredAssetBootstrap {



    private MaredAssetBootstrap(){

    }





    public static void bootstrap(){


        MaredAssetLoader.load();


    }


}