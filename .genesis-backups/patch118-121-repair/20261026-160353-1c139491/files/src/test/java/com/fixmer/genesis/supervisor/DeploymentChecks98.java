package com.fixmer.genesis.supervisor;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.jar.*;
/** Real temporary JARs and deployment journals. No Minecraft runtime or save receipt fixture. */
public final class DeploymentChecks98 {
 private static int checks;
 private static void ok(boolean b){checks++;if(!b)throw new AssertionError("check "+checks);}
 private static void rejects(IO action)throws Exception {checks++;try{action.run();}catch(IOException expected){return;}throw new AssertionError("accepted invalid input "+checks);}
 @FunctionalInterface private interface IO {void run()throws Exception;}
 private static Path jar(Path p,String id,String version,String extra)throws Exception {
  try(var out=new JarOutputStream(Files.newOutputStream(p))){out.putNextEntry(new JarEntry("META-INF/neoforge.mods.toml"));out.write(("modLoader=\"javafml\"\n[[mods]]\nmodId=\""+id+"\"\nversion=\""+version+"\"\n"+extra).getBytes(StandardCharsets.UTF_8));out.closeEntry();}return p;
 }
 private static void clean(Path root)throws Exception {try(var paths=Files.walk(root)){for(Path p:paths.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(p);}}
 public static void main(String[] args)throws Exception {
  Path root=Files.createTempDirectory("deployment98-");
  try {
   Path game=root.resolve("game"),mods=game.resolve("mods");Files.createDirectories(mods);
   Path source=jar(root.resolve("candidate.jar"),"example","1.0.0","");String hash=ModDeployment.hash(source);
   var deployment=new ModDeployment(game);Map<String,String> loaded=Map.of("minecraft","1.21.1","neoforge","21.1.264");
   var review=deployment.review98(source,hash,loaded,"project");
   ok(review.modId().equals("example"));ok(review.version().equals("1.0.0"));ok(review.priorHash().isEmpty());ok(review.project().equals("project"));ok(review.sha256().equals(hash));
   ok(!Files.exists(game.resolve("config/mared/dev/deployments/pending.properties")));try(var installed=Files.list(mods)){ok(installed.findAny().isEmpty());}
   ok(deployment.review98(source,hash,loaded,"project").equals(review));
   try{review.loaded().put("bad","1");throw new AssertionError();}catch(UnsupportedOperationException e){checks++;}
   var changedLoaded=new HashMap<>(loaded);changedLoaded.put("another","1");rejects(()->deployment.stageReviewed98(source,review,changedLoaded));
   Path foreign=jar(mods.resolve("foreign.JAR"),"othermod","1","");rejects(()->deployment.stageReviewed98(source,review,loaded));
   Files.delete(foreign);ok(deployment.review98(source,hash,loaded,"project").equals(review));
   Path otherGame=root.resolve("other-game");rejects(()->new ModDeployment(otherGame).stageReviewed98(source,review,loaded));
   jar(source,"example","2.0.0","");rejects(()->deployment.stageReviewed98(source,review,loaded));
   jar(source,"example","1.0.0",""); // ZIP timestamps can change: recapture the actual hash.
   final String expectedHash=ModDeployment.hash(source);
   final var stable=deployment.review98(source,expectedHash,loaded,"project");
   Path catalog=game.resolve("config/mared/dev/deployments/catalog.properties");Files.writeString(catalog,"# external catalog change\n");
   rejects(()->deployment.stageReviewed98(source,stable,loaded));Files.delete(catalog);
   var latest=deployment.review98(source,expectedHash,loaded,"project");String token=deployment.stageReviewed98(source,latest,loaded);
   Path pending=game.resolve("config/mared/dev/deployments/pending.properties");ok(Files.exists(pending));ok(!Files.exists(mods.resolve("mared-dev-example.jar")));
   var props=new Properties();try(var reader=Files.newBufferedReader(pending)){props.load(reader);}ok(props.getProperty("modsSnapshot98").equals(latest.modsSnapshot()));
   rejects(()->deployment.review98(source,expectedHash,loaded,"project"));
   jar(mods.resolve("late.jar"),"late","1","");rejects(deployment::beforeLaunch);ok(Files.exists(pending));ok(!Files.exists(mods.resolve("mared-dev-example.jar")));
   deployment.cancelPending(token);ok(!Files.exists(pending));ok(Files.exists(mods.resolve("late.jar")));
   var fresh=deployment.review98(source,expectedHash,loaded,"project");token=deployment.stageReviewed98(source,fresh,loaded);ok(deployment.beforeLaunch().equals(token));ok(Files.exists(mods.resolve("mared-dev-example.jar")));ok(ModDeployment.hash(mods.resolve("mared-dev-example.jar")).equals(expectedHash));
   deployment.confirm(token);
   Path v2=jar(root.resolve("v2.jar"),"example","2.0.0","");
   var running=new HashMap<>(loaded);running.put("example","1.0.0");
   var replacement=deployment.review98(v2,ModDeployment.hash(v2),running,"project");
   ok(replacement.priorHash().equals(expectedHash));ok(replacement.priorVersion().equals("1.0.0"));ok(replacement.version().equals("2.0.0"));
   rejects(()->deployment.review98(v2,ModDeployment.hash(v2),running,"different-project"));
   String t2=deployment.stageReviewed98(v2,replacement,running);
   Files.writeString(catalog,"# external change\n",StandardOpenOption.APPEND);rejects(deployment::beforeLaunch);ok(ModDeployment.hash(mods.resolve("mared-dev-example.jar")).equals(expectedHash));deployment.cancelPending(t2);
   // Compatibility of pre98 pending journals: no invented new authority field is required.
   String legacy=deployment.stage(v2,ModDeployment.hash(v2),running,"project");
   var legacyProps=new Properties();try(var reader=Files.newBufferedReader(pending)){legacyProps.load(reader);}legacyProps.remove("modsSnapshot98");legacyProps.remove("catalogHash98");try(var out=Files.newBufferedWriter(pending)){legacyProps.store(out,"");}
   ok(deployment.beforeLaunch().equals(legacy));deployment.confirm(legacy);ok(ModDeployment.hash(mods.resolve("mared-dev-example.jar")).equals(ModDeployment.hash(v2)));
   // Separate root exercises the legacy automatic entry point and stale foreign file deletion/rename/content.
   Path g2=root.resolve("game2");Files.createDirectories(g2.resolve("mods"));var d2=new ModDeployment(g2);
   Path f2=jar(g2.resolve("mods/foreign.jar"),"foreign","1","");
   var r2=d2.review98(source,expectedHash,loaded,"project");Files.move(f2,f2.resolveSibling("renamed.jar"));rejects(()->d2.stageReviewed98(source,r2,loaded));
   Files.move(f2.resolveSibling("renamed.jar"),f2);var r3=d2.review98(source,expectedHash,loaded,"project");jar(f2,"foreign","2","");rejects(()->d2.stageReviewed98(source,r3,loaded));
   var r4=d2.review98(source,expectedHash,loaded,"project");Files.delete(f2);rejects(()->d2.stageReviewed98(source,r4,loaded));
   String automatic=d2.stage(source,expectedHash,loaded,"project");ok(Files.readString(g2.resolve("config/mared/dev/deployments/pending.properties")).contains("modsSnapshot98"));d2.cancelPending(automatic);
   jar(g2.resolve("mods/duplicate.jar"),"example","1","");rejects(()->d2.review98(source,expectedHash,loaded,"project"));Files.delete(g2.resolve("mods/duplicate.jar"));
   Path required=jar(root.resolve("required.jar"),"dependent","1","[[dependencies.dependent]]\nmodId=\"missing\"\nversionRange=\"[1,)\"\ntype=\"required\"\n");rejects(()->d2.review98(required,ModDeployment.hash(required),loaded,"project"));
   Path linked=g2.resolve("mods/link.jar");boolean supported=true;try{Files.createSymbolicLink(linked,source);}catch(UnsupportedOperationException|IOException e){supported=false;}if(supported){rejects(()->d2.review98(source,expectedHash,loaded,"project"));Files.delete(linked);}
   Path directory=g2.resolve("mods/not-a-file.jar");Files.createDirectory(directory);rejects(()->d2.review98(source,expectedHash,loaded,"project"));Files.delete(directory);
   Path recovery=g2.resolve("config/mared/dev/deployments/world-review91.properties");Files.writeString(recovery,"phase=REVIEW\n");rejects(()->d2.review98(source,expectedHash,loaded,"project"));Files.delete(recovery);
   // All size/entry checks run on actual files without allocating multi-GiB payloads.
   for(int i=0;i<513;i++)Files.write(g2.resolve("mods/m"+i+".jar"),new byte[0]);rejects(()->DeploymentInventory98.capture(g2.resolve("mods")));
   ok(!Files.exists(g2.resolve("config/mared/dev/deployments/pending.properties")));
   System.out.println("Deployment98 real-JAR review/inventory/stale-plan/deferred-install checks passed: "+checks+"; Minecraft UI and Windows are not exercised.");
  } finally {clean(root);}
 }
}
