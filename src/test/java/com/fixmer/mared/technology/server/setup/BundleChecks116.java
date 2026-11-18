package com.fixmer.mared.technology.server.setup;
import java.nio.file.*;import java.io.*;import java.util.*;import java.util.zip.*;
/** Real resource extraction, not Minecraft or Windows ACL validation. */
public final class BundleChecks116 {
 static int checks;interface Work{void run()throws Exception;}static void ok(boolean x){checks++;if(!x)throw new AssertionError("bundle116 "+checks);}static void rejects(Work w)throws Exception{try{w.run();}catch(IOException expected){checks++;return;}throw new AssertionError("accepted bad bundle "+checks);}
 static byte[] zip(List<String> names)throws Exception{var out=new ByteArrayOutputStream();try(var z=new ZipOutputStream(out)){for(String n:names){z.putNextEntry(new ZipEntry(n));z.write("bounded fixture".getBytes(java.nio.charset.StandardCharsets.UTF_8));z.closeEntry();}}return out.toByteArray();}
 static void unpack(Path p,byte[] data)throws Exception{Files.createDirectories(p);ServerSetupBundle59.unpack(p,new ByteArrayInputStream(data));}
 public static void main(String[]args)throws Exception{Path t=Files.createTempDirectory("Genesis bundle116 ");try{
  var names=new ArrayList<>(List.of("tools/server_setup.py","tools/first_launch.py","runtime/core.jar"));for(int i=0;i<100;i++)names.add("tools/first58/module"+i+".py");Path valid=t.resolve("valid");unpack(valid,zip(names));ok(Files.isRegularFile(valid.resolve("tools/first58/module99.py")));ok(Files.isRegularFile(valid.resolve("runtime/core.jar")));
  var excess=new ArrayList<>(names);for(int i=0;i<160;i++)excess.add("tools/first58/extra"+i+".py");rejects(()->unpack(t.resolve("excess"),zip(excess)));
  for(String bad:List.of("../outside.py","/outside.py","tools/../outside.py","tools/./same.py","tools\\bad.py","C:/bad.py","runtime/evil.jar","tools/not_python.class"))rejects(()->unpack(t.resolve("bad"+checks),zip(List.of("tools/server_setup.py",bad))));
  rejects(()->unpack(t.resolve("case"),zip(List.of("tools/x.py","tools/X.py"))));rejects(()->unpack(t.resolve("incomplete"),zip(List.of("tools/x.py"))));Path e=Files.createDirectories(t.resolve("existing/tools"));Files.writeString(e.resolve("server_setup.py"),"keep");rejects(()->unpack(e.getParent(),zip(List.of("tools/server_setup.py"))));ok(Files.readString(e.resolve("server_setup.py")).equals("keep"));ok(!Files.exists(t.resolve("outside.py")));
  System.out.println("Bundle116 actual growth/path/type/duplicate/limit/no-overwrite checks passed: "+checks+"; no Minecraft runtime.");
 }finally{try(var paths=Files.walk(t)){for(Path p:paths.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(p);}}}
}
