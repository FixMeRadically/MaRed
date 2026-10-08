package com.fixmer.mared.gui2.assets;



public final class TextureAsset
        implements MaredAsset {



    private final String id;



    private final String path;





    public TextureAsset(

            String id,

            String path

    ){

        this.id = id;

        this.path = path;

    }





    @Override
    public String id(){

        return id;

    }





    @Override
    public MaredAssetType type(){

        return MaredAssetType.TEXTURE;

    }





    public String path(){

        return path;

    }


}