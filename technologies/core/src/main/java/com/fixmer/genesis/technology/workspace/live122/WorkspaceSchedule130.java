package com.fixmer.genesis.technology.workspace.live122;

/** Fair serial transport: sustained typing cannot starve cursors or project updates. */
public final class WorkspaceSchedule130 {
    public enum Lane { EDIT, PRESENCE, BACKGROUND, CATALOG, SOURCE }
    private final long[] next=new long[Lane.values().length];
    private final boolean[] scheduled=new boolean[Lane.values().length];
    private int turn;
    private static final long[] INTERVAL={50_000_000L,150_000_000L,1_200_000_000L,2_000_000_000L,2_000_000_000L};
    public Lane next(long now,boolean edit,boolean presence,boolean background,boolean catalog,boolean source){
        boolean[] ready={edit,presence,background,catalog,source};
        for(int i=0;i<ready.length;i++){
            int lane=(turn+i)%ready.length;
            if(ready[lane]&&(!scheduled[lane]||now-next[lane]>=0)){scheduled[lane]=true;next[lane]=now+INTERVAL[lane];turn=(lane+1)%ready.length;return Lane.values()[lane];}
        }
        return null;
    }
}
