package com.fixmer.mared.gui2.background;


import java.util.ArrayList;
import java.util.List;



public final class MaredParallaxScene {



    private final List<MaredParallaxLayer> layers =
            new ArrayList<>();





    public void add(
            MaredParallaxLayer layer
    ){

        layers.add(layer);

    }





    public void update(
            float mouseX,
            float mouseY
    ){


        for(
                MaredParallaxLayer layer :
                layers
        ){

            layer.update(
                    mouseX,
                    mouseY
            );

        }

    }





    public List<MaredParallaxLayer> layers(){

        return layers;

    }


}