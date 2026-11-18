package com.fixmer.genesis.technology.server.process;
import java.io.IOException;
import static com.fixmer.genesis.technology.server.process.ServerNativeOwnerProtocol.*;
final class ServerNativeJavaProtocol {
 enum Phase {NEW,RUNNING,STOPPING,STOP_TIMEOUT,FORCE_TIMEOUT,DRAINING,EMPTY,CLOSED,UNKNOWN}
 record Event(long sequence,Phase phase,long pid,long active,long exitCode,boolean forced,boolean stopSent,boolean ownerLost,boolean logFailed,long error){}
 static String hello(Binding b){return "HELLO 46 "+b.wire();}
 static String command(Binding b,long n,Verb v){if(n<1)throw new IllegalArgumentException("Invalid sequence");return "COMMAND 46 "+b.wire()+" "+n+" "+v;}
 static long number(String s,long max)throws IOException{if(!s.matches("0|[1-9][0-9]{0,18}"))throw new IOException("NATIVE46_NUMBER");try{long n=Long.parseLong(s);if(n>max)throw new IOException("NATIVE46_LIMIT");return n;}catch(NumberFormatException bad){throw new IOException("NATIVE46_OVERFLOW");}}
 static boolean flag(String s)throws IOException{if(s.equals("0"))return false;if(s.equals("1"))return true;throw new IOException("NATIVE46_FLAG");}
 static Event event(String line,Binding b,long expected)throws IOException {
  if(line==null||line.length()>512||!line.matches("[\\x20-\\x7e]+"))throw new IOException("NATIVE46_FRAME");String[] p=line.split(" ",-1);
  if(p.length!=15||!p[0].equals("EVENT")||!p[1].equals("46")||!p[2].equals(b.run().toString())||!p[3].equals(Long.toString(b.epoch()))||!p[4].equals(b.nonce()))throw new IOException("NATIVE46_BINDING");
  long seq=number(p[5],Long.MAX_VALUE);if(seq!=expected)throw new IOException("NATIVE46_SEQUENCE");Phase phase;try{phase=Phase.valueOf(p[6]);}catch(IllegalArgumentException bad){throw new IOException("NATIVE46_PHASE");}
  long pid=number(p[7],0xffffffffL),active=number(p[8],0xffffffffL),code=number(p[9],0xffffffffL),error=number(p[14],0xffffffffL);boolean forced=flag(p[10]),stop=flag(p[11]),lost=flag(p[12]),log=flag(p[13]);
  if(phase!=Phase.UNKNOWN&&(error!=0||active>4096)||phase==Phase.NEW&&(pid!=0||active!=0||forced||stop||lost||code!=0)||phase==Phase.RUNNING&&(pid==0||active==0||forced||stop||lost||code!=0)||phase==Phase.EMPTY&&(pid==0||active!=0)||phase==Phase.CLOSED&&(active!=0||pid==0&&(code!=0||forced||stop||lost))||phase==Phase.DRAINING&&(pid==0||active==0)||switch(phase){case STOPPING,STOP_TIMEOUT,FORCE_TIMEOUT->pid==0;default->false;})throw new IOException("NATIVE46_INCONSISTENT");
  return new Event(seq,phase,pid,active,code,forced,stop,lost,log,error);
 }
 static void transition(Event old,Event next,Verb verb)throws IOException {
  if(old.pid()!=0&&old.pid()!=next.pid()||old.forced()&&!next.forced()||old.stopSent()&&!next.stopSent()||old.ownerLost()&&!next.ownerLost()||old.logFailed()&&!next.logFailed()||old.error()!=0&&next.error()==0)throw new IOException("NATIVE46_REGRESSION");
  boolean allowed=switch(verb){
   case START->old.phase()==Phase.NEW&&(next.phase()==Phase.RUNNING||next.phase()==Phase.EMPTY||next.phase()==Phase.DRAINING||next.phase()==Phase.UNKNOWN);
   case CLOSE->(old.phase()==Phase.NEW||old.phase()==Phase.EMPTY)&&next.phase()==Phase.CLOSED;
   case STATUS->switch(old.phase()){case NEW->next.phase()==Phase.NEW||next.phase()==Phase.UNKNOWN;case EMPTY->next.phase()==Phase.EMPTY;case CLOSED->false;case RUNNING->next.phase()!=Phase.NEW&&next.phase()!=Phase.CLOSED;default->next.phase()!=Phase.NEW&&next.phase()!=Phase.CLOSED&&next.phase()!=Phase.RUNNING;};
   case STOP,FORCE->old.pid()>0&&old.phase()!=Phase.CLOSED&&next.phase()!=Phase.NEW&&next.phase()!=Phase.RUNNING&&next.phase()!=Phase.CLOSED;
  };
  if(!allowed||verb!=Verb.FORCE&&!old.forced()&&next.forced()||old.phase()==Phase.EMPTY&&(next.active()!=0||next.exitCode()!=old.exitCode()))throw new IOException("NATIVE46_TRANSITION");
 }
}
