package com.fixmer.mared.gui2.visual.effect;



import java.util.ArrayList;
import java.util.List;



public final class ParticleEmitter {



    private final List<Particle> particles =
            new ArrayList<>();





    public void emit(
            int count
    ){


        for(int i = 0; i < count; i++){


            particles.add(

                    new Particle()

            );


        }


    }





    public void tick(){


        particles.removeIf(

                Particle::dead

        );



        particles.forEach(

                Particle::tick

        );


    }





    public List<Particle> particles(){

        return particles;

    }


}