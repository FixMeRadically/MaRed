package com.fixmer.genesis.supervisor;
import java.nio.file.*;import java.util.*;import java.util.concurrent.TimeUnit;
/** Abrupt separate-JVM interruption, OS-lock release and CAS repair. No real Minecraft save signal. */
public final class ModSetCrashChecks99 {
 static int count;static void ok(boolean value){count++;if(!value)throw new AssertionError("crash99 "+count);}
 static Process run(Path root,String...args)throws Exception{
  var command=new ArrayList<String>(List.of(Path.of(System.getProperty("java.home"),"bin",System.getProperty("os.name").startsWith("Windows")?"java.exe":"java").toString(),"-Xms16m","-Xmx64m","-cp",System.getProperty("java.class.path"),ModSetCrashChecks99.class.getName(),root.toString()));command.addAll(List.of(args));
  var runtime=new RuntimeFiles(root);return new ProcessBuilder(JavaArgumentFile.wrap(command,runtime)).redirectErrorStream(true).redirectOutput(root.resolve("crash99-"+UUID.randomUUID()+".log").toFile()).start();
 }
 static Process cli(Path root,String...args)throws Exception{
  var command=new ArrayList<String>(List.of(Path.of(System.getProperty("java.home"),"bin",System.getProperty("os.name").startsWith("Windows")?"java.exe":"java").toString(),"-Xms16m","-Xmx64m","-cp",System.getProperty("java.class.path"),SetRecovery99.class.getName(),"--config="+root.resolve("config/mared/dev/supervisor.properties")));command.addAll(List.of(args));
  var runtime=new RuntimeFiles(root);return new ProcessBuilder(JavaArgumentFile.wrap(command,runtime)).redirectErrorStream(true).redirectOutput(root.resolve("cli99-"+UUID.randomUUID()+".log").toFile()).start();
 }
 static int exit(Process p)throws Exception{try{if(!p.waitFor(15,TimeUnit.SECONDS))throw new AssertionError("CLI timeout");return p.exitValue();}finally{p.destroyForcibly();}}
 static SupervisorConfig config(Path root)throws Exception{return SupervisorConfig.load(new String[]{"--config="+root.resolve("config/mared/dev/supervisor.properties")});}
 static void child(String[] args)throws Exception {
  Path root=Path.of(args[0]);var c=config(root);var d=new ModDeployment(root);
  try(var owner=OwnerLease94.acquire(c.configPath());var writer=ProfileLease92.writer(root)){
   if(args[1].equals("install")){int stop=Integer.parseInt(args[2]);int[] steps={0};ModSet99.testHook99.set(name->{if(++steps[0]==stop)Runtime.getRuntime().halt(91);});d.beforeLaunch();throw new AssertionError("did not interrupt install");}
   if(args[1].equals("restore")){var v=d.inspectInterruptedSet99(c);int stop=Integer.parseInt(args[2]);int[] steps={0};if(stop>0)ModSet99.testHook99.set(name->{if(++steps[0]==stop)Runtime.getRuntime().halt(92);});d.restoreAfterExit92(v.revision());return;}
   if(args[1].equals("hold")){Files.writeString(root.resolve("writer99.ready"),"ready");Thread.sleep(15000);return;}
  }throw new AssertionError("mode");
 }
 public static void main(String[] args)throws Exception {
  if(args.length>0){child(args);return;}
  for(int crashAfter:new int[]{1,2})try(var f=new ModSetChecks99.Fixture()){
   f.initial();String token=f.stage(List.of(ModSetChecks99.input("alpha_project",f.jar("alpha","2","")),ModSetChecks99.input("beta_project",f.jar("beta","2",""))),Set.of());
   Process install=run(f.game,"install",Integer.toString(crashAfter));try{ok(install.waitFor(15,TimeUnit.SECONDS));ok(install.exitValue()==91);}finally{install.destroyForcibly();}
   ok(f.d.recovery91().needsChoice());ok(!f.d.recovery91().keepAllowed());
   String inventory=DeploymentInventory98.capture(f.mods);ok(exit(cli(f.game,"--inspect"))==0);ok(inventory.equals(DeploymentInventory98.capture(f.mods)));
   ok(exit(cli(f.game,"--restore","--revision="+"0".repeat(64)))==2);ok(inventory.equals(DeploymentInventory98.capture(f.mods)));
   Process blocker=run(f.game,"hold");try{
    long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(8);while(!Files.exists(f.game.resolve("writer99.ready"))&&System.nanoTime()<end)Thread.sleep(20);ok(Files.exists(f.game.resolve("writer99.ready")));
    ok(exit(cli(f.game,"--inspect"))==2);
    try{ProfileLease92.writer(f.game);throw new AssertionError("writer admitted");}catch(java.io.IOException expected){count++;}
    try{OwnerLease94.acquire(f.config);throw new AssertionError("owner admitted");}catch(java.io.IOException expected){count++;}
   }finally{blocker.destroyForcibly();blocker.waitFor(5,TimeUnit.SECONDS);}
   Process restore=run(f.game,"restore","1");try{ok(restore.waitFor(15,TimeUnit.SECONDS));ok(restore.exitValue()==92);}finally{restore.destroyForcibly();}
   ok(f.d.recovery91().phase().equals("RESTORE_REQUESTED"));
   Process finish=run(f.game,"restore","0");try{ok(finish.waitFor(15,TimeUnit.SECONDS));ok(finish.exitValue()==0);}finally{finish.destroyForcibly();}
   ok(f.d.recovery91().phase().equals("RESTORED_REVIEW"));ok(ModJarMetadata.read(f.mods.resolve("mared-dev-alpha.jar")).version().equals("1"));ok(ModJarMetadata.read(f.mods.resolve("mared-dev-beta.jar")).version().equals("1"));ok(Files.readString(f.data.resolve("resume.properties")).contains("hold91"));
  }
  try(var f=new ModSetChecks99.Fixture()){
   f.initial();String token=f.stage(List.of(ModSetChecks99.input("alpha_project",f.jar("alpha","2",""))),Set.of());var pending=f.d.pendingSet99();String inventory=DeploymentInventory98.capture(f.mods);
   ok(exit(cli(f.game,"--inspect"))==0);ok(exit(cli(f.game,"--cancel-pending","--revision="+"0".repeat(64)))==2);ok(Files.exists(f.data.resolve("deployments/pending.properties")));
   ok(exit(cli(f.game,"--cancel-pending","--revision="+pending.revision()))==0);ok(!Files.exists(f.data.resolve("deployments/pending.properties")));ok(inventory.equals(DeploymentInventory98.capture(f.mods)));
  }
  System.out.println("ModSet99 separate-JVM abrupt-install/restore-crash/owner/profile-lock checks passed: "+count+"; fixture JARs/world only, Windows unverified here.");
 }
}
