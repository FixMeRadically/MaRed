#!/usr/bin/env python3
"""Compile actual native adapters against explicit scheduling/game fixtures. No Minecraft runtime claim."""
from pathlib import Path
import argparse,subprocess,tempfile,os
STUBS={
'com/fixmer/mared/MaredLang.java':'package com.fixmer.mared;public class MaredLang{public static String get(String s){return s;}}',
'com/fixmer/genesis/supervisor/GameSessionClient.java':'''package com.fixmer.genesis.supervisor;public class GameSessionClient{public static boolean supervised=true;public static String phase="";public static boolean supervised90(){return supervised;}public static void integrated90(String e,String p){phase=p;}}''',
'net/minecraft/server/MinecraftServer.java':'''package net.minecraft.server;import java.util.concurrent.*;public class MinecraftServer{public boolean published,dedicated,running=true,save=true,fail;public int saves;public final java.util.List<Runnable> queued=new java.util.concurrent.CopyOnWriteArrayList<>();public boolean queue;public boolean isDedicatedServer(){return dedicated;}public boolean isRunning(){return running;}public boolean isPublished(){return published;}public boolean saveEverything(boolean a,boolean b,boolean c){saves++;if(fail)throw new IllegalStateException();return save;}public void execute(Runnable r){if(queue)queued.add(r);else r.run();}}''',
'net/minecraft/client/Minecraft.java':'''package net.minecraft.client;import net.minecraft.server.MinecraftServer;public class Minecraft{public static final Minecraft I=new Minecraft();public MinecraftServer server;public static Minecraft getInstance(){return I;}public MinecraftServer getSingleplayerServer(){return server;}public void execute(Runnable r){r.run();}}''',
'com/fixmer/mared/dev/client/DevSessionResume.java':'''package com.fixmer.mared.dev.client;import com.fixmer.mared.dev.storage.DevResumeStore;public class DevSessionResume{public static DevResumeStore.Target target;public static DevResumeStore.Target captureCurrent(){return target;}}'''}
def main():
 p=argparse.ArgumentParser();p.add_argument('project',type=Path);p.add_argument('java',type=Path);a=p.parse_args();project=a.project.resolve();java=a.java.resolve()
 with tempfile.TemporaryDirectory(prefix='integrated90-fixture-') as raw:
  root=Path(raw);sources=[]
  for rel,s in STUBS.items():
   f=root/rel;f.parent.mkdir(parents=True,exist_ok=True);f.write_text(s);sources.append(f)
  for rel in ['src/main/java/com/fixmer/mared/dev/storage/DevResumeStore.java','src/main/java/com/fixmer/mared/dev/client/DevIntegratedRestart90.java','src/main/java/com/fixmer/mared/technology/server/IntegratedWorld90.java','verification/server59/IntegratedAdapterChecks90.java']:sources.append(project/rel)
  classes=root/'classes';classes.mkdir()
  subprocess.run([str(java),'-m','jdk.compiler/com.sun.tools.javac.Main','-encoding','UTF-8','-d',str(classes),*map(str,sources)],check=True,timeout=60)
  subprocess.run([str(java),'-cp',str(classes),'com.fixmer.mared.dev.client.IntegratedAdapterChecks90'],check=True,timeout=15)
if __name__=='__main__':main()
