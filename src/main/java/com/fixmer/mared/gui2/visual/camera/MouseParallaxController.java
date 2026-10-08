package com.fixmer.mared.gui2.visual.camera;



public final class MouseParallaxController {



    private final CameraTransform camera;



    private float targetX;


    private float targetY;





    public MouseParallaxController(

            CameraTransform camera

    ){

        this.camera = camera;

    }





    public void updateMouse(

            double mouseX,

            double mouseY,

            int width,

            int height

    ){



        float nx =
                (float)
                (
                    mouseX / width
                    -
                    0.5f
                );



        float ny =
                (float)
                (
                    mouseY / height
                    -
                    0.5f
                );



        targetX =
                nx * 30f;



        targetY =
                ny * 20f;


    }





    public void tick(){


        camera.set(

                lerp(
                        (float) camera.x(),
                        targetX,
                        0.08f
                ),


                lerp(
                        (float) camera.y(),
                        targetY,
                        0.08f
                )

        );


    }





    private float lerp(

            float a,

            float b,

            float t

    ){

        return a +
                (b-a)
                *
                t;

    }


}