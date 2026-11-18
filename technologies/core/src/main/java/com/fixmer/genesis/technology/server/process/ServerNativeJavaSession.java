package com.fixmer.genesis.technology.server.process;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import static com.fixmer.genesis.technology.server.process.ServerNativeOwnerProtocol.Binding;
import static com.fixmer.genesis.technology.server.process.ServerNativeOwnerProtocol.Verb;
import static com.fixmer.genesis.technology.server.process.ServerNativeJavaProtocol.*;
/** Diagnostic bridge. Lost channel closes stdin, never forcibly kills the owner. */
final class ServerNativeJavaSession implements AutoCloseable {
 interface LaunchObserver {void beforeStart()throws IOException;void started(Process process)throws IOException;}
 /** Carries the still-owned process even if observer/HELLO construction failed. No kill. */
 static final class StartFailure extends IOException {
  private final Process process;
  StartFailure(Process process,Exception cause){super("NATIVE48_OWNER_STARTED_CHANNEL_FAILED",cause);this.process=process;}
  Process process(){return process;}
 }
 private final Process owner;private final Binding binding;private final long timeout;
 private final BlockingQueue<String> replies=new ArrayBlockingQueue<>(8);private final AtomicReference<String> failure=new AtomicReference<>();
 private Event event;private long sequence;private boolean poisoned,closed,complete;
 private ServerNativeJavaSession(Process process,Binding b,long timeout)throws IOException {
  owner=process;binding=b;this.timeout=timeout;
  Thread reader=new Thread(this::read,"genesis-java46-owner-output");reader.setDaemon(true);reader.start();
  Thread stderr=new Thread(()->{try(var in=owner.getErrorStream()){byte[] bytes=new byte[1024];while(in.read(bytes)>=0){}}catch(IOException ignored){}},"genesis-java46-owner-error");stderr.setDaemon(true);stderr.start();
  try{write(hello(binding));event=receive(0,timeout);if(event.phase()!=Phase.NEW)throw new IOException("NATIVE46_HANDSHAKE");}catch(IOException bad){poison();throw bad;}
 }
 static ServerNativeJavaSession diagnostic(Path helper,ServerNativeLaunchCapsule.Frozen frozen)throws IOException {
  return diagnostic(helper,frozen,new LaunchObserver(){public void beforeStart(){}public void started(Process process){}});
 }
 static ServerNativeJavaSession diagnostic(Path helper,ServerNativeLaunchCapsule.Frozen frozen,LaunchObserver observer)throws IOException {
  Objects.requireNonNull(observer);
  if(!System.getProperty("os.name","").startsWith("Windows"))throw new IOException("NATIVE46_WINDOWS_REQUIRED");
  if(frozen.plan().kind()!=ServerNativeLaunchCapsule.DIAGNOSTIC)throw new IOException("NATIVE46_SERVER_LAUNCH_DISABLED");
  frozen=ServerNativeLaunchCapsule.verify(frozen.file(),frozen.sha());if(frozen.plan().kind()!=ServerNativeLaunchCapsule.DIAGNOSTIC)throw new IOException("NATIVE46_SERVER_LAUNCH_DISABLED");helper=helper.toAbsolutePath().normalize();ServerFiles.safe(helper);
  if(!Files.isRegularFile(helper,LinkOption.NOFOLLOW_LINKS)||Files.size(helper)>32L*1024*1024||!helper.getFileName().toString().endsWith(".exe"))throw new IOException("NATIVE46_HELPER_INVALID");
  observer.beforeStart();Process process=new ProcessBuilder(helper.toString(),"--java-session46",frozen.file().toString(),frozen.sha()).start();
  try{observer.started(process);return new ServerNativeJavaSession(process,frozen.plan().binding(),10000);}
  catch(IOException|RuntimeException bad){try{process.getOutputStream().close();}catch(IOException extra){bad.addSuppressed(extra);}throw new StartFailure(process,bad);}
 }
 synchronized Event start()throws IOException{return send(Verb.START,timeout);}
 synchronized Event status()throws IOException{return send(Verb.STATUS,timeout);}
 synchronized Event stop()throws IOException{return send(Verb.STOP,timeout);}
 synchronized Event force(boolean confirmed)throws IOException{if(!confirmed)throw new IOException("NATIVE46_FORCE_CONFIRMATION_REQUIRED");return send(Verb.FORCE,timeout);}
 synchronized Event awaitEmpty(long ms)throws IOException {
  if(ms<1||ms>60000)throw new IllegalArgumentException("Drain deadline");long start=System.nanoTime();
  while(event.phase()!=Phase.EMPTY){long left=ms-TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-start);if(left<=0)throw new IOException("NATIVE46_DRAIN_TIMEOUT");send(Verb.STATUS,Math.min(timeout,left));if(event.phase()!=Phase.EMPTY)try{Thread.sleep(10);}catch(InterruptedException bad){Thread.currentThread().interrupt();throw new IOException("NATIVE46_INTERRUPTED");}}
  return event;
 }
 synchronized void finish()throws IOException {
  Event last=send(Verb.CLOSE,timeout);waitOwner(timeout);if(owner.exitValue()!=0){poison();throw new IOException("NATIVE46_OWNER_EXIT_FAILED");}
  complete=last.pid()>0&&last.active()==0&&last.exitCode()==0&&last.stopSent()&&!last.forced()&&!last.ownerLost()&&!last.logFailed()&&last.error()==0;closed=true;closeStreams();
 }
 synchronized void disconnect(long ms)throws IOException {poison();waitOwner(ms);closed=true;closeStreams();}
 private void waitOwner(long ms)throws IOException {
  try{if(!owner.waitFor(ms,TimeUnit.MILLISECONDS))throw new IOException("NATIVE46_OWNER_STILL_HOLDS_JOB");}catch(InterruptedException bad){Thread.currentThread().interrupt();throw new IOException("NATIVE46_INTERRUPTED");}
 }
 synchronized Map<String,Object> snapshot(){
  boolean unknown=poisoned||!closed&&(!owner.isAlive()||failure.get()!=null);var out=new LinkedHashMap<String,Object>();out.put("schema",46);out.put("run",binding.run().toString());out.put("epoch",binding.epoch());out.put("phase",unknown?"UNKNOWN":event==null?"HANDSHAKE":event.phase().name());out.put("pid",event==null?0:event.pid());out.put("active",unknown?-1:event==null?-1:event.active());out.put("forced",event!=null&&event.forced());out.put("stopSent",event!=null&&event.stopSent());out.put("gracefulDiagnosticComplete",complete);out.put("ownerAlive",owner.isAlive());out.put("installAuthority",false);out.put("liveContainmentProof",false);return out;
 }
 private Event send(Verb verb,long budget)throws IOException {
  if(poisoned||closed||sequence==Long.MAX_VALUE)throw new IOException("NATIVE46_CHANNEL_UNAVAILABLE");
  boolean allowed=switch(verb){case START->event.phase()==Phase.NEW;case CLOSE->event.phase()==Phase.NEW||event.phase()==Phase.EMPTY;case STOP,FORCE->event.pid()>0&&event.phase()!=Phase.CLOSED;case STATUS->event.phase()!=Phase.CLOSED;};if(!allowed)throw new IOException("NATIVE46_COMMAND_NOT_ALLOWED");
  try{long next=sequence+1;write(command(binding,next,verb));Event result=receive(next,budget);transition(event,result,verb);event=result;sequence=next;return result;}catch(IOException bad){poison();throw bad;}
 }
 private void write(String line)throws IOException{owner.getOutputStream().write((line+"\n").getBytes(StandardCharsets.US_ASCII));owner.getOutputStream().flush();}
 private Event receive(long seq,long ms)throws IOException {
  try{String line=replies.poll(ms,TimeUnit.MILLISECONDS);if(line==null)throw new IOException(failure.get()==null?"NATIVE46_RESPONSE_TIMEOUT":failure.get());Event result=event(line,binding,seq);String error=failure.get();if(error!=null&&(!error.equals("NATIVE46_EOF")||result.phase()!=Phase.CLOSED))throw new IOException(error);return result;}
  catch(InterruptedException bad){Thread.currentThread().interrupt();throw new IOException("NATIVE46_INTERRUPTED");}
 }
 private void read(){try(var in=owner.getInputStream()){var line=new ByteArrayOutputStream();int c;while((c=in.read())>=0){if(c=='\n'){if(!replies.offer(line.toString(StandardCharsets.US_ASCII)))throw new IOException("flood");line.reset();}else{if(c<32||c>126||line.size()>=512)throw new IOException("frame");line.write(c);}}failure.compareAndSet(null,"NATIVE46_EOF");}catch(IOException bad){failure.compareAndSet(null,"NATIVE46_OUTPUT_FAILED");}}
 private void poison(){poisoned=true;complete=false;try{owner.getOutputStream().close();}catch(IOException ignored){}/* Owner retains job and follows capsule orphan policy. */}
 private void closeStreams(){Thread closer=new Thread(()->{try{owner.getInputStream().close();}catch(IOException ignored){}try{owner.getErrorStream().close();}catch(IOException ignored){}},"genesis-java46-pipe-close");closer.setDaemon(true);closer.start();}
 @Override public synchronized void close(){if(closed)return;if(!poisoned&&event!=null&&(event.phase()==Phase.NEW||event.phase()==Phase.EMPTY))try{finish();return;}catch(IOException ignored){}poison();closed=true;}
}
