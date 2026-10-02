package com.fixmer.mared.gui2.framework.components;


import java.util.ArrayList;
import java.util.List;


import com.fixmer.mared.gui2.framework.core.MaredRenderContext;



/**
 * Область со скроллом.
 */
public class MaredScrollArea
        extends MaredPanel {



    private final List<String> lines =
            new ArrayList<>();


    private int scroll;



    public void addLine(
            String text
    ){

        lines.add(text);

    }




    public void clear(){

        lines.clear();

        scroll = 0;

    }





    public void scroll(
            int amount
    ){

        scroll += amount;


        if(scroll < 0)
            scroll = 0;


        if(scroll > lines.size())
            scroll = lines.size();

    }





    @Override
    protected void safeRender(
            MaredRenderContext context
    ){

        super.safeRender(context);



        int y =
                bounds.y()
                +
                8;



        int index =
                scroll;



        while(
                index < lines.size()
        ){

            context.graphics()
                    .drawString(
                            net.minecraft.client.Minecraft
                                    .getInstance()
                                    .font,

                            lines.get(index),

                            bounds.x()+8,

                            y,

                            0xFFFFFFFF
                    );

            y += 12;


            if(y > bounds.bottom())
                break;


            index++;

        }

    }


}