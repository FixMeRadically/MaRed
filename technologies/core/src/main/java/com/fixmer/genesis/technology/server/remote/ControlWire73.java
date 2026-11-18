package com.fixmer.genesis.technology.server.remote;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;

/** Canonical public state, domain-separated Ed25519. This is NOT an install script format. */
public final class ControlWire73 {
 private static final byte[] DOMAIN="GENESIS-SERVER-CONTROL73\n".getBytes(StandardCharsets.US_ASCII);
 private ControlWire73(){}
 public static byte[] encode(Map<String,String> values)throws IOException{
  if(values.isEmpty()||values.size()>32)throw new IOException("CONTROL73_FIELDS");StringBuilder b=new StringBuilder();
  for(var e:new TreeMap<>(values).entrySet()){if(!e.getKey().matches("[A-Za-z][A-Za-z0-9]{0,31}")||Set.of("mac","sig").contains(e.getKey())||e.getValue()==null||e.getValue().getBytes(StandardCharsets.UTF_8).length>65536)throw new IOException("CONTROL73_FIELD");b.append(e.getKey()).append('=').append(Base64.getUrlEncoder().withoutPadding().encodeToString(e.getValue().getBytes(StandardCharsets.UTF_8))).append('\n');}
  byte[] data=b.toString().getBytes(StandardCharsets.US_ASCII);if(data.length>130000)throw new IOException("CONTROL73_SIZE");return data;
 }
 public static Map<String,String> decode(byte[] data)throws IOException{
  if(data.length>130000)throw new IOException("CONTROL73_SIZE");try{String text=new String(data,StandardCharsets.US_ASCII);Map<String,String> out=new TreeMap<>();for(String line:text.split("\n")){int at=line.indexOf('=');if(at<1)throw new IOException("CONTROL73_FORMAT");String k=line.substring(0,at),v=new String(Base64.getUrlDecoder().decode(line.substring(at+1)),StandardCharsets.UTF_8);if(out.put(k,v)!=null)throw new IOException("CONTROL73_DUPLICATE");}if(!Arrays.equals(data,encode(out)))throw new IOException("CONTROL73_CANONICAL");return Map.copyOf(out);}catch(IllegalArgumentException bad){throw new IOException("CONTROL73_FORMAT",bad);}
 }
 public static byte[] sign(PrivateKey key,Map<String,String> values)throws Exception{byte[] body=encode(values);Signature s=Signature.getInstance("Ed25519");s.initSign(key);s.update(DOMAIN);s.update(body);byte[] suffix=("sig="+Base64.getUrlEncoder().withoutPadding().encodeToString(s.sign())+"\n").getBytes(StandardCharsets.US_ASCII);var out=new ByteArrayOutputStream();out.write(body);out.write(suffix);return out.toByteArray();}
 public static Map<String,String> verify(PublicKey key,byte[] data)throws Exception{
  if(data.length>131072)throw new IOException("CONTROL73_SIZE");String text=new String(data,StandardCharsets.US_ASCII);int at=text.lastIndexOf("sig=");if(at<1||text.charAt(at-1)!='\n'||!text.endsWith("\n"))throw new IOException("CONTROL73_SIGNATURE");byte[] body=Arrays.copyOf(data,at);String encoded=text.substring(at+4,text.length()-1);byte[] signature=Base64.getUrlDecoder().decode(encoded);if(signature.length!=64||!Base64.getUrlEncoder().withoutPadding().encodeToString(signature).equals(encoded))throw new IOException("CONTROL73_SIGNATURE");Signature s=Signature.getInstance("Ed25519");s.initVerify(key);s.update(DOMAIN);s.update(body);if(!s.verify(signature))throw new IOException("CONTROL73_SIGNATURE");return decode(body);
 }
}
