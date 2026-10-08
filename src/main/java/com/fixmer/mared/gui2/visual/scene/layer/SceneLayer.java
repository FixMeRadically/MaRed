package com.fixmer.mared.gui2.visual.scene.layer;


import com.fixmer.mared.gui2.visual.object.VisualObject;
import com.fixmer.mared.gui2.visual.camera.CameraTransform;


import net.minecraft.client.gui.GuiGraphics;


import java.util.ArrayList;
import java.util.List;



public final class SceneLayer {



    private final LayerType type;



    private final List<VisualObject> objects =
            new ArrayList<>();





    public SceneLayer(

            LayerType type

    ){

        this.type = type;

    }





    public void add(

            VisualObject object

    ){


        object.setLayer(

                type.parallax()

        );


        objects.add(object);


    }





    public void tick(){


        objects.forEach(

                VisualObject::tick

        );


    }





    public void render(

            GuiGraphics graphics,

            CameraTransform camera

    ){



        for(VisualObject object : objects){


            object.render(

                    graphics

            );


        }


    }





    public LayerType type(){

        return type;

    }





    public List<VisualObject> objects(){

        return objects;

    }


}