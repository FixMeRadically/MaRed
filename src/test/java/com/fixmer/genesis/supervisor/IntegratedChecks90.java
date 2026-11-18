package com.fixmer.genesis.supervisor;
import java.nio.file.*;import java.io.*;import java.util.*;
/** Actual production session parser and state machine; OS life is an explicit fixture. */
public final class IntegratedChecks90 {
 static int n;static void ok(boolean v){n++;if(!v)throw new AssertionError("check "+n);}static void rejects(Runnable r){n++;try{r.run();throw new AssertionError("accepted invalid input "+n);}catch(IllegalArgumentException|IllegalStateException expected){}}
 static String epoch(){return UUID.randomUUID().toString();}
 static class Fixture implements AutoCloseable {
  Path dir=Files.createTempDirectory("integrated90-");Path file=dir.resolve("session.properties");String launch=epoch();GameSession.Life life=GameSession.Life.ALIVE;long seq;GameSession session=new GameSession(file,launch,60000,3000,(p,s)->life);
  Fixture()throws Exception{}
  void write(String e,String phase,boolean normal)throws Exception{Properties p=new Properties();p.setProperty("schema","1");p.setProperty("launch",launch);p.setProperty("pid","123");p.setProperty("processStart","99");p.setProperty("sequence",Long.toString(++seq));p.setProperty("state",normal?"CLOSING":"RUNNING");p.setProperty("normalClose",Boolean.toString(normal));if(e!=null)p.setProperty("integratedEpoch90",e);if(phase!=null)p.setProperty("integrated90",phase);try(Writer w=Files.newBufferedWriter(file)){p.store(w,"");}session.poll();}
  public void close()throws Exception{Files.deleteIfExists(file);Files.delete(dir);}
 }
 public static void main(String[]args)throws Exception{
  var s=new IntegratedState90();s.requireRestart(false,false);ok(!s.present());String a=epoch(),b=epoch();
  s.accept(a,"ACTIVE");ok(s.present()&&!s.stopped());rejects(()->s.requireRestart(true,true));s.accept(a,"SAVED");rejects(()->s.accept(a,"ACTIVE"));rejects(()->s.requireRestart(true,true));s.accept(a,"STOPPED");rejects(()->s.requireRestart(false,true));rejects(()->s.requireRestart(true,false));s.requireRestart(true,true);ok(s.stopped());rejects(()->s.accept(a,"SAVED"));s.accept(b,"ACTIVE");ok(!s.stopped());rejects(()->s.requireRestart(true,true));s.accept(b,"FAILED");rejects(()->s.accept(b,"STOPPED"));rejects(()->s.accept("bad","ACTIVE"));rejects(()->s.accept(epoch(),"READY"));
  try(var f=new Fixture()){f.write(a,"ACTIVE",false);ok(f.session.integrated90());rejects(f.session::requireIntegratedRestart90);f.write(a,"SAVED",true);f.life=GameSession.Life.DEAD;rejects(f.session::requireIntegratedRestart90);f.write(a,"STOPPED",true);f.session.requireIntegratedRestart90();ok(f.session.confirmedExit());}
  try(var f=new Fixture()){f.write(a,"STOPPED",true);rejects(f.session::requireIntegratedRestart90);f.life=GameSession.Life.DEAD;f.session.requireIntegratedRestart90();ok(true);}
  try(var f=new Fixture()){f.write(a,"STOPPED",false);f.life=GameSession.Life.DEAD;rejects(f.session::requireIntegratedRestart90);}
  try(var f=new Fixture()){f.write(a,"STOPPED",true);f.write(a,"ACTIVE",true);f.life=GameSession.Life.DEAD;rejects(f.session::requireIntegratedRestart90);}
  try(var f=new Fixture()){f.write(a,"STOPPED",true);f.write(null,null,true);f.life=GameSession.Life.DEAD;rejects(f.session::requireIntegratedRestart90);}
  try(var f=new Fixture()){f.write(a,"INVALID",true);ok(f.session.integrated90());f.life=GameSession.Life.DEAD;rejects(f.session::requireIntegratedRestart90);}
  try(var f=new Fixture()){f.write(a,null,true);ok(f.session.integrated90());f.life=GameSession.Life.DEAD;rejects(f.session::requireIntegratedRestart90);}
  try(var f=new Fixture()){f.write(a,"STOPPED",false);f.write(b,"ACTIVE",false);f.life=GameSession.Life.DEAD;rejects(f.session::requireIntegratedRestart90);}
  try(var f=new Fixture()){f.write(null,null,false);ok(!f.session.integrated90());f.session.requireIntegratedRestart90();}
  System.out.println("Integrated90 actual state/session/evidence checks passed: "+n+"; OS liveness supplied explicitly, no Minecraft or Windows process.");
 }
}
