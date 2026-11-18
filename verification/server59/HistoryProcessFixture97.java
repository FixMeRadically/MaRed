package com.fixmer.genesis.supervisor;
import java.nio.file.*;import java.util.*;import java.io.*;
/** Actual history writer/reader JVMs only; no game lifecycle or rendering claims. */
public final class HistoryProcessFixture97 {
 public static void main(String[]args)throws Exception{
  Path game=Path.of(args[1]);
  if(args[0].equals("reader")){
   var snapshot=RestartHistory97.read(game);if(snapshot.attempts().isEmpty()||snapshot.cycles().size()!=1||snapshot.unreadable()!=0)throw new AssertionError("snapshot");
   if(!args[2].equals("ANY")&&!snapshot.attempts().get(0).phase().equals(args[2]))throw new AssertionError("phase");
   String report=HistoryPresentation97.clipboard(snapshot,(k,a)->k+Arrays.toString(a));if(report.contains("PRIVATE")||report.contains(game.toString()))throw new AssertionError("privacy");System.out.println("READ97_OK");return;
  }
  String request=UUID.randomUUID().toString();try(var log=new SupervisorLog(game.resolve("fixture97.log"));var input=new BufferedReader(new InputStreamReader(System.in))){
   RestartHistory97.recordAttempt(game,"restart",request,"PREPARED","");RestartMetrics metrics=new RestartMetrics(game);String launch=UUID.randomUUID().toString();metrics.event(launch,"launch_direct_cache",0,log);metrics.event(launch,"mod_loaded",8000,log);metrics.event(launch,"resources_ready",12000,log);metrics.event(launch,"title_ready",12500,log);System.out.println("READY97");System.out.flush();
   for(String line;(line=input.readLine())!=null;){switch(line){case "commit"->RestartHistory97.recordAttempt(game,"restart",request,"COMMITTED","");case "done"->RestartHistory97.recordAttempt(game,"restart",request,"DONE","normal_exit_and_save_confirmed");case "burst"->{for(int i=0;i<160;i++)RestartHistory97.recordAttempt(game,"restart",request,"DONE","normal_exit_and_save_confirmed");}case "exit"->{return;}default->throw new IllegalArgumentException();}System.out.println("UPDATED97");System.out.flush();}
  }
 }
}
