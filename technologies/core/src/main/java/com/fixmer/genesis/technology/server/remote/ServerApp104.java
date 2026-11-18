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
 private static void file(Path p,byte[] b)throws IOException{
  directory(p.getParent());
  if(Files.exists(p,LinkOption.NOFOLLOW_LINKS)){if(!Files.isRegularFile(p,LinkOption.NOFOLLOW_LINKS)||!FirstLaunchFiles.hash(p,b.length).equals(hash(b)))throw new IOException("SERVER104_APP_CHANGED");}
  else try{FirstLaunchFiles.write(p,b,false);}catch(FileAlreadyExistsException race){if(!FirstLaunchFiles.hash(p,b.length).equals(hash(b)))throw new IOException("SERVER104_APP_CHANGED",race);}
 }
 private static boolean marker(byte[] bytes)throws IOException{
  Path temporary=Files.createTempFile("genesis-app105-",".jar");
  try{FirstLaunchFiles.privateMode(temporary);Files.write(temporary,bytes);try(var jar=new ZipFile(temporary.toFile())){return jar.getEntry("com/fixmer/mared/technology/server/firstlaunch/InPlaceWorld104.class")!=null;}}
  finally{Files.deleteIfExists(temporary);}
 }

 public static Path deploy(Path server,InputStream bundle,InputStream mod)throws IOException{
  if(!Files.isDirectory(FirstLaunchFiles.safe(server)))throw new IOException("SERVER104_SERVER_FOLDER_REQUIRED");
  byte[] zip=bytes(bundle),jar=bytes(mod);if(!marker(jar))throw new IOException("SERVER104_BUILD_JAR_REQUIRED");
  Path root=root(server);directory(root);Path app=root.resolve("app").resolve(hash((hash(zip)+hash(jar)).getBytes(java.nio.charset.StandardCharsets.US_ASCII)));directory(app);int count=0;long total=0;var names=new HashSet<String>();
  try(var in=new ZipInputStream(new ByteArrayInputStream(zip))){for(ZipEntry e;(e=in.getNextEntry())!=null;){String name=e.getName();Path relative=Path.of(name);if(relative.isAbsolute()||name.contains("\\")||name.contains(":")||name.startsWith("/")||Arrays.asList(name.split("/")).contains("..")||!names.add(name.toLowerCase(Locale.ROOT))||++count>2000)throw new IOException("SERVER104_APP_ENTRY");byte[] data=in.readNBytes(100*1024*1024+1);total+=data.length;if(total>100*1024*1024)throw new IOException("SERVER104_APP_LIMIT");if(e.isDirectory()){if(data.length!=0)throw new IOException("SERVER104_APP_ENTRY");continue;}file(app.resolve(relative),data);}}
  if(!Files.isRegularFile(app.resolve("tools/server104.py"))||!Files.isRegularFile(app.resolve("runtime/core.jar")))throw new IOException("SERVER104_APP_INCOMPLETE");file(app.resolve("mared.jar"),jar);return app;
 }
 public static Path reuseLastDeployment(Path server)throws IOException{
  Path root=root(server),record=root.resolve("start104");
  if(!Files.isRegularFile(record,LinkOption.NOFOLLOW_LINKS))throw new IOException("SERVER104_BUILD_JAR_REQUIRED");
  byte[] key=FirstLaunchFiles.read(root.resolve("owner74.key"),32);
  var start=LocalApply67.verify(key,FirstLaunchFiles.read(record,16384));
  if(!"104".equals(start.get("schema"))||!FirstLaunchFiles.safe(server).toString().equals(start.get("profile"))||start.get("app")==null)
   throw new IOException("SERVER104_START_BINDING");
  Path app=FirstLaunchFiles.safe(Path.of(start.get("app")));
  String id=app.getFileName().toString();
  if(!root.resolve("app").equals(app.getParent())||!id.matches("[a-f0-9]{64}"))
   throw new IOException("SERVER104_START_BINDING");
  Path jarPath=app.resolve("mared.jar");
  byte[] jar=FirstLaunchFiles.read(jarPath,64*1024*1024);
  byte[] bundle;
  try(var archive=new ZipFile(jarPath.toFile())){
   var entry=archive.getEntry("genesis/server59/controller.zip");
   if(entry==null||entry.isDirectory())throw new IOException("SERVER104_APP_INCOMPLETE");
   bundle=bytes(archive.getInputStream(entry));
  }
  String binding=hash((hash(bundle)+hash(jar)).getBytes(java.nio.charset.StandardCharsets.US_ASCII));
  if(!id.equals(binding))throw new IOException("SERVER104_APP_CHANGED");
  return deploy(server,new ByteArrayInputStream(bundle),new ByteArrayInputStream(jar));
 }
 public static Map<String,String> state(Path server)throws Exception{
  Path root=root(server),file=root.resolve("state104");if(!Files.exists(file))return Map.of("phase","STOPPED");byte[] key=FirstLaunchFiles.read(root.resolve("owner74.key"),32);var s=LocalApply67.verify(key,FirstLaunchFiles.read(file,16384));var owner=LocalApply67.verify(key,FirstLaunchFiles.read(root.resolve("owner104"),16384));
  if(!s.getOrDefault("schema","").equals("104")||!s.getOrDefault("run","").equals(owner.get("run"))||!owner.getOrDefault("profile","").equals(FirstLaunchFiles.safe(server).toString()))throw new IOException("SERVER104_STATE_BINDING");long age=System.currentTimeMillis()-Long.parseLong(s.get("updated"));if(!Set.of("STOPPED","FAILED","RECOVERY_REQUIRED","BOOTSTRAP_FAILED_RESTORED").contains(s.get("phase"))&&(age>10000||age< -30000)){var stale=new HashMap<>(s);stale.put("phase","OWNER_UNAVAILABLE");return Map.copyOf(stale);}return s;
 }
 public static void diagnostic(Path server,Throwable failed){try{if(!Files.isDirectory(FirstLaunchFiles.safe(server)))return;Path logs=root(server).resolve("logs");directory(logs);var text=new StringWriter();failed.printStackTrace(new PrintWriter(text));byte[] bytes=text.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);if(bytes.length>32768)bytes=Arrays.copyOf(bytes,32768);FirstLaunchFiles.write(logs.resolve("setup-error.log"),bytes,true);}catch(Exception ignored){}}
 public static Process helper106(Path app,Path server,Path java,UUID ticket,Path output)throws IOException{
  var command=new ArrayList<String>(List.of(app.resolve("tools/server104.py").toString(),app.toString(),"auto106",server.toString(),"--java",java.toString(),"--ticket",ticket.toString()));return invoke106(command,server,output);
 }
 public static Process helper(Path app,String operation,Path server,Path java,Path output)throws IOException{
  if(!Set.of("recover-prepared","start","status","restart","stop","acknowledge").contains(operation))throw new IOException("SERVER104_ACTION");var command=new ArrayList<String>(List.of(app.resolve("tools/server104.py").toString(),app.toString(),operation,server.toString()));if(java!=null)command.addAll(List.of("--java",java.toString()));return invoke106(command,server,output);
 }
 private record Python(Path path,boolean launcher){
  List<String> command(List<String> args){
   var result=new ArrayList<String>();
   result.add(path.toString());
   if(launcher)result.add("-3");
   result.addAll(args);
   return result;
  }
 }
 private static void addPython(Set<Python> candidates,Path dir,boolean launchers){
  if(launchers){
   candidates.add(new Python(dir.resolve("pyw.exe"),true));
   candidates.add(new Python(dir.resolve("py.exe"),true));
  }
  candidates.add(new Python(dir.resolve("pythonw.exe"),false));
  candidates.add(new Python(dir.resolve("python.exe"),false));
 }
 private static void addPythonInstalls(Set<Python> candidates,Path folder){
  if(!Files.isDirectory(folder))return;
  try(var paths=Files.list(folder)){
   for(Path install:paths.filter(p->p.getFileName().toString().matches("(?i)(?:Python3[0-9]{2}|pythoncore-3[.][0-9]+-(?:32|64|arm64))")).sorted(Comparator.reverseOrder()).limit(32).toList())
    addPython(candidates,install,false);
  }catch(IOException ignored){}
 }
 private static Set<Python> pythonCandidates(){
  var candidates=new LinkedHashSet<Python>();
  String explicit=System.getenv("MARED_PYTHON");
  if(explicit!=null&&!explicit.isBlank())try{
   Path path=Path.of(explicit);
   if(path.isAbsolute())candidates.add(new Python(path,false));
  }catch(InvalidPathException ignored){}
  String local=System.getenv("LOCALAPPDATA");
  if(local!=null&&!local.isBlank()){
   Path folder=Path.of(local).resolve("Programs/Python");
   addPython(candidates,folder.resolve("Launcher"),true);
   addPythonInstalls(candidates,folder);
   addPythonInstalls(candidates,Path.of(local).resolve("Python"));
  }
  String windows=System.getenv("SystemRoot");
  if(windows!=null&&!windows.isBlank())addPython(candidates,Path.of(windows),true);
  String programFiles=System.getenv("ProgramFiles");
  if(programFiles!=null&&!programFiles.isBlank())addPythonInstalls(candidates,Path.of(programFiles));
  String path=System.getenv("PATH");
  if(path!=null)for(String entry:path.split(java.util.regex.Pattern.quote(File.pathSeparator)))try{
   Path dir=Path.of(entry.strip().replace("\"",""));
   if(dir.isAbsolute()&&!dir.toString().toLowerCase(Locale.ROOT).contains("windowsapps"))addPython(candidates,dir,true);
  }catch(InvalidPathException ignored){}
  return candidates;
 }
 private static ProcessBuilder pythonProcess(Python python,List<String> command,Path server){
  var builder=new ProcessBuilder(python.command(command)).directory(server.toFile());
  builder.environment().keySet().removeIf(k->k.toUpperCase(Locale.ROOT).startsWith("GENESIS_")||Set.of("JAVA_TOOL_OPTIONS","JDK_JAVA_OPTIONS","_JAVA_OPTIONS","PYTHONPATH","PYTHONHOME","PYTHONSTARTUP","PYTHONINSPECT").contains(k.toUpperCase(Locale.ROOT)));
  builder.environment().put("PYTHONUTF8","1");builder.environment().put("PYTHONIOENCODING","utf-8");
  return builder;
 }
 private static Process invoke106(ArrayList<String> command,Path server,Path output)throws IOException{
  // Check redirection independently, so a missing or locked log is not reported as missing Python.
  try{
   Files.createDirectories(output.toAbsolutePath().getParent());
   try(var log=Files.newOutputStream(output,StandardOpenOption.CREATE,StandardOpenOption.APPEND)){}
  }catch(IOException failed){throw new IOException("SERVER105_HELPER_LOG_UNAVAILABLE",failed);}
  IOException missing=new IOException("SERVER105_PYTHON_REQUIRED");
  for(Python python:pythonCandidates()){
   if(!Files.isRegularFile(python.path()))continue;
   try{
    var probe=pythonProcess(python,List.of("-I","-c","import sys, tomllib; sys.exit(0 if sys.version_info >= (3, 11) else 1)"),server)
        .redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD).start();
    boolean ready;
    try{
     ready=probe.waitFor(5,java.util.concurrent.TimeUnit.SECONDS)&&probe.exitValue()==0;
    }catch(InterruptedException interrupted){
     Thread.currentThread().interrupt();throw new IOException("SERVER105_HELPER_INTERRUPTED",interrupted);
    }finally{if(probe.isAlive())probe.destroyForcibly();}
    if(!ready){
     missing.addSuppressed(new IOException("Python version probe failed or timed out: "+python.path()));
     continue;
    }
   }catch(IOException unavailable){
    if(Thread.currentThread().isInterrupted())throw unavailable;
    missing.addSuppressed(unavailable);continue;
   }
   try{return pythonProcess(python,command,server).redirectErrorStream(true).redirectOutput(output.toFile()).start();}
   catch(IOException launch){throw new IOException("SERVER105_HELPER_START_FAILED",launch);}
  }
  throw missing;
 }
}
