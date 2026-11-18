#!/usr/bin/env python3
"""Compile the real integrated adapter with explicit deterministic queue/API fixtures, not Minecraft."""
from pathlib import Path
import argparse,os,subprocess,tempfile
SOURCES={
'net/minecraft/client/Minecraft.java':'''package net.minecraft.client;
public final class Minecraft {
 static final Minecraft INSTANCE=new Minecraft();public static Minecraft getInstance(){return INSTANCE;}
 public final java.util.concurrent.BlockingQueue<Runnable> queue=new java.util.concurrent.LinkedBlockingQueue<>();
 public net.minecraft.server.MinecraftServer server;public Player player=new Player();public int stops;
 public static class Player {public java.util.UUID uuid=java.util.UUID.randomUUID();public java.util.UUID getUUID(){return uuid;}}
 public void execute(Runnable r){queue.add(r);}public net.minecraft.server.MinecraftServer getSingleplayerServer(){return server;}
 public void stop(){stops++;}
}''',
'net/minecraft/server/MinecraftServer.java':'''package net.minecraft.server;
public final class MinecraftServer {
 public boolean running=true,published,saveOK=true;public int saves;
 public final java.util.concurrent.BlockingQueue<Runnable> queue=new java.util.concurrent.LinkedBlockingQueue<>();
 public boolean isPublished(){return published;}public boolean isRunning(){return running;}public boolean isDedicatedServer(){return false;}
 public boolean saveEverything(boolean a,boolean b,boolean c){saves++;return saveOK;}public void execute(Runnable r){queue.add(r);}
}''',
'com/fixmer/mared/MaredLang.java':'package com.fixmer.mared;public final class MaredLang{public static String get(String key){return key;}}',
'com/fixmer/mared/dev/client/DevWorldProfile.java':'package com.fixmer.mared.dev.client;public final class DevWorldProfile{public static void restore(){}}',
'com/fixmer/mared/dev/client/DevSessionResume.java':'''package com.fixmer.mared.dev.client;public final class DevSessionResume{public static com.fixmer.mared.dev.storage.DevResumeStore.Target target;public static com.fixmer.mared.dev.storage.DevResumeStore.Target captureCurrent(){return target;}}''',
'com/fixmer/mared/dev/client/AdapterChecks95.java':'''package com.fixmer.mared.dev.client;
import net.minecraft.client.Minecraft;import net.minecraft.server.MinecraftServer;import com.fixmer.mared.technology.server.IntegratedWorld90;import com.fixmer.mared.dev.storage.DevResumeStore;import java.util.concurrent.*;
public final class AdapterChecks95 {
 static int checks;static final Minecraft mc=Minecraft.getInstance();static void ok(boolean v){checks++;if(!v)throw new AssertionError("check "+checks);}
 interface IO{void run()throws Exception;}static void reject(IO action)throws Exception{try{action.run();throw new AssertionError("accepted stale action");}catch(IllegalStateException|ExecutionException expected){checks++;}}
 static void pump(java.util.concurrent.BlockingQueue<Runnable> q)throws Exception{Runnable r=q.poll(4,TimeUnit.SECONDS);if(r==null)throw new AssertionError("fixture queue empty");r.run();}
 static MinecraftServer world(){MinecraftServer s=new MinecraftServer();mc.server=s;mc.player=new Minecraft.Player();DevSessionResume.target=DevResumeStore.Target.local("world");IntegratedWorld90.started(s);return s;}
 static CompletableFuture<DevIntegratedRestart90.Receipt> prepare(java.util.function.BooleanSupplier guard){return CompletableFuture.supplyAsync(()->{try{return DevIntegratedRestart90.prepare(DevSessionResume.target,guard);}catch(Exception e){throw new CompletionException(e);}});}
 static DevIntegratedRestart90.Receipt receipt(MinecraftServer s)throws Exception{var f=prepare(()->true);pump(mc.queue);pump(s.queue);return f.get(4,TimeUnit.SECONDS);}
 public static void main(String[]args)throws Exception{
  var s=world();var r=receipt(s);ok(s.saves==1);DevIntegratedRestart90.authorize95(r);ok(DevIntegratedRestart90.exit95(r));ok(mc.stops==1);ok(!DevIntegratedRestart90.cancel(r));ok(DevIntegratedRestart90.exit95(r));ok(mc.stops==1);
  // Reset EXITING only for the next independent fixture case, never production authority.
  var field=DevIntegratedRestart90.class.getDeclaredField("pending");field.setAccessible(true);field.set(null,null);
  s=world();r=receipt(s);var cancelled=r;ok(DevIntegratedRestart90.cancel(r));reject(()->DevIntegratedRestart90.authorize95(cancelled));reject(()->DevIntegratedRestart90.exit95(cancelled));ok(mc.stops==1);int saves=s.saves;IntegratedWorld90.stopping(s);ok(s.saves==saves);
  s=world();var f=prepare(()->true);pump(mc.queue);ok(DevIntegratedRestart90.cancelPending95());pump(s.queue);var beforeSave=f;reject(()->beforeSave.get(4,TimeUnit.SECONDS));ok(s.saves==0);
  s=world();f=prepare(()->true);while(mc.queue.isEmpty())Thread.yield();ok(DevIntegratedRestart90.cancelPending95());pump(mc.queue);var queued=f;reject(()->queued.get(4,TimeUnit.SECONDS));ok(s.saves==0);ok(s.queue.isEmpty());
  s=world();var current=new java.util.concurrent.atomic.AtomicBoolean(true);f=prepare(current::get);pump(mc.queue);pump(s.queue);r=f.get(4,TimeUnit.SECONDS);current.set(false);var changed=r;reject(()->DevIntegratedRestart90.authorize95(changed));ok(DevIntegratedRestart90.cancel(r));
  s=world();r=receipt(s);mc.player.uuid=java.util.UUID.randomUUID();var player=r;reject(()->DevIntegratedRestart90.authorize95(player));ok(DevIntegratedRestart90.cancel(r));
  s=world();r=receipt(s);world();var replaced=r;reject(()->DevIntegratedRestart90.authorize95(replaced));ok(DevIntegratedRestart90.cancel(r));
  s=world();s.saveOK=false;f=prepare(()->true);pump(mc.queue);pump(s.queue);var failed=f;reject(()->failed.get(4,TimeUnit.SECONDS));ok(mc.stops==1);
  DevIntegratedRestart90.cancelPending95();s=world();s.published=true;f=prepare(()->true);pump(mc.queue);var lan=f;reject(()->lan.get(4,TimeUnit.SECONDS));ok(s.saves==0);
  System.out.println("Restart95 real adapter/queued-cancel/context checks passed: "+checks+"; deterministic API fixtures, not Minecraft runtime.");
 }
}'''
}
def main():
 p=argparse.ArgumentParser();p.add_argument('--javac',required=True);p.add_argument('--java',required=True);p.add_argument('--classes',required=True,type=Path);a=p.parse_args();root=Path(__file__).resolve().parents[2]
 with tempfile.TemporaryDirectory(prefix='Genesis adapter95 ') as temp:
  folder=Path(temp);files=[]
  for rel,text in SOURCES.items():
   file=folder/rel;file.parent.mkdir(parents=True,exist_ok=True);file.write_text(text);files.append(str(file))
  for rel in ['com/fixmer/mared/dev/client/DevIntegratedRestart90.java','com/fixmer/mared/technology/server/IntegratedWorld90.java','com/fixmer/mared/dev/storage/DevResumeStore.java']:files.append(str(root/'src/main/java'/rel))
  subprocess.run([a.javac,'-cp',str(a.classes.resolve()),'-d',str(folder),*files],check=True,timeout=60)
  subprocess.run([a.java,'-cp',str(folder)+os.pathsep+str(a.classes.resolve()),'com.fixmer.mared.dev.client.AdapterChecks95'],check=True,timeout=30)
if __name__=='__main__':main()
