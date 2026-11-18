package com.fixmer.mared;
import java.nio.file.*;import java.net.*;import java.util.*;import java.util.jar.*;import java.lang.reflect.*;
/** Loads the actual standalone JAR with ONLY platform JDK classes as parent. */
public final class BootstrapIsolationChecks121 {
 private static int checks;
 private static void ok(boolean value){checks++;if(!value)throw new AssertionError("Bootstrap121 isolation "+checks);}
 private static void denied(Method method,Object... args)throws Exception{boolean rejected=false;try{method.invoke(null,args);}catch(InvocationTargetException expected){if(!(expected.getCause() instanceof java.io.IOException))throw expected;rejected=true;}ok(rejected);}
 public static void main(String[] args)throws Exception{
  if(args.length!=1)throw new IllegalArgumentException("bootstrap JAR required");Path jar=Path.of(args[0]).toRealPath();
  try(var archive=new JarFile(jar.toFile());var loader=new URLClassLoader(new URL[]{jar.toUri().toURL()},ClassLoader.getPlatformClassLoader())){
   ok(loader.getResource("net/minecraft/client/Minecraft.class")==null);
   for(String name:List.of("Approval120","FirstLaunchFiles","LocalApply67","FirstLaunchSession","FirstLaunchDev62")){
    String pkg=name.equals("Approval120")?"remote":"firstlaunch";String entry="com/fixmer/genesis/technology/server/"+pkg+"/"+name;
    ok(archive.getEntry(entry+".class")!=null);Class<?> type=Class.forName(entry.replace('/','.'),true,loader);ok(type.getClassLoader()==loader);type.getDeclaredMethods();type.getDeclaredConstructors();type.getDeclaredFields();
   }
   for(var entry:Collections.list(archive.entries()))if(entry.getName().endsWith(".class")&&(entry.getName().startsWith("com/fixmer/genesis/supervisor/")||entry.getName().startsWith("com/fixmer/genesis/admission/"))){
    Class<?> type=Class.forName(entry.getName().substring(0,entry.getName().length()-6).replace('/','.'),false,loader);type.getDeclaredMethods();type.getDeclaredConstructors();type.getDeclaredFields();ok(type.getClassLoader()==loader);
   }
   Class.forName("com.fixmer.genesis.technology.server.packages.PackageModel",true,loader).getDeclaredMethods();ok(true);
   ok(archive.getEntry("mared/dev/profile-admission93.jar")!=null);ok(archive.getEntry("mared/dev/startup-agent.jar")!=null);
   Class<?> type=loader.loadClass("com.fixmer.genesis.technology.server.remote.Approval120");Method issue=type.getMethod("issue",Path.class,String.class,String.class,String.class),verify=type.getMethod("verify",Path.class,String.class,String.class,String.class);
   Path root=Files.createTempDirectory("Genesis standalone approval121 ");try{
    String transaction=UUID.randomUUID().toString(),hash="a".repeat(64);issue.invoke(null,root,"SINGLE",transaction,hash);verify.invoke(null,root,"SINGLE",transaction,hash);ok(true);
    denied(verify,root,"SET",transaction,hash);denied(verify,root,"SINGLE",transaction,"b".repeat(64));denied(issue,root,"SINGLE",transaction,hash);
    Path receipt=root.resolve("approvals120").resolve(transaction);byte[] bytes=Files.readAllBytes(receipt);bytes[5]^=1;Files.write(receipt,bytes);denied(verify,root,"SINGLE",transaction,hash);
   }finally{try(var paths=Files.walk(root)){for(Path path:paths.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(path);}}
  }
  System.out.println("Bootstrap121 isolated archive/classloading/approval checks passed: "+checks+"; no Minecraft runtime.");
 }
}
