package com.fixmer.mared.gui2.welcome.profile;


import com.fixmer.mared.gui2.welcome.animation.WelcomeTimeline;



public final class WelcomeAnimationProfile {



    private final String id;



    private final WelcomeTimeline timeline;




    public WelcomeAnimationProfile(

            String id,

            WelcomeTimeline timeline

    ){

        this.id = id;

        this.timeline = timeline;

    }





    public String id(){

        return id;

    }





    public WelcomeTimeline timeline(){

        return timeline;

    }


}