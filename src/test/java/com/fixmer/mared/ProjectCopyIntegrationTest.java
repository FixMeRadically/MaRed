package com.fixmer.mared;
import com.fixmer.genesis.technology.scripts.*;
import java.nio.file.*;
import java.util.*;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ProjectCopyIntegrationTest {
 @Test void copyPreservesFilesEmptyFoldersAndOriginal()throws Exception{
  var root=Files.createTempDirectory("copy-entry-");var s=new SourceProjects(root);s.createEmpty("demo");s.createFile("demo","code/Main.java");s.save("demo","code/Main.java","","class Main {}");s.createDirectory("demo","code/empty/deep");s.createFile("demo","code/genesis-project.properties");s.save("demo","code/genesis-project.properties","","nested=true");s.createFile("demo","src/build/Helper.java");s.save("demo","src/build/Helper.java","","class Helper {}");Files.write(root.resolve("demo/code/icon.bin"),new byte[]{0,1,(byte)255});
  s.copyEntry("demo","code","code_copy",Map.of("code/Main.java","class Main {}"));assertEquals("class Main {}",s.read("demo","code_copy/Main.java"));assertTrue(s.folders("demo").contains("code_copy/empty/deep"));assertTrue(Arrays.equals(s.asset("demo","code/icon.bin"),s.asset("demo","code_copy/icon.bin")));assertEquals("class Main {}",s.read("demo","code/Main.java"));
  assertEquals("nested=true",s.read("demo","code_copy/genesis-project.properties"));s.copyEntry("demo","src","src_copy",Map.of());assertEquals("class Helper {}",Files.readString(root.resolve("demo/src_copy/build/Helper.java")));
  s.copyEntry("demo","code/icon.bin","icon_copy.bin",Map.of());assertTrue(Arrays.equals(new byte[]{0,1,(byte)255},s.asset("demo","icon_copy.bin")));
 }
 @Test void copyRefusesConflictsDraftsAliasesAndCancellation()throws Exception{
  var root=Files.createTempDirectory("copy-refusal-");var s=new SourceProjects(root);s.createEmpty("demo");s.createFile("demo","a.txt");s.save("demo","a.txt","","disk");
  assertThrows(IOException.class,()->s.copyEntry("demo","a.txt","b.txt",Map.of("a.txt","old")));assertFalse(Files.exists(root.resolve("demo/b.txt")));
  s.checkpoint("demo","a.txt","disk","draft");assertThrows(IOException.class,()->s.copyEntry("demo","a.txt","b.txt",Map.of()));s.checkpoint("demo","a.txt","disk","disk");
  assertThrows(IOException.class,()->s.copyEntry("demo","a.txt","A.txt",Map.of()));assertThrows(IOException.class,()->s.copyEntry("demo","genesis-project.properties","metadata.txt",Map.of()));assertThrows(IOException.class,()->s.copyEntry("demo","a.txt","b.txt",Map.of(),()->true));
  s.createDirectory("demo","folder");assertThrows(IOException.class,()->s.copyEntry("demo","folder","folder/child",Map.of()));assertThrows(IOException.class,()->s.copyEntry("demo","a.txt","missing/b.txt",Map.of()));assertEquals("disk",s.read("demo","a.txt"));
 }
 @Test void aggregateLimitAndDestinationTextLimitNeverPublish()throws Exception{
  var root=Files.createTempDirectory("copy-limit-");var s=new SourceProjects(root);s.createEmpty("demo");s.createDirectory("demo","many");for(int i=0;i<1100;i++)Files.write(root.resolve("demo/many/file"+i+".bin"),new byte[0]);
  assertThrows(IOException.class,()->s.copyEntry("demo","many","many_copy",Map.of()));assertFalse(Files.exists(root.resolve("demo/many_copy")));assertEquals(1100,s.entries("demo").size());
  Path large=root.resolve("demo/large.bin");try(var out=java.nio.channels.FileChannel.open(large,StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE)){out.position(SourceProjects.MAX_BYTES);out.write(java.nio.ByteBuffer.wrap(new byte[]{0}));}
  assertThrows(IOException.class,()->s.copyEntry("demo","large.bin","large.txt",Map.of()));assertFalse(Files.exists(root.resolve("demo/large.txt")));
  try(var list=Files.list(root)){assertFalse(list.anyMatch(p->p.getFileName().toString().startsWith(".genesis-copy-")));}
 }
}
