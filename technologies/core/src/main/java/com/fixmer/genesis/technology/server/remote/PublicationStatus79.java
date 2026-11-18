package com.fixmer.genesis.technology.server.remote;
import java.nio.file.*;import java.util.*;
/** Historical cryptographic inspection, not network acceptance or installation authority. */
public final class PublicationStatus79 {
 private PublicationStatus79(){}
 public static Map<String,Object> inspect(Path folder,long now)throws Exception{
  var o=RemoteUpdateCli69.verifyPublic(folder,false);var out=new TreeMap<String,Object>();out.put("release",o.release().toString());out.put("sequence",o.sequence());out.put("game",o.game());out.put("endpoint",o.endpoint());out.put("minecraft",o.minecraft());out.put("neoforge",o.neoforge());out.put("issued",o.issued());out.put("expires",o.expires());out.put("remainingMs",Math.max(0,o.expires()-now));out.put("state",o.issued()>now+120000?"CLOCK_AHEAD":o.expires()<=now?"EXPIRED":o.expires()-now<300000?"EXPIRING":"VALID");out.put("installAuthority",false);return out;
 }
 public static void main(String[] args){try{if(args.length!=1)throw new IllegalArgumentException();System.out.println(DedicatedTools74.json(inspect(Path.of(args[0]),System.currentTimeMillis())));}catch(Exception bad){System.err.println("PUBLICATION79_BASELINE");System.exit(2);}}
}
