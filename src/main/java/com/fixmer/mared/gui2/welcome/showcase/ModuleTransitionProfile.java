package com.fixmer.mared.gui2.welcome.showcase;


public final class ModuleTransitionProfile {



    private final String intro;


    private final String transition;


    private final String finalState;





    public ModuleTransitionProfile(

            String intro,

            String transition,

            String finalState

    ){

        this.intro = intro;

        this.transition = transition;

        this.finalState = finalState;

    }





    public String intro(){

        return intro;

    }





    public String transition(){

        return transition;

    }





    public String finalState(){

        return finalState;

    }


}