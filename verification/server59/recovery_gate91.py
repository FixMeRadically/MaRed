#!/usr/bin/env python3
"""Actual client gate + deployment journal against explicit client-thread scheduling stubs."""
from pathlib import Path
import argparse,tempfile,subprocess
STUBS={
'com/fixmer/mared/dev/DevTelemetry.java':'package com.fixmer.mared.dev;public class DevTelemetry {public static int marks;public static void mark(String s){if(s.equals("return_failed"))marks++;}}',
'com/fixmer/mared/Mared.java':'package com.fixmer.mared;public class Mared {public static final String MOD_ID="mared";}',
'com/fixmer/mared/MaredLang.java':'package com.fixmer.mared;public class MaredLang {public static String get(String s){return s;}}',
'net/minecraft/client/gui/screens/Screen.java':'package net.minecraft.client.gui.screens;public class Screen{}',
'net/minecraft/client/gui/screens/TitleScreen.java':'package net.minecraft.client.gui.screens;public class TitleScreen extends Screen{}',
'net/minecraft/client/Minecraft.java':'''package net.minecraft.client;import net.minecraft.client.gui.screens.Screen;public class Minecraft {public static final Minecraft I=new Minecraft();public java.io.File gameDirectory;public Screen screen;public Object server;public java.util.Queue<Runnable> queue=new java.util.concurrent.ConcurrentLinkedQueue<>();public static Minecraft getInstance(){return I;}public Object getSingleplayerServer(){return server;}public Object getOverlay(){return null;}public void setScreen(Screen s){screen=s;}public void execute(Runnable r){queue.add(r);}}''',
'com/fixmer/mared/dev/client/DevRestartService.java':'package com.fixmer.mared.dev.client;public class DevRestartService {public static boolean isUnderSupervisor(){return false;}}',
'com/fixmer/mared/dev/client/DevQuickStart.java':'package com.fixmer.mared.dev.client;public class DevQuickStart {public static int suppressed;public static void suppressForSession91(){suppressed++;}}',
'com/fixmer/mared/dev/client/LocalRecoveryScreen91.java':'package com.fixmer.mared.dev.client;import net.minecraft.client.gui.screens.Screen;public class LocalRecoveryScreen91 extends Screen{public LocalRecoveryScreen91(Screen previous){}}',
'net/neoforged/api/distmarker/Dist.java':'package net.neoforged.api.distmarker;public enum Dist {CLIENT,DEDICATED_SERVER}',
'net/neoforged/bus/api/SubscribeEvent.java':'package net.neoforged.bus.api;public @interface SubscribeEvent {}',
'net/neoforged/fml/common/EventBusSubscriber.java':'package net.neoforged.fml.common;import net.neoforged.api.distmarker.Dist;public @interface EventBusSubscriber {String modid();Dist[] value() default {};}',
'net/neoforged/neoforge/client/event/ClientTickEvent.java':'package net.neoforged.neoforge.client.event;public class ClientTickEvent {public static class Post {}}'}
def main():
 p=argparse.ArgumentParser();p.add_argument('project',type=Path);p.add_argument('java',type=Path);p.add_argument('--classes',required=True,type=Path);a=p.parse_args();project=a.project.resolve();java=a.java.resolve();core=a.classes.resolve()
 with tempfile.TemporaryDirectory(prefix='recovery91-gate-') as raw:
  root=Path(raw);sources=[]
  for rel,text in STUBS.items():
   file=root/rel;file.parent.mkdir(parents=True,exist_ok=True);file.write_text(text);sources.append(file)
  for rel in ['src/main/java/com/fixmer/mared/dev/storage/DevResumeStore.java','src/main/java/com/fixmer/mared/dev/client/LocalRecovery91.java','verification/server59/RecoveryGateChecks91.java']:sources.append(project/rel)
  classes=root/'classes';classes.mkdir()
  subprocess.run([str(java),'-m','jdk.compiler/com.sun.tools.javac.Main','-encoding','UTF-8','-cp',str(core),'-d',str(classes),*map(str,sources)],check=True,timeout=60)
  import os
  subprocess.run([str(java),'-cp',str(classes)+os.pathsep+str(core),'com.fixmer.mared.dev.client.RecoveryGateChecks91',str(root/'game')],check=True,timeout=20)
if __name__=='__main__':main()
