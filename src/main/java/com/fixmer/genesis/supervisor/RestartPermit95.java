package com.fixmer.genesis.supervisor;
import java.util.*;import java.io.*;
/** One prepare/commit/cancel reservation. A prepare never stops or relaunches a child. */
final class RestartPermit95 {
 final String request,snapshot,grant=UUID.randomUUID().toString();final long deadline;
 private String phase="PREPARED",reason="";
 RestartPermit95(String request,String snapshot,long now){uuid(request);parts(snapshot);this.request=request;this.snapshot=snapshot;this.deadline=now+30_000_000_000L;}
 static void uuid(String value){if(value==null||!UUID.fromString(value).toString().equals(value))throw new IllegalArgumentException("RESTART95_ID");}
 static String[] parts(String value){if(value==null||value.length()>80)throw new IllegalArgumentException("RESTART95_SNAPSHOT");String[] p=value.split("\\.",-1);if(p.length!=2)throw new IllegalArgumentException("RESTART95_SNAPSHOT");if(!p[0].equals("-"))uuid(p[0]);uuid(p[1]);return p;}
 synchronized String phase(long now){if(phase.equals("PREPARED")&&now-deadline>=0){phase="EXPIRED";reason="prepare_timeout";}return phase;}
 boolean matches(String id,String value){return request.equals(id)&&snapshot.equals(value);}
 synchronized boolean commit(String proof,long now){if(!grant.equals(proof)||!phase(now).equals("PREPARED"))return false;phase="COMMITTED";return true;}
 synchronized boolean cancel(String why,long now){String p=phase(now);if(p.equals("CANCELLED"))return true;if(!p.equals("PREPARED")&&!p.equals("COMMITTED"))return false;phase="CANCELLED";reason=why;return true;}
 synchronized boolean cancelled(){return phase.equals("CANCELLED");}
 synchronized void finish(String result,String why){if(!Set.of("DONE","HOLD").contains(result))throw new IllegalArgumentException();if(!phase.equals("CANCELLED")){phase=result;reason=why;}}
 synchronized Properties properties(long now,boolean wire){Properties p=new Properties();p.setProperty("schema","95");p.setProperty("request",request);p.setProperty("state",phase(now));p.setProperty("reason",reason);p.setProperty("remaining_ms",Long.toString(Math.max(0,(deadline-now)/1_000_000)));if(wire)p.setProperty("grant",grant);return p;}
}
