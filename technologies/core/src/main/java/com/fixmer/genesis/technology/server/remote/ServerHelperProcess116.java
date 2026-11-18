package com.fixmer.genesis.technology.server.remote;
import com.fixmer.genesis.technology.server.firstlaunch.FirstLaunchFiles;
import java.io.*;import java.nio.file.*;import java.util.*;import java.util.concurrent.TimeUnit;
/** File-backed local helper I/O. await is only for short helpers, never a game/lifetime owner. */
public final class ServerHelperProcess116 {
 private ServerHelperProcess116(){}
 public static Process start(String executable,List<String> arguments,Path home,Path output,Path error)throws IOException{
  home=FirstLaunchFiles.safe(home);output=FirstLaunchFiles.safe(output);error=FirstLaunchFiles.safe(error);
  if(!Files.isDirectory(home)||output.equals(error)||arguments.size()>128)throw new IOException("SERVER116_HELPER_ARGUMENTS");
  var command=new ArrayList<String>();command.add(executable);command.addAll(arguments);
  for(String arg:command)if(arg==null||arg.isEmpty()||arg.length()>32767||arg.indexOf(0)>=0||arg.indexOf('\n')>=0||arg.indexOf('\r')>=0)throw new IOException("SERVER116_HELPER_ARGUMENTS");
  var builder=new ProcessBuilder(command).directory(home.toFile()).redirectOutput(output.toFile()).redirectError(error.toFile());
  builder.environment().keySet().removeIf(key->{String k=key.toUpperCase(Locale.ROOT);return k.startsWith("GENESIS_")||Set.of("JAVA_TOOL_OPTIONS","JDK_JAVA_OPTIONS","_JAVA_OPTIONS","PYTHONPATH","PYTHONHOME","PYTHONSTARTUP","PYTHONINSPECT").contains(k);});
  builder.environment().put("PYTHONUTF8","1");builder.environment().put("PYTHONIOENCODING","utf-8");
  Process process=builder.start();try{process.getOutputStream().close();}catch(IOException failed){stop(process);throw failed;}return process;
 }
 public static boolean await(Process helper,long timeout,TimeUnit unit)throws InterruptedException{
  try{if(helper.waitFor(timeout,unit))return true;stop(helper);return false;}
  catch(InterruptedException interrupted){stop(helper);Thread.currentThread().interrupt();throw interrupted;}
 }
 private static void stop(Process helper){
  if(!helper.isAlive())return;helper.destroy();boolean interrupted=Thread.interrupted();
  try{if(!helper.waitFor(2,TimeUnit.SECONDS)){helper.destroyForcibly();helper.waitFor(2,TimeUnit.SECONDS);}}
  catch(InterruptedException again){interrupted=true;helper.destroyForcibly();}finally{if(interrupted)Thread.currentThread().interrupt();}
 }
}
