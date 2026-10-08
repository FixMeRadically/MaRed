package com.fixmer.mared.gui2.welcome.scene;


import com.fixmer.mared.gui2.modules.ModuleId;
import com.fixmer.mared.gui2.visual.scene.VisualScene;



public interface WelcomeSceneProvider {



    ModuleId module();



    VisualScene create();



}