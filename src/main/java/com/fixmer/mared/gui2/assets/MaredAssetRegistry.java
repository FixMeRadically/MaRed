package com.fixmer.mared.gui2.assets;


import java.util.HashMap;
import java.util.Map;



public final class MaredAssetRegistry {


    private static final Map<String,Object> ASSETS =
            new HashMap<>();



    private MaredAssetRegistry(){

    }



    public static void register(
            Object asset
    ){

        if(asset == null)
            return;


        ASSETS.put(
                asset.getClass().getSimpleName(),
                asset
        );

    }



    public static void register(
            String id,
            Object asset
    ){

        ASSETS.put(
                id,
                asset
        );

    }



    @SuppressWarnings("unchecked")
    public static <T> T get(
            String id
    ){

        return (T) ASSETS.get(id);

    }



    public static boolean contains(
            String id
    ){

        return ASSETS.containsKey(id);

    }


}