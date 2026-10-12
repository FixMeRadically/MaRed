package com.fixmer.mared;
import com.fixmer.genesis.technology.scripts.*;
import com.fixmer.genesis.technology.editor.ProjectFileTree;
import java.nio.file.*;
import java.util.*;
import java.io.IOException;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ProjectFileManagementIntegrationTest {
 @Test void emptyProjectHasFreeFoldersVisibleDotfilesAndRootJavaExecution()throws Exception{
  Path root=Files.createTempDirectory("project-folders-");var store=new SourceProjects(root);store.createEmpty("demo");assertTrue(store.entries("demo").isEmpty());
  store.createDirectory("demo","Новая папка");store.createFile("demo","Новая папка/заметки.md");
  assertThrows(IOException.class,()->store.createDirectory("demo","CON .txt"));assertThrows(IOException.class,()->store.createDirectory("demo"," trailing "));
  assertThrows(IOException.class,()->store.createFile("demo","GENESIS-PROJECT.PROPERTIES"));
  store.create("legacy_python",SourceProjects.Language.PYTHON);Files.writeString(root.resolve("legacy_python/genesis-project.properties"),"format=1\nlanguage=PYTHON\n");store.createFile("legacy_python","notes.md");
  try(var exported=new ZipFile(store.exportProject("legacy_python",Map.of()).toFile())){assertNotNull(exported.getEntry("notes.md"));}
  store.createDirectory("demo","empty/deep");store.createDirectory("demo","code");store.createFile("demo",".gitignore");store.createFile("demo","code/Main.java");
  String code="public class Main { public static void main(String[]a){System.out.println(42);} }";store.save("demo","code/Main.java","",code);
  assertTrue(store.folders("demo").contains("empty/deep"));var tree=ProjectFileTree.of(store.entries("demo"),store.folders("demo"));assertTrue(tree.directories().contains("empty/deep"));assertTrue(tree.containsFile(".gitignore"));
  assertTrue(tree.rows(Set.of("empty")).stream().anyMatch(r->r.path().equals("empty/deep")&&r.directory()));
  var result=new JavaSourceRunner().run(store.javaSources("demo",Map.of()),"Main",JavaSourceRunner.Limits.defaults(),new JavaSourceRunner.Cancellation());assertEquals(JavaSourceRunner.Status.SUCCESS,result.status());assertTrue(result.output().contains("42"));
  try(var zip=new ZipFile(store.exportJava("demo",Map.of()).toFile())){assertNotNull(zip.getEntry("empty/deep/"));assertNotNull(zip.getEntry(".gitignore"));assertNotNull(zip.getEntry("code/Main.java"));}
 }
 @Test void renamePreservesBinaryAndRefusesAliasesConflictsAndDraftLoss()throws Exception{
  Path root=Files.createTempDirectory("project-rename-");var store=new SourceProjects(root);store.createEmpty("demo");store.createFile("demo","code/One.java");store.save("demo","code/One.java","","class One {}\n");
  store.checkpoint("demo","code/One.java","class One {}\n","draft");assertThrows(IOException.class,()->store.renameEntry("demo","code","moved",Map.of()));assertTrue(Files.exists(root.resolve("demo/code/One.java")));
  store.checkpoint("demo","code/One.java","class One {}\n","class One {}\n");assertThrows(IOException.class,()->store.renameEntry("demo","code","moved",Map.of("code/One.java","stale")));
  store.renameEntry("demo","code","moved",Map.of("code/One.java","class One {}\n"));assertEquals("class One {}\n",store.read("demo","moved/One.java"));
  Files.write(root.resolve("demo/moved/data.bin"),new byte[]{0,1,(byte)255});store.renameEntry("demo","moved/data.bin","moved/renamed.bin",Map.of());assertTrue(Arrays.equals(new byte[]{0,1,(byte)255},store.asset("demo","moved/renamed.bin")));
  assertThrows(IOException.class,()->store.createDirectory("demo","MOVED/deep"));assertThrows(IOException.class,()->store.renameEntry("demo","moved","moved/child",Map.of()));assertThrows(IOException.class,()->store.renameEntry("demo","genesis-project.properties","bad.properties",Map.of()));
 }
 @Test void trashAndRestoreAreReversibleAndNeverReplaceExistingData()throws Exception{
  Path root=Files.createTempDirectory("project-trash-");var store=new SourceProjects(root);store.createEmpty("demo");store.createDirectory("demo","assets/empty");Files.write(root.resolve("demo/assets/data.bin"),new byte[]{1,2,3});
  Path trash=store.trashEntry("demo","assets",Map.of());assertFalse(Files.exists(root.resolve("demo/assets")));assertTrue(Files.exists(trash.resolve("data/empty")));assertTrue(Files.exists(trash.resolve("origin.properties")));
  store.createDirectory("demo","assets");assertThrows(IOException.class,()->store.restoreTrash(trash));assertTrue(Files.exists(trash.resolve("data/data.bin")));Files.delete(root.resolve("demo/assets"));
  assertEquals("demo/assets",store.restoreTrash(trash));assertTrue(Arrays.equals(new byte[]{1,2,3},store.asset("demo","assets/data.bin")));assertFalse(Files.exists(trash));assertThrows(IOException.class,()->store.trashEntry("demo","genesis-project.properties",Map.of()));assertThrows(IOException.class,()->store.restoreTrash(root));
 }
 @Test void genericFolderImportAndGenesisExportRoundTripPreserveContent()throws Exception{
  Path root=Files.createTempDirectory("project-folder-import-");Path original=Files.createDirectory(root.resolve("plain project"));Files.writeString(original.resolve("Main.java"),"class Main {}\n");Files.createDirectory(original.resolve("empty"));
  var store=new SourceProjects(root.resolve("managed"));assertThrows(IOException.class,()->store.importGradle("not_gradle",original,()->false));store.importFolder("plain",original,()->false);assertFalse(store.importedGradle("plain"));assertTrue(store.javaSources("plain",Map.of()).containsKey("Main.java"));
  Path zip=store.exportJava("plain",Map.of());Path extracted=Files.createDirectory(root.resolve("exported"));try(var archive=new ZipFile(zip.toFile())){for(var entry:Collections.list(archive.entries())){Path target=extracted.resolve(entry.getName());if(entry.isDirectory())Files.createDirectories(target);else{Files.createDirectories(target.getParent());Files.write(target,archive.getInputStream(entry).readAllBytes());}}}
  store.importFolder("again",extracted,()->false);assertEquals(store.read("plain","Main.java"),store.read("again","Main.java"));assertTrue(store.folders("again").contains("empty"));assertEquals("class Main {}\n",Files.readString(original.resolve("Main.java")));
  Files.writeString(extracted.resolve("genesis-project.properties"),"format=999\nlanguage=JAVA\n");assertThrows(IOException.class,()->store.importFolder("invalid_metadata",extracted,()->false));assertFalse(Files.exists(store.root().resolve("invalid_metadata")));
 }
 @Test void readerCapabilityListsAllFilesAndCannotMutateSnapshots()throws Exception{
  Path root=Files.createTempDirectory("project-reader-");var store=new SourceProjects(root);store.createEmpty("demo");store.createDirectory("demo","assets");Files.write(root.resolve("demo/assets/icon.png"),new byte[]{1,2});var reader=new ProjectReadView(store);
  var snapshot=reader.snapshot("demo");assertTrue(snapshot.files().contains("assets/icon.png"));assertThrows(UnsupportedOperationException.class,()->snapshot.files().add("x"));byte[] asset=reader.asset("demo","assets/icon.png");asset[0]=9;assertTrue(Arrays.equals(new byte[]{1,2},reader.asset("demo","assets/icon.png")));
  for(var method:ProjectReadView.class.getDeclaredMethods())assertTrue(Set.of("catalog","snapshot","text","asset").contains(method.getName()));assertThrows(IOException.class,()->reader.asset("demo","../outside"));assertThrows(IOException.class,()->reader.text("demo","genesis-project.properties"));
 }
 @Test void explicitFoldersRetainHierarchyAndRejectFileFolderAliases()throws Exception{
  var tree=ProjectFileTree.of(List.of("src/Main.java"),List.of("src","empty","empty/deep",".github"));assertTrue(tree.directories().contains("empty/deep"));assertTrue(tree.rows(Set.of()).stream().anyMatch(r->r.path().equals("empty")&&r.directory()));assertThrows(IllegalArgumentException.class,()->ProjectFileTree.of(List.of("one"),List.of("one")));assertThrows(IllegalArgumentException.class,()->ProjectFileTree.of(List.of(),List.of("Dir","dir")));assertThrows(IllegalArgumentException.class,()->ProjectFileTree.of(List.of(),List.of("../outside")));
 }
}
