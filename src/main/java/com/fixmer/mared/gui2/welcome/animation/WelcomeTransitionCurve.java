package com.fixmer.mared.gui2.welcome.animation;


public enum WelcomeTransitionCurve {


    LINEAR,


    EASE_IN,


    EASE_OUT,


    EASE_IN_OUT;



    public float apply(
            float value
    ){


        return switch(this){


            case LINEAR ->
                    value;



            case EASE_IN ->
                    value * value;



            case EASE_OUT ->
                    1f -
                    (1f - value)
                    *
                    (1f - value);



            case EASE_IN_OUT -> {


                if(value < 0.5f){

                    yield
                            2f
                            *
                            value
                            *
                            value;

                }


                yield
                        1f -
                        (
                                (float)Math.pow(
                                        -2f * value + 2f,
                                        2
                                )
                                /
                                2f
                        );


            }


        };


    }


}