package com.fixmer.mared;
import com.fixmer.genesis.technology.scripts.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
public class BuildArtifactsIntegrationTest {
 static byte[] archive(byte[] bytes)throws Exception{var b=new java.io.ByteArrayOutputStream();try(var j=new java.util.jar.JarOutputStream(b)){j.putNextEntry(new java.util.jar.JarEntry("fixture.bin"));j.write(bytes);j.closeEntry();}return b.toByteArray();}
 private static void put(Path root,String path,byte[] bytes)throws Exception{Path p=root.resolve(path);Files.createDirectories(p.getParent());Files.write(p,path.endsWith(".jar")&&!(bytes.length>2&&bytes[0]==80&&bytes[1]==75)?archive(bytes):bytes);}
 @Test public void nestedModulesAndArchiveKindsAreDiscoveredWithoutScanningCaches()throws Exception{
  Path root=Files.createTempDirectory("artifacts-scan-");put(root,"build/libs/demo.jar",new byte[]{1,2});put(root,"modules/Мой мод/build/libs/demo.jar",new byte[]{3,4});put(root,"api/build/libs/api-sources.jar",new byte[]{5});put(root,"api/build/libs/api-javadoc.jar",new byte[]{6});put(root,"build/distributions/demo.tar.gz",new byte[]{7});put(root,"src/build/libs/asset.jar",new byte[]{8});put(root,".gradle/build/libs/cache.jar",new byte[]{9});put(root,"node_modules/build/libs/cache.jar",new byte[]{10});put(root,"mod/build/classes/unwanted.jar",new byte[]{11});
  var found=BuildArtifacts.scan(root,()->false);assertEquals(6,found.artifacts().size());assertFalse(found.limited());assertTrue(found.issues().isEmpty());assertTrue(found.artifacts().stream().anyMatch(a->a.path().equals("modules/Мой мод/build/libs/demo.jar")));assertTrue(found.artifacts().stream().anyMatch(a->a.kind().equals("sources")));assertTrue(found.artifacts().stream().anyMatch(a->a.kind().equals("javadoc")));assertTrue(found.artifacts().stream().anyMatch(a->a.kind().equals("distribution")));assertThrows(UnsupportedOperationException.class,()->found.artifacts().clear());
 }
 @Test public void deliveryPreservesBytesAndNeverOverwritesPreviousDelivery()throws Exception{
  Path managed=Files.createTempDirectory("artifacts-deliver-");var storage=new SourceProjects(managed);var project=storage.createEmpty("Мой проект");Path root=storage.projectDirectory(project.id());byte[] bytes=archive(new byte[]{0,1,2,(byte)255});put(root,"module/build/libs/mod-0.4.0+mc1.21.jar",bytes);var artifact=BuildArtifacts.scan(root,()->false).artifacts().get(0);
  Path first=storage.deliverArtifact(project.id(),artifact,()->false),second=storage.deliverArtifact(project.id(),artifact,()->false);assertFalse(first.equals(second));assertTrue(first.startsWith(managed.resolve(".artifacts")));assertTrue(Arrays.equals(bytes,Files.readAllBytes(first)));assertTrue(Arrays.equals(bytes,Files.readAllBytes(second)));assertTrue(Arrays.equals(bytes,Files.readAllBytes(root.resolve(artifact.path()))));assertTrue(storage.entries(project.id()).isEmpty());
  put(root,artifact.path(),new byte[]{0,1,3,(byte)255});assertThrows(java.io.IOException.class,()->storage.deliverArtifact(project.id(),artifact,()->false));assertTrue(Arrays.equals(bytes,Files.readAllBytes(first)));Files.delete(root.resolve(artifact.path()));assertThrows(java.io.IOException.class,()->storage.deliverArtifact(project.id(),artifact,()->false));
 }
 @Test public void cancellationInvalidPathsLinksAndLimitsCannotPublishArtifacts()throws Exception{
  Path root=Files.createTempDirectory("artifacts-refuse-");put(root,"build/libs/demo.jar",new byte[200000]);var artifact=BuildArtifacts.scan(root,()->false).artifacts().get(0);Path out=root.resolveSibling(root.getFileName()+"-exports");
  assertThrows(java.io.IOException.class,()->BuildArtifacts.scan(root,()->true));assertThrows(java.io.IOException.class,()->BuildArtifacts.deliver(root,artifact,out,()->true));assertFalse(Files.exists(out));
  int[] calls={0};assertThrows(java.io.IOException.class,()->BuildArtifacts.deliver(root,artifact,out,()->++calls[0]>12));if(Files.exists(out))try(var entries=Files.list(out)){assertEquals(0L,entries.count());}
  assertThrows(IllegalArgumentException.class,()->new BuildArtifacts.Artifact("../bad.jar",1,"0".repeat(64)));assertThrows(IllegalArgumentException.class,()->new BuildArtifacts.Artifact("bad\n\n.jar",1,"0".repeat(64)));
  Path external=Files.createTempFile("artifact-external-",".jar");Files.write(external,new byte[]{9});Path link=root.resolve("build/libs/external.jar");boolean linked=false;try{Files.createSymbolicLink(link,external);linked=true;}catch(java.io.IOException|UnsupportedOperationException unsupported){}if(linked){var scanned=BuildArtifacts.scan(root,()->false);assertFalse(scanned.artifacts().stream().anyMatch(a->a.path().endsWith("external.jar")));assertFalse(scanned.issues().isEmpty());}
  for(int i=0;i<130;i++)put(root,"many/build/libs/a"+i+".jar",new byte[]{1});var limited=BuildArtifacts.scan(root,()->false);assertTrue(limited.limited());assertTrue(limited.artifacts().size()<=BuildArtifacts.MAX_ARTIFACTS);
 }
}
