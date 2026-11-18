package com.fixmer.genesis.technology.server.process;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import com.fixmer.genesis.technology.server.packages.ProfileCoordinator;

/** Disposable approved-handoff fixture; parent and guardian are this diagnostic JVM. */
public final class ServerNativeBoundCli {
 private ServerNativeBoundCli(){}
 private static void require(boolean yes,String message)throws IOException{if(!yes)throw new IOException(message);}
 public static Map<String,Object> demo(Path helper,Path bundle,String sha,Path root)throws Exception {
  if(!System.getProperty("os.name","").startsWith("Windows"))throw new IOException("NATIVE48_WINDOWS_REQUIRED");
  var evidence=ServerNativeEvidence.read(bundle,sha,helper);
  root=root.toAbsolutePath().normalize();ServerFiles.safe(root);require(!Files.exists(root,LinkOption.NOFOLLOW_LINKS),"NATIVE48_NEW_DIRECTORY_REQUIRED");Files.createDirectory(root);ServerFiles.privateMode(root,true);
  byte[] code;try(var in=ServerNativeJavaProbe.class.getResourceAsStream("ServerNativeJavaProbe.class")){if(in==null)throw new IOException("NATIVE48_PROBE_MISSING");code=in.readNBytes(1024*1024+1);}
  var result=new LinkedHashMap<String,Object>();result.put("schema",48);
  for(String name:List.of("graceful","cancelBeforeStart","forced")){
   Path directory=root.resolve(name);ServerFiles.directory(directory);UUID id=UUID.randomUUID();
   var binding=new ServerNativeOwnerProtocol.Binding(id,1,ServerNativeOwnerProtocol.Binding.fresh().nonce());
   var frozen=ServerNativeLaunchCapsule.probe(directory.resolve("probe"),Path.of(System.getProperty("java.home"),"bin","java.exe"),code,name.equals("forced")?"ignore":"normal",name.equals("forced"),binding);
   try(var coordinator=ProfileCoordinator.openLive(frozen.file().getParent())){
    var permit=coordinator.reserveLaunch(id);Path run=directory.resolve("state/runs").resolve(id.toString());ServerFiles.directory(run);
    var config=new Properties();config.setProperty("javaHome",System.getProperty("java.home"));config.setProperty("workDir",frozen.plan().cwd());config.setProperty("stateDir",directory.resolve("state").toString());for(int i=0;i<frozen.plan().arguments().size();i++)config.setProperty("arg."+i,frozen.plan().arguments().get(i));ServerFiles.write(run.resolve("launch.properties"),config);
    long pid=ProcessHandle.current().pid();String start=ServerFiles.start(ProcessHandle.current());var handoff=ServerLaunchHandoff.create(run,coordinator.snapshot().profile(),permit.epoch(),pid,start);var ready=handoff.ready(pid,start);ServerFiles.write(run.resolve("handoff.ready"),ready);handoff.approve(run,ready,pid);
    var status=new Properties();handoff.stamp(status);status.setProperty("guardian.pid",Long.toString(pid));status.setProperty("guardian.start",start);status.setProperty("phase","AWAITING_APPROVAL");status.setProperty("launchIntent","false");status.setProperty("child.pid","0");ServerFiles.write(run.resolve("status.properties"),status);
    try(var session=ServerNativeBoundSession.prepare(coordinator,permit,run,helper,frozen,evidence)){
     session.connect();
     if(name.equals("cancelBeforeStart")){
      Files.writeString(run.resolve("native.cancel"),"diagnostic cancellation");boolean rejected=false;try{session.start();}catch(IOException expected){rejected=true;}require(rejected,"NATIVE48_CANCEL_IGNORED");session.close();require(Boolean.FALSE.equals(session.snapshot().get("diagnosticComplete")),"NATIVE48_CANCEL_FALSE_SUCCESS");
     }else{
      session.start();Path log=frozen.file().getParent().resolve("child-output.log");long until=System.nanoTime()+15_000_000_000L;while(!Files.exists(log)||Files.size(log)==0){require(System.nanoTime()<until,"NATIVE48_PROBE_LOG_TIMEOUT");Thread.sleep(10);}
      session.stop();if(name.equals("forced"))session.force(true);session.awaitEmpty(15000);
      if(name.equals("forced")){session.close();ServerNativeJavaCli.receipt(frozen,false,true,true);require(Boolean.FALSE.equals(session.snapshot().get("diagnosticComplete")),"NATIVE48_FORCE_FALSE_SUCCESS");}
      else{session.finish();require(Boolean.TRUE.equals(session.snapshot().get("diagnosticComplete")),"NATIVE48_COMPLETION_MISSING");}
     }
     session.close();require(Boolean.FALSE.equals(session.snapshot().get("ownerAlive")),"NATIVE48_OWNER_NOT_EXITED");require(coordinator.requiresReview(),"NATIVE48_PROFILE_WAS_RELEASED");result.put(name,true);
    }
   }
  }
  result.put("all_passed",true);result.put("singleJvmHandoffFixture",true);result.put("gameLaunched",false);result.put("productionLaunchAllowed",false);result.put("installAuthority",false);return result;
 }
 public static void main(String[] args){try{if(args.length!=4)throw new IOException("Usage: HELPER EVIDENCE SHA256 NEW_DIRECTORY");System.out.println(ServerJson.object(demo(Path.of(args[0]),Path.of(args[1]),args[2],Path.of(args[3]))));}catch(Exception bad){System.err.println("Bound diagnostic: "+bad.getMessage());System.exit(2);}}
}
