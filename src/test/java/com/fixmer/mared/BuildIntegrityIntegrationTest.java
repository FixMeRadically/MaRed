package com.fixmer.mared;
import com.fixmer.genesis.technology.scripts.*;
import java.nio.file.*;import java.io.*;import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
/** Exercises production parsing, discovery, delivery and a real child JVM. */
public class BuildIntegrityIntegrationTest {
 private static Path put(Path root,String name,byte[] data)throws Exception{Path p=root.resolve(name);Files.createDirectories(p.getParent());return Files.write(p,name.endsWith(".jar")&&data.length>0?BuildArtifactsIntegrationTest.archive(data):data);}
 @Test public void nonPortableOutputDoesNotDiscardValidArchives()throws Exception{
  Path root=Files.createTempDirectory("artifact-name-");put(root,"build/libs/CON.jar",new byte[]{1});put(root,"build/libs/good.jar",new byte[]{2});put(root,"build/libs/folder.jar/note",new byte[]{3});
  var scan=BuildArtifacts.scan(root,()->false);assertEquals(1,scan.artifacts().size());assertEquals("build/libs/good.jar",scan.artifacts().get(0).path());assertEquals(2,scan.issues().size());
 }
 @Test public void successfulBuildRemainsSuccessfulWithRejectedOutput()throws Exception{
  Path root=Files.createTempDirectory("build-name-");Files.writeString(root.resolve("build.gradle"),"// fixture");put(root,"build/libs/NUL.jar",new byte[]{1});put(root,"build/libs/good.jar",new byte[]{2});
  try(var runner=new GradleBuildRunner(5000)){runner.start(root,new GradleBuildRunner.Settings(Path.of(System.getProperty("java.home")),BuildStabilityIntegrationTest.wrapper()),"build");var result=BuildStabilityIntegrationTest.finish(runner);assertEquals(GradleBuildRunner.Status.SUCCESS,result.status());assertFalse(result.inventory().artifacts().stream().anyMatch(a->a.path().endsWith("good.jar")));assertFalse(result.inventory().issues().isEmpty());assertTrue(result.output().contains("[Artifacts]"));}
 }
 @Test public void linkedDestinationIsRejectedBeforeCreatingFolders()throws Exception{
  Path root=Files.createTempDirectory("export-link-");put(root,"build/libs/good.jar",new byte[]{1});var a=BuildArtifacts.scan(root,()->false).artifacts().get(0);Path outside=Files.createTempDirectory("export-outside-");Path link=root.resolve("linked");try{Files.createSymbolicLink(link,outside);}catch(FileSystemException|UnsupportedOperationException unavailable){assumeTrue(false,"Symbolic links unavailable for this filesystem/user: "+unavailable.getMessage());return;}
  assertThrows(IOException.class,()->BuildArtifacts.deliver(root,a,link.resolve("nested/exports"),()->false));assertFalse(Files.exists(outside.resolve("nested")));assertEquals(a.size(),Files.size(root.resolve(a.path())));
 }
 @Test public void replacingInodeWithRestoredTimestampIsDetected()throws Exception{
  Path root=Files.createTempDirectory("artifact-inode-");Path file=put(root,"build/libs/good.jar",new byte[0]);var stamp=Files.getLastModifiedTime(file);
  assumeTrue(Files.readAttributes(file,java.nio.file.attribute.BasicFileAttributes.class).fileKey()!=null,"Filesystem does not expose file identity");int[] calls={0};boolean[] replaced={false};
  var scan=BuildArtifacts.scan(root,()->{
   boolean hashing=StackWalker.getInstance().walk(frames->frames.anyMatch(f->f.getClassName().equals(BuildArtifacts.class.getName())&&f.getMethodName().equals("fingerprint")));
   // Empty input: the second hashing checkpoint runs after closing the stream, before identity verification.
   // Count hashing checkpoints only; directory iteration order must not influence this race fixture.
   if(hashing&&++calls[0]==2)try{Path next=Files.createTempFile(file.getParent(),"replacement-",".part");Files.setLastModifiedTime(next,stamp);Files.move(next,file,StandardCopyOption.REPLACE_EXISTING);replaced[0]=true;}catch(IOException bad){throw new UncheckedIOException(bad);}return false;
  });assertTrue(replaced[0]);
  assertTrue(scan.artifacts().isEmpty());assertTrue(scan.issues().stream().anyMatch(i->i.contains("changed while reading")));
 }
 private static SourceProjects store()throws Exception{var s=new SourceProjects(Files.createTempDirectory("metadata-semantics-"));s.createEmpty("demo");return s;}
 private static Path metadata(SourceProjects s)throws Exception{return s.projectDirectory("demo").resolve("genesis-project.properties");}
 @Test public void whitespaceAndEscapedKeysAreReplacedRatherThanDuplicated()throws Exception{
  var s=store();Path p=metadata(s);Files.writeString(p,Files.readString(p)+"# untouched\r\ncustom:value\r\nbuild_max_workers 3\r\ngradle\\u005ftask :api:test\r\n");s.setBuildOption("demo","build_max_workers",4);s.setGradleTask("demo",":api:build");assertEquals(4,s.buildOptions("demo").maxWorkers());assertEquals(":api:build",s.gradleTask("demo"));assertTrue(Files.readString(p).contains("# untouched\ncustom:value\n"));
  var props=new Properties();props.load(new StringReader(Files.readString(p)));assertEquals("4",props.getProperty("build_max_workers"));assertFalse(Files.readString(p).contains("build_max_workers 3"));
 }
 @Test public void continuationAndCommentsPreserveUnrelatedConfiguration()throws Exception{
  var s=store();Path p=metadata(s);Files.writeString(p,Files.readString(p)+"# comment with slash \\\ncustom=one\\\n  two\nbuild_timeout_\\\n minutes:60\n");assertEquals(3600000L,s.buildOptions("demo").timeoutMillis());s.setBuildOption("demo","build_timeout_minutes",90);assertEquals(5400000L,s.buildOptions("demo").timeoutMillis());assertTrue(Files.readString(p).contains("custom=one\\\n  two\n"));assertFalse(Files.readString(p).contains(" minutes:60"));
 }
 @Test public void semanticDuplicateSettingsAreRejectedWithoutWrites()throws Exception{
  var s=store();Path p=metadata(s);String before=Files.readString(p)+"build_max_workers=2\nbuild_max_\\\n workers:4\n";Files.writeString(p,before);assertThrows(IOException.class,()->s.buildOptions("demo"));assertThrows(IOException.class,()->s.setBuildOption("demo","build_max_workers",3));assertThrows(IOException.class,()->s.setGradleTask("demo","build"));assertEquals(before,Files.readString(p));
 }
 @Test public void invalidTaskAndMalformedEscapesAreRejectedAtReadAndWrite()throws Exception{
  var s=store();Path p=metadata(s);String base=Files.readString(p);Files.writeString(p,base+"gradle_task=--scan\n");assertThrows(IOException.class,()->s.gradleTask("demo"));Files.writeString(p,base+"custom=\\u00zz\n");String before=Files.readString(p);assertThrows(IOException.class,()->s.buildOptions("demo"));assertThrows(IOException.class,()->s.setBuildOption("demo","build_max_workers",4));assertEquals(before,Files.readString(p));
 }
 @Test public void carriageReturnOnlyMetadataRetainsOtherDeclarations()throws Exception{
  var s=store();Path p=metadata(s);Files.writeString(p,Files.readString(p).replace("\n","\r")+"build_max_workers 3\rcustom=keep\r");s.setBuildOption("demo","build_max_workers",5);assertEquals(5,s.buildOptions("demo").maxWorkers());assertTrue(Files.readString(p).contains("custom=keep\n"));assertEquals(SourceProjects.Language.JAVA,s.project("demo").language());
 }
 @Test public void trailingContinuationCannotSwallowAppendedSetting()throws Exception{
  var s=store();Path p=metadata(s);Files.writeString(p,Files.readString(p)+"custom=value"+(char)92);s.setBuildOption("demo","build_max_workers",6);assertEquals(6,s.buildOptions("demo").maxWorkers());var props=new Properties();props.load(new StringReader(Files.readString(p)));assertEquals("value",props.getProperty("custom"));
 }
 public static void main(String[]args)throws Exception{var t=new BuildIntegrityIntegrationTest();t.nonPortableOutputDoesNotDiscardValidArchives();t.successfulBuildRemainsSuccessfulWithRejectedOutput();t.linkedDestinationIsRejectedBeforeCreatingFolders();t.replacingInodeWithRestoredTimestampIsDetected();t.whitespaceAndEscapedKeysAreReplacedRatherThanDuplicated();t.continuationAndCommentsPreserveUnrelatedConfiguration();t.semanticDuplicateSettingsAreRejectedWithoutWrites();t.invalidTaskAndMalformedEscapesAreRejectedAtReadAndWrite();t.carriageReturnOnlyMetadataRetainsOtherDeclarations();t.trailingContinuationCannotSwallowAppendedSetting();System.out.println("Build integrity integration scenarios passed: 10; real filesystem and child JVM, artifact identities, linked export refusal, Java Properties semantics; not an actual Gradle distribution");}
}
