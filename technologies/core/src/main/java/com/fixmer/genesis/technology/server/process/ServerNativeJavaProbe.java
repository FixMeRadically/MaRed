package com.fixmer.genesis.technology.server.process;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
/** Fixed diagnostic: no game/world, no network, no application file writes. */
public final class ServerNativeJavaProbe {
 private ServerNativeJavaProbe(){}
 public static void main(String[] args)throws Exception {
  if(args.length!=2||!args[1].equals("--genesis-native-probe46")||!Set.of("normal","ignore","flood","root-exit","leaf").contains(args[0]))System.exit(2);
  for(String name:List.of("JAVA_TOOL_OPTIONS","_JAVA_OPTIONS","JDK_JAVA_OPTIONS","CLASSPATH","GENESIS_SUPERVISOR_TOKEN"))if(System.getenv(name)!=null)System.exit(9);
  System.out.println("GENESIS_JAVA_PROBE_READY46");System.out.flush();
  if(args[0].equals("root-exit")){
   String exe=Path.of(System.getProperty("java.home"),"bin",System.getProperty("os.name").startsWith("Windows")?"java.exe":"java").toString();
   new ProcessBuilder(exe,"-cp",System.getProperty("java.class.path"),ServerNativeJavaProbe.class.getName(),"leaf","--genesis-native-probe46").inheritIO().start();return;
  }
  if(args[0].equals("flood")){for(int i=0;i<30000;i++)System.out.println("diagnostic46 log drain bounded output 012345678901234567890123456789 "+i);System.out.flush();}
  if(args[0].equals("ignore")||args[0].equals("leaf")){for(;;)Thread.sleep(1000);}
  try(var input=new BufferedReader(new InputStreamReader(System.in,StandardCharsets.UTF_8))){for(String line;(line=input.readLine())!=null;)if(line.equals("stop")){System.out.println("GENESIS_JAVA_PROBE_STOPPED46");return;}}
  System.exit(7);
 }
}
