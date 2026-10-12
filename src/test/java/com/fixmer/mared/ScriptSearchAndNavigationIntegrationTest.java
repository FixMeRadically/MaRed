package com.fixmer.mared;

import com.fixmer.genesis.technology.scripts.*;
import com.fixmer.mared.gui2.framework.components.editor.EditorDocument;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ScriptSearchAndNavigationIntegrationTest {
    @Test void projectSearchReportsUnreadableFilesAndUsesOriginalCoordinates() throws Exception {
        var result=ScriptSearch.project(List.of("bad.java","good.java"),path->{
            if(path.equals("bad.java"))throw new java.io.IOException("missing");
            return "first\r\n  NEEDLE\r\n";
        },"needle",()->false);
        assertEquals(1,result.errors().size());assertEquals(1,result.hits().size());
        assertEquals(1,result.hits().get(0).line());assertEquals(2,result.hits().get(0).column());
        assertEquals(6,result.hits().get(0).length());
    }
    @Test void emptyFileQueryListsAllCachedFilesAndTopPanelHasItsOwnGeometry() {
        var files=new ArrayList<String>();for(int i=0;i<600;i++)files.add("File"+i+".java");
        var result=ScriptSearch.files(files,"",()->false);assertEquals(600,result.hits().size());assertFalse(result.limited());
        var frame=com.fixmer.genesis.technology.editor.EditorLayout.calculate(640,400,.18,.29,.24,true,false,true,46);
        assertEquals(46,frame.toolbar().height());assertEquals(46,frame.editor().y());assertEquals(46,frame.files().y());
        assertEquals(22,com.fixmer.genesis.technology.editor.EditorLayout.calculate(640,400,.18,.29,.24,true,false,true).toolbar().height());
    }
    @Test void keyboardSelectionKeepsAnchorAndDoesNotMutateContent() {
        var doc=new EditorDocument();doc.setValue("alpha beta\nshort\n\nlast");int version=doc.contentVersion();
        assertTrue(doc.navigate(262,true,true));assertEquals("alpha ",doc.getSelectedText());
        doc.navigate(262,true,true);assertEquals("alpha beta\n",doc.getSelectedText());
        doc.navigate(263,false,false);assertEquals(0,doc.cursorCol());assertFalse(doc.hasSelection());
        doc.navigate(269,true,true);assertEquals(doc.getValue(),doc.getSelectedText());
        doc.navigate(268,true,false);assertEquals(0,doc.cursorLine());assertFalse(doc.hasSelection());assertEquals(version,doc.contentVersion());
    }
    @Test void verticalSelectionPreservesColumnAndUnicodeArrowsKeepCodepoints() {
        var doc=new EditorDocument();doc.setValue("123456789\nx\n123456789");doc.setCursor(0,8);
        doc.navigate(264,false,true);doc.navigate(264,false,true);assertEquals(8,doc.cursorCol());assertEquals("9\nx\n12345678",doc.getSelectedText());
        doc.setValue("A😀B");doc.setCursor(0,1);doc.navigate(262,false,true);assertEquals("😀",doc.getSelectedText());assertEquals(3,doc.cursorCol());
    }
    @Test void literalSnapshotSearchPreservesPositionsHandlesDraftsAndHasBounds() {
        var result=ScriptSearch.text("src/Main.java","İx TARGET\nчерновик target","target",()->false);
        assertEquals(2,result.hits().size());assertEquals(3,result.hits().get(0).column());assertEquals(1,result.hits().get(1).line());
        assertEquals(9,result.hits().get(1).column());assertEquals(6,result.hits().get(1).length());
        assertEquals(0,ScriptSearch.text("a","anything",".*",()->false).hits().size());
        result=ScriptSearch.text("a","a".repeat(1000),"a",()->false);assertEquals(200,result.hits().size());assertTrue(result.limited());
        assertThrows(java.util.concurrent.CancellationException.class,()->ScriptSearch.text("a","abc","b",()->true));
        assertThrows(IllegalArgumentException.class,()->ScriptSearch.text("a","abc","q".repeat(129),()->false));
        assertEquals(List.of("src/deep/Robot.java"),ScriptSearch.files(List.of("a.txt","src/deep/Robot.java"),"robot",()->false).hits().stream().map(ScriptSearch.Hit::path).toList());
    }
    @Test void snippetsInferConventionalPackagesAndNeverAlterExistingFiles()throws Exception {
        String source=JavaFileTemplate.source("src/main/java/com/example/Robot.java");assertEquals("package com.example;\n\npublic class Robot {\n\n}\n",source);
        assertEquals("public class Root {\n\n}\n",JavaFileTemplate.source("Root.java"));
        assertEquals("",JavaFileTemplate.source("src/class.java"));assertEquals("",JavaFileTemplate.source("src/module-info.java"));assertEquals("",JavaFileTemplate.source("src/package-info.java"));assertEquals("",JavaFileTemplate.source("notes.md"));
        var store=new SourceProjects(Files.createTempDirectory("java-snippet-"));store.createEmpty("demo");store.createFile("demo","src/main/java/com/example/Robot.java",source);assertEquals(source,store.read("demo","src/main/java/com/example/Robot.java"));
        assertThrows(java.io.IOException.class,()->store.createFile("demo","src/main/java/com/example/Robot.java","overwrite"));assertEquals(source,store.read("demo","src/main/java/com/example/Robot.java"));
        store.createFile("demo","src/Empty.java");assertEquals("",store.read("demo","src/Empty.java"));
    }
    @Test void generatedClassCompilesWithARealCaller() {
        var source=JavaFileTemplate.source("src/demo/Robot.java");
        var result=new JavaSourceRunner().run(Map.of("src/demo/Robot.java",source,"src/demo/Main.java","package demo;public class Main{public static void main(String[]a){System.out.print(new Robot().getClass().getSimpleName());}}"),"demo.Main",JavaSourceRunner.Limits.defaults(),new JavaSourceRunner.Cancellation());
        assertEquals(JavaSourceRunner.Status.SUCCESS,result.status());assertTrue(result.output().contains("Robot"));
    }
}
