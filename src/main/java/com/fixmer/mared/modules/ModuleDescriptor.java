package com.fixmer.mared.modules;


public final class ModuleDescriptor {


    private final String id;

    private final ModuleAvailability availability;

    private final String icon;

    private final String displayNameKey;

    private final String descriptionKey;



    public ModuleDescriptor(
            String id
    ){

        this(
                id,
                ModuleAvailability.AVAILABLE,
                "",
                "module." + id + ".name",
                "module." + id + ".description"
        );

    }



    public ModuleDescriptor(

            String id,

            ModuleAvailability availability,

            String icon,

            String displayNameKey,

            String descriptionKey

    ){

        this.id=id;

        this.availability=availability;

        this.icon=icon;

        this.displayNameKey=displayNameKey;

        this.descriptionKey=descriptionKey;

    }



    public String id(){

        return id;

    }



    public ModuleAvailability availability(){

        return availability;

    }



    public String icon(){

        return icon;

    }



    public String displayNameKey(){

        return displayNameKey;

    }



    public String descriptionKey(){

        return descriptionKey;

    }


}