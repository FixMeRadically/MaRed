package com.fixmer.mared.gui2.welcome.showcase;



public final class ModuleShowcaseProfile {



    private final String moduleId;


    private final int color;


    private final ModuleTransitionProfile transition;





    public ModuleShowcaseProfile(

            String moduleId,

            int color,

            ModuleTransitionProfile transition

    ){

        this.moduleId = moduleId;

        this.color = color;

        this.transition = transition;

    }





    public String moduleId(){

        return moduleId;

    }





    public int color(){

        return color;

    }





    public ModuleTransitionProfile transition(){

        return transition;

    }


}