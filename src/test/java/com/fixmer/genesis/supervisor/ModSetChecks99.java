package com.fixmer.genesis.supervisor;
import java.io.*;import java.nio.charset.StandardCharsets;import java.nio.file.*;import java.util.*;import java.util.jar.*;
/** Real files, full revisions, reverse dependencies, CAS and recovery; not Minecraft/world compatibility. */
public final class ModSetChecks99 {
 static int count;static void ok(boolean b){count++;if(!b)throw new AssertionError("set99 "+count);}interface IO{void run()throws Exception;}static void reject(IO run)throws Exception{count++;try{run.run();}catch(IOException expected){return;}throw new AssertionError("accepted "+count);}
 static Path jar(Path file,String id,String version,String extra)throws Exception{try(var out=new JarOutputStream(Files.newOutputStream(file))){out.putNextEntry(new JarEntry("META-INF/neoforge.mods.toml"));out.write(("modLoader=\"javafml\"\n[[mods]]\nmodId=\""+id+"\"\nversion=\""+version+"\"\n"+extra).getBytes(StandardCharsets.UTF_8));out.closeEntry();}return file;}
 static ModSet99.Input input(String project,Path jar)throws Exception{return new ModSet99.Input(project,jar,ModDeployment.hash(jar));}
 static class Fixture implements AutoCloseable{
  final Path root=Files.createTempDirectory("set99-"),game=root.resolve("profile"),data=game.resolve("config/mared/dev"),mods=game.resolve("mods"),config=data.resolve("supervisor.properties");final ModDeployment d=new ModDeployment(game);final SupervisorConfig settings;final Map<String,String> loaded=new HashMap<>(Map.of("minecraft","1.21.1","neoforge","21.1.264"));
  Fixture()throws Exception{Files.createDirectories(data);Files.createDirectories(mods);Files.createDirectories(game.resolve("saves/world"));Files.writeString(game.resolve("saves/world/level.dat"),"UNCHANGED_WORLD");Properties p=new Properties();p.setProperty("gameDir",game.toString());p.setProperty("workDir",game.toString());p.setProperty("instanceId",UUID.randomUUID().toString());p.setProperty("controlToken","a".repeat(64));p.setProperty("command.0",Path.of(System.getProperty("java.home"),"bin","java").toString());p.setProperty("command.1","-version");try(var out=Files.newBufferedWriter(config)){p.store(out,"");}settings=SupervisorConfig.load(new String[]{"--config="+config});}
  Path jar(String id,String v,String extra)throws Exception{return ModSetChecks99.jar(root.resolve(id+"-"+v+".jar"),id,v,extra);}
  String stage(List<ModSet99.Input> inputs,Set<String> removals)throws Exception{return d.stageSet99(inputs,removals,loaded,d.reviewSet99(inputs,removals,loaded));}
  void install(String token)throws Exception{try(var owner=OwnerLease94.acquire(config);var writer=ProfileLease92.writer(game)){ok(d.beforeLaunch().equals(token));}}
  void confirmed(String token)throws Exception{d.confirm(token);loaded.clear();loaded.put("minecraft","1.21.1");loaded.put("neoforge","21.1.264");for(var e:d.confirmedSet99())loaded.put(e.modId(),e.version());}
  void initial()throws Exception{String t=stage(List.of(input("alpha_project",jar("alpha","1","")),input("beta_project",jar("beta","1",""))),Set.of());install(t);confirmed(t);}
  void restore()throws Exception{try(var owner=OwnerLease94.acquire(config);var writer=ProfileLease92.writer(game)){var v=d.inspectInterruptedSet99(settings);d.restoreAfterExit92(v.revision());}}
  public void close()throws Exception{ModSet99.testHook99.remove();ok(Files.readString(game.resolve("saves/world/level.dat")).equals("UNCHANGED_WORLD"));try(var paths=Files.walk(root)){for(Path p:paths.sorted(Comparator.reverseOrder()).toList())Files.delete(p);}}
 }
 public static void main(String[] args)throws Exception{
  try(var f=new Fixture()){
   var inputs=List.of(input("alpha_project",f.jar("alpha","1","")),input("beta_project",f.jar("beta","1","[[dependencies.beta]]\nmodId=\"alpha\"\nversionRange=\"[1,)\"\ntype=\"required\"\n")));
   var r=f.d.reviewSet99(inputs,Set.of(),f.loaded);ok(r.entries().size()==2);ok(r.changes().size()==2);ok(r.changes().stream().allMatch(c->c.action().equals("ADD")));ok(!Files.exists(f.data.resolve("deployments/pending.properties")));
   reject(()->f.d.reviewSet99(List.of(inputs.get(1)),Set.of(),f.loaded));
   String token=f.d.stageSet99(inputs,Set.of(),f.loaded,r);ok(!Files.exists(f.mods.resolve("mared-dev-alpha.jar")));reject(f.d::beforeLaunch);f.install(token);ok(Files.exists(f.mods.resolve("mared-dev-beta.jar")));f.confirmed(token);ok(f.d.confirmedSet99().size()==2);
   reject(()->f.d.reviewSet99(List.of(),Set.of("alpha_project"),f.loaded)); // retained beta requires alpha
   reject(()->f.d.reviewSet99(inputs,Set.of(),f.loaded)); // no changes, no restart
   var replace=List.of(input("alpha_project",f.jar("alpha","2","")),input("beta_project",f.jar("beta","2","[[dependencies.beta]]\nmodId=\"alpha\"\nversionRange=\"[2,)\"\ntype=\"required\"\n")));
   var next=f.d.reviewSet99(replace,Set.of(),f.loaded);ok(next.entries().stream().allMatch(e->e.version().equals("2")));ok(next.changes().stream().allMatch(c->c.action().equals("REPLACE")));
   token=f.d.stageSet99(replace,Set.of(),f.loaded,next);f.install(token);f.confirmed(token);ok(f.loaded.get("alpha").equals("2"));ok(f.loaded.get("beta").equals("2"));
   String removal=f.stage(List.of(),Set.of("alpha_project","beta_project"));f.install(removal);ok(!Files.exists(f.mods.resolve("mared-dev-alpha.jar")));ok(!Files.exists(f.mods.resolve("mared-dev-beta.jar")));f.confirmed(removal);ok(f.d.confirmedSet99().isEmpty());
  }
  try(var f=new Fixture()){
   f.initial();Path foreign=ModSetChecks99.jar(f.mods.resolve("foreign.JAR"),"foreign","1","[[dependencies.foreign]]\nmodId=\"alpha\"\nversionRange=\"[1,)\"\ntype=\"required\"\n");String unchanged=ModDeployment.hash(foreign);
   reject(()->f.d.reviewSet99(List.of(),Set.of("alpha_project"),f.loaded));
   var inputs=List.of(input("alpha_project",f.jar("alpha","2","")));var review=f.d.reviewSet99(inputs,Set.of(),f.loaded);
   Files.move(foreign,foreign.resolveSibling("renamed.jar"));reject(()->f.d.stageSet99(inputs,Set.of(),f.loaded,review));Files.move(foreign.resolveSibling("renamed.jar"),foreign);
   var fresh=f.d.reviewSet99(inputs,Set.of(),f.loaded);String token=f.d.stageSet99(inputs,Set.of(),f.loaded,fresh);
   ModSetChecks99.jar(f.mods.resolve("late.jar"),"late","1","");try(var owner=OwnerLease94.acquire(f.config);var writer=ProfileLease92.writer(f.game)){reject(f.d::beforeLaunch);}ok(!Files.exists(f.data.resolve("deployments/trial.properties")));f.d.cancelPending(token);ok(ModDeployment.hash(foreign).equals(unchanged));
  }
  for(int failAt:new int[]{1,2})try(var f=new Fixture()){
   f.initial();String token=f.stage(List.of(input("alpha_project",f.jar("alpha","2","")),input("beta_project",f.jar("beta","2",""))),Set.of());
   int[] steps={0};try(var owner=OwnerLease94.acquire(f.config);var writer=ProfileLease92.writer(f.game)){
    ModSet99.testHook99.set(name->{if(++steps[0]==failAt)throw new IllegalStateException("fixture interruption");});
    try{f.d.beforeLaunch();throw new AssertionError();}catch(IllegalStateException expected){}finally{ModSet99.testHook99.remove();}
    var v=f.d.inspectInterruptedSet99(f.settings);ok(v.needsChoice());ok(!v.keepAllowed());reject(()->f.d.confirm(token));reject(f.d::beforeLaunch);reject(()->f.d.decide91(v.revision(),"KEEP"));
   }
   f.restore();ok(ModJarMetadata.read(f.mods.resolve("mared-dev-alpha.jar")).version().equals("1"));ok(ModJarMetadata.read(f.mods.resolve("mared-dev-beta.jar")).version().equals("1"));ok(f.d.recovery91().phase().equals("RESTORED_REVIEW"));
   f.d.decide91(f.d.recovery91().revision(),"ACK");ok(!f.d.recovery91().blocked());ok(Files.readString(f.data.resolve("resume.properties")).contains("kind=hold91"));
  }
  try(var f=new Fixture()){
   f.initial();String token=f.stage(List.of(input("alpha_project",f.jar("alpha","2",""))),Set.of("beta_project"));f.install(token);f.d.protectTrial91(token);var v=f.d.recovery91();ok(v.keepAllowed());
   try(var owner=OwnerLease94.acquire(f.config);var writer=ProfileLease92.writer(f.game)){reject(()->f.d.rollback("unknown world"));reject(()->f.d.restoreAfterExit92("f".repeat(64)));f.d.restoreAfterExit92(v.revision());}
   ok(Files.exists(f.mods.resolve("mared-dev-beta.jar")));ok(ModJarMetadata.read(f.mods.resolve("mared-dev-alpha.jar")).version().equals("1"));
  }
  try(var f=new Fixture()){
   f.initial();String token=f.stage(List.of(input("alpha_project",f.jar("alpha","2",""))),Set.of());f.install(token);
   ModSetChecks99.jar(f.mods.resolve("mared-dev-alpha.jar"),"alpha","666","");reject(f.d::recovery91);reject(()->f.d.confirm(token));
   try(var owner=OwnerLease94.acquire(f.config);var writer=ProfileLease92.writer(f.game)){reject(()->f.d.rollback("external"));}ok(ModJarMetadata.read(f.mods.resolve("mared-dev-alpha.jar")).version().equals("666"));
  }
  try(var f=new Fixture()){
   f.initial();String token=f.stage(List.of(input("alpha_project",f.jar("alpha","2",""))),Set.of());var pending=f.d.pendingSet99();ok(pending.phase().equals("PENDING99"));reject(()->f.d.cancelReviewedSet99("0".repeat(64)));f.d.cancelReviewedSet99(pending.revision());ok(!Files.exists(f.data.resolve("deployments/pending.properties")));ok(f.loaded.get("alpha").equals("1"));
  }
  try(var f=new Fixture()){
   f.initial();String token=f.stage(List.of(input("alpha_project",f.jar("alpha","2",""))),Set.of());f.install(token);var v=f.d.recovery91();
   try(var owner=OwnerLease94.acquire(f.config);var writer=ProfileLease92.writer(f.game)){f.d.inspectInterruptedSet99(f.settings);Files.writeString(f.config,"# config changed\n",StandardOpenOption.APPEND);reject(()->f.d.inspectInterruptedSet99(SupervisorConfig.load(new String[]{"--config="+f.config})));}
   ok(f.d.recovery91().revision().equals(v.revision()));
  }
  try(var f=new Fixture()){
   f.initial();String token=f.stage(List.of(input("alpha_project",f.jar("alpha","2",""))),Set.of());
   Path pending=f.data.resolve("deployments/pending.properties");byte[] staged=Files.readAllBytes(pending);
   f.install(token); // emulate a crash between trial publication and pending deletion
   Files.write(pending,staged);f.restore();ok(ModJarMetadata.read(f.mods.resolve("mared-dev-alpha.jar")).version().equals("1"));ok(!Files.exists(pending));
  }
  try(var f=new Fixture()){
   f.initial();for(int i=2;i<=12;i++){String token=f.stage(List.of(input("alpha_project",f.jar("alpha",Integer.toString(i),""))),Set.of());f.install(token);f.confirmed(token);}
   try(var dirs=Files.list(f.data.resolve("deployments/transactions"))){ok(dirs.count()<=9);}ok(f.loaded.get("alpha").equals("12"));
  }
  try(var f=new Fixture()){
   Path serverOnly=f.jar("clientonly","1","[[dependencies.clientonly]]\nmodId=\"absent_server_mod\"\nversionRange=\"[1,)\"\ntype=\"required\"\nside=\"SERVER\"\n");
   var input=List.of(input("client_project",serverOnly));var review=f.d.reviewSet99(input,Set.of(),f.loaded);ok(review.entries().size()==1);
   String token=f.d.stageSet99(input,Set.of(),f.loaded,review);ok(f.d.status("client_project").contains("Managed set: PENDING"));f.d.cancelPending(token);
   Path clientRequired=f.jar("clientrequired","1","[[dependencies.clientrequired]]\nmodId=\"missing_client\"\nversionRange=\"[1,)\"\ntype=\"required\"\nside=\"CLIENT\"\n");reject(()->f.d.reviewSet99(List.of(input("required_project",clientRequired)),Set.of(),f.loaded));
  }
  System.out.println("ModSet99 actual multi-JAR/whole-revision/dependency/CAS/partial-install/restore checks passed: "+count+"; no Minecraft or Windows process.");
 }
}
