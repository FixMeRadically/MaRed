package com.fixmer.mared.gui2.welcome.data.validation;



public final class WelcomeValidationError {



    private final String field;


    private final String message;





    public WelcomeValidationError(

            String field,

            String message

    ){

        this.field = field;

        this.message = message;

    }





    public String field(){

        return field;

    }





    public String message(){

        return message;

    }





    @Override
    public String toString(){

        return field + ": " + message;

    }


}