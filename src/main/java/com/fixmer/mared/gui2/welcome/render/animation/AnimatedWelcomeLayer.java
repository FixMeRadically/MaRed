package com.fixmer.mared.gui2.welcome.render.animation;



import java.util.HashMap;
import java.util.Map;



public abstract class AnimatedWelcomeLayer {



    private final Map<String, WelcomeLayerBinding> bindings =
            new HashMap<>();





    protected void registerLayer(

            String id

    ){

        bindings.put(

                id,

                new WelcomeLayerBinding(id)

        );

    }





    public void update(

            String id,

            float progress

    ){


        WelcomeLayerBinding binding =
                bindings.get(id);



        if(binding == null)

            return;



        binding.update(progress);


    }





    public LayerAnimationState state(

            String id

    ){


        WelcomeLayerBinding binding =
                bindings.get(id);



        if(binding == null)

            return null;



        return binding.state();

    }


}