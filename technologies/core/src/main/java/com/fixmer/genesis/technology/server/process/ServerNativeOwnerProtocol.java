package com.fixmer.genesis.technology.server.process;

import java.io.IOException;
import java.security.SecureRandom;
import java.util.*;

/** Version44 diagnostic-only IPC. A response is never an install capability. */
final class ServerNativeOwnerProtocol {
    static final int VERSION=44,MAX_LINE=512;
    enum Verb {START,STATUS,STOP,FORCE,CLOSE}
    enum Phase {NEW,RUNNING,STOPPING,EMPTY,CLOSED}
    record Binding(UUID run,long epoch,String nonce) {
        Binding {Objects.requireNonNull(run);Objects.requireNonNull(nonce);if(run.equals(new UUID(0,0))||epoch<1||!nonce.matches("[0-9a-f]{64}"))throw new IllegalArgumentException("Invalid native binding");}
        static Binding fresh(){byte[] bytes=new byte[32];new SecureRandom().nextBytes(bytes);return new Binding(UUID.randomUUID(),1,HexFormat.of().formatHex(bytes));}
        String wire(){return run+" "+epoch+" "+nonce;}
    }
    record Event(long sequence,Phase phase,long pid,long active,long exitCode,boolean forced) {}
    static String hello(Binding b){return "HELLO 44 "+b.wire();}
    static String command(Binding b,long sequence,Verb verb){if(sequence<1)throw new IllegalArgumentException("Invalid native sequence");return "COMMAND 44 "+b.wire()+" "+sequence+" "+Objects.requireNonNull(verb);}
    static Event event(String line,Binding binding,long expected)throws IOException {
        if(line==null||line.length()>MAX_LINE||!line.matches("[\\x20-\\x7e]+"))throw new IOException("NATIVE_INVALID_FRAME");
        String[] p=line.split(" ",-1);
        if(p.length!=11||!p[0].equals("EVENT")||!p[1].equals("44")||!p[2].equals(binding.run().toString())||!p[3].equals(Long.toString(binding.epoch()))||!p[4].equals(binding.nonce()))throw new IOException("NATIVE_BINDING_MISMATCH");
        long sequence=number(p[5],Long.MAX_VALUE);if(sequence!=expected)throw new IOException("NATIVE_SEQUENCE_MISMATCH");
        Phase phase;try{phase=Phase.valueOf(p[6]);}catch(IllegalArgumentException invalid){throw new IOException("NATIVE_INVALID_PHASE");}
        long pid=number(p[7],0xffffffffL),active=number(p[8],4096),code=number(p[9],0xffffffffL);
        if(!p[10].equals("0")&&!p[10].equals("1"))throw new IOException("NATIVE_INVALID_FORCE_FLAG");boolean forced=p[10].equals("1");
        if(phase==Phase.NEW&&(pid!=0||active!=0||code!=0||forced)||phase==Phase.RUNNING&&(pid==0||active==0||code!=0||forced)||phase==Phase.STOPPING&&(pid==0||active==0||code!=0)||phase==Phase.EMPTY&&(pid==0||active!=0)||phase==Phase.CLOSED&&(active!=0||pid==0&&(code!=0||forced)))throw new IOException("NATIVE_INCONSISTENT_STATE");
        return new Event(sequence,phase,pid,active,code,forced);
    }
    private static long number(String value,long max)throws IOException {
        if(!value.matches("0|[1-9][0-9]{0,18}"))throw new IOException("NATIVE_INVALID_NUMBER");
        try{long number=Long.parseLong(value);if(number>max)throw new IOException("NATIVE_NUMBER_LIMIT");return number;}catch(NumberFormatException invalid){throw new IOException("NATIVE_NUMBER_OVERFLOW");}
    }
    static void transition(Event old,Event next,Verb command)throws IOException {
        if(old.phase()==Phase.CLOSED)throw new IOException("NATIVE_ALREADY_CLOSED");
        if(old.pid()!=0&&old.pid()!=next.pid()||old.forced()&&!next.forced())throw new IOException("NATIVE_IDENTITY_REGRESSION");
        boolean valid=switch(command){
            case START -> old.phase()==Phase.NEW&&(next.phase()==Phase.RUNNING||next.phase()==Phase.EMPTY);
            case STATUS -> switch(old.phase()){
                case NEW -> next.phase()==Phase.NEW;
                case RUNNING -> next.phase()==Phase.RUNNING||next.phase()==Phase.EMPTY;
                case STOPPING -> next.phase()==Phase.STOPPING||next.phase()==Phase.EMPTY;
                case EMPTY -> next.phase()==Phase.EMPTY;
                case CLOSED -> false;
            };
            case STOP -> (old.phase()==Phase.RUNNING||old.phase()==Phase.EMPTY)&&(next.phase()==Phase.STOPPING||next.phase()==Phase.EMPTY);
            case FORCE -> (old.phase()==Phase.RUNNING||old.phase()==Phase.STOPPING||old.phase()==Phase.EMPTY)&&(next.phase()==Phase.STOPPING||next.phase()==Phase.EMPTY);
            case CLOSE -> (old.phase()==Phase.NEW||old.phase()==Phase.EMPTY)&&next.phase()==Phase.CLOSED;
        };
        if(!valid||command!=Verb.FORCE&&!old.forced()&&next.forced())throw new IOException("NATIVE_ILLEGAL_TRANSITION");
        if(old.phase()==Phase.EMPTY&&(old.exitCode()!=next.exitCode()||old.forced()!=next.forced()))throw new IOException("NATIVE_EXIT_REGRESSION");
    }
}
