package com.fixmer.genesis.supervisor;
import java.io.*;import java.nio.charset.StandardCharsets;import java.nio.file.*;import java.net.*;import java.util.*;
/** Fixed loopback, bounded wire, owner/config/instance/profile bound. Never sends a token in argv/URL. */
public final class RecoveryConnection92 {
 private final Path config,game;private final int port;private final String token,instance;private String ownerPid="";
 public RecoveryConnection92(Path path)throws IOException{
  config=path.toAbsolutePath().normalize();RuntimeFiles.safe(config);if(!Files.isRegularFile(config,LinkOption.NOFOLLOW_LINKS)||Files.size(config)>65536)throw new IOException("RECOVERY92_CONFIG");
  Properties p=parse(Files.readString(config,StandardCharsets.UTF_8));
  instance=p.getProperty("instanceId", "").trim();
  try {
   token=SupervisorConfig.controlToken(p);
   if(!UUID.fromString(instance).toString().equals(instance))throw new IllegalArgumentException();
   port=SupervisorConfig.parseInt(p,"controlPort",27670,1,65535);
   Path work=SupervisorConfig.resolve(config.getParent(),p.getProperty("workDir", "."));
   game=SupervisorConfig.resolve(config.getParent(),p.getProperty("gameDir",work.toString())).toAbsolutePath().normalize();
  }catch(IOException|RuntimeException bad){throw new IOException("RECOVERY92_CONFIG",bad);}
 }
 static Properties parse(String body)throws IOException{
  if(body.length()>65536)throw new IOException("RECOVERY92_LIMIT");
  Properties p=new Properties(){@Override public synchronized Object put(Object k,Object v){if(containsKey(k))throw new IllegalArgumentException("duplicate");return super.put(k,v);}};
  try{p.load(new StringReader(body));}catch(IllegalArgumentException e){throw new IOException("RECOVERY92_WIRE");}return p;
 }
 private void verify()throws IOException{
  Properties p=parse(wire("GET","/recovery92/identity",null));
  if(!"genesis-supervisor".equals(p.getProperty("service"))||!"4".equals(p.getProperty("protocol"))||!"10".equals(p.getProperty("lifecycleVersion"))||!"92".equals(p.getProperty("recoveryVersion92"))||!instance.equals(p.getProperty("instanceId")))throw new IOException("RECOVERY92_OWNER");
  try{if(!config.equals(Path.of(p.getProperty("configPath")).toAbsolutePath().normalize())||!game.equals(Path.of(p.getProperty("gameDir")).toAbsolutePath().normalize())||port!=Integer.parseInt(p.getProperty("controlPort"))||Long.parseLong(p.getProperty("supervisorPid"))<=0)throw new IllegalArgumentException();}catch(RuntimeException e){throw new IOException("RECOVERY92_OWNER");}
  String pid=p.getProperty("supervisorPid");if(!ownerPid.isEmpty()&&!ownerPid.equals(pid))throw new IOException("RECOVERY92_OWNER_CHANGED");ownerPid=pid;
 }
 public synchronized Properties status()throws IOException{verify();Properties p=parse(wire("GET","/recovery92",null));if(!"92".equals(p.getProperty("schema")))throw new IOException("RECOVERY92_WIRE");return p;}
 public synchronized boolean decide(Properties reviewed,String action)throws IOException{
  if(!Set.of("RESTORE","RETRY","REFRESH","CLOSE").contains(action))throw new IOException("RECOVERY92_ACTION");verify();
  Map<String,String> headers=Map.of("X-Genesis-Instance",instance,"X-Genesis-Recovery-Ticket",reviewed.getProperty("ticket",""),"X-Genesis-Recovery-Revision",reviewed.getProperty("revision",""),"X-Genesis-Recovery-Action",action);
  try{return wire("POST","/recovery92/decide",headers).contains("\"ok\":true");}catch(HttpConflict92 conflict){return false;}
 }
 private static final class HttpConflict92 extends IOException{}
 private String wire(String method,String path,Map<String,String> headers)throws IOException{
  HttpURLConnection c=(HttpURLConnection)new URL("http://127.0.0.1:"+port+path).openConnection(Proxy.NO_PROXY);c.setInstanceFollowRedirects(false);c.setConnectTimeout(1000);c.setReadTimeout(2000);c.setRequestMethod(method);String nonce=UUID.randomUUID().toString().replace("-","");String time=Long.toString(System.currentTimeMillis());
  c.setRequestProperty("X-Genesis-Instance",instance);c.setRequestProperty("X-Genesis-Recovery-Nonce",nonce);c.setRequestProperty("X-Genesis-Recovery-Time",time);
  c.setRequestProperty("X-Genesis-Recovery-Proof",RecoveryAuth92.request(token,instance,method,path,nonce,time,headers==null?null:headers.get("X-Genesis-Recovery-Ticket"),headers==null?null:headers.get("X-Genesis-Recovery-Revision"),headers==null?null:headers.get("X-Genesis-Recovery-Action")));if(headers!=null)for(var h:headers.entrySet()){if(h.getValue().length()>128||h.getValue().chars().anyMatch(v->v<32||v>126))throw new IOException("RECOVERY92_HEADER");c.setRequestProperty(h.getKey(),h.getValue());}
  try{int code=c.getResponseCode();if(code!=200&&code!=202&&code!=409)throw new IOException("RECOVERY92_UNAVAILABLE");try(InputStream in=code>=400?c.getErrorStream():c.getInputStream()){if(in==null)throw new IOException("RECOVERY92_WIRE");byte[] b=in.readNBytes(16385);if(b.length>16384)throw new IOException("RECOVERY92_LIMIT");String body=new String(b,StandardCharsets.UTF_8);if(!RecoveryAuth92.equal(c.getHeaderField("X-Genesis-Recovery-Response"),RecoveryAuth92.response(token,nonce,code,body)))throw new IOException("RECOVERY92_OWNER_PROOF");if(code==409)throw new HttpConflict92();return body;}}finally{c.disconnect();}
 }
}
