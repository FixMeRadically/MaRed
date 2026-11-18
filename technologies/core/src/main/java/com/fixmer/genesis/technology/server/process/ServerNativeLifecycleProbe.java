package com.fixmer.genesis.technology.server.process;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Fixed metadata-only lifecycle harness. No Minecraft classes, world or network. */
public final class ServerNativeLifecycleProbe {
 private ServerNativeLifecycleProbe(){}
 public static void main(String[] args)throws Exception {
  if(args.length!=2||!args[1].equals("--genesis-native-lifecycle50")||!Set.of("normal","save-fail","stop-crash","stall").contains(args[0]))throw new IOException("LIFECYCLE50_ARGUMENTS");
  for(String key:List.of("JAVA_TOOL_OPTIONS","_JAVA_OPTIONS","JDK_JAVA_OPTIONS","CLASSPATH","GENESIS_SUPERVISOR_TOKEN"))if(System.getenv(key)!=null)throw new IOException("LIFECYCLE50_INJECTED_ENVIRONMENT");
  if(!"1".equals(System.getenv("GENESIS_SERVER_SUPERVISED")))throw new IOException("LIFECYCLE50_SUPERVISED_REQUIRED");
  Path run=Path.of(System.getenv("GENESIS_SERVER_RUN_DIR")).toAbsolutePath().normalize();String id=System.getenv("GENESIS_SERVER_RUN_ID");
  if(!ServerFiles.canonicalUuid(id)||!run.getFileName().toString().equals(id))throw new IOException("LIFECYCLE50_RUN_MISMATCH");
  Properties scope=ServerFiles.read(run.resolve("probe.scope50"));if(!scope.stringPropertyNames().equals(Set.of("schema","scope","run"))||!scope.getProperty("schema").equals("50")||!scope.getProperty("scope").equals("DIAGNOSTIC_NO_GAME")||!scope.getProperty("run").equals(id))throw new IOException("LIFECYCLE50_PRIVATE_SCOPE_REQUIRED");
  var stop=new AtomicBoolean();Thread stdin=new Thread(()->{try(var in=new BufferedReader(new InputStreamReader(System.in,StandardCharsets.UTF_8))){for(String line;(line=in.readLine())!=null;)if(line.equals("stop"))break;}catch(IOException ignored){}finally{stop.set(true);}},"genesis-lifecycle50-input");stdin.setDaemon(true);stdin.start();
  try(var reporter=ServerLifecycleReporter.open(System.getenv())){
   reporter.started();int ticks=0;System.out.println("GENESIS_LIFECYCLE_PROBE50_NO_GAME");System.out.flush();
   while(!stop.get()){
    if(!args[0].equals("stall")||ticks==0)reporter.tick(()->{ServerFiles.write(run.resolve("diagnostic-save50"),scope);return !args[0].equals("save-fail");},()->{ServerFiles.write(run.resolve("diagnostic-stop50"),scope);if(args[0].equals("stop-crash"))Runtime.getRuntime().halt(9);stop.set(true);});
    ticks++;Thread.sleep(20);
   }
   reporter.stopping();reporter.stopped();
  }
 }
}
