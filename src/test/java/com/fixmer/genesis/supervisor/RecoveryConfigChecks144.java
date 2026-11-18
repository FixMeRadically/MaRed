package com.fixmer.genesis.supervisor;
import java.io.*;import java.nio.charset.StandardCharsets;import java.nio.file.*;import java.net.*;import java.util.*;import com.sun.net.httpserver.HttpServer;import java.util.concurrent.atomic.AtomicInteger;
public final class RecoveryConfigChecks144 {
 static int count;static void check(boolean c,String name){if(!c)throw new AssertionError(name);count++;}
 static void save(Path file,Properties p)throws Exception{try(var out=Files.newBufferedWriter(file)){p.store(out,"");}}
 public static void main(String[] args)throws Exception{
  Path root=Files.createTempDirectory("recovery144-");Path file=root.resolve("supervisor.properties");String token="a".repeat(32),instance=UUID.randomUUID().toString();
  Files.createDirectories(root.resolve("quoted game"));var p=new Properties();p.setProperty("command.0","java");p.setProperty("command.1","-version");p.setProperty("instanceId",instance);
  try{
   for(String bad:List.of("a".repeat(513),token+" space",token+"Ю","short")){
    p.setProperty("controlToken",bad);save(file,p);boolean server=false,client=false;try{SupervisorConfig.load(new String[]{"--config="+file});}catch(IOException expected){server=true;}try{new RecoveryConnection92(file);}catch(IOException expected){client=true;}check(server&&client,"both reject invalid token");
   }
   p.setProperty("controlToken"," "+token+" ");p.setProperty("instanceId"," "+instance+" ");p.setProperty("gameDir","\"quoted game\"");save(file,p);
   SupervisorConfig config=SupervisorConfig.load(new String[]{"--config="+file});new RecoveryConnection92(file);check(config.controlPort()==27670,"default control port");check(config.token().equals(token)&&config.instanceId().equals(instance),"shared trim");check(config.gameDir().equals(root.resolve("quoted game")),"quoted game path");
   var owner=new Properties();owner.setProperty("instanceId",instance);owner.setProperty("configPath",file.toString());owner.setProperty("controlPort","27670");
   check(SupervisorRecovery.matches(owner,file,p,27670),"legacy recovery shares trimmed instance");
   var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);int port=server.getAddress().getPort();var requests=new AtomicInteger();
   p.setProperty("controlPort"," "+port+" ");save(file,p);
   server.createContext("/recovery92",e->{String path=e.getRequestURI().getPath();String nonce=e.getRequestHeaders().getFirst("X-Genesis-Recovery-Nonce"),time=e.getRequestHeaders().getFirst("X-Genesis-Recovery-Time"),proof=e.getRequestHeaders().getFirst("X-Genesis-Recovery-Proof");
    boolean valid=RecoveryAuth92.equal(proof,RecoveryAuth92.request(token,instance,"GET",path,nonce,time,null,null,null));
    if(!valid){e.sendResponseHeaders(403,-1);e.close();return;}requests.incrementAndGet();
    Properties reply=new Properties();
    if(path.endsWith("identity")){
     reply.setProperty("service","genesis-supervisor");reply.setProperty("protocol","4");reply.setProperty("lifecycleVersion","10");reply.setProperty("recoveryVersion92","92");
     reply.setProperty("instanceId",instance);reply.setProperty("configPath",file.toString());reply.setProperty("gameDir",root.resolve("quoted game").toString());reply.setProperty("controlPort",Integer.toString(port));reply.setProperty("supervisorPid","42");
    }else reply.setProperty("schema","92");
    StringWriter text=new StringWriter();reply.store(text,"");String body=text.toString();
    byte[] bytes=body.getBytes(StandardCharsets.UTF_8);e.getResponseHeaders().set("X-Genesis-Recovery-Response",RecoveryAuth92.response(token,nonce,200,body));e.sendResponseHeaders(200,bytes.length);try(var out=e.getResponseBody()){out.write(bytes);}
   });server.start();try{check("92".equals(new RecoveryConnection92(file).status().getProperty("schema"))&&requests.get()==2,"real signed identity and status");}finally{server.stop(0);}
   p.setProperty("controlPort"," 0 ");save(file,p);try{new RecoveryConnection92(file);throw new AssertionError("bad port accepted");}catch(IOException expected){count++;}
  }finally{try(var walk=Files.walk(root)){for(Path item:walk.sorted(Comparator.reverseOrder()).toList())Files.delete(item);}}
  System.out.println("RecoveryConfigChecks144: "+count+" passed");
 }
}
