package com.fixmer.genesis.technology.server.process;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import com.fixmer.genesis.technology.server.packages.ProfileCoordinator;

/** Real native owner + metadata-only Reporter harness. No game or server installation. */
public final class ServerNativeLifecycleCli {
 private ServerNativeLifecycleCli(){}
 private static void require(boolean yes,String message)throws IOException{if(!yes)throw new IOException(message);}
 static Map<String,Object> demo(Path helper,Path bundle,String sha,Path root)throws Exception {
  if(!System.getProperty("os.name","").startsWith("Windows"))throw new IOException("LIFECYCLE50_WINDOWS_REQUIRED");
  var evidence=ServerNativeEvidence.read(bundle,sha,helper);root=root.toAbsolutePath().normalize();ServerFiles.safe(root);require(!Files.exists(root,LinkOption.NOFOLLOW_LINKS),"LIFECYCLE50_NEW_DIRECTORY_REQUIRED");Files.createDirectory(root);ServerFiles.privateMode(root,true);
  var results=new LinkedHashMap<String,Object>();results.put("schema",50);
  for(String name:List.of("graceful","saveFailure","stopCrash","cancellation")){
   String mode=switch(name){case "saveFailure"->"save-fail";case "stopCrash"->"stop-crash";case "cancellation"->"stall";default->"normal";};
   Path directory=root.resolve(name);ServerFiles.directory(directory);UUID id=UUID.randomUUID();Path run=directory.resolve("state/runs").resolve(id.toString());ServerFiles.directory(run);
   var binding=new ServerNativeOwnerProtocol.Binding(id,1,ServerNativeOwnerProtocol.Binding.fresh().nonce());var frozen=ServerNativeLaunchCapsule.lifecycleProbe(directory.resolve("probe"),Path.of(System.getProperty("java.home"),"bin","java.exe"),mode,binding,run);
   try(var coordinator=ProfileCoordinator.openLive(frozen.file().getParent())){
    var permit=coordinator.reserveLaunch(id);var config=new Properties();config.setProperty("javaHome",System.getProperty("java.home"));config.setProperty("workDir",frozen.plan().cwd());config.setProperty("stateDir",directory.resolve("state").toString());for(int i=0;i<frozen.plan().arguments().size();i++)config.setProperty("arg."+i,frozen.plan().arguments().get(i));ServerFiles.write(run.resolve("launch.properties"),config);
    long pid=ProcessHandle.current().pid();String start=ServerFiles.start(ProcessHandle.current());var handoff=ServerLaunchHandoff.create(run,coordinator.snapshot().profile(),permit.epoch(),pid,start);var ready=handoff.ready(pid,start);ServerFiles.write(run.resolve("handoff.ready"),ready);handoff.approve(run,ready,pid);var status=new Properties();handoff.stamp(status);status.setProperty("guardian.pid",Long.toString(pid));status.setProperty("guardian.start",start);status.setProperty("phase","AWAITING_APPROVAL");status.setProperty("launchIntent","false");status.setProperty("child.pid","0");ServerFiles.write(run.resolve("status.properties"),status);ServerLifecycleChannel.prepare(run);
    var scope=new Properties();scope.setProperty("schema","50");scope.setProperty("scope","DIAGNOSTIC_NO_GAME");scope.setProperty("run",id.toString());ServerFiles.write(run.resolve("probe.scope50"),scope);
    try(var session=ServerNativeBoundSession.prepare(coordinator,permit,run,helper,frozen,evidence)){
     session.connect();session.start();long until=System.nanoTime()+10_000_000_000L;
     while(!Files.exists(run.resolve("lifecycle.status"))||!ServerFiles.read(run.resolve("lifecycle.status")).getProperty("phase").equals("READY")){require(session.status().phase()==ServerNativeJavaProtocol.Phase.RUNNING,"LIFECYCLE50_ROOT_EXITED_BEFORE_READY");require(System.nanoTime()<until,"LIFECYCLE50_READY_TIMEOUT");Thread.sleep(20);}
     session.beginSaveStop(UUID.randomUUID(),1,true);
     if(name.equals("cancellation")){session.cancelSaveStop();require(!Files.exists(run.resolve("diagnostic-save50")),"LIFECYCLE50_STALL_SAVED");}
     else{
      boolean rejected=false;until=System.nanoTime()+15_000_000_000L;
      try{while(!session.pollSaveStop()){require(System.nanoTime()<until,"LIFECYCLE50_STOP_TIMEOUT");Thread.sleep(20);}}catch(IOException expected){rejected=true;}
      if(name.equals("graceful")){require(!rejected,"LIFECYCLE50_GRACEFUL_REJECTED");session.finishSaveStop();require(Boolean.TRUE.equals(session.snapshot().get("diagnosticComplete")),"LIFECYCLE50_REVIEW_MISSING");require(ServerNativeStopBarrier.inspect(run).get("reviewComplete").equals(true),"LIFECYCLE50_REVIEW_NOT_RECORDED");}
      else{require(rejected,"LIFECYCLE50_FAILURE_ACCEPTED");require(Files.isRegularFile(run.resolve("diagnostic-save50")),"LIFECYCLE50_SAVE_NOT_ATTEMPTED");if(name.equals("stopCrash")){require(Files.isRegularFile(run.resolve("diagnostic-stop50")),"LIFECYCLE50_STOP_NOT_ATTEMPTED");var stopped=session.status();require(stopped.phase()==ServerNativeJavaProtocol.Phase.EMPTY&&stopped.exitCode()==9,"LIFECYCLE50_CRASH_NOT_OBSERVED");}else{require(!Files.exists(run.resolve("diagnostic-stop50")),"LIFECYCLE50_FAILED_SAVE_STOPPED");require(ServerFiles.read(run.resolve("lifecycle.status")).getProperty("outcome").equals("FAILED"),"LIFECYCLE50_SAVE_FAILURE_NOT_REPORTED");}}
     }
     if(!name.equals("graceful"))require(Boolean.FALSE.equals(session.snapshot().get("diagnosticComplete")),"LIFECYCLE50_FALSE_COMPLETION");session.close();require(Boolean.FALSE.equals(session.snapshot().get("ownerAlive")),"LIFECYCLE50_OWNER_STILL_ALIVE");require(coordinator.requiresReview(),"LIFECYCLE50_PROFILE_RELEASED");results.put(name,true);
    }
   }
  }
  results.put("all_passed",true);results.put("singleJvmHandoffFixture",true);results.put("saveCallback","diagnostic_marker_only");results.put("gameLaunched",false);results.put("worldSaveVerified",false);results.put("productionLaunchAllowed",false);results.put("installAuthority",false);return results;
 }
 public static void main(String[] args){try{if(args.length!=4)throw new IOException("Usage: HELPER EVIDENCE SHA256 NEW_DIRECTORY");System.out.println(ServerJson.object(demo(Path.of(args[0]),Path.of(args[1]),args[2],Path.of(args[3]))));}catch(Exception bad){System.err.println("Native lifecycle harness: "+bad.getMessage());System.exit(2);}}
}
