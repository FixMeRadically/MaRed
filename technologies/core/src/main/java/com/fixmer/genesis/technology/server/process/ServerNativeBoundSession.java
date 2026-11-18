package com.fixmer.genesis.technology.server.process;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import com.fixmer.genesis.technology.server.packages.ProfileCoordinator;

/** One diagnostic lifecycle owns intent, process handle and channel, including failed HELLO. */
final class ServerNativeBoundSession implements AutoCloseable {
 private final ServerNativeLaunchIntent intent;
 private final ServerNativeLaunchCapsule.Frozen capsule;
 private final Path helper;
 private final Path run;private final ProfileCoordinator coordinator;private final ProfileCoordinator.LaunchPermit permit;
 private ServerNativeStopBarrier stopBarrier;
 private ServerNativeJavaSession channel;
 private Process owner;
 private boolean attempted,childAttempted,failed,complete,closed;
 private String failure="NONE";

 private ServerNativeBoundSession(ServerNativeLaunchIntent intent,Path helper,ServerNativeLaunchCapsule.Frozen capsule,Path run,ProfileCoordinator coordinator,ProfileCoordinator.LaunchPermit permit){this.intent=intent;this.helper=helper;this.capsule=capsule;this.run=run;this.coordinator=coordinator;this.permit=permit;}
 static ServerNativeBoundSession prepare(ProfileCoordinator coordinator,ProfileCoordinator.LaunchPermit permit,Path run,Path helper,ServerNativeLaunchCapsule.Frozen capsule,ServerNativeEvidence.Checked evidence)throws IOException {
  return new ServerNativeBoundSession(ServerNativeLaunchIntent.prepare(coordinator,permit,run,helper,capsule,evidence),helper.toAbsolutePath().normalize(),capsule,run.toAbsolutePath().normalize(),coordinator,permit);
 }
 /** Caller already holds this object before any OS process can be created. */
 synchronized void connect()throws IOException {
  if(closed||attempted||failed)throw new IOException("NATIVE48_CONNECTION_NOT_REUSABLE");attempted=true;
  try{
   intent.beginOwnerStart();
   channel=ServerNativeJavaSession.diagnostic(helper,capsule,new ServerNativeJavaSession.LaunchObserver(){
    public void beforeStart()throws IOException{intent.requireStartStillValid();}
    public void started(Process process)throws IOException{owner=process;intent.observeOwner(process);}
   });
  }catch(IOException|RuntimeException bad){if(bad instanceof ServerNativeJavaSession.StartFailure started)owner=started.process();fail("CONNECT_OUTCOME_UNKNOWN");throw bad;}
 }
 synchronized ServerNativeJavaProtocol.Event start()throws IOException {
  connected();if(childAttempted||failed)throw new IOException("NATIVE48_START_NOT_REUSABLE");childAttempted=true;
  try{intent.beginChildStart();var event=channel.start();if(unreliable(event)||event.forced()||event.stopSent())throw new IOException("NATIVE48_START_UNKNOWN");intent.observeChildStart(event.pid());return event;}
  catch(IOException|RuntimeException bad){fail("START_OUTCOME_UNKNOWN");throw bad;}
 }
 private static boolean unreliable(ServerNativeJavaProtocol.Event event){return event.phase()==ServerNativeJavaProtocol.Phase.UNKNOWN||event.ownerLost()||event.logFailed()||event.error()!=0;}
 synchronized ServerNativeJavaProtocol.Event status()throws IOException {connected();try{var event=channel.status();if(unreliable(event))throw new IOException("NATIVE48_STATUS_UNKNOWN");return event;}catch(IOException|RuntimeException bad){fail("STATUS_OUTCOME_UNKNOWN");throw bad;}}
 synchronized ServerNativeJavaProtocol.Event stop()throws IOException {connected();if(stopBarrier!=null)throw new IOException("NATIVE49_USE_SAVE_STOP_COMPLETION");try{var event=channel.stop();if(unreliable(event))throw new IOException("NATIVE48_STOP_UNKNOWN");return event;}catch(IOException|RuntimeException bad){fail("STOP_OUTCOME_UNKNOWN");throw bad;}}
 synchronized void beginSaveStop(java.util.UUID request,long sequence,boolean confirmed)throws IOException {
  connected();if(!confirmed)throw new IOException("NATIVE49_STOP_CONFIRMATION_REQUIRED");if(failed||complete||stopBarrier!=null)throw new IOException("NATIVE49_SAVE_STOP_NOT_REUSABLE");
  try{intent.validateRunningForStop();stopBarrier=ServerNativeStopBarrier.arm(run,owner,coordinator,permit,capsule,channel.status(),request,sequence);}catch(IOException|RuntimeException bad){fail("SAVE_STOP_ARM_UNKNOWN");throw bad;}
 }
 synchronized boolean pollSaveStop()throws IOException {
  connected();if(failed||stopBarrier==null)throw new IOException("NATIVE49_SAVE_STOP_UNAVAILABLE");
  try{return stopBarrier.observe(channel.status());}catch(IOException|RuntimeException bad){fail("SAVE_STOP_OBSERVATION_UNKNOWN");throw bad;}
 }
 synchronized void cancelSaveStop()throws IOException {connected();if(stopBarrier==null)throw new IOException("NATIVE49_SAVE_STOP_UNAVAILABLE");stopBarrier.cancel();fail("SAVE_STOP_CANCEL_OUTCOME_UNCONFIRMED");}
 synchronized void finishSaveStop()throws IOException {
  connected();if(failed||stopBarrier==null)throw new IOException("NATIVE49_SAVE_STOP_UNAVAILABLE");
  ServerNativeJavaProtocol.Event event;boolean drained;
  try{event=channel.status();drained=stopBarrier.observe(event);}catch(IOException|RuntimeException bad){fail("SAVE_STOP_OBSERVATION_UNKNOWN");throw bad;}
  if(!drained)throw new IOException("NATIVE49_SAVE_STOP_NOT_DRAINED");
  try{channel.finish();intent.observeOwnerExit();var receipt=ServerNativeJavaCli.checkedReceipt(capsule,false,false,event.stopSent(),event.pid());stopBarrier.complete(receipt);intent.completeDiagnostic();complete=true;}
  catch(IOException|RuntimeException bad){fail("SAVE_STOP_COMPLETION_UNKNOWN");throw bad;}
 }
 synchronized ServerNativeJavaProtocol.Event force(boolean confirmed)throws IOException {
  connected();if(!confirmed)throw new IOException("NATIVE48_FORCE_CONFIRMATION_REQUIRED");
  try{var event=channel.force(true);fail("FORCED_DIAGNOSTIC_REQUIRES_REVIEW");return event;}catch(IOException|RuntimeException bad){fail("FORCE_OUTCOME_UNKNOWN");throw bad;}
 }
 synchronized void awaitEmpty(long ms)throws IOException {connected();try{channel.awaitEmpty(ms);}catch(IOException|RuntimeException bad){fail("DRAIN_OUTCOME_UNKNOWN");throw bad;}}
 synchronized void finish()throws IOException {
  connected();if(stopBarrier!=null)throw new IOException("NATIVE49_USE_SAVE_STOP_COMPLETION");if(failed||!childAttempted)throw new IOException("NATIVE48_COMPLETION_UNAVAILABLE");
  try{
   var event=channel.status();if(event.phase()!=ServerNativeJavaProtocol.Phase.EMPTY||event.active()!=0||event.pid()<1||event.exitCode()!=0||!event.stopSent()||event.forced()||event.ownerLost()||event.logFailed()||event.error()!=0)throw new IOException("NATIVE48_GRACEFUL_EMPTY_REQUIRED");
   channel.finish();intent.observeOwnerExit();
   ServerNativeJavaCli.receipt(capsule,false,false,true,event.pid());
   intent.completeDiagnostic();complete=true;
  }catch(IOException|RuntimeException bad){fail("COMPLETION_OUTCOME_UNKNOWN");throw bad;}
 }
 private void connected()throws IOException {if(closed||complete||channel==null)throw new IOException("NATIVE48_CHANNEL_UNAVAILABLE");}
 private void fail(String reason){failed=true;complete=false;failure=reason;if(stopBarrier!=null)stopBarrier.fail(reason);intent.fail(reason);}
 synchronized Map<String,Object> snapshot(){
  var out=new LinkedHashMap<String,Object>();out.put("schema",48);out.put("intent",intent.describe());out.put("connectionAttempted",attempted);out.put("childStartAttempted",childAttempted);out.put("ownerRetained",owner!=null);out.put("ownerAlive",owner!=null&&owner.isAlive());out.put("diagnosticComplete",complete);out.put("failure",failure);out.put("closed",closed);if(channel!=null)out.put("channel",channel.snapshot());if(stopBarrier!=null)out.put("stopReview",stopBarrier.describe());out.put("gameLaunched",false);out.put("nativeRuntimeVerified",false);out.put("productionLaunchAllowed",false);out.put("installAuthority",false);return out;
 }
 /** Timeout keeps the lease and process reference. Retain this object and retry close later. */
 synchronized void close(long waitMs)throws IOException {
  if(closed)return;if(waitMs<1||waitMs>60000)throw new IllegalArgumentException("Owner exit deadline");
  if(!complete&&attempted)fail("CLOSED_WITHOUT_DIAGNOSTIC_COMPLETION");
  if(channel!=null)channel.close();
  else if(owner!=null)try{owner.getOutputStream().close();}catch(IOException ignored){}
  if(owner!=null){
   try{if(!owner.waitFor(waitMs,TimeUnit.MILLISECONDS)){fail("OWNER_STILL_HOLDS_JOB");throw new IOException("NATIVE48_OWNER_STILL_HELD_RETAIN_SESSION");}}
   catch(InterruptedException interrupted){Thread.currentThread().interrupt();fail("OWNER_WAIT_INTERRUPTED");throw new IOException("NATIVE48_INTERRUPTED_RETAIN_SESSION",interrupted);}
   // Also covers observer failure before Session started its pipe reader threads.
   // A retained inherited writer must not block the control thread on stream.close().
   Thread pipes=new Thread(()->{try{owner.getInputStream().close();}catch(IOException ignored){}try{owner.getErrorStream().close();}catch(IOException ignored){}},"genesis-bound48-pipe-close");pipes.setDaemon(true);pipes.start();
  }
  if(stopBarrier!=null)stopBarrier.close();intent.close();closed=true;
 }
 @Override public void close()throws IOException {close(10000);}
}
