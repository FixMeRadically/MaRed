package com.fixmer.mared;
import com.fixmer.mared.technology.server.setup.ClientModSet157;
import java.nio.file.*;import java.util.*;
public final class ClientModSetChecks157 {
 static int count;static void ok(boolean value){if(!value)throw new AssertionError();count++;}
 public static void main(String[] args)throws Exception{
  Path root=Files.createTempDirectory("client-set-test157-");try{
   byte[] mared="packaged-mared".getBytes(java.nio.charset.StandardCharsets.UTF_8);
   Path empty=ClientModSet157.stage(root,List.of(),mared);try(var entries=Files.list(empty)){var files=entries.toList();ok(files.size()==1);ok(Arrays.equals(Files.readAllBytes(files.get(0)),mared));}ClientModSet157.cleanup(empty);ok(!Files.exists(empty));
   Path cache=Files.createDirectory(root.resolve("launcher-cache")),source=cache.resolve("extra.jar");Files.writeString(source,"external mod");Path complete=ClientModSet157.stage(root,List.of(source,source),mared);
   try(var entries=Files.list(complete)){ok(entries.count()==2);}ok(Files.readString(complete.resolve("extra.jar")).equals("external mod"));ClientModSet157.cleanup(complete);ok(Files.readString(source).equals("external mod"));
   Path other=Files.createDirectory(root.resolve("other"));Files.writeString(other.resolve("extra.jar"),"different");long before;try(var entries=Files.list(root)){before=entries.count();}
   try{ClientModSet157.stage(root,List.of(source,other.resolve("extra.jar")),mared);throw new AssertionError("collision accepted");}catch(java.io.IOException expected){ok(expected.getMessage().equals("AUTO157_CLIENT_JAR_NAME"));}
   try(var entries=Files.list(root)){ok(entries.count()==before);}
   try{ClientModSet157.stage(root,List.of(),new byte[0]);throw new AssertionError("empty own jar");}catch(java.io.IOException expected){ok(expected.getMessage().equals("AUTO157_CLIENT_SET_LIMIT"));}
  }finally{try(var paths=Files.walk(root)){for(Path p:paths.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(p);}}
  System.out.println("ClientModSetChecks157: "+count+" passed");
 }
}
