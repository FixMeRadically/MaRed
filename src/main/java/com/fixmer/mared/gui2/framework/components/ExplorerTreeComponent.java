package com.fixmer.mared.gui2.framework.components;


import com.fixmer.mared.gui2.framework.core.MaredComponent;
import com.fixmer.mared.gui2.framework.core.MaredRenderContext;



/**
 * Дерево проекта MaRed.
 *
 * Пока статическое.
 */
public final class ExplorerTreeComponent
        extends MaredComponent {



    @Override
    protected void safeRender(
            MaredRenderContext context
    ){



        int x =
                bounds.x() + 10;



        int y =
                bounds.y() + 30;




        int color =
                0xFFFFFFFF;



        int line =
                18;







        draw(
                context,
                "📁 Project",
                x,
                y,
                color
        );



        draw(
                context,
                "   📁 assets",
                x,
                y + line,
                color
        );



        draw(
                context,
                "   📁 models",
                x,
                y + line * 2,
                color
        );



        draw(
                context,
                "   📁 textures",
                x,
                y + line * 3,
                color
        );



        draw(
                context,
                "   📁 scripts",
                x,
                y + line * 4,
                color
        );



        draw(
                context,
                "   📁 scenes",
                x,
                y + line * 5,
                color
        );


    }









    private void draw(
            MaredRenderContext context,
            String text,
            int x,
            int y,
            int color
    ){


        context.graphics()
                .drawString(
                        context.font(),
                        text,
                        x,
                        y,
                        color
                );


    }


}