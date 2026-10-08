package com.fixmer.mared.commands.input;

import net.minecraft.server.MinecraftServer;

/** One shared path for native keyboard/mouse events and recording integration hosts. */
public final class MaredInputBridge {
    private MaredInputBridge() {}
    public static void input(int code,int action,MinecraftServer server){
        if(action!=0&&action!=1)return;
        for(String key:MaredBindRegistry.keysForCode(code)){
            if(action==1){MaredBindRegistry.fire(key,server);MaredBindRegistry.startHold(key,server);}
            else{MaredBindRegistry.stopHold(key);MaredBindRegistry.fireRelease(key,server);}
        }
    }
    public static void tick(MinecraftServer server,boolean enabled){
        if(enabled)MaredBindRegistry.tickHolds(server);else MaredBindRegistry.stopAllHolds();
    }
}
