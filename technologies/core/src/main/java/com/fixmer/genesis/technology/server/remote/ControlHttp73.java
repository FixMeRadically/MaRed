package com.fixmer.genesis.technology.server.remote;
import com.fixmer.genesis.technology.server.firstlaunch.*;
import javax.net.ssl.HttpsURLConnection;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;

/** HTTPS + pinned signed decision; receipt HMAC secrets only come from a private local config file. */
public final class ControlHttp73 {
 private ControlHttp73(){}
 public static Map<String,String> config(Path file)throws Exception{var c=ControlWire73.decode(FirstLaunchFiles.read(file,16384));if(!c.keySet().equals(Set.of("schema","authMode","instance","round","client","secret","account","ticket","release","archive","distribution","game","endpoint","signer"))||!"73".equals(c.get("schema"))||!Set.of("ONLINE","LOCAL_OFFLINE").contains(c.get("authMode"))||!c.get("secret").matches("[a-f0-9]{64}")||!c.get("archive").matches("[a-f0-9]{64}")||!c.get("signer").matches("[a-f0-9]{64}")||!RemoteOffer69.gameAddress(c.get("game")).equals(c.get("game"))||!RemoteOffer69.base(c.get("endpoint")).equals(c.get("endpoint")))throw new IOException("CONTROL73_CONFIG");for(String k:List.of("instance","round","client","account","ticket","release","distribution"))if(!UUID.fromString(c.get(k)).toString().equals(c.get(k)))throw new IOException("CONTROL73_UUID");PinnedTls159.hint(c.get("endpoint"),c.get("signer"));return c;}
 static byte[] fetch(URI uri,byte[] body,String client)throws Exception{
  HttpsURLConnection c=(HttpsURLConnection)uri.toURL().openConnection();PinnedTls159.apply(c);c.setConnectTimeout(8000);c.setReadTimeout(5000);c.setInstanceFollowRedirects(false);c.setUseCaches(false);c.setRequestProperty("Accept-Encoding","identity");
  try{if(body!=null){c.setRequestMethod("POST");c.setRequestProperty("X-Genesis-Client",client);c.setDoOutput(true);c.setFixedLengthStreamingMode(body.length);try(var out=c.getOutputStream()){out.write(body);}if(c.getResponseCode()!=202)throw new IOException("CONTROL73_POST");return new byte[0];}
   if(c.getResponseCode()!=200||c.getContentLengthLong()>131072)throw new IOException("CONTROL73_HTTP");String encoding=c.getHeaderField("Content-Encoding");if(encoding!=null&&!encoding.equalsIgnoreCase("identity"))throw new IOException("CONTROL73_ENCODING");try(var in=c.getInputStream()){byte[] bytes=in.readNBytes(131073);if(bytes.length>131072||c.getContentLengthLong()>=0&&bytes.length!=c.getContentLengthLong())throw new IOException("CONTROL73_SIZE");return bytes;}
  }finally{c.disconnect();}
 }
 public static Map<String,String> status(Map<String,String> cfg,byte[] publicBytes)throws Exception{
  if(!FirstLaunchFiles.sha(publicBytes).equals(cfg.get("signer")))throw new IOException("CONTROL73_PIN");PinnedTls159.hint(cfg.get("endpoint"),cfg.get("signer"));var d=ControlWire73.verify(RemoteOffer69.publicKey(publicBytes),fetch(URI.create(cfg.get("endpoint")+"control73/"+cfg.get("round")+".state"),null,null));
  if(!d.keySet().equals(Set.of("schema","instance","round","release","archive","distribution","game","endpoint","signer","phase","sequence","expires","needed","installed","admission","clients")))throw new IOException("CONTROL73_STATE");for(String k:List.of("schema","instance","round","release","archive","distribution","game","endpoint","signer"))if(!d.get(k).equals(cfg.get(k)))throw new IOException("CONTROL73_BINDING");
  long expires=Long.parseLong(d.get("expires")),now=System.currentTimeMillis();if(expires<now||expires>now+30000||Long.parseLong(d.get("sequence"))<1||!Set.of("PREPARING","COMMITTED","CANCELLED","CLOSED").contains(d.get("phase"))||!Set.of("true","false").contains(d.get("admission")))throw new IOException("CONTROL73_STALE");int needed=Integer.parseInt(d.get("needed")),installed=Integer.parseInt(d.get("installed"));if(needed<1||needed>16||installed<0||installed>64)throw new IOException("CONTROL73_COUNTS");boolean found=false;Set<UUID> ids=new HashSet<>();for(String row:d.get("clients").split(";")){String[] v=row.split(",",-1);if(v.length!=3||!ids.add(UUID.fromString(v[0]))||ids.size()>64||!Set.of("WAITING","PREPARED","INSTALLED","JOINED","ABORTED").contains(v[1]))throw new IOException("CONTROL73_CLIENTS");UUID.fromString(v[2]);if(v[0].equals(cfg.get("client")))found=true;}if(!found)throw new IOException("CONTROL73_CLIENT");return d;
 }
 public static void post(Map<String,String> cfg,String action,String inventory,UUID request)throws Exception{
  if(!Set.of("PREPARED","INSTALLED","JOINED","ABORT").contains(action)||!inventory.matches("[a-f0-9]{64}")||request.equals(new UUID(0,0)))throw new IOException("CONTROL73_POST");PinnedTls159.hint(cfg.get("endpoint"),cfg.get("signer"));var d=Map.of("schema","73","round",cfg.get("round"),"client",cfg.get("client"),"request",request.toString(),"action",action,"archive",cfg.get("archive"),"inventory",inventory);fetch(URI.create(cfg.get("endpoint")+"control73/receipt"),LocalApply67.sign(HexFormat.of().parseHex(cfg.get("secret")),d),cfg.get("client"));
 }
 public static void main(String[] args){try{var cfg=config(Path.of(args[1]));if(args[0].equals("poll")&&args.length==3){var d=status(cfg,FirstLaunchFiles.read(Path.of(args[2]),256));System.out.println(Base64.getEncoder().encodeToString(ControlWire73.encode(d)));return;}if(args[0].equals("post")&&args.length==5){post(cfg,args[2],args[3],UUID.fromString(args[4]));System.out.println("QUEUED");return;}throw new IOException();}catch(Exception bad){System.err.println("CONTROL73_REJECTED");System.exit(2);}}
}
