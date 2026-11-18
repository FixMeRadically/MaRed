package com.fixmer.genesis.supervisor;
import java.io.*;import java.net.*;import java.nio.charset.StandardCharsets;import java.util.*;
/** Bounded signed loopback request/response; never sends the raw control token to a port occupant. */
public final class RestartConnection95 {
 private final int port;private final String token,instance,launch;private final String request,snapshot;
 public RestartConnection95(int port,String token,String instance,String launch,String request,String snapshot){
  if(port<1||port>65535||token==null||token.length()<32||token.length()>512)throw new IllegalArgumentException();RestartPermit95.uuid(instance);RestartPermit95.uuid(launch);RestartPermit95.uuid(request);RestartPermit95.parts(snapshot);this.port=port;this.token=token;this.instance=instance;this.launch=launch;this.request=request;this.snapshot=snapshot;
 }
 public Properties prepare()throws IOException{return wire("prepare","");}
 public Properties commit(String grant)throws IOException{return wire("commit",grant);}
 public Properties cancel()throws IOException{return wire("cancel","");}
 public Properties status()throws IOException{return wire("status","");}
 private Properties wire(String action,String grant)throws IOException{
  String path="/restart95/"+action,method=action.equals("status")?"GET":"POST",nonce=UUID.randomUUID().toString().replace("-",""),time=Long.toString(System.currentTimeMillis());
  String bound=snapshot+"/"+launch;
  HttpURLConnection c=(HttpURLConnection)new URL("http://127.0.0.1:"+port+path).openConnection(Proxy.NO_PROXY);c.setInstanceFollowRedirects(false);c.setConnectTimeout(1000);c.setReadTimeout(2000);c.setRequestMethod(method);
  c.setRequestProperty("X-Genesis-Instance",instance);c.setRequestProperty("X-Genesis-Recovery-Ticket",request);c.setRequestProperty("X-Genesis-Recovery-Revision",bound);c.setRequestProperty("X-Genesis-Recovery-Action",grant);c.setRequestProperty("X-Genesis-Recovery-Nonce",nonce);c.setRequestProperty("X-Genesis-Recovery-Time",time);c.setRequestProperty("X-Genesis-Recovery-Proof",RecoveryAuth92.request(token,instance,method,path,nonce,time,request,bound,grant));
  try{int code=c.getResponseCode();if(code!=200&&code!=202&&code!=409)throw new IOException("RESTART95_UNAVAILABLE");try(var in=code>=400?c.getErrorStream():c.getInputStream()){if(in==null)throw new IOException("RESTART95_WIRE");byte[] bytes=in.readNBytes(8193);if(bytes.length>8192)throw new IOException("RESTART95_LIMIT");String body=new String(bytes,StandardCharsets.UTF_8);if(!RecoveryAuth92.equal(c.getHeaderField("X-Genesis-Recovery-Response"),RecoveryAuth92.response(token,nonce,code,body)))throw new IOException("RESTART95_OWNER_PROOF");if(code==409)throw new IOException("RESTART95_STALE");Properties p=RecoveryConnection92.parse(body);if(!"95".equals(p.getProperty("schema"))||!request.equals(p.getProperty("request")))throw new IOException("RESTART95_OWNER_BINDING");return p;}}finally{c.disconnect();}
 }
}
