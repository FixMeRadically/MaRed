package com.fixmer.mared.gui2.welcome.hero;



import com.fixmer.mared.gui2.modules.ModuleId;



import java.util.ArrayList;
import java.util.List;



public final class WelcomeModuleSelector {



    private final List<WelcomeModuleButton> buttons =
            new ArrayList<>();



    private WelcomeModuleButton active;





    public WelcomeModuleSelector(){



    }





    public void register(

            WelcomeModuleButton button

    ){

        buttons.add(button);


        if(active == null){

            active = button;

            active.select();

        }

    }





    public void select(

            ModuleId id

    ){



        for(
                WelcomeModuleButton button :
                buttons

        ){


            if(button.module() == id){



                if(active != null){

                    active.deselect();

                }



                active = button;


                active.select();


                return;

            }


        }


    }





    public WelcomeModuleButton active(){

        return active;

    }





    public List<WelcomeModuleButton> buttons(){

        return buttons;

    }


}