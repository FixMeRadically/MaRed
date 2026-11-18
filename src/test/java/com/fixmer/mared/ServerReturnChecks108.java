package com.fixmer.mared;
import com.fixmer.genesis.technology.server.remote.*;
import java.nio.file.*;import java.util.*;import java.util.zip.*;
/** Clock/connection observations are explicit inputs. Actual HMAC owner and Minecraft transport are tested elsewhere. */
public final class ServerReturnChecks108 {
 static int checks;static void ok(boolean v){checks++;if(!v)throw new AssertionError("108 check "+checks);}
 static RestartReturn108 accepted(){var p=new RestartReturn108();UUID id=UUID.randomUUID();ok(p.begin(id,0));p.answer(id,true,1);ok(p.phase()==RestartReturn108.Phase.ARMED);return p;}
 static void policy(){
  var p=new RestartReturn108();p.tick(999999,false,false,false);ok(p.phase()==RestartReturn108.Phase.IDLE);UUID id=UUID.randomUUID();ok(p.begin(id,0));ok(!p.begin(UUID.randomUUID(),1));p.answer(UUID.randomUUID(),true,2);ok(p.phase()==RestartReturn108.Phase.PENDING);p.answer(id,false,3);ok(p.phase()==RestartReturn108.Phase.CANCELLED);p.tick(999999,false,false,false);ok(p.attempts()==0);
  p=new RestartReturn108();p.begin(id,0);p.tick(10000,false,false,false);ok(!p.active());p.answer(id,true,10001);ok(!p.active());
  p=accepted();p.tick(1000,true,true,false);ok(p.phase()==RestartReturn108.Phase.ARMED);p.tick(60001,true,false,false);ok(!p.active());ok(p.attempts()==0);
  p=accepted();p.tick(100,false,false,false);ok(p.phase()==RestartReturn108.Phase.WAITING);p.tick(1100,false,false,true);ok(p.phase()==RestartReturn108.Phase.WAITING);p.tick(1101,false,false,false);ok(p.phase()==RestartReturn108.Phase.CONNECTING);ok(p.attempts()==1);p.tick(1102,false,false,false);ok(p.attempts()==1);ok(!p.attemptTimedOut(16100));ok(p.attemptTimedOut(16101));p.failed(16101);ok(p.phase()==RestartReturn108.Phase.WAITING);p.tick(17100,false,false,false);ok(p.attempts()==1);p.tick(17101,false,false,false);ok(p.attempts()==2);p.tick(17102,false,true,false);ok(p.phase()==RestartReturn108.Phase.COMPLETE);p.failed(17103);ok(p.phase()==RestartReturn108.Phase.COMPLETE);
  p=accepted();p.tick(100,false,false,false);p.tick(30100,false,false,false);ok(p.phase()==RestartReturn108.Phase.PROMPT);ok(p.attempts()==0);p.tick(99999,false,false,false);ok(p.phase()==RestartReturn108.Phase.PROMPT);p.continueWaiting(100000);ok(p.phase()==RestartReturn108.Phase.WAITING);p.tick(100500,false,false,false);ok(p.attempts()==1);p.cancel();p.tick(999999,false,false,false);ok(!p.active());ok(p.attempts()==1);p.failed(999999);ok(!p.active());
 }
 static void report()throws Exception{
  Path dir=Files.createTempDirectory("Genesis return108 "),client=dir.resolve("client.log");Files.writeString(client,"[Genesis return108] phase=WAITING; attempt=1\n--accessToken PRIVATE-TOKEN\n");Path report=ServerDiagnostics105.export(dir,client);
  try(var zip=new ZipFile(report.toFile())){ok(zip.getEntry("client-latest.log")!=null);String text=new String(zip.getInputStream(zip.getEntry("client-latest.log")).readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);ok(text.contains("phase=WAITING"));ok(!text.contains("PRIVATE-TOKEN"));ok(text.contains("[redacted]"));}
  Files.delete(client);report=ServerDiagnostics105.export(dir,client);try(var zip=new ZipFile(report.toFile())){ok(zip.getEntry("client-latest.log")==null);ok(zip.getEntry("README.txt")!=null);}
 }
 public static void main(String[]args)throws Exception{policy();report();System.out.println("Return108 intent/rejection/timeout/backoff/cancel/redacted-client-report checks passed: "+checks+"; game disconnect and connection events are explicit inputs, not Minecraft runtime.");}
}
