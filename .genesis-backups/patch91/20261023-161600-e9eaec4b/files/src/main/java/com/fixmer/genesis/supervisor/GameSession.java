package com.fixmer.genesis.supervisor;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Binds one launch to the actual game JVM rather than to a potentially lingering wrapper. */
final class GameSession {
    enum End { NONE, NORMAL, FAILED }
    enum Life { ALIVE, DEAD, UNKNOWN }
    interface Probe { Life life(long pid,long start); }
    private static final boolean OS_VISIBLE=visible();
    private final Path file;
    private final String launch;
    private final long begin=System.nanoTime();
    private final Probe probe;
    private final long startupNanos,leaseNanos;
    private long pid,start,sequence=-1,lastSeen,exitSeen;
    private String state="UNREGISTERED";
    private boolean integratedSeen90,integratedInvalid90;
    private final IntegratedState90 integrated90=new IntegratedState90();
    private boolean normal,ready,frameHandoff,frameReady;
    private long readySince;
    private long jvmStartMs;
    private String leaseToken="";
    private GameLease.State leaseState=GameLease.State.UNKNOWN;
    GameSession(RuntimeFiles runtime,SupervisorConfig config){this(runtime.directory.resolve("session.properties"),runtime.id,config.startupTimeoutMs(),config.sessionLeaseMs(),GameSession::osLife);}
    GameSession(Path file,String launch,long startupMs,long leaseMs,Probe probe){this.file=file;this.launch=launch;this.probe=probe;startupNanos=startupMs*1_000_000L;leaseNanos=leaseMs*1_000_000L;}
    synchronized End poll(){return poll(System.nanoTime());}
    synchronized End poll(long now){
        read(now);
        if(pid==0)return now-begin>=startupNanos?End.FAILED:End.NONE;
        Life life=probe.life(pid,start);
        if(life==Life.DEAD)return normal?End.NORMAL:End.FAILED;
        // A live, same-identity JVM is never killed because GC or a disk stall delayed heartbeats.
        if(life==Life.ALIVE)return End.NONE;
        GameLease.State lease=lease();
        if(lease==GameLease.State.HELD)return End.NONE;
        if(lease==GameLease.State.RELEASED)return normal?End.NORMAL:End.FAILED;
        if(!leaseToken.isEmpty())return now-lastSeen>=leaseNanos?End.FAILED:End.NONE;
        // Hidden OS metadata without a usable file lease retains the bounded legacy fallback.
        if(state.equals("EXITED")&&now-exitSeen>=3_000_000_000L)return normal?End.NORMAL:End.FAILED;
        return now-lastSeen>=leaseNanos?End.FAILED:End.NONE;
    }
    private void read(long now){
        try{
            RuntimeFiles.safe(file);
            if(!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS)||Files.size(file)>2048)return;
            Properties p=new Properties();try(Reader in=Files.newBufferedReader(file,StandardCharsets.UTF_8)){p.load(in);}
            if(!"1".equals(p.getProperty("schema"))||!launch.equals(p.getProperty("launch")))return;
            long nextPid=Long.parseLong(p.getProperty("pid")),nextStart=Long.parseLong(p.getProperty("processStart")),nextSequence=Long.parseLong(p.getProperty("sequence"));
            String nextState=p.getProperty("state");
            if(nextPid<=0||nextStart<0||nextSequence<=sequence||!Set.of("RUNNING","CLOSING","EXITED").contains(nextState))return;
            String nextLease=p.getProperty("leaseToken","");
            if(!nextLease.isEmpty())UUID.fromString(nextLease);
            if(!leaseToken.isEmpty()&&!leaseToken.equals(nextLease))return;
            if(pid!=0&&(pid!=nextPid||start!=nextStart))return; // A second JVM cannot take over an existing session.
            if(state.equals("EXITED")&&!nextState.equals("EXITED"))return;
            if(state.equals("CLOSING")&&nextState.equals("RUNNING"))return;
            if(p.containsKey("integrated90")||p.containsKey("integratedEpoch90")){
                integratedSeen90=true;try{integrated90.accept(p.getProperty("integratedEpoch90"),p.getProperty("integrated90"));}catch(RuntimeException bad){integratedInvalid90=true;}
            }else if(integratedSeen90)integratedInvalid90=true;
            leaseToken=nextLease;pid=nextPid;start=nextStart;sequence=nextSequence;lastSeen=now;state=nextState;
            boolean nextReady=Boolean.parseBoolean(p.getProperty("ready","false"));
            if(nextReady&&!ready)readySince=now;ready|=nextReady;
            frameHandoff=Boolean.parseBoolean(p.getProperty("frameHandoff","false"));frameReady|=Boolean.parseBoolean(p.getProperty("frameReady","false"));
            try{jvmStartMs=Long.parseLong(p.getProperty("jvmStartMs","0"));}catch(NumberFormatException ignored){}
            normal|=Boolean.parseBoolean(p.getProperty("normalClose","false"));
            if(state.equals("EXITED")&&exitSeen==0)exitSeen=now;
        }catch(IOException|RuntimeException ignored){/* Retain the last valid identity/lease. */}
    }
    private GameLease.State lease(){
        if(leaseToken.isEmpty())return leaseState=GameLease.State.UNKNOWN;
        return leaseState=GameLease.probe(file.resolveSibling("game.lease"),GameLease.marker(launch,leaseToken,pid,start));
    }
    synchronized boolean integrated90(){return integratedSeen90;}
    synchronized void requireIntegratedRestart90(){if(integratedInvalid90||integratedSeen90&&!integrated90.present())throw new IllegalStateException("INTEGRATED90_INVALID");integrated90.requireRestart(confirmedExit(),normal);}
    synchronized boolean integratedStopped90(){return !integrated90.present()||integrated90.stopped();}
    synchronized boolean confirmedExit(){
        if(pid==0)return false;
        Life life=probe.life(pid,start);if(life==Life.ALIVE)return false;if(life==Life.DEAD)return true;
        GameLease.State lease=lease();if(lease==GameLease.State.HELD)return false;if(lease==GameLease.State.RELEASED)return true;
        return leaseToken.isEmpty()&&state.equals("EXITED");
    }
    synchronized String leaseState(){return leaseToken.isEmpty()?"LEGACY":lease().name();}
    synchronized String exitSource(){
        if(pid==0)return "unconfirmed";
        Life life=probe.life(pid,start);if(life==Life.DEAD)return "os";if(life==Life.ALIVE)return "unconfirmed";
        if(lease()==GameLease.State.RELEASED)return "file_lease";
        return leaseToken.isEmpty()&&state.equals("EXITED")?"legacy_marker":"unconfirmed";
    }
    synchronized boolean presentationReady(){return presentationReady(System.nanoTime());}
    synchronized boolean presentationReady(long now){return ready&&(!frameHandoff||frameReady||now-readySince>=5_000_000_000L);}
    synchronized boolean ready(){return ready;}
    synchronized long jvmStartMs(){return jvmStartMs;}
    synchronized long pid(){return pid;}
    synchronized boolean closing(){return normal||state.equals("EXITED");}
    synchronized String state(){return state;}
    synchronized boolean registered(){return pid!=0;}
    synchronized long heartbeatAgeMs(){return pid==0?-1:Math.max(0,(System.nanoTime()-lastSeen)/1_000_000);}
    static Life osLife(long pid,long start){
        if(!OS_VISIBLE)return Life.UNKNOWN;
        try{
            var handle=ProcessHandle.of(pid);if(handle.isEmpty()||!handle.get().isAlive())return Life.DEAD;
            var actual=handle.get().info().startInstant();
            if(start<=0||actual.isEmpty())return Life.UNKNOWN;
            return actual.get().toEpochMilli()==start?Life.ALIVE:Life.DEAD;
        }catch(RuntimeException e){return Life.UNKNOWN;}
    }
    private static boolean visible(){try{return ProcessHandle.current().isAlive()&&ProcessHandle.current().info().startInstant().isPresent();}catch(RuntimeException e){return false;}}
}
