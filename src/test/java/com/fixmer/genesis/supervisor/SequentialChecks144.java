package com.fixmer.genesis.supervisor;
import java.io.*;import java.nio.charset.StandardCharsets;import java.nio.file.*;import java.util.*;
public final class SequentialChecks144 {
 static int count;
 static void check(boolean condition,String name){if(!condition)throw new AssertionError(name);count++;}
 public static void main(String[] args)throws Exception {
  var lines=new ArrayList<String>();ChildOutput.copy(new StringReader("one\rtwo\rthree"),lines::add);check(lines.equals(List.of("one","two","three")),"CR progress lines");
  lines.clear();ChildOutput.copy(new StringReader("one\r\ntwo\n\r\nend"),lines::add);check(lines.equals(List.of("one","two","","end")),"mixed CR/LF");
  lines.clear();String unicode="x".repeat(16383)+"😀tail";ChildOutput.copy(new StringReader(unicode),lines::add);check(String.join("",lines).equals(unicode),"long output retained");check(lines.stream().allMatch(s->s.length()<=16384&&StandardCharsets.UTF_8.newEncoder().canEncode(s)),"UTF16 pairs retained");
  for(String secret:List.of("password=\"private secret value\"","'password': 'private secret value'","--accessToken \"private secret value\"","--password=\"private secret value\"","password=\"private \\\"secret\\\" value\"","password=\"private secret value","Bearer private==")){
   String masked=FailureReport101.redact(secret);check(!masked.contains("private")&&!masked.contains("secret")&&!masked.contains("value"),"secret masking: "+secret.split("[ =:]")[0]);
  }
  check(FailureReport101.redact("password=one\nsafe log").contains("safe log"),"next line preserved");
  check(DiagnosticText.prefix("a😀",2).equals("a"),"unicode diagnostic bound");check(DiagnosticText.prefix("😀",0).isEmpty(),"zero bound");
  check(!StartupAbort101.reason(null),"missing report reason");
  Path root=Files.createTempDirectory("sequential144-");
  try{
   var capture=new FailureReport101.Capture();capture.add("a"+"😀".repeat(9000));String launch=UUID.randomUUID().toString();
   Path latest=root.resolve("logs/latest.log");Files.createDirectories(latest.getParent());Files.writeString(latest,"password=\""+"x".repeat(2*1024*1024)+"PRIVATE_SUFFIX\"\nsafe tail\n");
   FailureReport101.capture(root,launch,"","PROCESS_EXIT",capture,0);
   Path folder=root.resolve("config/mared/dev/failures101").resolve(launch);
   check(Files.readString(folder.resolve("console.log")).contains("😀"),"Unicode report written");String tail=Files.readString(folder.resolve("game-latest.log"));
   check(!tail.contains("PRIVATE_SUFFIX")&&tail.contains("safe tail"),"partial secret tail omitted");
  }finally{try(var walk=Files.walk(root)){for(Path p:walk.sorted(Comparator.reverseOrder()).toList())Files.delete(p);}}
  System.out.println("SequentialChecks144: "+count+" passed");
 }
}
