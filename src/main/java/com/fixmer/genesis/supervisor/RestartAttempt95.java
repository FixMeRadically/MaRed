package com.fixmer.genesis.supervisor;
import java.util.*;
/** Client-side permission: cancellation and a queued exit race for one synchronized transition. */
public final class RestartAttempt95 {
 private final String id=UUID.randomUUID().toString();private final long deadline;private String phase="QUEUED";
 public RestartAttempt95(long now,long budgetNs){if(budgetNs<=0||budgetNs>120_000_000_000L)throw new IllegalArgumentException();deadline=now+budgetNs;}
 public String id(){return id;}
 public synchronized String phase(){return phase;}
 public synchronized boolean active(long now){if(!phase.equals("EXITING")&&now-deadline>=0)phase="CANCELLED";return !phase.equals("CANCELLED");}
 public synchronized boolean beginSave(long now){if(!active(now)||!phase.equals("QUEUED"))return false;phase="SAVING";return true;}
 public synchronized boolean saved(long now,Runnable arm){if(!active(now)||!phase.equals("SAVING"))return false;arm.run();phase="SAVED";return true;}
 public synchronized boolean authorize(long now){if(!active(now)||!phase.equals("SAVED"))return false;phase="AUTHORIZED";return true;}
 public synchronized boolean claimExit(long now){return claimExit(now,()->{});}
 public synchronized boolean claimExit(long now,Runnable beforeExit){if(!active(now)||!phase.equals("AUTHORIZED"))return false;beforeExit.run();phase="EXITING";return true;}
 public synchronized boolean cancel(){if(phase.equals("EXITING"))return false;phase="CANCELLED";return true;}
 public synchronized boolean exiting(){return phase.equals("EXITING");}
}
