package com.fixmer.mared.gui2.welcome.data.validation;



import java.util.ArrayList;
import java.util.List;



public final class WelcomeValidationResult {



    private final List<WelcomeValidationError> errors;





    public WelcomeValidationResult(){


        errors =
                new ArrayList<>();


    }





    public void add(

            String field,

            String message

    ){

        errors.add(

                new WelcomeValidationError(

                        field,

                        message

                )

        );

    }





    public boolean valid(){

        return errors.isEmpty();

    }





    public List<WelcomeValidationError> errors(){

        return errors;

    }


}