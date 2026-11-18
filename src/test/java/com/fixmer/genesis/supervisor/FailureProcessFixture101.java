package com.fixmer.genesis.supervisor;
import java.nio.file.*;import java.util.*;import java.io.*;
/** Separate JVM with explicit loader/world signals; does not load Minecraft. */
public final class FailureProcessFixture101 {
 public static void main(String[] a)throws Exception{
  if(a.length==1&&a[0].equals("probe")){
   try(var raw=ProfileLaunch93.class.getResourceAsStream("/mared/dev/profile-admission93.jar")){
    if(raw==null)throw new IOException("PREFLIGHT102_PROFILE_CAPSULE_MISSING: Gradle must include supervisorBootstrapJar on the fixture classpath");
    try(var jar=new java.util.jar.JarInputStream(raw)){
     var manifest=jar.getManifest();if(manifest==null||!"com.fixmer.genesis.admission.ProfileAgent93".equals(manifest.getMainAttributes().getValue("Premain-Class")))throw new IOException("PREFLIGHT102_PROFILE_CAPSULE_MANIFEST");
     boolean agent=false;for(var entry=jar.getNextJarEntry();entry!=null;entry=jar.getNextJarEntry())if(entry.getName().equals("com/fixmer/genesis/admission/ProfileAgent93.class"))agent=true;
     if(!agent)throw new IOException("PREFLIGHT102_PROFILE_CAPSULE_CLASS");
    }
   }
   System.out.println("Failure102 preflight: bundled profile capsule found and inspected on the actual JVM classpath");return;
  }
  Path game=Path.of(a[1]),data=game.resolve("config/mared/dev");String mode=a[2];
  if(a[0].equals("prepare")){
   Files.createDirectories(data);Files.createDirectories(game.resolve("mods"));Files.createDirectories(game.resolve("saves/world"));Files.writeString(game.resolve("saves/world/level.dat"),"UNCHANGED_WORLD");
   Properties p=new Properties();p.setProperty("gameDir",game.toString());p.setProperty("workDir",game.toString());p.setProperty("instanceId",a[6]);p.setProperty("controlToken",a[5]);p.setProperty("controlPort",a[4]);p.setProperty("logFile",data.resolve("supervisor.log").toString());p.setProperty("restartUi.enabled","false");p.setProperty("fastLaunch.enabled","false");p.setProperty("cds.mode","off");p.setProperty("gracefulStopMs","1000");p.setProperty("trial.startupTimeoutMs","3500");
   String[] command={a[3],"-cp",a[7],FailureProcessFixture101.class.getName(),"child",game.toString(),mode};for(int i=0;i<command.length;i++)p.setProperty("command."+i,command[i]);Path config=data.resolve("supervisor.properties");try(var w=Files.newBufferedWriter(config)){p.store(w,"");}
   ModDeployment d=new ModDeployment(game);Map<String,String> loaded=new HashMap<>(Map.of("minecraft","1.21.1","neoforge","21.1.264"));
   Path old=ModSetChecks99.jar(game.resolve("old.jar"),"alpha","1","");var inputs=List.of(ModSetChecks99.input("alpha_project",old));String t=d.stageSet99(inputs,Set.of(),loaded,d.reviewSet99(inputs,Set.of(),loaded));try(var owner=OwnerLease94.acquire(config);var writer=ProfileLease92.writer(game)){d.beforeLaunch();}d.confirm(t);loaded.put("alpha","1");
   Path candidate=ModSetChecks99.jar(game.resolve("new.jar"),"alpha","2","");inputs=List.of(ModSetChecks99.input("alpha_project",candidate));d.stageSet99(inputs,Set.of(),loaded,d.reviewSet99(inputs,Set.of(),loaded));return;
  }
  String version=ModJarMetadata.read(game.resolve("mods/mared-dev-alpha.jar")).version();Files.writeString(game.resolve("starts101"),version+"\n",StandardOpenOption.CREATE,StandardOpenOption.APPEND);
  Files.createDirectories(game.resolve("logs"));Files.writeString(game.resolve("logs/latest.log"),version.equals("2")?"CANDIDATE_LOG accessToken=PRIVATE_GAME_SECRET":"PREVIOUS_VERSION_LOG");
  if(version.equals("1")){
   GameSessionClient.start();if(mode.equals("return-fails")){System.err.println("OLD_VERSION_ALSO_FAILED");System.exit(9);}GameSessionClient.milestone("title_ready");GameSessionClient.milestone("frame_ready");Files.writeString(game.resolve("returned101"),"MENU_ONLY");
  }else{
   System.err.println("CANDIDATE_ERROR Mod.java:42 --accessToken PRIVATE_CONSOLE_SECRET");
   if(mode.equals("crash")){System.exit(7);}if(mode.equals("exit-zero")){System.exit(0);}
   if(!mode.equals("unguarded")){
    GameSessionClient.start();long end=System.nanoTime()+4_000_000_000L;while(!mode.equals("late-ack")&&System.nanoTime()<end&&!Files.readString(data.resolve("deployments/trial.properties")).contains("guardAck101=true"))Thread.sleep(20);
    if(mode.equals("world")||mode.equals("world-crash")){new ModDeployment(game).protectTrial91(System.getenv("GENESIS_MOD_TRIAL"));GameSessionClient.integrated90(UUID.randomUUID().toString(),"ACTIVE");if(mode.equals("world-crash"))System.exit(8);}
    if(!mode.equals("timeout"))GameSessionClient.failure101("LOADER_ERROR");
   }
  }
  long end=System.nanoTime()+60_000_000_000L;while(System.nanoTime()<end){
   if(version.equals("2")&&Files.exists(game.resolve("allow-close101")))break;
   if(version.equals("1")||mode.equals("fatal"))if(Files.exists(Path.of(System.getenv("GENESIS_SHUTDOWN_FILE"))))break;
   Thread.sleep(20);
  }
  if(mode.equals("world")&&version.equals("2")){String epoch=Files.readString(Path.of(System.getenv("GENESIS_SESSION_FILE"))).lines().filter(l->l.startsWith("integratedEpoch90=")).findFirst().orElseThrow().substring("integratedEpoch90=".length());GameSessionClient.integrated90(epoch,"SAVED");GameSessionClient.integrated90(epoch,"STOPPED");}
  GameSessionClient.normalClosing();
 }
}
