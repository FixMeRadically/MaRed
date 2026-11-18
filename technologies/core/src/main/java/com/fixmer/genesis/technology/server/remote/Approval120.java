package com.fixmer.genesis.technology.server.remote;
import com.fixmer.genesis.technology.server.firstlaunch.*;
import java.io.*;import java.nio.file.*;import java.security.SecureRandom;import java.util.*;
/** Persisted exact-artifact approval. Never authorizes a live write, role escalation or world downgrade. */
public final class Approval120 {
 private Approval120(){}
 private static Path scope(Path root)throws IOException{return FirstLaunchFiles.safe(root.toAbsolutePath().normalize());}
 private static Path file(Path root,String transaction)throws IOException{if(!UUID.fromString(transaction).toString().equals(transaction))throw new IOException("APPROVAL120_ID");return scope(root).resolve("approvals120").resolve(transaction);}
 public static void issue(Path root,String purpose,String transaction,String artifact)throws IOException{
  root=scope(root);if(!purpose.matches("[A-Z][A-Z0-9_]{0,31}")||!artifact.matches("[a-f0-9]{64}"))throw new IOException("APPROVAL120_INPUT");
  Files.createDirectories(root);Path key=root.resolve("approval120.key");if(!Files.exists(key,LinkOption.NOFOLLOW_LINKS)){byte[] bytes=new byte[32];new SecureRandom().nextBytes(bytes);FirstLaunchFiles.write(key,bytes,false);FirstLaunchFiles.privateMode(key);}
  byte[] secret=FirstLaunchFiles.read(key,32);if(secret.length!=32)throw new IOException("APPROVAL120_KEY");Path target=file(root,transaction);Files.createDirectories(target.getParent());
  var data=Map.of("schema","120","scope",root.toString(),"purpose",purpose,"transaction",transaction,"artifact",artifact,"issued",Long.toString(System.currentTimeMillis()));
  if(Files.exists(target,LinkOption.NOFOLLOW_LINKS))throw new IOException("APPROVAL120_ALREADY_ISSUED");FirstLaunchFiles.write(target,LocalApply67.sign(secret,data),false);
 }
 public static void verify(Path root,String purpose,String transaction,String artifact)throws IOException{
  root=scope(root);var d=LocalApply67.verify(FirstLaunchFiles.read(root.resolve("approval120.key"),32),FirstLaunchFiles.read(file(root,transaction),16384));
  if(!d.keySet().equals(Set.of("schema","scope","purpose","transaction","artifact","issued"))||!"120".equals(d.get("schema"))||!root.toString().equals(d.get("scope"))||!purpose.equals(d.get("purpose"))||!transaction.equals(d.get("transaction"))||!artifact.equals(d.get("artifact"))||!d.get("issued").matches("[1-9][0-9]{11,16}"))throw new IOException("APPROVAL120_REQUIRED_OR_CHANGED");
 }
}
