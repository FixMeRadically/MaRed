package com.fixmer.mared.gui2.visual.scene;


import com.fixmer.mared.gui2.visual.effect.VisualEffect;
import com.fixmer.mared.gui2.visual.object.VisualObject;
import com.fixmer.mared.gui2.visual.scene.layer.LayerManager;
import com.fixmer.mared.gui2.visual.scene.layer.LayerType;

import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.List;



public final class VisualScene {



    private final LayerManager layers;


    private final List<VisualObject> objects =
            new ArrayList<>();


    private final List<VisualEffect> effects =
            new ArrayList<>();


    private final VisualEnvironment environment;



    public VisualScene(){


        layers =
                new LayerManager();


        environment =
                new VisualEnvironment();

    }




    public void addObject(
            VisualObject object
    ){

        objects.add(object);

    }




    public List<VisualObject> objects(){

        return objects;

    }





    public void add(
            VisualObject object
    ){

        objects.add(object);


        layers.add(

                LayerType.OBJECTS,

                object

        );

    }





    public void add(
            LayerType layer,
            VisualObject object
    ){


        objects.add(object);


        layers.add(

                layer,

                object

        );

    }





    public void addEffect(
            VisualEffect effect
    ){

        if(effect != null){

            effects.add(effect);

            effect.start();

        }

    }





    public List<VisualEffect> effects(){

        return effects;

    }





    public VisualEnvironment environment(){

        return environment;

    }





    public void tick(){


        layers.tick();



        for(VisualEffect effect : effects){

            effect.tick();

        }



        effects.removeIf(
                VisualEffect::finished
        );

    }





    public void render(
            GuiGraphics graphics
    ){


        layers.render(
                graphics
        );



        for(VisualEffect effect : effects){

            effect.render(
                    graphics
            );

        }

    }





    public LayerManager layers(){

        return layers;

    }


}