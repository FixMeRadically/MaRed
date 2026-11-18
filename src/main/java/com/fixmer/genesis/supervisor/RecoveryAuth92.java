package com.fixmer.genesis.supervisor;
import javax.crypto.Mac;import javax.crypto.spec.SecretKeySpec;import java.nio.charset.StandardCharsets;import java.security.*;import java.util.*;
/** Bounded local HMAC requests/responses; the configured control secret never goes onto the recovery wire. */
final class RecoveryAuth92 {
 private final String key,instance;private final Map<String,Long> seen=new HashMap<>();
 RecoveryAuth92(String key,String instance){this.key=key;this.instance=instance;}
 static String mac(String key,String text){try{Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8),"HmacSHA256"));return HexFormat.of().formatHex(mac.doFinal(text.getBytes(StandardCharsets.UTF_8)));}catch(GeneralSecurityException impossible){throw new IllegalStateException(impossible);}}
 static String request(String key,String instance,String method,String path,String nonce,String time,String ticket,String revision,String action){return mac(key,String.join("\n","GENESIS_RECOVERY92",instance,method,path,nonce,time,Objects.toString(ticket,""),Objects.toString(revision,""),Objects.toString(action,"")));}
 static String response(String key,String nonce,int code,String body){return mac(key,"GENESIS_RECOVERY92_RESPONSE\n"+nonce+"\n"+code+"\n"+body);}
 static boolean equal(String actual,String expected){return actual!=null&&actual.matches("[a-f0-9]{64}")&&MessageDigest.isEqual(actual.getBytes(StandardCharsets.US_ASCII),expected.getBytes(StandardCharsets.US_ASCII));}
 synchronized boolean accept(String instance,String method,String path,String nonce,String time,String ticket,String revision,String action,String proof,long nowMs,long nowNs){
  if(!this.instance.equals(instance)||nonce==null||!nonce.matches("[a-f0-9]{32}")||time==null||!time.matches("[0-9]{1,16}"))return false;
  long epoch;try{epoch=Long.parseLong(time);}catch(NumberFormatException bad){return false;}
  if(epoch>nowMs+1000||epoch<nowMs-10000||!equal(proof,request(key,instance,method,path,nonce,time,ticket,revision,action)))return false;
  seen.entrySet().removeIf(e->nowNs-e.getValue()>=30_000_000_000L);if(seen.containsKey(nonce)||seen.size()>=1024)return false;seen.put(nonce,nowNs);return true;
 }
}
