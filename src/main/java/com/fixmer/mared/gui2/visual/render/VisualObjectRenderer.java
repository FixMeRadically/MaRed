package com.fixmer.mared.gui2.visual.render;


import com.fixmer.mared.gui2.visual.object.VisualObject;



public interface VisualObjectRenderer {



    void render(

            VisualObject object,

            VisualRenderContext context

    );


}