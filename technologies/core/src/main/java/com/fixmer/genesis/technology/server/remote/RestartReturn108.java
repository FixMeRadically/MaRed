package com.fixmer.genesis.technology.server.remote;
import java.util.*;
/** One explicitly requested restart, one connection attempt at a time. Monotonic milliseconds. */
public final class RestartReturn108 {
    public enum Phase { IDLE, PENDING, ARMED, WAITING, CONNECTING, LOADING, PROMPT, COMPLETE, CANCELLED }
    private Phase phase=Phase.IDLE;private UUID request;private long since,round,next,attemptSince;private int attempts;private long paused=-1,loadingSince;
    public Phase phase(){return phase;}public int attempts(){return attempts;}
    public boolean active(){return !Set.of(Phase.IDLE,Phase.COMPLETE,Phase.CANCELLED).contains(phase);}
    public boolean begin(UUID id,long now){if(active()||id==null)return false;request=id;phase=Phase.PENDING;since=now;attempts=0;paused=-1;return true;}
    public void answer(UUID id,boolean queued,long now){if(phase!=Phase.PENDING||!Objects.equals(id,request))return;phase=queued?Phase.ARMED:Phase.CANCELLED;since=now;}
    public void tick(long now,boolean originalConnection,boolean joined,boolean transportBusy){
        if(!active()||paused>=0)return;
        if(phase==Phase.PENDING){if(now-since>=10000)phase=Phase.CANCELLED;return;}
        if(phase==Phase.ARMED){if(!originalConnection){phase=Phase.WAITING;round=now;next=now+1000;}else if(now-since>=60000)phase=Phase.CANCELLED;return;}
        if(joined){phase=Phase.COMPLETE;return;}
        if(phase==Phase.WAITING){if(now-round>=30000){phase=Phase.PROMPT;return;}if(now>=next&&!transportBusy){phase=Phase.CONNECTING;attemptSince=now;attempts++;}}
    }
    public boolean attemptTimedOut(long now){return phase==Phase.CONNECTING&&paused<0&&now-attemptSince>=15000;}
    public void loading(long now){if(phase==Phase.CONNECTING){phase=Phase.LOADING;loadingSince=now;}}
    public boolean loadingSlow(long now){return phase==Phase.LOADING&&paused<0&&now-loadingSince>=120000;}
    public void pause(long now){if(active()&&paused<0)paused=now;}
    public void resume(long now){if(paused>=0){long gap=Math.max(0,now-paused);since+=gap;round+=gap;next+=gap;attemptSince+=gap;loadingSince+=gap;paused=-1;}}
    public void failed(long now){if(phase!=Phase.CONNECTING&&phase!=Phase.LOADING)return;phase=Phase.WAITING;next=now+Math.min(5000,1000L<<Math.min(attempts-1,3));}
    public void continueWaiting(long now){if(phase==Phase.LOADING){loadingSince=now;return;}if(phase==Phase.PROMPT){phase=Phase.WAITING;round=now;next=now+500;}}
    public void cancel(){if(active())phase=Phase.CANCELLED;}
}
