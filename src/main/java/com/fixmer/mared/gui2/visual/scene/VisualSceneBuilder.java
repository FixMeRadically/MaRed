package com.fixmer.mared.gui2.visual.scene;



import com.fixmer.mared.gui2.visual.object.VisualObject;



public final class VisualSceneBuilder {



    private final VisualScene scene;





    public VisualSceneBuilder(){


        scene =
                new VisualScene();


    }





    public VisualSceneBuilder object(

            VisualObject object

    ){


        scene.addObject(object);


        return this;

    }





    public VisualSceneBuilder background(

            int color

    ){


        scene.environment()
                .backgroundColor(color);


        return this;

    }





    public VisualSceneBuilder fog(

            float value

    ){


        scene.environment()
                .fog(value);


        return this;

    }





    public VisualScene build(){

        return scene;

    }


}