package com.fixmer.mared.gui2.visual.scene.layer;


import com.fixmer.mared.gui2.visual.object.VisualObject;


import net.minecraft.client.gui.GuiGraphics;


import java.util.EnumMap;
import java.util.Map;



public final class LayerManager {



    private final Map<LayerType, SceneLayer> layers =
            new EnumMap<>(LayerType.class);





    public LayerManager(){



        for(LayerType type : LayerType.values()){


            layers.put(

                    type,

                    new SceneLayer(type)

            );


        }


    }





    public void add(

            LayerType type,

            VisualObject object

    ){


        layers.get(type)

                .add(object);


    }





    public void tick(){


        layers.values()

                .forEach(

                        SceneLayer::tick

                );


    }





    public void render(

            GuiGraphics graphics

    ){



        layers.values()

                .stream()

                .sorted(

                        (a,b) ->
                                Integer.compare(

                                        a.type().priority(),

                                        b.type().priority()

                                )

                )

                .forEach(

                        layer ->

                                layer.render(

                                        graphics,

                                        null

                                )

                );


    }





    public SceneLayer get(

            LayerType type

    ){

        return layers.get(type);

    }


}