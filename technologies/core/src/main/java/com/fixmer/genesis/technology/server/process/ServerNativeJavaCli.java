package com.fixmer.genesis.technology.server.process;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Pattern;
/** Explicit Windows diagnostics with only private generated probes, never Minecraft. */
public final class ServerNativeJavaCli {
 record ExitReceipt(long pid,String sha256,boolean stopSent){}
 private ServerNativeJavaCli(){}
 private static void require(boolean ok,String why)throws IOException{if(!ok)throw new IOException(why);}
 static void receipt(ServerNativeLaunchCapsule.Frozen frozen,boolean lost,boolean forced,boolean stopped)throws IOException {receipt(frozen,lost,forced,stopped,0);}
 static void receipt(ServerNativeLaunchCapsule.Frozen frozen,boolean lost,boolean forced,boolean stopped,long expectedPid)throws IOException {checkedReceipt(frozen,lost,forced,stopped,expectedPid);}
 static ExitReceipt checkedReceipt(ServerNativeLaunchCapsule.Frozen frozen,boolean lost,boolean forced,boolean stopped,long expectedPid)throws IOException {
  byte[] raw=ServerNativeLaunchCapsule.bounded(frozen.file().getParent().resolve("native-exit.json"),4096);String text=new String(raw,StandardCharsets.US_ASCII);
  String pattern="\\{\\\"schema\\\":46,\\\"run\\\":\\\""+Pattern.quote(frozen.plan().binding().run().toString())+"\\\",\\\"epoch\\\":"+frozen.plan().binding().epoch()+",\\\"capsuleSha256\\\":\\\""+frozen.sha()+"\\\",\\\"pid\\\":([1-9][0-9]{0,9}),\\\"active\\\":0,\\\"exitCode\\\":(0|124),\\\"stopSent\\\":"+stopped+",\\\"forced\\\":"+forced+",\\\"ownerLost\\\":"+lost+",\\\"logFailed\\\":false,\\\"nativeError\\\":0,\\\"installAuthority\\\":false}\\n";
  var matcher=Pattern.compile(pattern).matcher(text);require(matcher.matches(),"NATIVE46_RECEIPT_INVALID");long pid=Long.parseLong(matcher.group(1));require(pid<=0xffffffffL&&(expectedPid==0||pid==expectedPid),"NATIVE46_RECEIPT_PID");if(!forced)require(matcher.group(2).equals("0"),"NATIVE46_RECEIPT_EXIT");
  return new ExitReceipt(pid,HexFormat.of().formatHex(ServerNativeLaunchCapsule.digest(raw)),stopped);
 }
 public static Map<String,Object> demo(Path helper,Path root)throws Exception {
  if(!System.getProperty("os.name").startsWith("Windows"))throw new IOException("NATIVE46_WINDOWS_REQUIRED");
  root=root.toAbsolutePath().normalize();ServerFiles.safe(root);require(!Files.exists(root,LinkOption.NOFOLLOW_LINKS),"NATIVE46_NEW_DIRECTORY_REQUIRED");Files.createDirectory(root);ServerFiles.privateMode(root,true);
  byte[] code;try(var in=ServerNativeJavaProbe.class.getResourceAsStream("ServerNativeJavaProbe.class")){if(in==null)throw new IOException("NATIVE46_PROBE_MISSING");code=in.readNBytes(1024*1024+1);}
  Path java=Path.of(System.getProperty("java.home"),"bin","java.exe");var results=new LinkedHashMap<String,Object>();results.put("schema",46);
  String[] cases={"graceful","forced","orphanGraceful","orphanForce","flood","rootExitDescendant"};
  for(String name:cases){String mode=switch(name){case "forced","orphanForce"->"ignore";case "flood"->"flood";case "rootExitDescendant"->"root-exit";default->"normal";};boolean orphan=name.startsWith("orphan")||name.equals("rootExitDescendant"),forced=name.equals("forced")||name.equals("orphanForce")||name.equals("rootExitDescendant");
   var frozen=ServerNativeLaunchCapsule.probe(root.resolve(name),java,code,mode,forced,ServerNativeOwnerProtocol.Binding.fresh());
   try(var session=ServerNativeJavaSession.diagnostic(helper,frozen)){
    session.start();if(name.equals("flood"))waitLog(frozen,1048576);else waitLog(frozen,1);
    if(orphan){if(name.equals("rootExitDescendant")){long deadline=System.nanoTime()+10_000_000_000L;while(session.status().phase()!=ServerNativeJavaProtocol.Phase.DRAINING){if(System.nanoTime()>deadline)throw new IOException("NATIVE46_DESCENDANT_NOT_OBSERVED");Thread.sleep(10);}}session.disconnect(15000);}
    else{session.stop();if(forced)session.force(true);session.awaitEmpty(15000);session.finish();require(Boolean.TRUE.equals(session.snapshot().get("gracefulDiagnosticComplete"))!=forced,"NATIVE46_COMPLETION_CLASSIFICATION");}
    receipt(frozen,orphan,forced,!name.equals("rootExitDescendant"));long bytes=Files.size(frozen.file().getParent().resolve("child-output.log"));require(bytes<=1048576&&bytes>0,"NATIVE46_LOG_CAP");if(name.equals("flood"))require(bytes==1048576,"NATIVE46_FLOOD_NOT_DRAINED");results.put(name,true);
   }
  }
  results.put("all_passed",true);results.put("gameLaunched",false);results.put("installAuthority",false);return results;
 }
 private static void waitLog(ServerNativeLaunchCapsule.Frozen frozen,long minimum)throws Exception {
  Path log=frozen.file().getParent().resolve("child-output.log");long deadline=System.nanoTime()+15_000_000_000L;
  while(!Files.exists(log)||Files.size(log)<minimum){if(System.nanoTime()>deadline)throw new IOException("NATIVE46_PROBE_LOG_TIMEOUT");Thread.sleep(10);}
 }
 public static void main(String[] args){try{if(args.length!=2)throw new IOException("Usage: HELPER NEW_DIRECTORY");System.out.println(ServerJson.object(demo(Path.of(args[0]),Path.of(args[1]))));}catch(Exception bad){System.err.println("Native Java diagnostic: "+bad.getMessage());System.exit(2);}}
}
