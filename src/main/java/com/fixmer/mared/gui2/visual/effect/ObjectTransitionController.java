package com.fixmer.mared.gui2.visual.effect;



import java.util.ArrayList;
import java.util.List;



public final class ObjectTransitionController {



    private final List<VisualEffect> effects =
            new ArrayList<>();





    public void add(

            VisualEffect effect

    ){


        effect.start();


        effects.add(effect);


    }





    public void tick(){


        effects.forEach(

                VisualEffect::tick

        );


        effects.removeIf(

                VisualEffect::finished

        );


    }





    public List<VisualEffect> effects(){

        return effects;

    }


}