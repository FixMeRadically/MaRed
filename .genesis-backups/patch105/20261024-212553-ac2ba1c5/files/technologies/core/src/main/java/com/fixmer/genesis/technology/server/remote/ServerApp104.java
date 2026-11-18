package com.fixmer.genesis.technology.server.remote;
import com.fixmer.genesis.technology.server.firstlaunch.*;
import java.io.*;import java.nio.file.*;import java.security.*;import java.util.*;import java.util.zip.*;
/** Versioned application files only, in the selected server directory. Never copies the server/world. */
public final class ServerApp104 {
 private ServerApp104(){}
 public static Path root(Path server)throws IOException{return FirstLaunchFiles.safe(server).resolve(".genesis/server104");}
 private static byte[] bytes(InputStream stream)throws IOException{if(stream==null)throw new IOException("SERVER104_APP_RESOURCE");try(stream){byte[] b=stream.readNBytes(100*1024*1024+1);if(b.length>100*1024*1024)throw new IOException("SERVER104_APP_LIMIT");return b;}}
 private static String hash(byte[] b){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(b));}catch(Exception e){throw new IllegalStateException(e);}}
 private static void directory(Path p)throws IOException{FirstLaunchFiles.safe(p);Files.createDirectories(p);FirstLaunchFiles.privateMode(p);}
 private static void file(Path p,byte[] b)throws IOException{directory(p.getParent());if(Files.exists(p,LinkOption.NOFOLLOW_LINKS)){if(!Files.isRegularFile(p,LinkOption.NOFOLLOW_LINKS)||!hash(Files.readAllBytes(p)).equals(hash(b)))throw new IOException("SERVER104_APP_CHANGED");}else FirstLaunchFiles.write(p,b,false);}
 public static Path deploy(Path server,InputStream bundle,InputStream mod)throws IOException{
  if(!Files.isDirectory(FirstLaunchFiles.safe(server)))throw new IOException("SERVER104_SERVER_FOLDER_REQUIRED");
  byte[] zip=bytes(bundle),jar=bytes(mod);boolean found=false;try(var in=new ZipInputStream(new ByteArrayInputStream(jar))){for(ZipEntry e;(e=in.getNextEntry())!=null;)if(e.getName().equals("com/fixmer/mared/technology/server/firstlaunch/InPlaceWorld104.class"))found=true;}if(!found)throw new IOException("SERVER104_BUILD_JAR_REQUIRED");
  Path root=root(server);directory(root);Path app=root.resolve("app").resolve(hash((hash(zip)+hash(jar)).getBytes(java.nio.charset.StandardCharsets.US_ASCII)));directory(app);int count=0;long total=0;var names=new HashSet<String>();
  try(var in=new ZipInputStream(new ByteArrayInputStream(zip))){for(ZipEntry e;(e=in.getNextEntry())!=null;){if(e.isDirectory())continue;String name=e.getName();Path relative=Path.of(name);if(relative.isAbsolute()||name.contains("\\")||name.contains(":")||name.startsWith("/")||Arrays.asList(name.split("/")).contains("..")||!names.add(name.toLowerCase(Locale.ROOT))||++count>2000)throw new IOException("SERVER104_APP_ENTRY");byte[] data=in.readNBytes(100*1024*1024+1);total+=data.length;if(total>100*1024*1024)throw new IOException("SERVER104_APP_LIMIT");file(app.resolve(relative),data);}}
  if(!Files.isRegularFile(app.resolve("tools/server104.py"))||!Files.isRegularFile(app.resolve("runtime/core.jar")))throw new IOException("SERVER104_APP_INCOMPLETE");file(app.resolve("mared.jar"),jar);return app;
 }
 public static Map<String,String> state(Path server)throws Exception{
  Path root=root(server),file=root.resolve("state104");if(!Files.exists(file))return Map.of("phase","STOPPED");byte[] key=FirstLaunchFiles.read(root.resolve("owner74.key"),32);var s=LocalApply67.verify(key,FirstLaunchFiles.read(file,16384));var owner=LocalApply67.verify(key,FirstLaunchFiles.read(root.resolve("owner104"),16384));
  if(!s.getOrDefault("schema","").equals("104")||!s.getOrDefault("run","").equals(owner.get("run"))||!owner.getOrDefault("profile","").equals(FirstLaunchFiles.safe(server).toString()))throw new IOException("SERVER104_STATE_BINDING");long age=System.currentTimeMillis()-Long.parseLong(s.get("updated"));if(!Set.of("STOPPED","FAILED","RECOVERY_REQUIRED","BOOTSTRAP_FAILED_RESTORED").contains(s.get("phase"))&&(age>10000||age< -30000)){var stale=new HashMap<>(s);stale.put("phase","OWNER_UNAVAILABLE");return Map.copyOf(stale);}return s;
 }
 public static void diagnostic(Path server,Throwable failed){try{Path logs=root(server).resolve("logs");directory(logs);var text=new StringWriter();failed.printStackTrace(new PrintWriter(text));byte[] bytes=text.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);if(bytes.length>32768)bytes=Arrays.copyOf(bytes,32768);FirstLaunchFiles.write(logs.resolve("setup-error.log"),bytes,true);}catch(Exception ignored){}}
 public static Process helper(Path app,String operation,Path server,Path java,Path output)throws IOException{
  if(!Set.of("start","status","restart","stop","acknowledge").contains(operation))throw new IOException("SERVER104_ACTION");var command=new ArrayList<String>(List.of("pyw.exe","-3",app.resolve("tools/server104.py").toString(),app.toString(),operation,server.toString()));if(java!=null)command.addAll(List.of("--java",java.toString()));var builder=new ProcessBuilder(command).directory(server.toFile()).redirectErrorStream(true).redirectOutput(output.toFile());builder.environment().put("PYTHONUTF8","1");builder.environment().put("PYTHONIOENCODING","utf-8");try{return builder.start();}catch(IOException missing){command.set(0,"py.exe");return builder.start();}
 }
}
