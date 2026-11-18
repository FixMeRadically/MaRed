package com.fixmer.genesis.verification;
import com.fixmer.genesis.technology.scripts.*;import java.nio.file.*;import java.nio.charset.StandardCharsets;import java.util.*;import java.util.zip.*;
/** ONLY a linked-file verification fixture. Does not run Gradle and is not included in controller core. */
public final class ScriptBuildFixture84 {
 public static void main(String[] args)throws Exception{
  Path root=Path.of(args[0]),project=Files.createDirectory(root.resolve("script-project")),jar=Files.createDirectories(project.resolve("build/libs")).resolve("example.jar");Files.writeString(project.resolve("code.java"),"fixture source");
  try(var z=new ZipOutputStream(Files.newOutputStream(jar))){z.putNextEntry(new ZipEntry("META-INF/neoforge.mods.toml"));z.write("[[mods]]\nmodId=\"example\"\nversion=\"2\"\n".getBytes(StandardCharsets.UTF_8));z.closeEntry();z.putNextEntry(new ZipEntry("data"));z.write("compiled fixture".getBytes(StandardCharsets.UTF_8));z.closeEntry();}
  String id=UUID.randomUUID().toString();Path report=Files.createDirectories(project.resolve(".genesis-build").resolve(id)).resolve("outputs.properties");Files.writeString(report,"id="+id+"\narchives=1\narchive.0="+Base64.getEncoder().encodeToString(jar.toString().getBytes(StandardCharsets.UTF_8))+"\nexcludes=0\nruntime.0.count=0\n");
  var raw=BuildArtifacts.capture(project,List.of(jar),()->false).artifacts().get(0);var artifact=raw.withBuild(BuildInputs.capture(project,()->false),id);var result=new GradleBuildRunner.Result(GradleBuildRunner.Status.SUCCESS,0,"EXPLICIT SUCCESS FIXTURE",false,List.of(jar),new BuildArtifacts.Inventory(List.of(artifact),false,List.of()));System.out.println(BuildSnapshot84.create(project,"example","build",result,artifact,root.resolve("snapshots"),()->false).directory());
 }
}
