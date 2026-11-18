package com.fixmer.genesis.technology.scripts;

import com.fixmer.genesis.technology.server.firstlaunch.FirstLaunchFiles;
import com.fixmer.genesis.technology.server.remote.ControlWire73;
import java.io.*;import java.nio.file.*;import java.util.*;import java.util.function.BooleanSupplier;

/** Local build provenance, not a remote trust credential. Never saves drafts or installs a JAR. */
public final class BuildSnapshot84 {
 private BuildSnapshot84(){}
 private static String dependencies(Path project,BuildArtifacts.Artifact a,BooleanSupplier cancel)throws Exception{
  var resolved=GradleOutputs.runtime(project,a.buildId(),a.path(),cancel);var hash=java.security.MessageDigest.getInstance("SHA-256");for(var e:new TreeMap<>(resolved).entrySet()){hash.update(e.getKey().getBytes(java.nio.charset.StandardCharsets.US_ASCII));hash.update((byte)0);hash.update(Long.toString(e.getValue().length).getBytes(java.nio.charset.StandardCharsets.US_ASCII));hash.update((byte)10);}return HexFormat.of().formatHex(hash.digest());
 }
 private static String reportSeal(Path project,String id)throws Exception{return FirstLaunchFiles.hash(project.resolve(".genesis-build").resolve(id).resolve("outputs.properties"),4L*1024*1024);}
 private static IOException fail(String code){return new IOException("SCRIPT84_"+code);}
 public record Snapshot(Path directory,Path project,String projectId,String task,BuildArtifacts.Artifact artifact,List<String> modIds,String seal){}
 public static Snapshot create(Path project,String projectId,String task,GradleBuildRunner.Result result,BuildArtifacts.Artifact chosen,Path exports,BooleanSupplier cancel)throws Exception{
  if(result.status()!=GradleBuildRunner.Status.SUCCESS||result.exitCode()!=0||result.inventory().limited()||!result.inventory().artifacts().contains(chosen))throw fail("SUCCESS_REQUIRED");
  if(chosen.inputs().isEmpty()||chosen.buildId().isEmpty()||!Set.of("mod","mod_embedded").contains(chosen.kind()))throw fail("PROVENANCE_REQUIRED");
  project=FirstLaunchFiles.safe(project);exports=FirstLaunchFiles.safe(exports);if(exports.startsWith(project))throw fail("OUTPUT_IN_PROJECT");
  if(!GradleOutputs.reported(project,chosen.buildId()))throw fail("BUILD_REPORT_MISSING");
  String report=reportSeal(project,chosen.buildId()),runtime=dependencies(project,chosen,cancel);
  Path copy=BuildArtifacts.deliver(project,chosen,exports,cancel),folder=copy.getParent();boolean complete=false;
  try{
   if(cancel.getAsBoolean())throw fail("CANCELLED");FirstLaunchFiles.privateMode(folder);Path binary=folder.resolve("artifact.jar");if(!copy.equals(binary))Files.move(copy,binary);
   var info=JarInspection.inspect(binary,cancel);if(!info.mod()||info.modIds().isEmpty()||info.modIds().size()>64)throw fail("NEOFORGE_REQUIRED");
   try(var z=new java.util.zip.ZipFile(binary.toFile())){if(z.getEntry("com/fixmer/mared/Mared.class")!=null)throw fail("BOOTSTRAP_FIXED");}
   var fields=new TreeMap<String,String>();fields.putAll(Map.of("schema","84","project",project.toString(),"projectId",projectId,"task",task,"path",chosen.path(),"bytes",Long.toString(chosen.size()),"sha256",chosen.sha256(),"inputs",chosen.inputs(),"buildId",chosen.buildId(),"kind",chosen.profile()));
   fields.put("report",report);fields.put("runtime",runtime);
   fields.put("modIds",String.join(",",new TreeSet<>(info.modIds())));FirstLaunchFiles.write(folder.resolve("build84"),ControlWire73.encode(fields),false);
   Snapshot snapshot=verify(folder,cancel);complete=true;return snapshot;
  }finally{if(!complete)try(var paths=Files.walk(folder)){for(Path p:paths.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(p);}}
 }
 public static Snapshot verify(Path folder,BooleanSupplier cancel)throws Exception{
  folder=FirstLaunchFiles.safe(folder);byte[] raw=FirstLaunchFiles.read(folder.resolve("build84"),16384);var f=ControlWire73.decode(raw);
  if(!f.keySet().equals(Set.of("schema","project","projectId","task","path","bytes","sha256","inputs","buildId","kind","modIds","report","runtime"))||!"84".equals(f.get("schema")))throw fail("SNAPSHOT_INVALID");
  var a=new BuildArtifacts.Artifact(f.get("path"),Long.parseLong(f.get("bytes")),f.get("sha256"),f.get("inputs"),f.get("buildId"),f.get("kind"));
  if(a.inputs().isEmpty()||a.buildId().isEmpty()||!Set.of("mod","mod_embedded").contains(a.kind()))throw fail("PROVENANCE_REQUIRED");
  Path project=FirstLaunchFiles.safe(Path.of(f.get("project")));if(!GradleOutputs.reported(project,a.buildId()))throw fail("BUILD_REPORT_MISSING");
  if(!reportSeal(project,a.buildId()).equals(f.get("report"))||!dependencies(project,a,cancel).equals(f.get("runtime")))throw fail("DEPENDENCIES_CHANGED");
  if(!BuildInputs.capture(project,cancel,GradleOutputs.inputExclusions(project,a.buildId())).equals(a.inputs()))throw fail("INPUTS_CHANGED");
  Path jar=FirstLaunchFiles.safe(folder.resolve("artifact.jar"));if(Files.size(jar)!=a.size()||!FirstLaunchFiles.hash(jar,64L*1024*1024).equals(a.sha256()))throw fail("BINARY_CHANGED");
  var info=JarInspection.inspect(jar,cancel);var ids=new TreeSet<>(info.modIds());if(ids.isEmpty()||!String.join(",",ids).equals(f.get("modIds")))throw fail("METADATA_CHANGED");
  try(var z=new java.util.zip.ZipFile(jar.toFile())){if(z.getEntry("com/fixmer/mared/Mared.class")!=null)throw fail("BOOTSTRAP_FIXED");}
  if(cancel.getAsBoolean())throw fail("CANCELLED");return new Snapshot(folder,project,f.get("projectId"),f.get("task"),a,List.copyOf(ids),FirstLaunchFiles.sha(raw));
 }
}
