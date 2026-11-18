package com.fixmer.genesis.technology.server.process;

import java.io.IOException;
import java.util.UUID;

/** Server-thread lifecycle. A completed save call is not a quiescent-world or installation barrier. */
public final class ServerLifecycleState {
    public enum Phase { STARTING, STARTED, READY, CHECKPOINTING, STOP_ARMED, STOPPING, STOPPED, FAILED }
    public enum Operation { CHECKPOINT, SAVE_AND_STOP }
    public enum Outcome { NONE, RUNNING, COMPLETED, FAILED, EXPIRED, CANCELLED }
    public record Snapshot(long revision,Phase phase,long ticks,long tickAgeMs,long requestSequence,UUID request,Outcome outcome,long saveMs,String error,Operation operation,boolean stopRequested){
        public Snapshot(long revision,Phase phase,long ticks,long tickAgeMs,long requestSequence,UUID request,Outcome outcome,long saveMs,String error){this(revision,phase,ticks,tickAgeMs,requestSequence,request,outcome,saveMs,error,Operation.CHECKPOINT,false);}
    }
    private final Thread owner=Thread.currentThread();
    private long revision=1,ticks,lastTick=-1,requestSequence,saveMs;
    private Operation operation=Operation.CHECKPOINT;private boolean stopRequested;
    private Phase phase=Phase.STARTING;private UUID request;private Outcome outcome=Outcome.NONE;private String error="";
    public synchronized void started(){owned();if(phase==Phase.STARTING){phase=Phase.STARTED;revision++;}}
    public synchronized void tick(){owned();if(phase==Phase.STOP_ARMED||phase==Phase.STOPPING||phase==Phase.STOPPED||phase==Phase.FAILED)return;if(ticks==Long.MAX_VALUE)throw new IllegalStateException("LIFECYCLE_COUNTER_OVERFLOW");ticks++;lastTick=System.nanoTime();if(phase==Phase.STARTED){phase=Phase.READY;revision++;}}
    public synchronized boolean begin(long sequence,UUID id,long expiresAt,long now,boolean cancelled)throws IOException {
        return begin(sequence,id,expiresAt,now,cancelled,Operation.CHECKPOINT);
    }
    public synchronized boolean begin(long sequence,UUID id,long expiresAt,long now,boolean cancelled,Operation operation)throws IOException {
        owned();if(operation==null)throw new IOException("INVALID_OPERATION");if(sequence<1||id==null||!ServerFiles.canonicalUuid(id.toString()))throw new IOException("INVALID_CHECKPOINT_REQUEST");
        if(sequence<requestSequence)return false;
        if(sequence==requestSequence){if(!id.equals(request))throw new IOException("CHECKPOINT_SEQUENCE_REUSED");return false;}
        if(phase==Phase.CHECKPOINTING||phase==Phase.STOP_ARMED||stopRequested)throw new IOException("CHECKPOINT_ALREADY_RUNNING");
        requestSequence=sequence;request=id;saveMs=0;error="";this.operation=operation;stopRequested=false;
        if(cancelled)outcome=Outcome.CANCELLED;
        else if(now>expiresAt)outcome=Outcome.EXPIRED;
        else if(phase!=Phase.READY){outcome=Outcome.CANCELLED;error="SERVER_NOT_READY";}
        else{outcome=Outcome.RUNNING;phase=Phase.CHECKPOINTING;revision++;return true;}
        revision++;return false;
    }
    public synchronized void complete(boolean success,long durationMs){owned();if(phase!=Phase.CHECKPOINTING)throw new IllegalStateException("NO_CHECKPOINT_RUNNING");saveMs=Math.max(0,durationMs);outcome=success?Outcome.COMPLETED:Outcome.FAILED;error=success?"":"SAVE_CALL_FAILED";phase=success&&operation==Operation.SAVE_AND_STOP?Phase.STOP_ARMED:Phase.READY;revision++;}
    public synchronized void stopRequested(){owned();if(phase!=Phase.STOP_ARMED)throw new IllegalStateException("STOP_NOT_ARMED");stopRequested=true;phase=Phase.STOPPING;revision++;}
    public synchronized void stopFailed(){owned();if(phase!=Phase.STOP_ARMED)throw new IllegalStateException("STOP_NOT_ARMED");phase=Phase.STOPPING;outcome=Outcome.FAILED;error="STOP_CALL_OUTCOME_UNKNOWN";revision++;}
    public synchronized void stopCancelled(){owned();if(phase!=Phase.STOP_ARMED)throw new IllegalStateException("STOP_NOT_ARMED");phase=Phase.READY;outcome=Outcome.CANCELLED;error="STOP_CANCELLED_AFTER_SAVE";revision++;}
    public synchronized void stopPublicationFailed(){owned();if(phase!=Phase.STOP_ARMED)throw new IllegalStateException("STOP_NOT_ARMED");phase=Phase.READY;outcome=Outcome.FAILED;error="STOP_INTENT_NOT_CONFIRMED";revision++;}
    public synchronized void stopping(){owned();if(phase==Phase.STOPPED)return;phase=Phase.STOPPING;if(outcome==Outcome.RUNNING)outcome=Outcome.CANCELLED;revision++;}
    public synchronized void stopped(){owned();phase=Phase.STOPPED;if(outcome==Outcome.RUNNING)outcome=Outcome.CANCELLED;revision++;}
    public synchronized Snapshot snapshot(){return new Snapshot(revision,phase,ticks,ticks==0?Long.MAX_VALUE:Math.max(0,(System.nanoTime()-lastTick)/1_000_000),requestSequence,request,outcome,saveMs,error,operation,stopRequested);}
    private void owned(){if(Thread.currentThread()!=owner)throw new IllegalStateException("SERVER_THREAD_REQUIRED");}
}
