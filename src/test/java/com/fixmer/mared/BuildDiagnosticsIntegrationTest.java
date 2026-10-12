package com.fixmer.mared;
import com.fixmer.genesis.technology.scripts.BuildDiagnostics;
import com.fixmer.genesis.technology.scripts.BuildDiagnostics.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
public class BuildDiagnosticsIntegrationTest {
 @Test public void javaAndGradlePositionsSupportPortableWindowsUnixAndUnicode(){
  String win="C:\\Рабочая папка\\demo\\src\\Main.java:12:5: error: missing symbol\nC:\\Рабочая папка\\demo\\src\\Other.java:3: warning: [deprecation] old API\nBuild file 'C:\\Рабочая папка\\demo\\build.gradle' line: 8\nExecution failed for task ':compileJava'.\n";
  var result=BuildDiagnostics.parse(win,"C:\\Рабочая папка\\demo");assertEquals(4,result.diagnostics().size());assertFalse(result.limited());var a=result.diagnostics().get(0);assertEquals("src/Main.java",a.file());assertEquals(12,a.line());assertEquals(5,a.column());assertEquals(Severity.ERROR,a.severity());assertEquals(Severity.WARNING,result.diagnostics().get(1).severity());assertEquals("build.gradle",result.diagnostics().get(2).file());assertEquals("",result.diagnostics().get(3).file());
  var unix=BuildDiagnostics.parse("\u001b[31m/root/demo/src/Имя.java:4: ошибка: нет символа\u001b[0m\n./src/Other.java:2: предупреждение: устарело\nfile:///root/demo/src/My%20Class.java:9: error: bad\nSettings file '/root/demo/settings.gradle.kts' line: 11\n","/root/demo");assertEquals(4,unix.diagnostics().size());assertEquals("src/Имя.java",unix.diagnostics().get(0).file());assertEquals(Severity.WARNING,unix.diagnostics().get(1).severity());assertEquals("src/My Class.java",unix.diagnostics().get(2).file());assertEquals("settings.gradle.kts",unix.diagnostics().get(3).file());
 }
 @Test public void outsidePathsTraversalInvalidPositionsAndUrlsNeverNavigate(){
  String[] unsafe={"/root/demo-other/Main.java","/root/else/Main.java","../Main.java","src/../../Main.java","/root/demo/../Main.java","https://evil/Main.java","file://evil/root/demo/Main.java","/root/demo/assets/image.png","C:\\else\\Main.java"};
  for(String path:unsafe){var d=BuildDiagnostics.parse(path+":4: error: problem","/root/demo").diagnostics().get(0);assertEquals("",d.file());assertEquals(0,d.line());assertTrue(d.raw().contains("problem"));}
  for(String line:List.of("0","999999999999999999999","10000001")){var d=BuildDiagnostics.parse("src/Main.java:"+line+": error: invalid","/root/demo").diagnostics().get(0);assertEquals("",d.file());}
  assertThrows(IllegalArgumentException.class,()->new Diagnostic(Severity.ERROR,"../bad",1,0,"bad","bad"));assertTrue(BuildDiagnostics.parse("unrecognized compiler format\n","/root/demo").diagnostics().isEmpty());
 }
 @Test public void outputLinesAndDiagnosticCountsAreBoundedAndDuplicatesCollapsed(){
  String duplicate="src/Main.java:1: error: bad\n";assertEquals(1,BuildDiagnostics.parse(duplicate.repeat(20),"/root/demo").diagnostics().size());
  var text=new StringBuilder();for(int i=0;i<201;i++)text.append("src/Main.java:").append(i+1).append(": error: problem\n");var result=BuildDiagnostics.parse(text.toString(),"/root/demo");assertEquals(200,result.diagnostics().size());assertTrue(result.limited());
  assertTrue(BuildDiagnostics.parse("x".repeat(BuildDiagnostics.MAX_LINE+1),"/root/demo").limited());var tail=BuildDiagnostics.parse("x".repeat(BuildDiagnostics.MAX_OUTPUT)+"\n"+duplicate,"/root/demo");assertTrue(tail.limited());assertEquals(1,tail.diagnostics().size());assertEquals("src/Main.java",tail.diagnostics().get(0).file());
  assertThrows(UnsupportedOperationException.class,()->result.diagnostics().clear());
 }
}
