package com.fixmer.genesis.supervisor;
import java.io.*;import java.nio.charset.StandardCharsets;import java.nio.file.*;import java.security.*;import java.util.*;
/** A launch-local cooperative stop signal. A report/timer alone never authorizes force or mod changes. */
public final class StartupAbort101 {
 private StartupAbort101(){}
 public static boolean reason(String reason){return reason!=null&&Set.of("LOADER_ERROR","STARTUP_TIMEOUT","RETURN_FAILED","PROCESS_EXIT").contains(reason);}
 public static String sha(String s){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));}catch(GeneralSecurityException e){throw new AssertionError(e);}}
 static void write(Path file,String launch,String nonce,String reason)throws IOException {
  if(!reason(reason))throw new IOException("STARTUP101_REASON");RuntimeFiles.safe(file);Properties p=new Properties();p.setProperty("schema","101");p.setProperty("launch",launch);p.setProperty("reason",reason);p.setProperty("mac",RecoveryAuth92.mac(nonce,"startup101\n"+launch+"\n"+reason));Path temp=Files.createTempFile(file.getParent(),"abort101-",".tmp");
  try{try(var out=Files.newBufferedWriter(temp,StandardCharsets.UTF_8)){p.store(out,"Bound cooperative startup stop");}Files.move(temp,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}finally{Files.deleteIfExists(temp);}
 }
 static String read(Path file,String launch,String nonce){try{RuntimeFiles.safe(file);if(!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS)||Files.size(file)>2048)return "";Properties p=RecoveryConnection92.parse(Files.readString(file));String r=p.getProperty("reason","");if(!p.stringPropertyNames().equals(Set.of("schema","launch","reason","mac"))||!"101".equals(p.getProperty("schema"))||!launch.equals(p.getProperty("launch"))||!reason(r)||!RecoveryAuth92.equal(p.getProperty("mac"),RecoveryAuth92.mac(nonce,"startup101\n"+launch+"\n"+r)))return "";return r;}catch(IOException|RuntimeException invalid){return "";}}
}
