package com.fixmer.mared.gui2.welcome.particles;


import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;



public final class MaredParticleEmitter {



    private final List<MaredParticle> particles =
            new ArrayList<>();



    private final Random random =
            new Random();





    public void emitCode(
            float x,
            float y
    ){


        String[] symbols = {


                "{ }",

                "if",

                "->",

                "==",

                "event",

                "()",

                "[]"

        };



        for(int i = 0; i < 20; i++){


            MaredCodeParticle particle =
                    new MaredCodeParticle(

                            x +
                            random.nextInt(80)
                            -
                            40,


                            y +
                            random.nextInt(80)
                            -
                            40,


                            symbols[
                                    random.nextInt(
                                            symbols.length
                                    )
                            ]

                    );



            particle.velocity(

                    random.nextFloat()
                    -
                    0.5f,


                    random.nextFloat()
                    -
                    0.5f

            );


            particles.add(
                    particle
            );


        }


    }





    public void tick(){


        Iterator<MaredParticle> iterator =
                particles.iterator();



        while(iterator.hasNext()){


            MaredParticle particle =
                    iterator.next();



            particle.update();



            if(particle.dead()){

                iterator.remove();

            }


        }


    }





    public List<MaredParticle> particles(){

        return particles;

    }


}