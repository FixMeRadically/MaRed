package com.fixmer.genesis.supervisor;
import java.nio.file.*;import java.util.*;import java.util.concurrent.*;import java.util.concurrent.atomic.*;
/** Real state/atomic-gate/files tests. No Minecraft or Windows simulation is called game acceptance. */
public final class RestartChecks95 {
 static int checks;static void ok(boolean value){checks++;if(!value)throw new AssertionError("check "+checks);}
 interface IO{void run()throws Exception;}static void reject(IO action)throws Exception{try{action.run();throw new AssertionError("accepted invalid input");}catch(IllegalArgumentException expected){checks++;}}
 static String uuid(){return UUID.randomUUID().toString();}
 static RestartAttempt95 saved(){var g=new RestartAttempt95(0,1000);ok(g.beginSave(1));ok(g.saved(2,()->{}));return g;}
 public static void main(String[]args)throws Exception{
  String request=uuid(),snapshot=uuid()+"."+uuid();var p=new RestartPermit95(request,snapshot,100);
  ok(p.phase(101).equals("PREPARED"));ok(!p.commit(uuid(),101));ok(p.phase(102).equals("PREPARED"));ok(p.matches(request,snapshot));ok(!p.matches(uuid(),snapshot));
  ok(p.commit(p.grant,102));ok(!p.commit(p.grant,103));ok(p.cancel("user",104));ok(p.cancel("again",105));p.finish("DONE","");ok(p.phase(106).equals("CANCELLED"));ok(!p.commit(p.grant,107));
  p=new RestartPermit95(uuid(),"-."+uuid(),100);ok(p.phase(30_000_000_100L).equals("EXPIRED"));ok(!p.commit(p.grant,30_000_000_101L));ok(!p.cancel("late",30_000_000_101L));ok(!p.properties(0,false).containsKey("grant"));
  reject(()->new RestartPermit95("bad",snapshot,0));reject(()->RestartPermit95.parts(snapshot+".x"));reject(()->RestartPermit95.parts("unknown."+uuid()));reject(()->RestartPermit95.uuid("1-1-1-1-1"));
  p=new RestartPermit95(uuid(),snapshot,Long.MAX_VALUE-100);ok(p.phase(Long.MIN_VALUE+100).equals("PREPARED"));
  var g=saved();ok(!g.claimExit(3));ok(g.authorize(4));ok(g.claimExit(5));ok(!g.cancel());ok(g.exiting());ok(g.active(2000));
  g=saved();ok(g.cancel());ok(!g.authorize(3));ok(!g.claimExit(4));
  g=new RestartAttempt95(0,1000);ok(g.cancel());AtomicInteger armed=new AtomicInteger();ok(!g.beginSave(1));ok(!g.saved(2,armed::incrementAndGet));ok(armed.get()==0);
  g=saved();ok(!g.authorize(1000));ok(g.phase().equals("CANCELLED"));
  reject(()->new RestartAttempt95(0,0));reject(()->new RestartAttempt95(0,120_000_000_001L));
  // Race queued stop versus user Esc: exactly one transition may win.
  for(int i=0;i<80;i++){
   var gate=new RestartAttempt95(System.nanoTime(),10_000_000_000L);gate.beginSave(System.nanoTime());gate.saved(System.nanoTime(),()->{});gate.authorize(System.nanoTime());
   var start=new CountDownLatch(1);var cancelled=new AtomicBoolean();var exited=new AtomicBoolean();Thread a=new Thread(()->{try{start.await();cancelled.set(gate.cancel());}catch(InterruptedException e){throw new AssertionError(e);}}),b=new Thread(()->{try{start.await();exited.set(gate.claimExit(System.nanoTime()));}catch(InterruptedException e){throw new AssertionError(e);}});a.start();b.start();start.countDown();a.join();b.join();ok(cancelled.get()!=exited.get());
  }
  Path dir=Files.createTempDirectory("Genesis restart95 ");try{
   RuntimeFiles files=new RuntimeFiles(dir);String one=uuid(),two=uuid();files.shutdown95(one);ok(Files.readString(files.shutdownFile()).equals(files.id+"\nrestart95="+one));files.cancelShutdown95(two);ok(Files.exists(files.shutdownFile()));files.cancelShutdown95(one);ok(!Files.exists(files.shutdownFile()));files.shutdown();files.cancelShutdown95(one);ok(Files.readString(files.shutdownFile()).equals(files.id));
  }finally{try(var walk=Files.walk(dir)){for(Path path:walk.sorted(Comparator.reverseOrder()).toList())Files.delete(path);}}
  // Actual bounded HTTP client: a port occupant cannot forge the owner response or obtain the token.
  String secret=uuid()+uuid(),instance=uuid(),launch=uuid(),rid=uuid(),snap="-."+uuid();AtomicInteger mode=new AtomicInteger();AtomicBoolean leaked=new AtomicBoolean();
  var server=com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1",0),0);
  server.createContext("/restart95/prepare",exchange->{
   var h=exchange.getRequestHeaders();leaked.set(h.getFirst("X-Genesis-Token")!=null);String nonce=h.getFirst("X-Genesis-Recovery-Nonce"),time=h.getFirst("X-Genesis-Recovery-Time");
   boolean bound=RecoveryAuth92.equal(h.getFirst("X-Genesis-Recovery-Proof"),RecoveryAuth92.request(secret,instance,"POST","/restart95/prepare",nonce,time,rid,snap+"/"+launch,""));
   if(!bound)throw new AssertionError("request framing");String body="schema=95\nrequest="+(mode.get()==2?uuid():rid)+"\nstate=PREPARED\n";
   if(mode.get()>0)exchange.getResponseHeaders().set("X-Genesis-Recovery-Response",RecoveryAuth92.response(secret,nonce,200,body));byte[] bytes=body.getBytes(java.nio.charset.StandardCharsets.UTF_8);exchange.sendResponseHeaders(200,bytes.length);try(var out=exchange.getResponseBody()){out.write(bytes);}finally{exchange.close();}
  });server.start();try{
   var wire=new RestartConnection95(server.getAddress().getPort(),secret,instance,launch,rid,snap);
   try{wire.prepare();throw new AssertionError("unsigned owner accepted");}catch(java.io.IOException denied){ok(denied.getMessage().equals("RESTART95_OWNER_PROOF"));}
   ok(!leaked.get());mode.set(1);ok(wire.prepare().getProperty("state").equals("PREPARED"));mode.set(2);
   try{wire.prepare();throw new AssertionError("foreign request accepted");}catch(java.io.IOException denied){ok(denied.getMessage().equals("RESTART95_OWNER_BINDING"));}
  }finally{server.stop(0);}
  System.out.println("Restart95 real gate/race/reservation/file checks passed: "+checks+"; no Minecraft runtime.");
 }
}
