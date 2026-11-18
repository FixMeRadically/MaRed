package com.fixmer.mared;
import com.fixmer.genesis.technology.server.remote.*;
import com.fixmer.genesis.technology.server.firstlaunch.*;
import com.fixmer.genesis.bootstrap.agent106.Entry;
import java.nio.file.*;import java.nio.channels.*;import java.net.*;import java.io.*;import java.util.*;import java.util.jar.*;import java.util.zip.*;import java.util.concurrent.*;
/** Real signed files, listening sockets and OS locks, including a separate JVM. No Minecraft fixtures claim game readiness. */
public final class ServerRecoveryChecks107 {
 static int checks;static byte[] key;static Path server,root;static String run=UUID.randomUUID().toString();
 interface Work{void run()throws Exception;}static void ok(boolean v){checks++;if(!v)throw new AssertionError("check "+checks);}static void rejects(Work w)throws Exception{try{w.run();}catch(Exception expected){checks++;return;}throw new AssertionError("accepted invalid input");}
 static void sign(String file,Map<String,String> values)throws Exception{FirstLaunchFiles.write(root.resolve(file),LocalApply67.sign(key,values),true);}
 static Map<String,String> state()throws Exception{return ServerLocal105.observe(server);}
 static void stale()throws Exception{sign("state104",Map.of("schema","104","run",run,"phase","RUNNING","updated",Long.toString(System.currentTimeMillis()-86400000L),"busy","true","generation","2","launch",UUID.randomUUID().toString()));}
 static FileChannel channel(Path p)throws Exception{return FileChannel.open(p,StandardOpenOption.CREATE,StandardOpenOption.WRITE);}
 static void blocked()throws Exception{ok(state().get("phase").equals("OWNER_UNAVAILABLE"));ok(!ServerView105.of(state(),true,false).canStart());}
 static byte[] core()throws Exception{try(var input=ServerRecoveryChecks107.class.getResourceAsStream("/genesis/server59/controller.zip")){if(input==null)throw new AssertionError("missing bundled controller");try(var zip=new ZipInputStream(input)){for(ZipEntry e;(e=zip.getNextEntry())!=null;)if(e.getName().equals("runtime/core.jar"))return zip.readAllBytes();}}throw new AssertionError("missing core");}
 static void modes(){
  for(String mode:List.of("forgeserver","neoforgeserver")){ok(Entry.applies("cpw.BootstrapLauncher --launchTarget "+mode,Map.of(),false));ok(Entry.applies("cpw.BootstrapLauncher --launchTarget\t"+mode+" nogui",Map.of(),false));ok(!Entry.applies("cpw --launchTarget "+mode,Map.of("GENESIS_INPLACE_VERSION","104"),false));}
  for(String mode:List.of("forgeclient","neoforgeclient","forgeserverdev","neoforgeserverextra"))ok(!Entry.applies("cpw --launchTarget "+mode,Map.of(),false));
  ok(!Entry.applies("cpw --launchTarget forgeserver",Map.of(),true));
 }
 static void locks()throws Exception{
  byte[] before=Files.readAllBytes(root.resolve("state104"));ok(state().get("phase").equals("STOPPED"));ok(ServerView105.of(state(),true,false).canStart());ok(Arrays.equals(before,Files.readAllBytes(root.resolve("state104"))));
  for(String name:List.of("owner104.lock","game104.lock","start104.lock"))try(var c=channel(root.resolve(name));var lock=c.lock(0,1,false)){blocked();}
  try(var c=channel(server.resolve("world/session.lock"));var lock=c.lock(0,1,false)){blocked();}
  int port=Integer.parseInt(Files.readString(server.resolve("server.properties")).split("server-port=")[1].trim());
  try(var socket=new ServerSocket()){socket.setReuseAddress(false);socket.bind(new InetSocketAddress("0.0.0.0",port));blocked();}
  Files.writeString(root.resolve("change104"),"DO NOT BYPASS AN INTERRUPTED INSTALL");blocked();ok(Files.exists(root.resolve("change104")));Files.delete(root.resolve("change104"));
  sign("review104",Map.of("schema","104","reason","WORLD_MAY_HAVE_CHANGED"));ok(state().get("phase").equals("RECOVERY_REQUIRED"));ok(!ServerView105.of(state(),true,false).canStart());Files.delete(root.resolve("review104"));
  Files.delete(server.resolve("world/session.lock"));blocked();Files.createFile(server.resolve("world/session.lock"));
  Files.writeString(server.resolve("world/level.dat"),"world must remain untouched");state();ok(Files.readString(server.resolve("world/level.dat")).equals("world must remain untouched"));
  sign("state104",Map.of("schema","104","run",run,"phase","RUNNING","updated",Long.toString(System.currentTimeMillis()),"busy","false"));ok(state().get("phase").equals("RUNNING"));
  stale();byte[] bytes=Files.readAllBytes(root.resolve("state104"));Files.writeString(root.resolve("state104"),"not signed");rejects(()->state());Files.write(root.resolve("state104"),bytes);
 }
 static void processLock()throws Exception{
  Path ready=root.resolve("probe-ready"),release=root.resolve("probe-release");String executable=Path.of(System.getProperty("java.home"),"bin",System.getProperty("os.name").toLowerCase(Locale.ROOT).startsWith("windows")?"java.exe":"java").toString();
  Process child=new ProcessBuilder(executable,"-cp",System.getProperty("java.class.path"),ServerRecoveryChecks107.class.getName(),"lock",root.toString()).redirectErrorStream(true).start();
  try{long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);while(!Files.exists(ready)&&child.isAlive()&&System.nanoTime()<until)Thread.sleep(20);ok(Files.exists(ready));blocked();}
  finally{Files.writeString(release,"release");if(!child.waitFor(15,TimeUnit.SECONDS)){child.destroyForcibly();throw new AssertionError("lock probe did not exit");}}
  ok(child.exitValue()==0);ok(state().get("phase").equals("STOPPED"));
 }
 static void installation()throws Exception{
  Path app=root.resolve("app/test107");Files.createDirectories(app.resolve("runtime"));byte[] first=core();Files.write(app.resolve("runtime/core.jar"),first);Files.writeString(server.resolve("user_jvm_args.txt"),"# keep my settings\r\n-Xmx2G\r\n");
  try(var c=channel(root.resolve("owner104.lock"));var lock=c.lock(0,1,false)){rejects(()->ServerAgent106.prepareStopped(server,app,key));ok(!Files.exists(root.resolve("boot106/agent.jar")));}
  ServerAgent106.prepareStopped(server,app,key);ok(Files.readString(server.resolve("user_jvm_args.txt")).contains(ServerAgent106.OPTION));ok(Files.readString(server.resolve("user_jvm_args.txt")).contains("-Xmx2G"));byte[] args=Files.readAllBytes(server.resolve("user_jvm_args.txt"));
  var modified=new ByteArrayOutputStream();try(var input=new JarInputStream(new ByteArrayInputStream(first));var jar=new JarOutputStream(modified)){for(JarEntry e;(e=input.getNextJarEntry())!=null;){jar.putNextEntry(new JarEntry(e.getName()));input.transferTo(jar);jar.closeEntry();}jar.putNextEntry(new JarEntry("upgrade107-marker"));jar.write(new byte[]{1});jar.closeEntry();}
  Files.write(app.resolve("runtime/core.jar"),modified.toByteArray());ServerAgent106.install(server,app,key);ok(Arrays.equals(Files.readAllBytes(root.resolve("boot106/core.jar")),first));
  ServerAgent106.prepareStopped(server,app,key);ok(Arrays.equals(Files.readAllBytes(root.resolve("boot106/core.jar")),modified.toByteArray()));ok(Arrays.equals(args,Files.readAllBytes(server.resolve("user_jvm_args.txt"))));
  var pin=LocalApply67.verify(key,FirstLaunchFiles.read(root.resolve("agent106"),16384));ok(pin.get("coreSha").equals(FirstLaunchFiles.sha(modified.toByteArray())));
  Files.writeString(root.resolve("boot106/agent.jar"),"tampered");rejects(()->ServerAgent106.prepareStopped(server,app,key));ok(Arrays.equals(args,Files.readAllBytes(server.resolve("user_jvm_args.txt"))));
 }
 public static void main(String[] args)throws Exception{
  if(args.length==2&&args[0].equals("lock")){Path p=Path.of(args[1]);try(var c=channel(p.resolve("owner104.lock"));var lock=c.lock(0,1,false)){Files.writeString(p.resolve("probe-ready"),"ready");long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(12);while(!Files.exists(p.resolve("probe-release"))&&System.nanoTime()<until)Thread.sleep(20);}return;}
  modes();server=Files.createTempDirectory("Genesis recovery107 ").toRealPath();root=ServerApp104.root(server);key=ServerBootstrap106.key(server);Files.createDirectories(server.resolve("world"));Files.createFile(server.resolve("world/session.lock"));int port;try(var socket=new ServerSocket(0)){port=socket.getLocalPort();}Files.writeString(server.resolve("server.properties"),"level-name=world\nserver-port="+port+"\n");sign("owner104",Map.of("schema","104","run",run,"profile",server.toString()));stale();locks();processLock();installation();System.out.println("Recovery107 real HMAC/file/port/locks/child-JVM/bootstrap checks passed: "+checks+"; Minecraft and Windows UI are not exercised here.");
 }
}
