package com.fixmer.genesis.supervisor;

import com.fixmer.genesis.technology.server.remote.*;
import com.fixmer.genesis.technology.server.firstlaunch.*;
import com.sun.net.httpserver.*;
import javax.net.ssl.*;
import java.io.*;import java.net.*;import java.nio.charset.StandardCharsets;import java.nio.file.*;import java.security.*;import java.util.*;import java.util.concurrent.*;

/** Actual loopback TLS, signed control, HMAC and many independent JVM clients with real mod-set installs.
 * The Minecraft sessions, dedicated READY and endpoint handoff are explicit fixtures, not game certification. */
public final class MultiplayerChecks99 {
 static int count;static void ok(boolean b){count++;if(!b)throw new AssertionError("multiplayer99 "+count);}
 static final String PASSWORD="Genesis99-fixture-only";
 static String java(){return Path.of(System.getProperty("java.home"),"bin",System.getProperty("os.name").startsWith("Windows")?"java.exe":"java").toString();}
 static void write(Path path,Properties p)throws Exception{Files.createDirectories(path.getParent());Path temp=Files.createTempFile(path.getParent(),"fixture99-",".tmp");try{try(var out=Files.newBufferedWriter(temp)){p.store(out,"");}Files.move(temp,path,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}finally{Files.deleteIfExists(temp);}}
 static Properties read(Path p)throws Exception{var out=new Properties();try(var in=Files.newBufferedReader(p)){out.load(in);}return out;}
 static boolean flag(Path root,String name){return Files.exists(root.resolve(name));}
 interface Condition {boolean ready()throws Exception;}
 static void waitFor(Condition condition,int seconds)throws Exception{long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(seconds);while(System.nanoTime()<end){if(condition.ready())return;Thread.sleep(50);}throw new AssertionError("multiplayer99 wait expired");}
 static Process spawn(Path root,String mode,String...args)throws Exception{
  var command=new ArrayList<String>(List.of(java(),"-Xms16m","-Xmx80m","-Djava.net.preferIPv4Stack=true","-Djavax.net.ssl.trustStore="+root.resolve("trust.p12"),"-Djavax.net.ssl.trustStorePassword="+PASSWORD,"-Djavax.net.ssl.trustStoreType=PKCS12","-cp",System.getProperty("java.class.path"),MultiplayerChecks99.class.getName(),mode,root.toString()));command.addAll(List.of(args));
  var runtime=new RuntimeFiles(root);return new ProcessBuilder(JavaArgumentFile.wrap(command,runtime)).redirectErrorStream(true).redirectOutput(root.resolve(mode+"-"+String.join("-",args)+"-"+UUID.randomUUID()+".log").toFile()).start();
 }
 static void keytool(Path root,String...args)throws Exception {
  String tool=Path.of(System.getProperty("java.home"),"bin",System.getProperty("os.name").startsWith("Windows")?"keytool.exe":"keytool").toString();var command=new ArrayList<>(List.of(tool));command.addAll(List.of(args));Process p=new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.appendTo(root.resolve("keytool99.log").toFile())).start();if(!p.waitFor(25,TimeUnit.SECONDS)||p.exitValue()!=0){p.destroyForcibly();throw new IOException("fixture99 keytool failed");}
 }
 static void setupTls(Path root)throws Exception {
  keytool(root,"-genkeypair","-alias","server99","-keyalg","RSA","-keysize","2048","-validity","2","-dname","CN=localhost","-ext","SAN=dns:localhost","-storetype","PKCS12","-keystore",root.resolve("server.p12").toString(),"-storepass",PASSWORD,"-keypass",PASSWORD,"-noprompt");
  keytool(root,"-exportcert","-alias","server99","-keystore",root.resolve("server.p12").toString(),"-storepass",PASSWORD,"-file",root.resolve("server.cer").toString());
  keytool(root,"-importcert","-alias","server99","-file",root.resolve("server.cer").toString(),"-storetype","PKCS12","-keystore",root.resolve("trust.p12").toString(),"-storepass",PASSWORD,"-noprompt");
 }
 static SSLContext tls(Path root)throws Exception {KeyStore ks=KeyStore.getInstance("PKCS12");try(var in=Files.newInputStream(root.resolve("server.p12"))){ks.load(in,PASSWORD.toCharArray());}KeyManagerFactory km=KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());km.init(ks,PASSWORD.toCharArray());SSLContext context=SSLContext.getInstance("TLS");context.init(km.getKeyManagers(),null,new SecureRandom());return context;}
 static void config(Path root,ControlRoom73 room,RemoteConsent72 consent,List<RemoteConsent72.Session> sessions,int index)throws Exception {
  var session=sessions.get(index);var frame=consent.frame(session,System.currentTimeMillis());var member=room.member(session.player(),index<8);
  FirstLaunchFiles.write(root.resolve("client"+index+".cfg"),ControlWire73.encode(room.config(member,frame.ticket())),true);
  var extra=new Properties();extra.setProperty("connection",session.connection().toString());extra.setProperty("consentRound",frame.round().toString());extra.setProperty("root",root.toString());write(root.resolve("extra"+index+".properties"),extra);
 }
 static void reply(HttpExchange e,int code,byte[] bytes)throws IOException {try{e.sendResponseHeaders(code,bytes.length);try(var out=e.getResponseBody()){out.write(bytes);}}finally{e.close();}}
 static void owner(Path root,boolean restore,boolean cancel)throws Exception {
  var server=HttpsServer.create(new InetSocketAddress(InetAddress.getByName("127.0.0.1"),restore?Integer.parseInt(Files.readString(root.resolve("port99"))):0),32);server.setHttpsConfigurator(new HttpsConfigurator(tls(root)));var executor=Executors.newFixedThreadPool(10);server.setExecutor(executor);
  ControlRoom73 room;RemoteConsent72 consent;List<RemoteConsent72.Session> sessions=new ArrayList<>();KeyPair keys;
  Path identity=root.resolve("identity99");
  if(restore){room=ControlRoom73.restore(root.resolve("control99"),identity);consent=null;keys=null;try{room.cancel();throw new AssertionError("cancelled committed round");}catch(IOException expected){}}
  else {
   keys=KeyPairGenerator.getInstance("Ed25519").generateKeyPair();Files.createDirectories(identity);Files.write(identity.resolve("public.der"),keys.getPublic().getEncoded());Files.write(identity.resolve("private.der"),keys.getPrivate().getEncoded());
   Files.write(root.resolve("alpha.jar"),Files.readAllBytes(root.resolve("payload-alpha.jar")));Files.write(root.resolve("beta.jar"),Files.readAllBytes(root.resolve("payload-beta.jar")));
   var manifest=Map.of("schema","99","alpha",ModDeployment.hash(root.resolve("alpha.jar")),"beta",ModDeployment.hash(root.resolve("beta.jar")));byte[] signed=ControlWire73.sign(keys.getPrivate(),manifest);Files.write(root.resolve("manifest99"),signed);
   long now=System.currentTimeMillis();String endpoint="https://localhost:"+server.getAddress().getPort()+"/genesis/";
   var offer=new RemoteOffer69("localhost:25565",endpoint,UUID.randomUUID(),UUID.randomUUID(),null,"1.21.1","21.1.264",1,now,now+3600000,signed.length,FirstLaunchFiles.sha(signed),FirstLaunchFiles.sha(keys.getPublic().getEncoded()));
   int original=cancel?2:8;for(int i=0;i<(cancel?2:9);i++)sessions.add(new RemoteConsent72.Session(UUID.randomUUID(),UUID.randomUUID()));
   consent=new RemoteConsent72(UUID.randomUUID(),offer,sessions.subList(0,original),now);if(!cancel)consent.bind(sessions.get(8),now);
   room=ControlRoom73.create(root.resolve("control99"),identity,offer,consent.frame(sessions.get(0),now).instance(),UUID.randomUUID(),sessions.subList(0,original).stream().map(RemoteConsent72.Session::player).toList());
   for(int i=0;i<sessions.size();i++)config(root,room,consent,sessions,i);
   Files.writeString(root.resolve("port99"),Integer.toString(server.getAddress().getPort()));
  }
  final ControlRoom73 active=room;final RemoteConsent72 choices=consent;
  server.createContext("/genesis/",e->{try{
   String path=e.getRequestURI().getPath();if(e.getRequestURI().getQuery()!=null){reply(e,404,new byte[0]);return;}
   synchronized(active){
    if(path.equals("/genesis/decision")&&e.getRequestMethod().equals("POST")){
     byte[] bytes=e.getRequestBody().readNBytes(16385);boolean accepted=false;if(choices!=null)for(int i=0;i<sessions.size();i++){
      var session=sessions.get(i);var member=active.member(session.player(),false);Map<String,String> d;try{d=LocalApply67.verify(member.secret,bytes);}catch(IOException wrong){continue;}
      var choice=new RemoteConsent72.Choice(UUID.fromString(d.get("round")),active.offer.release(),active.offer.archiveSha(),UUID.fromString(d.get("ticket")),UUID.fromString(d.get("request")),Boolean.parseBoolean(d.get("accept")));
      accepted=choices.choose(session,choice,System.currentTimeMillis());if(accepted){active.decision(session.player(),choice.accept()?"ACCEPTED":"DECLINED");active.persist();}break;
     }
     reply(e,accepted?202:403,new byte[0]);return;
    }
    if(path.equals("/genesis/control73/receipt")&&e.getRequestMethod().equals("POST")){byte[] packet=e.getRequestBody().readNBytes(16385);if(packet.length>16384)throw new IOException();active.receipt(packet);reply(e,202,new byte[0]);return;}
    if(path.equals("/genesis/control73/"+active.round+".state")){reply(e,200,Files.readAllBytes(active.root.resolve("public/"+active.round+".state")));return;}
    if(Set.of("/genesis/manifest99","/genesis/alpha.jar","/genesis/beta.jar").contains(path)){reply(e,200,Files.readAllBytes(root.resolve(path.substring(path.lastIndexOf('/')+1)))) ;return;}
    if(path.equals("/genesis/truncated")){byte[] all=Files.readAllBytes(root.resolve("alpha.jar"));e.sendResponseHeaders(200,all.length);try(var out=e.getResponseBody()){out.write(all,0,all.length/2);}finally{e.close();}return;}
    reply(e,404,new byte[0]);
   }
  }catch(Exception invalid){try{reply(e,403,new byte[0]);}catch(IOException ignored){e.close();}}});
  try{
   synchronized(active){active.publish(System.currentTimeMillis());}server.start();Files.writeString(root.resolve("owner99.ready"),Long.toString(ProcessHandle.current().pid()));
   long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(110);
   while(!flag(root,"stop99")&&System.nanoTime()<deadline){
    synchronized(active){
     if(choices!=null&&active.phase().equals("PREPARING")){
      long now=System.currentTimeMillis();choices.tick(now);
      for(var session:sessions){String decision=choices.frame(session,now).decision();active.decision(session.player(),decision);}
      if(cancel&&active.members().stream().allMatch(m->m.state.equals("PREPARED")))active.cancel();
      if(!cancel){try{active.commit();}catch(IOException pending){if(!Set.of("CONTROL73_NOT_PREPARED","CONTROL73_EMPTY").contains(pending.getMessage()))throw pending;}}
     }
     if(active.phase().equals("COMMITTED")&&!active.admission()&&flag(root,"server-ready99"))try{active.admit();}catch(IOException threshold){if(!"CONTROL73_THRESHOLD".equals(threshold.getMessage()))throw threshold;}
     active.publish(System.currentTimeMillis());
     var progress=new Properties();progress.setProperty("pid",Long.toString(ProcessHandle.current().pid()));progress.setProperty("phase",active.phase());progress.setProperty("installed",Integer.toString(active.installed()));progress.setProperty("needed",Integer.toString(active.needed()));progress.setProperty("denominator",Integer.toString(active.denominator()));progress.setProperty("admission",Boolean.toString(active.admission()));progress.setProperty("lateInstalled",Boolean.toString(active.members().stream().anyMatch(m->!m.original&&m.state.equals("INSTALLED"))));
     progress.setProperty("declined",Long.toString(active.members().stream().filter(m->m.decision.equals("DECLINED")).count()));progress.setProperty("expired",Long.toString(active.members().stream().filter(m->m.decision.equals("EXPIRED")).count()));write(root.resolve("progress99.properties"),progress);
    }
    Thread.sleep(100);
   }
  }finally{server.stop(0);executor.shutdownNow();}
 }
 static byte[] fetch(String endpoint,String name,byte[] body)throws Exception {
  HttpsURLConnection c=(HttpsURLConnection)URI.create(endpoint+name).toURL().openConnection();c.setConnectTimeout(1500);c.setReadTimeout(2000);c.setInstanceFollowRedirects(false);
  try{if(body!=null){c.setDoOutput(true);c.setRequestMethod("POST");c.setFixedLengthStreamingMode(body.length);try(var out=c.getOutputStream()){out.write(body);}}
   int status=c.getResponseCode();if(status!=(body==null?200:202))throw new IOException("fixture HTTP "+status);try(var in=c.getInputStream()){byte[] bytes=in.readNBytes(131073);if(bytes.length>131072||c.getContentLengthLong()>=0&&bytes.length!=c.getContentLengthLong())throw new IOException("fixture truncated response");return bytes;}
  }finally{c.disconnect();}
 }
 static Map<String,String> status(Map<String,String> cfg,byte[] pub)throws Exception {return ControlHttp73.status(cfg,pub);}
 static void client(Path root,int index,boolean cancelled)throws Exception {
  var cfg=ControlHttp73.config(root.resolve("client"+index+".cfg"));byte[] publicKey=Files.readAllBytes(root.resolve("identity99/public.der"));var extra=read(root.resolve("extra"+index+".properties"));
  Path game=root.resolve("player"+index);Files.createDirectories(game.resolve("mods"));Files.createDirectories(game.resolve("saves/world"));Files.writeString(game.resolve("saves/world/level.dat"),"DUMMY_PLAYER_WORLD");
  Path config=game.resolve("config/mared/dev/supervisor.properties");if(!Files.exists(config)){Properties p=new Properties();p.setProperty("gameDir",game.toString());p.setProperty("workDir",game.toString());p.setProperty("instanceId",UUID.randomUUID().toString());p.setProperty("controlToken",UUID.randomUUID().toString().replace("-","")+UUID.randomUUID().toString().replace("-",""));p.setProperty("command.0",java());p.setProperty("command.1","-version");write(config,p);}
  // Invalid signature / pin / HMAC fail before staging; these are actual HTTPS requests.
  if(index==0){
   try{var wrong=new HashMap<>(cfg);wrong.put("signer","0".repeat(64));status(wrong,publicKey);throw new AssertionError("bad pin accepted");}catch(IOException expected){}
   byte[] invalid=LocalApply67.sign(new byte[32],Map.of("schema","73","round",cfg.get("round"),"client",cfg.get("client"),"request",UUID.randomUUID().toString(),"action","PREPARED","archive",cfg.get("archive"),"inventory","0".repeat(64)));
   try{fetch(cfg.get("endpoint"),"control73/receipt",invalid);throw new AssertionError("bad HMAC accepted");}catch(IOException expected){}Files.writeString(root.resolve("invalid99.checked"),"refused");
  }
  if(!cancelled&&index==7){waitFor(()->{try{return !status(cfg,publicKey).get("phase").equals("PREPARING");}catch(Exception transientError){return false;}},70);Files.writeString(root.resolve("player7.expired"),"no install");return;}
  boolean decline=!cancelled&&index==6;
  // Restored clients keep the already accepted identity/config and do not auto-consent to a different round.
  if(!Files.exists(root.resolve("choice"+index+".sent"))){byte[] choice=LocalApply67.sign(HexFormat.of().parseHex(cfg.get("secret")),Map.of("round",extra.getProperty("consentRound"),"ticket",cfg.get("ticket"),"request",UUID.randomUUID().toString(),"accept",Boolean.toString(!decline)));fetch(cfg.get("endpoint"),"decision",choice);Files.writeString(root.resolve("choice"+index+".sent"),"same round");}
  if(decline){Files.writeString(root.resolve("player6.declined"),"no install");return;}
  byte[] signed=fetch(cfg.get("endpoint"),"manifest99",null);if(!FirstLaunchFiles.sha(signed).equals(cfg.get("archive")))throw new IOException("manifest hash");var manifest=ControlWire73.verify(RemoteOffer69.publicKey(publicKey),signed);
  if(!cancelled&&index==4&&!flag(root,"truncated99.checked")){try{fetch(cfg.get("endpoint"),"truncated",null);throw new AssertionError("truncated download accepted");}catch(IOException expected){}Files.writeString(root.resolve("truncated99.checked"),"retry required");}
  Path download=Files.createDirectories(game.resolve("download99"));for(String id:List.of("alpha","beta")){byte[] bytes=fetch(cfg.get("endpoint"),id+".jar",null);if(!FirstLaunchFiles.sha(bytes).equals(manifest.get(id)))throw new IOException("payload SHA");Files.write(download.resolve(id+".jar"),bytes);}
  var d=new ModDeployment(game);Map<String,String> loaded=Map.of("minecraft","1.21.1","neoforge","21.1.264");String token,revision;
  if(Files.exists(game.resolve("config/mared/dev/deployments/pending.properties"))){var pending=d.pendingSet99();token=pending.transaction();revision=pending.version();}
  else {var inputs=List.of(ModSetChecks99.input("alpha_project",download.resolve("alpha.jar")),ModSetChecks99.input("beta_project",download.resolve("beta.jar")));var review=d.reviewSet99(inputs,Set.of(),loaded);revision=review.revision();token=d.stageSet99(inputs,Set.of(),loaded,review);}
  ControlHttp73.post(cfg,"PREPARED",revision,UUID.randomUUID());
  Map<String,String> committed=null;long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(90);
  while(System.nanoTime()<end){try{var s=status(cfg,publicKey);if(s.get("phase").equals("CANCELLED")){d.cancelPending(token);try(var files=Files.list(game.resolve("mods"))){if(files.findAny().isPresent())throw new AssertionError("cancel wrote mods");}Files.writeString(root.resolve("player"+index+".cancelled"),"unchanged");return;}if(s.get("phase").equals("COMMITTED")){committed=s;break;}}catch(IOException transientFailure){}Thread.sleep(150);}
  if(committed==null)throw new IOException("commit unavailable");
  if(!cancelled&&index==3&&!flag(root,"crash3.once")){Files.writeString(root.resolve("crash3.once"),"committed, not installed");Runtime.getRuntime().halt(93);}
  if(!cancelled&&index==0)waitFor(()->flag(root,"release-first99"),50);
  else if(!cancelled&&index==1)waitFor(()->flag(root,"release-second99"),50);
  else if(!cancelled&&index!=8)waitFor(()->{try{return status(cfg,publicKey).get("admission").equals("true");}catch(Exception down){return false;}},60);
  // Recheck signed COMMIT immediately before the actual installation. No pre-COMMIT client restart.
  if(!status(cfg,publicKey).get("phase").equals("COMMITTED"))throw new IOException("commit changed");var settings=SupervisorConfig.load(new String[]{"--config="+config});
  try(var owner=OwnerLease94.acquire(config);var writer=ProfileLease92.writer(game)){if(!d.beforeLaunch().equals(token))throw new AssertionError("token");}
  if(d.confirmedSet99().size()!=0||!ModJarMetadata.read(game.resolve("mods/mared-dev-alpha.jar")).version().equals("2"))throw new AssertionError("not full trial");
  ControlHttp73.post(cfg,"INSTALLED",revision,UUID.randomUUID());waitFor(()->{try{return status(cfg,publicKey).get("admission").equals("true");}catch(Exception down){return false;}},60);
  // WORLD_FRAME_READY is deliberately not fabricated here: only file installation and protocol JOINED are tested.
  ControlHttp73.post(cfg,"JOINED",revision,UUID.randomUUID());Files.writeString(root.resolve("player"+index+".joined"),"protocol fixture, not Minecraft");
 }
 static Properties progress(Path root)throws Exception {return read(root.resolve("progress99.properties"));}
 static void scenario(Path root,boolean cancel)throws Exception {
  Files.createDirectories(root);setupTls(root);ModSetChecks99.jar(root.resolve("payload-alpha.jar"),"alpha","2","");ModSetChecks99.jar(root.resolve("payload-beta.jar"),"beta","2","[[dependencies.beta]]\nmodId=\"alpha\"\nversionRange=\"[2,)\"\ntype=\"required\"\n");
  var processes=new ArrayList<Process>();Process owner=spawn(root,cancel?"cancel-owner":"owner");processes.add(owner);
  try{
   waitFor(()->flag(root,"owner99.ready"),15);for(int i=0;i<(cancel?2:9);i++)processes.add(spawn(root,cancel?"cancel-client":"client",Integer.toString(i)));
   if(cancel){waitFor(()->flag(root,"player0.cancelled")&&flag(root,"player1.cancelled"),30);ok(progress(root).getProperty("phase").equals("CANCELLED"));for(int i=0;i<2;i++){try(var files=Files.list(root.resolve("player"+i+"/mods"))){ok(files.findAny().isEmpty());}ok(!Files.exists(root.resolve("player"+i+"/config/mared/dev/deployments/pending.properties")));}}
   else {
    waitFor(()->flag(root,"progress99.properties")&&progress(root).getProperty("lateInstalled").equals("true"),65);
    var late=progress(root);ok(late.getProperty("denominator").equals("6"));ok(late.getProperty("needed").equals("2"));ok(late.getProperty("installed").equals("0"));ok(late.getProperty("admission").equals("false"));ok(late.getProperty("declined").equals("1"));ok(late.getProperty("expired").equals("1"));
    Files.writeString(root.resolve("release-first99"),"fixture original ready");waitFor(()->progress(root).getProperty("installed").equals("1"),15);ok(progress(root).getProperty("admission").equals("false"));
    owner.destroyForcibly();ok(owner.waitFor(8,TimeUnit.SECONDS));Files.delete(root.resolve("owner99.ready"));Process restarted=spawn(root,"restore-owner");processes.add(restarted);waitFor(()->flag(root,"owner99.ready")&&Files.readString(root.resolve("owner99.ready")).equals(Long.toString(restarted.pid())),20);
    waitFor(()->progress(root).getProperty("pid").equals(Long.toString(restarted.pid())),10);ok(progress(root).getProperty("installed").equals("1"));ok(progress(root).getProperty("denominator").equals("6"));ok(progress(root).getProperty("admission").equals("false"));
    Files.writeString(root.resolve("release-second99"),"fixture second ready");waitFor(()->progress(root).getProperty("installed").equals("2"),20);ok(progress(root).getProperty("admission").equals("false"));Files.writeString(root.resolve("server-ready99"),"EXPLICIT DEDICATED READY FIXTURE");
    waitFor(()->progress(root).getProperty("admission").equals("true"),15);ok(true);waitFor(()->flag(root,"player0.joined")&&flag(root,"player1.joined")&&flag(root,"player8.joined"),15);
    waitFor(()->flag(root,"crash3.once"),10);Process retry=spawn(root,"client","3");processes.add(retry);waitFor(()->flag(root,"player3.joined"),20);ok(true);
    waitFor(()->flag(root,"player2.joined")&&flag(root,"player4.joined")&&flag(root,"player5.joined"),15);ok(flag(root,"invalid99.checked"));ok(flag(root,"truncated99.checked"));ok(flag(root,"player6.declined"));ok(flag(root,"player7.expired"));
    for(int i:new int[]{6,7}){try(var files=Files.list(root.resolve("player"+i+"/mods"))){ok(files.findAny().isEmpty());}}
   }
   for(int i=0;i<(cancel?2:9);i++)ok(Files.readString(root.resolve("player"+i+"/saves/world/level.dat")).equals("DUMMY_PLAYER_WORLD"));
   Files.writeString(root.resolve("stop99"),"stop only this fixture owner");
  }finally{for(Process p:processes)if(p.isAlive())p.destroyForcibly();for(Process p:processes)p.waitFor(5,TimeUnit.SECONDS);}
 }
 public static void main(String[] args)throws Exception {
  if(args.length>0){Path root=Path.of(args[1]);switch(args[0]){case "owner"->owner(root,false,false);case "cancel-owner"->owner(root,false,true);case "restore-owner"->owner(root,true,false);case "client"->client(root,Integer.parseInt(args[2]),false);case "cancel-client"->client(root,Integer.parseInt(args[2]),true);default->throw new IllegalArgumentException();}return;}
  Path root=Files.createTempDirectory("genesis multiplayer99 ");boolean success=false;try{scenario(root.resolve("commit"),false);scenario(root.resolve("cancel"),true);success=true;System.out.println("Multiplayer99 real TLS/Ed25519/HMAC/9-client-JVM/set-install/30s-expiry/25-percent/owner-crash/cancel/retry checks passed: "+count+"; sessions, server READY and JOINED are explicit fixtures, not Minecraft.");}
  finally{if(success){try(var paths=Files.walk(root)){for(Path p:paths.sorted(Comparator.reverseOrder()).toList())Files.delete(p);}}else System.err.println("Fixture evidence retained at "+root+" (generated test credentials only).");}
 }
}
