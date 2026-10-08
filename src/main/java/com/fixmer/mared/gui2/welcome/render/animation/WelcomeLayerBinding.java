package com.fixmer.mared.gui2.welcome.render.animation;



public final class WelcomeLayerBinding {



    private final String layerId;


    private final LayerAnimationState state;





    public WelcomeLayerBinding(

            String layerId

    ){

        this.layerId = layerId;


        this.state =
                new LayerAnimationState();

    }





    public void update(

            float progress

    ){


        state.update(progress);


    }





    public String layerId(){

        return layerId;

    }





    public LayerAnimationState state(){

        return state;

    }


}