package com.fixmer.genesis.admission;
import java.io.*;import java.nio.channels.FileChannel;import java.nio.charset.StandardCharsets;import java.nio.file.*;import java.security.*;import javax.crypto.*;import javax.crypto.spec.SecretKeySpec;import java.util.*;

/** Actual premain receipt, distinct from the diagnostic93. Never proves that a process exited. */
public final class AdmissionReceipt94 {
 private static final Set<String> KEYS=Set.of("schema","record","launch","game","pid","created","mac");
 private AdmissionReceipt94(){}
 private static void uuid(String v)throws IOException{try{if(!UUID.fromString(v).toString().equals(v))throw new IllegalArgumentException();}catch(RuntimeException e){throw new IOException("COLD94_ADMISSION_RECEIPT_INVALID");}}
 private static Path path(Path game)throws IOException{Path p=game.toAbsolutePath().normalize().resolve("config/mared/dev/profile-admitted94.properties");for(Path n=p;n!=null;n=n.getParent())if(Files.isSymbolicLink(n))throw new IOException("COLD94_ADMISSION_LINK");return p;}
 private static String hash(Path game)throws IOException{try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(game.toAbsolutePath().normalize().toString().getBytes(StandardCharsets.UTF_8)));}catch(GeneralSecurityException e){throw new IOException(e);}}
 private static String mac(String token,Properties p)throws IOException {
  try{Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(token.getBytes(StandardCharsets.UTF_8),"HmacSHA256"));StringBuilder b=new StringBuilder("GENESIS_ADMITTED_BEFORE_MAIN94\n");for(String k:new TreeSet<>(KEYS))if(!k.equals("mac"))b.append(k).append('\0').append(p.getProperty(k,"")).append('\0');return HexFormat.of().formatHex(mac.doFinal(b.toString().getBytes(StandardCharsets.UTF_8)));}catch(GeneralSecurityException e){throw new IOException(e);}
 }
 static void write(Path game)throws IOException {
  String record=System.getenv("GENESIS_COLD_RECORD94");if(record==null)return;
  String launch=System.getenv("GENESIS_LAUNCH_ID"),token=System.getenv("GENESIS_SUPERVISOR_TOKEN");uuid(record);uuid(launch);
  if(!"1".equals(System.getenv("GENESIS_SUPERVISOR"))||token==null||token.length()<32||token.length()>4096)throw new IOException("COLD94_ADMISSION_CONTEXT_MISSING");
  Properties p=new Properties();p.setProperty("schema","94");p.setProperty("record",record);p.setProperty("launch",launch);p.setProperty("game",hash(game));p.setProperty("pid",Long.toString(ProcessHandle.current().pid()));p.setProperty("created",Long.toString(System.currentTimeMillis()));p.setProperty("mac",mac(token,p));
  Path target=path(game),temp=Files.createTempFile(target.getParent(),".admitted94-",".tmp");
  try{
   try(var out=Files.newBufferedWriter(temp,StandardCharsets.UTF_8)){p.store(out,"Actual guarded premain entry, not process exit evidence.");}
   try(var channel=FileChannel.open(temp,StandardOpenOption.WRITE)){channel.force(true);}
   try{Files.move(temp,target,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}catch(AtomicMoveNotSupportedException e){Files.move(temp,target,StandardCopyOption.REPLACE_EXISTING);}
  }finally{Files.deleteIfExists(temp);}
 }
 public static void verify(Path game,String token,String record,String launch)throws IOException {
  uuid(record);uuid(launch);Path file=path(game);
  if(!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS)||Files.size(file)>8192)throw new IOException("COLD94_NO_PREMAIN_RECEIPT");
  Properties p=new Properties(){public synchronized Object put(Object k,Object v){if(containsKey(k))throw new IllegalArgumentException();return super.put(k,v);}};
  try{p.load(new StringReader(Files.readString(file,StandardCharsets.UTF_8)));}catch(IllegalArgumentException e){throw new IOException("COLD94_ADMISSION_RECEIPT_INVALID");}
  String signature=p.getProperty("mac","");
  if(!p.stringPropertyNames().equals(KEYS)||!signature.matches("[a-f0-9]{64}")||!MessageDigest.isEqual(signature.getBytes(StandardCharsets.US_ASCII),mac(token,p).getBytes(StandardCharsets.US_ASCII))||!"94".equals(p.getProperty("schema"))||!record.equals(p.getProperty("record"))||!launch.equals(p.getProperty("launch"))||!hash(game).equals(p.getProperty("game"))||!p.getProperty("pid","").matches("[1-9][0-9]{0,19}")||!p.getProperty("created","").matches("[0-9]{1,16}"))throw new IOException("COLD94_ADMISSION_RECEIPT_INVALID");
 }
}
