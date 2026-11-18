package com.fixmer.genesis.technology.server.remote;
/** Scope of the custom paint/input hooks. External dialogs and unrelated connections stay native. */
public final class ReturnPresentation110 {
    private ReturnPresentation110(){}
    public enum Surface { NONE, COVER, OWN_CONNECT, RECEIVING, DISCONNECTED, OTHER }
    public static boolean masks(RestartReturn108.Phase phase,Surface surface,boolean attemptOwned,boolean current,boolean overlay,boolean detached){
        if(!current||overlay)return false;
        if(phase==RestartReturn108.Phase.ARMED)return surface==Surface.DISCONNECTED&&detached;
        if(phase!=RestartReturn108.Phase.WAITING&&phase!=RestartReturn108.Phase.CONNECTING&&phase!=RestartReturn108.Phase.LOADING&&phase!=RestartReturn108.Phase.PROMPT)return false;
        return switch(surface){case OWN_CONNECT,RECEIVING->attemptOwned;case DISCONNECTED->detached;default->false;};
    }
}
