from pathlib import Path
import tempfile
import subprocess
r=Path(__file__).resolve().parents[2]
with tempfile.TemporaryDirectory(prefix="mared-audit144-state-") as temp:
    w=Path(temp)
    stubs={
    'Minecraft.java':'''package net.minecraft.client;public class Minecraft{static final Minecraft INSTANCE=new Minecraft();public static Minecraft getInstance(){return INSTANCE;}public net.minecraft.client.player.LocalPlayer player;public GameMode gameMode=new GameMode();public static class GameMode{public Mode getPlayerMode(){return new Mode();}}public static class Mode{public int getId(){return 0;}}}''',
    'LocalPlayer.java':'''package net.minecraft.client.player;public class LocalPlayer{public double x,y,z;public int experienceLevel,totalExperience;public double getX(){return x;}public double getY(){return y;}public double getZ(){return z;}public float getHealth(){return 20;}public Food getFoodData(){return new Food();}public Item getMainHandItem(){return new Item();}public Inv getInventory(){return new Inv();}public static class Food{public int getFoodLevel(){return 20;}}public static class Item{public int getCount(){return 1;}}public static class Inv{public int selected;}}''',
    'MaredEventRegistry.java':'''package com.fixmer.mared.commands.events;public class MaredEventRegistry{public static int getVersion(){return 1;}public static boolean has(String s){return s.equals("player_move");}}''',
    'StateProbe144.java':'''import com.fixmer.mared.commands.engine.MaredStateTracker;import net.minecraft.client.Minecraft;import net.minecraft.client.player.LocalPlayer;
    public class StateProbe144{public static void main(String[]a){var mc=Minecraft.getInstance();mc.player=new LocalPlayer();MaredStateTracker.reset();MaredStateTracker.poll();for(int i=0;i<3;i++){mc.player.x+=.04;var diff=MaredStateTracker.poll();if(i==2){if(!diff.data.containsKey("dx"))throw new AssertionError("slow movement never emits");if(Math.abs(((Number)diff.data.get("dx")).doubleValue()-.12)>.00001)throw new AssertionError("fractional delta lost");}}mc.player=null;MaredStateTracker.poll();mc.player=new LocalPlayer();mc.player.x=100;var diff=MaredStateTracker.poll();if(diff.hasChanges)throw new AssertionError("new player compared against prior session");System.out.println("StateProbe144: 3 passed");}}'''
    }
    paths=[r/'src/main/java/com/fixmer/mared/commands/engine/MaredStateTracker.java']
    for n,s in stubs.items():p=w/n;p.write_text(s);paths.append(p)
    p=subprocess.run(['java','com.sun.tools.javac.Main','-encoding','UTF-8','-d',str(w/'classes'),*map(str,paths)],capture_output=True,text=True);(w/'compile.log').write_text(p.stdout+p.stderr);p.check_returncode()
    p=subprocess.run(['java','-cp',str(w/'classes'),'StateProbe144'],capture_output=True,text=True);(w/'results.log').write_text(p.stdout+p.stderr);print(p.stdout,p.stderr);p.check_returncode()
