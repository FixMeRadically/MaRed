package com.fixmer.mared;
import com.fixmer.genesis.technology.scripts.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GradleProjectImportIntegrationTest {
 static Path source(Path parent)throws Exception{
  Path p=Files.createDirectory(parent.resolve("existing Gradle project"));Files.writeString(p.resolve("build.gradle.kts"),"plugins { java }\r\n// original configuration\r\n");Files.writeString(p.resolve("settings.gradle.kts"),"rootProject.name = \"example\"\n");
  Path java=p.resolve("src/main/java/example/Code.java");Files.createDirectories(java.getParent());Files.writeString(java,"package example; class Code {}\r\n");
  Path resource=p.resolve("src/main/resources/assets/example/data.bin");Files.createDirectories(resource.getParent());Files.write(resource,new byte[]{0,1,(byte)255,2});
  Path module=p.resolve("module/src/main/java/example/Other.java");Files.createDirectories(module.getParent());Files.writeString(module,"package example; class Other {}\n");
  Files.writeString(p.resolve("module/build.gradle"),"plugins { id 'java' }\n");Files.writeString(p.resolve(".gitignore"),"build/\n");
  Files.createDirectories(p.resolve(".github/workflows"));Files.writeString(p.resolve(".github/workflows/build.yml"),"name: build\n");
  Files.createDirectories(p.resolve("build/generated"));Files.writeString(p.resolve("build/generated/no.txt"),"generated");Files.createDirectory(p.resolve(".git"));Files.writeString(p.resolve(".git/secret"),"local history");
  Files.createDirectories(p.resolve("src/main/java/example/build"));Files.writeString(p.resolve("src/main/java/example/build/Builder.java"),"package example.build; class Builder {}\n");return p;
 }
 @Test void copyPreservesSourcesBinaryResourcesAndBuildConfigurationWithoutTouchingOriginal()throws Exception{
  Path root=Files.createTempDirectory("import-copy-");Path original=source(root);var store=new SourceProjects(root.resolve("managed"));byte[] before=Files.readAllBytes(original.resolve("build.gradle.kts"));
  var result=store.importGradle("copied",original,()->false);assertTrue(store.importedGradle("copied"));assertTrue(store.hasGradle("copied"));assertTrue(result.summary().files()>5);
  Path copy=store.projectDirectory("copied");assertTrue(Arrays.equals(before,Files.readAllBytes(copy.resolve("build.gradle.kts"))));assertTrue(Arrays.equals(before,Files.readAllBytes(original.resolve("build.gradle.kts"))));
  assertFalse(Files.exists(copy.resolve("build")));assertFalse(Files.exists(copy.resolve(".git")));assertTrue(Files.exists(copy.resolve("src/main/java/example/build/Builder.java")));assertTrue(Files.exists(copy.resolve(".github/workflows/build.yml")));
  assertTrue(store.files("copied").contains("module/src/main/java/example/Other.java"));assertThrows(IOException.class,()->store.javaSources("copied",Map.of()));
  String old=store.read("copied","module/build.gradle");store.save("copied","module/build.gradle",old,old+"// changed copy\n");assertFalse(Files.readString(original.resolve("module/build.gradle")).contains("changed copy"));
  store.createFile("copied","module/src/main/java/example/New.java");assertTrue(store.files("copied").contains("module/src/main/java/example/New.java"));
  assertThrows(IOException.class,()->store.importGradle("copied",original,()->false));
 }
 @Test void exportKeepsLayoutBinariesHiddenProjectFilesAndDraftsWithoutSaving()throws Exception{
  Path root=Files.createTempDirectory("import-export-");Path original=source(root);var store=new SourceProjects(root.resolve("managed"));store.importGradle("copy",original,()->false);
  String path="module/build.gradle",baseline=store.read("copy",path),draft=baseline+"// draft\n";
  try(var zip=new ZipFile(store.exportJava("copy",Map.of(path,new SourceProjects.Recovery(baseline,draft),"build.gradle.kts",new SourceProjects.Recovery(store.read("copy","build.gradle.kts"),store.read("copy","build.gradle.kts")))).toFile())){
   assertEquals(draft,new String(zip.getInputStream(zip.getEntry(path)).readAllBytes(),java.nio.charset.StandardCharsets.UTF_8));
   assertTrue(Arrays.equals(new byte[]{0,1,(byte)255,2},zip.getInputStream(zip.getEntry("src/main/resources/assets/example/data.bin")).readAllBytes()));
   assertTrue(Arrays.equals(Files.readAllBytes(original.resolve("build.gradle.kts")),zip.getInputStream(zip.getEntry("build.gradle.kts")).readAllBytes()));assertNotNull(zip.getEntry(".gitignore"));assertNotNull(zip.getEntry(".github/workflows/build.yml"));assertNull(zip.getEntry("build/generated/no.txt"));assertNull(zip.getEntry(".git/secret"));
  }
  assertEquals(baseline,store.read("copy",path));store.save("copy",path,baseline,"external\n");
  long before;try(var exports=Files.list(store.root().resolve(".exports"))){before=exports.count();}
  assertThrows(IOException.class,()->store.exportJava("copy",Map.of(path,new SourceProjects.Recovery(baseline,draft))));
  try(var exports=Files.list(store.root().resolve(".exports"))){assertEquals(before,exports.count());}
 }
 @Test void ownWrapperWinsAndBrokenOwnWrapperDoesNotSilentlyFallBack()throws Exception{
  Path root=Files.createTempDirectory("import-wrapper-");Path original=source(root);Path wrapper=BuildToolsIntegrationTest.wrapper(root);Files.createDirectories(original.resolve("gradle/wrapper"));Files.copy(wrapper,original.resolve("gradle/wrapper/gradle-wrapper.jar"));Files.copy(wrapper.resolveSibling("gradle-wrapper.properties"),original.resolve("gradle/wrapper/gradle-wrapper.properties"));
  var store=new SourceProjects(root.resolve("managed"));store.importGradle("copy",original,()->false);store.setBuildTool("java_home",BuildToolsIntegrationTest.jdk(root).toString());
  assertEquals(store.projectDirectory("copy").resolve("gradle/wrapper/gradle-wrapper.jar"),store.buildTools("copy").wrapperJar());
  Files.delete(store.projectDirectory("copy").resolve("gradle/wrapper/gradle-wrapper.properties"));assertThrows(IOException.class,()->store.buildTools("copy"));
 }
 @Test void invalidPathsLinksSizeAndCancellationNeverPublishPartialProject()throws Exception{
  Path root=Files.createTempDirectory("import-refuse-");Path original=source(root);var store=new SourceProjects(root.resolve("managed"));
  assertThrows(IOException.class,()->store.importGradle("cancelled",original,()->true));assertFalse(Files.exists(store.root().resolve("cancelled")));
  assertThrows(IOException.class,()->store.importGradle("relative",Path.of("relative"),()->false));assertThrows(IOException.class,()->store.importGradle("nested",root,()->false));
  if(!System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win")){
   Files.createSymbolicLink(original.resolve("escape.txt"),root.resolve("elsewhere"));assertThrows(IOException.class,()->store.importGradle("linked",original,()->false));Files.delete(original.resolve("escape.txt"));
  }
  Files.writeString(original.resolve("Case.txt"),"one");Files.writeString(original.resolve("case.txt"),"two");
  if(!Files.isSameFile(original.resolve("Case.txt"),original.resolve("case.txt")))assertThrows(IOException.class,()->store.importGradle("alias",original,()->false));
  Files.deleteIfExists(original.resolve("Case.txt"));Files.deleteIfExists(original.resolve("case.txt"));
  Files.writeString(original.resolve("GENESIS-PROJECT.PROPERTIES"),"do not overwrite");assertThrows(IOException.class,()->store.importGradle("metadata",original,()->false));Files.delete(original.resolve("GENESIS-PROJECT.PROPERTIES"));
  try(var out=new RandomAccessFile(original.resolve("huge.bin").toFile(),"rw")){out.setLength((long)GradleProjectImport.MAX_FILE_BYTES+1);}assertThrows(IOException.class,()->store.importGradle("huge",original,()->false));Files.delete(original.resolve("huge.bin"));
  for(int i=0;i<2000;i++)Files.writeString(original.resolve("F"+i+".txt"),"");assertThrows(IOException.class,()->store.importGradle("many",original,()->false));
  try(var entries=Files.list(store.root())){assertTrue(entries.noneMatch(p->p.getFileName().toString().startsWith(".import-")||!p.getFileName().toString().startsWith(".")));}
 }
 @Test void changesDuringCopyAreDetectedBeforePublishing()throws Exception{
  Path root=Files.createTempDirectory("import-change-");Path original=source(root);var store=new SourceProjects(root.resolve("managed"));boolean[] modified={false};
  assertThrows(IOException.class,()->store.importGradle("changed",original,()->{
   try(var stages=Files.list(store.root())){var stage=stages.filter(p->p.getFileName().toString().startsWith(".import-")).findFirst();
    if(!modified[0]&&stage.isPresent()&&Files.exists(stage.get().resolve("build.gradle.kts"))){Files.writeString(original.resolve("newly_added.txt"),"changed during copy");modified[0]=true;}
   }catch(IOException bad){throw new RuntimeException(bad);}return false;
  }));assertTrue(modified[0]);assertFalse(Files.exists(store.root().resolve("changed")));
 }
 @Test void totalLimitAndCancellationAfterFirstFilesCleanStaging()throws Exception{
  Path root=Files.createTempDirectory("import-total-");Path original=source(root);var store=new SourceProjects(root.resolve("managed"));
  assertThrows(IOException.class,()->store.importGradle("late_cancel",original,()->{
   try(var entries=Files.list(store.root())){return entries.filter(p->p.getFileName().toString().startsWith(".import-")).anyMatch(p->Files.exists(p.resolve("build.gradle.kts")));}
   catch(IOException bad){throw new RuntimeException(bad);}
  }));assertFalse(Files.exists(store.root().resolve("late_cancel")));
  for(int i=0;i<9;i++)try(var file=new RandomAccessFile(original.resolve("blob"+i+".bin").toFile(),"rw")){file.setLength(GradleProjectImport.MAX_FILE_BYTES);}
  assertThrows(IOException.class,()->store.importGradle("total",original,()->false));assertFalse(Files.exists(store.root().resolve("total")));
  try(var entries=Files.list(store.root())){assertTrue(entries.noneMatch(p->p.getFileName().toString().startsWith(".import-")));}
 }

 @Test void metadataOnlyChangesDuringCopyDoNotRejectUnchangedContent()throws Exception{
  Path root=Files.createTempDirectory("import-metadata-");Path original=source(root);var store=new SourceProjects(root.resolve("managed"));boolean[] changed={false};
  byte[] expected=Files.readAllBytes(original.resolve("build.gradle.kts"));
  store.importGradle("copy",original,()->{
   try(var stages=Files.list(store.root())){var stage=stages.filter(p->p.getFileName().toString().startsWith(".import-")).findFirst();
    if(!changed[0]&&stage.isPresent()&&Files.exists(stage.get().resolve("build.gradle.kts"))){
     changed[0]=true;var time=java.nio.file.attribute.FileTime.fromMillis(1234567890000L);
     Files.setLastModifiedTime(original.resolve("build.gradle.kts"),time);Files.setLastModifiedTime(original.resolve("module"),time);
    }
   }catch(IOException bad){throw new RuntimeException(bad);}return false;
  });
  assertTrue(changed[0]);assertTrue(Arrays.equals(expected,Files.readAllBytes(store.projectDirectory("copy").resolve("build.gradle.kts"))));
 }
 @Test void sameSizeContentChangesWithRestoredTimestampAreRejected()throws Exception{
  Path root=Files.createTempDirectory("import-fingerprint-");Path original=source(root);var store=new SourceProjects(root.resolve("managed"));boolean[] changed={false};
  Path file=original.resolve("module/build.gradle");byte[] before=Files.readAllBytes(file);var time=Files.getLastModifiedTime(file);
  assertThrows(IOException.class,()->store.importGradle("changed",original,()->{
   try(var stages=Files.list(store.root())){var stage=stages.filter(p->p.getFileName().toString().startsWith(".import-")).findFirst();
    if(!changed[0]&&stage.isPresent()&&Files.exists(stage.get().resolve("build.gradle.kts"))){
     changed[0]=true;byte[] after=before.clone();after[0]=(byte)'P';Files.write(file,after);Files.setLastModifiedTime(file,time);
    }
   }catch(IOException bad){throw new RuntimeException(bad);}return false;
  }));
  assertTrue(changed[0]);assertFalse(Files.exists(store.root().resolve("changed")));
  try(var stages=Files.list(store.root())){assertTrue(stages.noneMatch(p->p.getFileName().toString().startsWith(".import-")));}
 }

}
