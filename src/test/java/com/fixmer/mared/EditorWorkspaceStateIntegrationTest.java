package com.fixmer.mared;
import com.fixmer.genesis.technology.scripts.EditorWorkspaceState;
import com.fixmer.genesis.technology.scripts.EditorWorkspaceState.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
public class EditorWorkspaceStateIntegrationTest {
 @Test public void unicodeAndSpacesRoundTripWithoutWritingAnySources()throws Exception{
  Path root=Files.createTempDirectory("workspace-meta-");var storage=new EditorWorkspaceState(root);assertEquals(EditorWorkspaceState.empty(),storage.load());
  var state=new State("Мой проект",List.of(new Document("Мой проект","src/Мой класс.java",true),new Document("other",".gitignore",false)),List.of(new View("Мой проект",List.of("src","src/папка"),"src/папка","src/папка/read me.txt",19)));
  storage.save(state);assertEquals(state,storage.load());try(var entries=Files.list(root)){assertEquals(1L,entries.count());}assertTrue(Files.size(root.resolve(".genesis-workspace.properties"))<=EditorWorkspaceState.MAX_BYTES);
 }
 @Test public void invalidStateDoesNotReplaceExistingContext()throws Exception{
  Path root=Files.createTempDirectory("workspace-refuse-");var storage=new EditorWorkspaceState(root);storage.save(EditorWorkspaceState.empty());byte[] original=Files.readAllBytes(root.resolve(".genesis-workspace.properties"));
  assertThrows(java.io.IOException.class,()->storage.save(new State("ok",List.of(new Document("ok","../escape.java",true)),List.of())));
  assertThrows(java.io.IOException.class,()->storage.save(new State("ok",List.of(new Document("ok","a.java",true),new Document("ok","b.java",true)),List.of())));
  assertThrows(java.io.IOException.class,()->storage.save(new State("ok",List.of(new Document("ok","A.java",true),new Document("ok","a.java",false)),List.of())));
  assertThrows(java.io.IOException.class,()->storage.save(new State("ok",List.of(),List.of(new View("ok",List.of(),"","",-1)))));
  var many=new ArrayList<String>();for(int i=0;i<128;i++)many.add(("界".repeat(60)+"/").repeat(3)+"目".repeat(50)+i);
  assertThrows(java.io.IOException.class,()->storage.save(new State("ok",List.of(),List.of(new View("ok",many,"","",0)))));
  assertTrue(Arrays.equals(original,Files.readAllBytes(root.resolve(".genesis-workspace.properties"))));
  try(var entries=Files.list(root)){assertEquals(1L,entries.count());}
 }
 @Test public void malformedOversizedAndLinkedMetadataAreRefused()throws Exception{
  Path root=Files.createTempDirectory("workspace-load-");var storage=new EditorWorkspaceState(root);Path file=root.resolve(".genesis-workspace.properties");
  for(String value:List.of("format=2\n","format=1\ndocuments=17\nviews=0\n","format=1\ndocuments=0\nviews=-1\n","format=1\ndocuments=1\nviews=0\ndocument.0.project=p\ndocument.0.file=../../evil.java\ndocument.0.selected=true\n","format=1\ndocuments=0\nviews=1\nview.0.project=p\nview.0.expanded=129\n")){Files.writeString(file,value);assertThrows(java.io.IOException.class,storage::load);}
  Files.write(file,new byte[]{(byte)0xff});assertThrows(java.io.IOException.class,storage::load);
  Files.writeString(file,"x".repeat(EditorWorkspaceState.MAX_BYTES+1));assertThrows(java.io.IOException.class,storage::load);
  // Link creation may need privileges on Windows; retain the portable checks above.
  Path other=Files.createTempFile("workspace-other-",".properties");Files.delete(file);boolean linked=false;try{Files.createSymbolicLink(file,other);linked=true;}catch(java.io.IOException|UnsupportedOperationException unsupported){}
  if(linked){assertThrows(java.io.IOException.class,storage::load);assertThrows(java.io.IOException.class,()->storage.save(EditorWorkspaceState.empty()));assertEquals(0L,Files.size(other));}
 }
}
