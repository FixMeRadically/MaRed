package com.fixmer.genesis.supervisor;
import java.util.*;
/** Launch-local evidence. It never grants permission to open a world while its JVM is alive. */
public final class IntegratedState90 {
 private String epoch="",phase="NONE";
 public synchronized void accept(String nextEpoch,String next){
  if(!Set.of("ACTIVE","SAVED","STOPPED","FAILED").contains(next))throw new IllegalArgumentException("INTEGRATED90_STATE");
  if(!UUID.fromString(nextEpoch).toString().equals(nextEpoch))throw new IllegalArgumentException("INTEGRATED90_EPOCH");
  if(!epoch.equals(nextEpoch)){
   // Heartbeats may skip intermediate states. A new epoch supersedes the previous world's proof.
   epoch=nextEpoch;phase=next;return;
  }
  if(phase.equals("FAILED")&&!next.equals("FAILED"))throw new IllegalArgumentException("INTEGRATED90_FAILED");
  if(phase.equals("STOPPED")&&!next.equals("STOPPED"))throw new IllegalArgumentException("INTEGRATED90_REGRESSION");
  if(phase.equals("SAVED")&&next.equals("ACTIVE"))throw new IllegalArgumentException("INTEGRATED90_REGRESSION");
  phase=next;
 }
 public synchronized String epoch(){return epoch;}public synchronized String phase(){return phase;}
 public synchronized boolean present(){return !epoch.isEmpty();}
 public synchronized boolean stopped(){return phase.equals("STOPPED");}
 public synchronized void requireRestart(boolean exited,boolean normal){if(present()&&(!exited||!normal||!stopped()))throw new IllegalStateException("INTEGRATED90_UNCONFIRMED");}
}
